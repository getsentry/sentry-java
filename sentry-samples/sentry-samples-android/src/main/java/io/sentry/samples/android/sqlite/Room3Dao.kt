package io.sentry.samples.android.sqlite

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase

@Entity(tableName = "song")
data class SongEntity3(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val artist: String,
)

@Dao
interface SongDao3 {

  @Insert suspend fun insert(song: SongEntity3)

  /** Batch insert: Room runs all rows in a single transaction, reusing one compiled statement. */
  @Insert suspend fun insertAll(songs: List<SongEntity3>)

  @Query("SELECT * FROM song") suspend fun getAll(): List<SongEntity3>

  @Query("SELECT count(*) FROM song") suspend fun count(): Int

  /**
   * No-op write (matches no rows) used at warm-up to open Room's writer connection up front. A read
   * like [count] only opens a reader, so without this the first INSERT would (noisily) open and
   * bootstrap the writer connection inside a demo transaction.
   */
  @Query("DELETE FROM song WHERE id < 0") suspend fun primeWriter()
}

@Database(entities = [SongEntity3::class], version = 1, exportSchema = false)
abstract class SampleRoom3Database : RoomDatabase() {

  abstract fun songDao(): SongDao3
}
