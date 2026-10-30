package io.github.yuj1ng1.mycampus.data

import androidx.room.withTransaction
import io.github.yuj1ng1.mycampus.domain.*
import java.util.UUID

class CampusRepository(private val database: CampusDatabase) {
    private val dao = database.dao()
    val snapshots = dao.snapshots()
    val connections = dao.connections()
    val ledger = dao.ledger()

    suspend fun saveCapture(source: CampusSource, capture: Capture) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.putSnapshot(SavedSnapshot(source.name + "|" + capture.scope, source.name, capture.scope, capture.payload, capture.rows.size, now))
            dao.putConnection(ConnectionState(source.name, "SUCCESS", if (capture.empty) "当前查询暂无记录" else "已保存本次查询结果", now))
        }
    }

    suspend fun recordFailure(source: CampusSource, state: String, message: String) {
        // A failed attempt never changes a successful snapshot or its timestamp.
        dao.putConnection(ConnectionState(source.name, state, message.take(250), System.currentTimeMillis()))
    }

    suspend fun saveCandidate(candidate: PaymentCandidate) {
        dao.insertEntry(LedgerEntry(
            candidate.eventId, candidate.sourceName, candidate.kind, candidate.amountCents,
            candidate.notificationTime, "NOTIFICATION", "", "DRAFT",
        ))
    }

    suspend fun addExpense(cents: Long, note: String) {
        require(cents > 0)
        dao.insertEntry(LedgerEntry(UUID.randomUUID().toString(), "手动", "EXPENSE", cents,
            System.currentTimeMillis(), "USER", note.trim().take(100), "CONFIRMED"))
    }

    suspend fun confirmExpense(id: String, cents: Long, note: String) {
        require(cents > 0)
        dao.confirmExpense(id, cents, note.trim().take(100))
    }

    suspend fun deleteEntry(id: String) = dao.deleteEntry(id)
}
