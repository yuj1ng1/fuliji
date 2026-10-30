package io.github.yuj1ng1.mycampus

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.yuj1ng1.mycampus.data.*
import io.github.yuj1ng1.mycampus.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var database: CampusDatabase
    private lateinit var repository: CampusRepository

    @Before fun openIsolatedDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, CampusDatabase::class.java,
        ).build()
        repository = CampusRepository(database)
    }
    @After fun closeDatabase() { database.close() }

    @Test fun failurePreservesSuccessfulSnapshotAndTimestamp() = runBlocking {
        val rows = listOf(DisplayRow("示例课程", "合成测试数据"))
        repository.saveCapture(CampusSource.COURSES, Capture("示例学期", rows, CaptureReader.encode(rows), false))
        val before = repository.snapshots.first().single()
        repository.recordFailure(CampusSource.COURSES, "NEEDS_LOGIN", "需要重新登录")
        assertEquals(before, repository.snapshots.first().single())
        assertEquals("NEEDS_LOGIN", repository.connections.first().single().state)
    }
    @Test fun twoQueryScopesRemainSeparate() = runBlocking {
        val rowsA = listOf(DisplayRow("示例房间A", "10.00 度"))
        val rowsB = listOf(DisplayRow("示例房间B", "20.00 度"))
        repository.saveCapture(CampusSource.POWER, Capture("示例房间A", rowsA, CaptureReader.encode(rowsA), false))
        repository.saveCapture(CampusSource.POWER, Capture("示例房间B", rowsB, CaptureReader.encode(rowsB), false))
        assertEquals(setOf("示例房间A", "示例房间B"), repository.snapshots.first().map { it.scope }.toSet())
    }

    @Test fun monthlyUsageMergesKnownDaysOnlyWithinTheSameRoom() = runBlocking {
        suspend fun save(room: String, vararg days: Pair<String, String>) {
            val rows = days.map { DisplayRow(it.first, it.second) }
            repository.saveCapture(CampusSource.POWER_USAGE, Capture(room, rows, CaptureReader.encode(rows), false))
        }
        save("示例房间A", "2026-09-30" to "1", "2026-10-01" to "2")
        save("示例房间B", "2026-09-30" to "99")
        save("示例房间A", "2026-10-01" to "2.5", "2026-10-02" to "0")
        val snapshots = repository.snapshots.first()
        val daysA = CaptureReader.decode(snapshots.single { it.scope == "示例房间A" }.payload)
        assertEquals(listOf(DisplayRow("2026-09-30", "1"), DisplayRow("2026-10-01", "2.5"), DisplayRow("2026-10-02", "0")), daysA)
        assertEquals("99", CaptureReader.decode(snapshots.single { it.scope == "示例房间B" }.payload).single().detail)
    }

    @Test fun clearingSchoolCacheDoesNotRemoveConfirmedLedgerEntries() = runBlocking {
        repository.addExpense(321, "合成测试账目")
        val rows = listOf(DisplayRow("示例课程", "示例内容"))
        repository.saveCapture(CampusSource.COURSES, Capture("示例学期", rows, CaptureReader.encode(rows), false))
        repository.clearCampusCache()
        assertTrue(repository.snapshots.first().isEmpty())
        assertTrue(repository.connections.first().isEmpty())
        assertEquals("合成测试账目", repository.ledger.first().single().note)
    }
    @Test fun notificationDuplicateDoesNotOverwriteUserConfirmation() = runBlocking {
        val candidate = PaymentCandidate("synthetic-event-id", "合成支付来源", "EXPENSE", 1230, 1234)
        repository.saveCandidate(candidate)
        repository.saveCandidate(candidate)
        assertEquals(1, repository.ledger.first().size)
        assertEquals("DRAFT", repository.ledger.first().single().status)
        repository.confirmExpense(candidate.eventId, 1300, "本人修正")
        repository.saveCandidate(candidate)
        val saved = repository.ledger.first().single()
        assertEquals("CONFIRMED", saved.status)
        assertEquals(1300L, saved.amountCents)
        assertEquals("本人修正", saved.note)
    }
}
