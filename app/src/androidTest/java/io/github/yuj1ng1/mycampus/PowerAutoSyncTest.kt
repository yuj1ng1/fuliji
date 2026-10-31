package io.github.yuj1ng1.mycampus

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.*
import io.github.yuj1ng1.mycampus.sync.SchoolAutoSync
import io.github.yuj1ng1.mycampus.web.SchoolSyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PowerAutoSyncTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun withSync(room: String, block: suspend (SchoolAutoSync, CampusRepository, CampusDatabase, SyntheticPowerSite) -> Unit) = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, CampusDatabase::class.java).build()
        val repository = CampusRepository(database)
        val namespace = "synthetic-power-auto"
        val options = SchoolPreferences(context, namespace).apply { update(autoRefresh = false, powerDailyRefresh = true) }
        val secrets = SchoolSecrets(context, namespace + ".secrets")
        val site = SyntheticPowerSite()
        val engine = SchoolSyncEngine(context, options, secrets, site::createView)
        val sync = SchoolAutoSync(context, repository, options, secrets, engine::fetch)
        try {
            val old = System.currentTimeMillis() - 2 * 86_400_000L
            database.dao().putSnapshot(SavedSnapshot("POWER|$room", "POWER", room,
                CaptureReader.encode(listOf(DisplayRow(room, "77.70 度"))), 1, old))
            block(sync, repository, database, site)
        } finally { database.close(); context.deleteSharedPreferences(namespace) }
    }

    @Test fun scheduledRefreshReselectsSameRoomAndUpdatesBothDatasetsAcrossDays() = withSync("合成校区 示例楼 101") { sync, repository, database, site ->
        sync.refresh(onlyStale = true, scheduled = true)
        var snapshots = repository.snapshots.first()
        assertEquals(2, snapshots.size)
        assertEquals(listOf("getelec", "getelecday"), site.requests.toList())
        assertTrue(snapshots.all { it.scope == site.room })
        assertEquals("9.75 度", CaptureReader.decode(snapshots.single { it.source == "POWER" }.payload).single().detail)
        val usage = CaptureReader.decode(snapshots.single { it.source == "POWER_USAGE" }.payload)
        assertEquals(3, usage.size)
        assertEquals("0", usage[1].detail)
        sync.refresh(onlyStale = true, scheduled = true)
        assertEquals(2, site.requests.size)
        for (saved in snapshots) database.dao().putSnapshot(saved.copy(capturedAt = saved.capturedAt - 86_400_000L))
        sync.refresh(onlyStale = true, scheduled = true)
        assertEquals(listOf("getelec", "getelecday", "getelec", "getelecday"), site.requests.toList())
        snapshots = repository.snapshots.first()
        assertTrue(snapshots.all { System.currentTimeMillis() - it.capturedAt < 60_000 })
        assertTrue(repository.connections.first().all { it.state == "SUCCESS" })
    }

    @Test fun missingRoomNeverFallsBackToAnotherRoomOrReplacesOldBalance() = withSync("合成校区 示例楼 不存在") { sync, repository, _, site ->
        val old = repository.snapshots.first().single()
        sync.refresh(onlyStale = true, scheduled = true)
        assertEquals(old, repository.snapshots.first().single())
        assertTrue(site.requests.isEmpty())
        assertEquals(2, repository.connections.first().size)
        assertTrue(repository.connections.first().all { it.state == "NEEDS_SELECTION" })
    }

    @Test fun disablingPowerDailyRefreshPreventsPowerRequestsEvenWhenAcademicRefreshIsOn() = withSync("合成校区 示例楼 101") { sync, repository, _, site ->
        SchoolPreferences(context, "synthetic-power-auto").update(autoRefresh = true, powerDailyRefresh = false)
        // Each preferences instance keeps a state flow, so use a coordinator bound to the updated instance.
        val options = SchoolPreferences(context, "synthetic-power-auto")
        val secrets = SchoolSecrets(context, "synthetic-power-auto.secrets")
        val engine = SchoolSyncEngine(context, options, secrets, site::createView)
        val configured = SchoolAutoSync(context, repository, options, secrets, engine::fetch)
        val old = repository.snapshots.first().single()
        configured.refresh(onlyStale = true, scheduled = true)
        assertEquals(old, repository.snapshots.first().single())
        assertTrue(site.requests.isEmpty())
    }
}
