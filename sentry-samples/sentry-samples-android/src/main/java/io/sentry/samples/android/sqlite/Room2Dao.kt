package io.sentry.samples.android.sqlite

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "song")
data class SongEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val artist: String,
)

@Dao
interface SongDao {

  @Insert suspend fun insert(song: SongEntity)

  /** Batch insert: Room runs all rows in a single transaction, reusing one compiled statement. */
  @Insert suspend fun insertAll(songs: List<SongEntity>)

  @Query("SELECT * FROM song") suspend fun getAll(): List<SongEntity>

  @Query("SELECT count(*) FROM song") suspend fun count(): Int

  /**
   * No-op write (matches no rows) used at warm-up to open Room's writer connection up front. A read
   * like [count] only opens a reader, so without this the first INSERT would (noisily) open and
   * bootstrap the writer connection inside a demo transaction.
   */
  @Query("DELETE FROM song WHERE id < 0") suspend fun primeWriter()
}

@Database(entities = [SongEntity::class], version = 1, exportSchema = false)
abstract class SampleRoom2Database : RoomDatabase() {

  abstract fun songDao(): SongDao
}
