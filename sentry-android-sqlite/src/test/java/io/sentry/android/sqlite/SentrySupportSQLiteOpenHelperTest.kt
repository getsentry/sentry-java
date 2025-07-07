package io.sentry.android.sqlite

import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentrySupportSQLiteOpenHelperTest {
  class Fixture {
    val mockOpenHelper = mock<SupportSQLiteOpenHelper>()

    init {
      whenever(mockOpenHelper.writableDatabase).thenReturn(mock())
      whenever(mockOpenHelper.readableDatabase).thenReturn(mock())
    }

    fun getSut(): SentrySupportSQLiteOpenHelper =
      SentrySupportSQLiteOpenHelper.create(mockOpenHelper) as SentrySupportSQLiteOpenHelper
  }

  private val fixture = Fixture()

  @Test
  fun `all calls are propagated to the delegate`() {
    val openHelper = fixture.getSut()

    openHelper.writableDatabase
    verify(fixture.mockOpenHelper).writableDatabase

    openHelper.readableDatabase
    verify(fixture.mockOpenHelper).readableDatabase

    openHelper.databaseName
    verify(fixture.mockOpenHelper, times(2)).databaseName

    openHelper.close()
    verify(fixture.mockOpenHelper).close()
  }

  @Test
  fun `writableDatabase returns a SentrySupportSQLiteDatabase`() {
    val openHelper = fixture.getSut()
    assertIs<SentrySupportSQLiteDatabase>(openHelper.writableDatabase)
  }

  @Test
  fun `create returns a SentrySupportSQLiteOpenHelper wrapper`() {
    val openHelper: SupportSQLiteOpenHelper =
      SentrySupportSQLiteOpenHelper.Companion.create(fixture.mockOpenHelper)
    assertIs<SentrySupportSQLiteDatabase>(openHelper.writableDatabase)
    assertNotEquals(fixture.mockOpenHelper, openHelper)
  }

  @Test
  fun `create returns the passed openHelper if it is a SentrySupportSQLiteOpenHelper`() {
    val sentryOpenHelper = mock<SentrySupportSQLiteOpenHelper>()
    val openHelper: SupportSQLiteOpenHelper =
      SentrySupportSQLiteOpenHelper.Companion.create(sentryOpenHelper)
    assertEquals(sentryOpenHelper, openHelper)
  }
}
