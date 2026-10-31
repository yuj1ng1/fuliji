package io.github.yuj1ng1.mycampus.sync

import android.content.Context
import androidx.work.*
import io.github.yuj1ng1.mycampus.CampusApplication
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.web.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

data class SchoolSyncProgress(val running: Boolean = false, val message: String = "尚未开始自动更新")

class SchoolAutoSync(private val context: Context, private val repository: CampusRepository,
                     private val options: SchoolPreferences, secrets: SchoolSecrets,
                     private val fetch: suspend (CampusSource, String?) -> Capture = SchoolSyncEngine(context, options, secrets)::fetch) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(SchoolSyncProgress())
    val progress = mutable.asStateFlow()
    private var lastForegroundAttempt = 0L

    // Settings holds a browser lease first, then waits until an in-flight refresh has exited.
    // This prevents an old account's response from being saved after its cache is cleared.
    suspend fun editConnection(action: suspend () -> Unit) = mutex.withLock { action() }

    fun configureSchedule() {
        val work = WorkManager.getInstance(context)
        if (!options.current.autoRefresh && !options.current.powerDailyRefresh) { work.cancelUniqueWork("rongji-school-periodic"); return }
        val request = PeriodicWorkRequestBuilder<SchoolSyncWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        work.enqueueUniquePeriodicWork("rongji-school-periodic", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    suspend fun onForeground() {
        if ((!options.current.autoRefresh && !options.current.powerDailyRefresh) || SchoolBrowserLease.active ||
            System.currentTimeMillis() - lastForegroundAttempt in 0 until 30 * 60_000L) return
        lastForegroundAttempt = System.currentTimeMillis()
        refresh(onlyStale = true, scheduled = true)
    }

    suspend fun refresh(only: CampusSource? = null, onlyStale: Boolean = false, requestedScope: String? = null, scheduled: Boolean = false) {
        if (SchoolBrowserLease.active || !mutex.tryLock()) return
        try {
            val snapshots = repository.snapshots.first()
            val powerSources = listOf(CampusSource.POWER, CampusSource.POWER_USAGE)
            val powerRoom = requestedScope?.takeIf { only in powerSources } ?: snapshots.firstOrNull { it.source in powerSources.map(CampusSource::name) }?.scope
            val targets = when {
                only in powerSources -> if (powerRoom != null) powerSources else emptyList()
                only != null -> listOf(only)
                else -> CampusSource.entries.filter { source -> source.isAcademic && (!scheduled || options.current.autoRefresh) && snapshots.any { it.source == source.name } } +
                    if (powerRoom != null && (!scheduled || options.current.powerDailyRefresh)) powerSources else emptyList()
            }
            if (targets.isEmpty()) { mutable.value = SchoolSyncProgress(false, "请先连接并保存需要更新的校园数据。"); return }
            mutable.value = SchoolSyncProgress(true, "正在更新已接入的校园数据…")
            var updated = 0
            var attention = 0
            for (source in targets) {
                val scope = if (source.isAcademic) requestedScope else powerRoom
                val previous = snapshots.firstOrNull { it.source == source.name && (scope == null || it.scope == scope) }
                if (onlyStale && previous != null && System.currentTimeMillis() - previous.capturedAt < 30 * 60_000L) continue
                try {
                    val capture = fetch(source, scope ?: previous?.scope)
                    if (SchoolBrowserLease.active) break
                    repository.saveCapture(source, capture)
                    updated++
                } catch (_: SchoolBrowserBusy) { break }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (problem: Exception) {
                    val captureProblem = problem as? CaptureException
                    repository.recordFailure(source, captureProblem?.state ?: "UNRECOGNIZED", captureProblem?.userMessage ?: "本次更新未完成，请本人检查学校连接；原有数据已保留。")
                    attention++
                }
            }
            mutable.value = SchoolSyncProgress(false, if (attention == 0) "本次已更新 " + updated + " 项。" else "已更新 " + updated + " 项，另有 " + attention + " 项需要本人检查。")
        } finally { if (mutable.value.running) mutable.value = SchoolSyncProgress(false, "本次更新已结束，可在对应页面查看状态。"); mutex.unlock() }
    }
}

class SchoolSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CampusApplication
        if ((app.schoolOptions.current.autoRefresh || app.schoolOptions.current.powerDailyRefresh) && !SchoolBrowserLease.active) app.schoolSync.refresh(onlyStale = true, scheduled = true)
        // An authentication problem is recorded in the app, not retried repeatedly by WorkManager.
        return Result.success()
    }
}
