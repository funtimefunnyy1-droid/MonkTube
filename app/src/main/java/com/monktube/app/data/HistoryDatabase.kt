package com.monktube.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watch_history")
data class HistoryItem(
    @PrimaryKey val videoId: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: HistoryItem)

    @Delete
    suspend fun delete(item: HistoryItem)
}

@Database(entities = [HistoryItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
