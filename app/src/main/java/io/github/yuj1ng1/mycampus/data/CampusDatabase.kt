package io.github.yuj1ng1.mycampus.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "campus_snapshots", indices = [Index("source")])
data class SavedSnapshot(
    @PrimaryKey val id: String,
    val source: String,
    val scope: String,
    val payload: String,
    val itemCount: Int,
    val capturedAt: Long,
)

@Entity(tableName = "connection_states")
data class ConnectionState(
    @PrimaryKey val source: String,
    val state: String,
    val message: String,
    val attemptedAt: Long,
)

@Entity(tableName = "ledger_entries")
data class LedgerEntry(
    @PrimaryKey val id: String,
    val source: String,
    val kind: String,
    val amountCents: Long?,
    val occurredAt: Long,
    val timeBasis: String,
    val note: String,
    val status: String,
)

@Dao
interface CampusDao {
    @Query("SELECT * FROM campus_snapshots ORDER BY capturedAt DESC")
    fun snapshots(): Flow<List<SavedSnapshot>>
    @Query("SELECT * FROM campus_snapshots WHERE id = :id LIMIT 1")
    suspend fun snapshot(id: String): SavedSnapshot?
    @Query("SELECT * FROM connection_states")
    fun connections(): Flow<List<ConnectionState>>
    @Query("SELECT * FROM ledger_entries ORDER BY occurredAt DESC")
    fun ledger(): Flow<List<LedgerEntry>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSnapshot(snapshot: SavedSnapshot)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putConnection(state: ConnectionState)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: LedgerEntry): Long
    @Query("UPDATE ledger_entries SET amountCents = :cents, kind = 'EXPENSE', note = :note, status = 'CONFIRMED' WHERE id = :id AND status = 'DRAFT'")
    suspend fun confirmExpense(id: String, cents: Long, note: String)
    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteEntry(id: String)
    @Query("DELETE FROM campus_snapshots")
    suspend fun clearSnapshots()
    @Query("DELETE FROM connection_states")
    suspend fun clearConnections()
    @Query("DELETE FROM campus_snapshots WHERE source IN ('POWER', 'POWER_USAGE')")
    suspend fun clearPowerSnapshots()
    @Query("DELETE FROM connection_states WHERE source IN ('POWER', 'POWER_USAGE')")
    suspend fun clearPowerConnections()
}

@Database(entities = [SavedSnapshot::class, ConnectionState::class, LedgerEntry::class], version = 1, exportSchema = true)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun dao(): CampusDao
}
