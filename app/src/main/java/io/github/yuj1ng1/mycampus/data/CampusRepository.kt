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
            val id = source.name + "|" + capture.scope
            val rows = if (source == CampusSource.POWER_USAGE) {
                // A new month can omit the end of the previous month. Preserve already-known days for this room only.
                (dao.snapshot(id)?.let { CaptureReader.decode(it.payload) }.orEmpty() + capture.rows)
                    .associateBy { it.title }.values.sortedBy { it.title }.takeLast(366)
            } else capture.rows
            dao.putSnapshot(SavedSnapshot(id, source.name, capture.scope, CaptureReader.encode(rows), rows.size, now))
            dao.putConnection(ConnectionState(source.name, "SUCCESS", if (capture.empty) "当前查询暂无记录" else "已保存本次查询结果", now))
        }
    }

    suspend fun recordFailure(source: CampusSource, state: String, message: String) {
        // A failed attempt never changes a successful snapshot or its timestamp.
        dao.putConnection(ConnectionState(source.name, state, message.take(250), System.currentTimeMillis()))
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

    suspend fun clearCampusCache() = database.withTransaction { dao.clearSnapshots(); dao.clearConnections() }
    suspend fun clearPowerCache() = database.withTransaction { dao.clearPowerSnapshots(); dao.clearPowerConnections() }
}
