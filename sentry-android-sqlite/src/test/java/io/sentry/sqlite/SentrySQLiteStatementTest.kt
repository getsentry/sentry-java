package io.sentry.sqlite

import androidx.sqlite.SQLiteStatement
import io.sentry.SpanStatus
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentrySQLiteStatementTest {

  private class Fixture {
    val mockStatement = mock<SQLiteStatement>()
    val mockSpans = mock<SQLiteSpanInstrumentation>()
    val startTimestampNanos = 1_000_000_000_000L
    val fakeClock = AtomicLong(0L)

    fun getSut(sql: String): SentrySQLiteStatement {
      whenever(mockSpans.startTimestamp()).thenReturn(startTimestampNanos)
      return SentrySQLiteStatement(mockStatement, mockSpans, sql, fakeClock::getAndIncrement)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `step calls recordSpan once after iteration completes`() {
    val sut = fixture.getSut("SELECT * FROM users")
    whenever(fixture.mockStatement.step()).thenReturn(true, true, false)
    sut.step()
    sut.step()
    verifyNeverCalledRecordSpan()
    sut.step()
    verify(fixture.mockSpans)
      .recordSpan(
        eq("SELECT * FROM users"),
        eq(fixture.startTimestampNanos),
        any(),
        eq(SpanStatus.OK),
        anyOrNull(),
      )
  }

  @Test
  fun `step that throws an exception calls recordSpan with INTERNAL_ERROR and exception`() {
    val sut = fixture.getSut("BAD SQL")
    val exception = RuntimeException("db error")
    whenever(fixture.mockStatement.step()).thenThrow(exception)

    assertFailsWith<RuntimeException> { sut.step() }

    verify(fixture.mockSpans)
      .recordSpan(
        eq("BAD SQL"),
        eq(fixture.startTimestampNanos),
        any(),
        eq(SpanStatus.INTERNAL_ERROR),
        eq(exception),
      )
  }

  @Test
  fun `step after exception calls recordSpan once new iteration cycle completes`() {
    val sut = fixture.getSut("SELECT 1")
    whenever(fixture.mockStatement.step())
      .thenThrow(RuntimeException("first failure"))
      .thenReturn(false)

    assertFailsWith<RuntimeException> { sut.step() }
    verifyCalledRecordSpan(times = 1)

    sut.step()
    verifyCalledRecordSpan(times = 2)
  }

  @Test
  fun `step after step iteration completes does not call recordSpan again`() {
    val sut = fixture.getSut("SELECT 1")
    whenever(fixture.mockStatement.step()).thenReturn(true, false, false)

    sut.step()
    sut.step()
    verifyCalledRecordSpan(times = 1)

    sut.step()

    verifyCalledRecordSpan(times = 1)
    verify(fixture.mockStatement, times(3)).step()
  }

  @Test
  fun `reset calls recordSpan if step iteration is in progress`() {
    val sut = fixture.getSut("SELECT * FROM users")
    whenever(fixture.mockStatement.step()).thenReturn(true)
    sut.step()
    sut.step()
    verifyNeverCalledRecordSpan()

    sut.reset()

    verifyCalledRecordSpan()
  }

  @Test
  fun `reset does not call recordSpan if step iteration has not started`() {
    val sut = fixture.getSut("SELECT 1")
    sut.reset()
    verifyNeverCalledRecordSpan()
  }

  @Test
  fun `reset does not call recordSpan if step iteration has completed`() {
    val sut = fixture.getSut("SELECT * FROM users")
    whenever(fixture.mockStatement.step()).thenReturn(true, false)
    sut.step()
    sut.step()
    verifyCalledRecordSpan(times = 1)

    sut.reset()

    verifyCalledRecordSpan(times = 1)
  }

  @Test
  fun `step after reset calls recordSpan when new iteration cycle completes`() {
    val sut = fixture.getSut("SELECT 1")
    sut.step()
    verifyCalledRecordSpan(times = 1)

    sut.reset()
    sut.step()

    verifyCalledRecordSpan(times = 2)
  }

  @Test
  fun `close calls recordSpan if step iteration is in progress`() {
    val sut = fixture.getSut("SELECT * FROM users")
    whenever(fixture.mockStatement.step()).thenReturn(true)
    sut.step()
    sut.step()
    verifyNeverCalledRecordSpan()

    sut.close()

    verifyCalledRecordSpan()
  }

  @Test
  fun `close does not call recordSpan if step iteration has not started`() {
    val sut = fixture.getSut("SELECT 1")
    sut.close()
    verifyNeverCalledRecordSpan()
  }

  @Test
  fun `close does not call recordSpan if step iteration has completed`() {
    val sut = fixture.getSut("SELECT * FROM users")
    whenever(fixture.mockStatement.step()).thenReturn(true, false)
    sut.step()
    sut.step()
    verifyCalledRecordSpan(times = 1)

    sut.close()

    verifyCalledRecordSpan(times = 1)
  }

  @Test
  fun `step after close does not call recordSpan`() {
    val sut = fixture.getSut("SELECT 1")
    sut.step()
    verifyCalledRecordSpan(times = 1)

    sut.close()
    sut.step()

    verifyCalledRecordSpan(times = 1)
  }

  @Test
  fun `reset after close does not call recordSpan`() {
    val sut = fixture.getSut("SELECT 1")
    whenever(fixture.mockStatement.step()).thenReturn(true)
    sut.step()
    sut.close()
    verifyCalledRecordSpan(times = 1)

    sut.reset()

    verifyCalledRecordSpan(times = 1)
  }

  @Test
  fun `recorded duration captures step time but excludes time between steps`() {
    val sut = fixture.getSut("SELECT * FROM users")
    whenever(fixture.mockStatement.step())
      .thenAnswer {
        fixture.fakeClock.addAndGet(10)
        true
      }
      .thenAnswer {
        fixture.fakeClock.addAndGet(20)
        true
      }
      .thenAnswer {
        fixture.fakeClock.addAndGet(30)
        false
      }

    sut.step()
    // Simulate work done between steps.
    fixture.fakeClock.addAndGet(1_000_000)
    sut.step()
    fixture.fakeClock.addAndGet(2_000_000)
    sut.step()

    val durationCaptor = argumentCaptor<Long>()
    verify(fixture.mockSpans).recordSpan(any(), any(), durationCaptor.capture(), any(), anyOrNull())
    // Each step contributes its internal time (10 + 20 + 30) plus one unit from
    // fakeClock::getAndIncrement between before/after reads, so total is 63.
    assertEquals(63L, durationCaptor.firstValue)
  }

  @Test
  fun `all calls are propagated to the delegate`() {
    val sut = fixture.getSut("SELECT 1")

    sut.bindBlob(0, byteArrayOf())
    verify(fixture.mockStatement).bindBlob(0, byteArrayOf())

    sut.bindDouble(0, 1.0)
    verify(fixture.mockStatement).bindDouble(0, 1.0)

    sut.bindLong(0, 1L)
    verify(fixture.mockStatement).bindLong(0, 1L)

    sut.bindText(0, "text")
    verify(fixture.mockStatement).bindText(0, "text")

    sut.bindNull(0)
    verify(fixture.mockStatement).bindNull(0)

    sut.getDouble(0)
    verify(fixture.mockStatement).getDouble(0)

    sut.getLong(0)
    verify(fixture.mockStatement).getLong(0)

    sut.getText(0)
    verify(fixture.mockStatement).getText(0)

    sut.isNull(0)
    verify(fixture.mockStatement).isNull(0)

    sut.getColumnCount()
    verify(fixture.mockStatement).getColumnCount()

    sut.getColumnName(0)
    verify(fixture.mockStatement).getColumnName(0)

    sut.step()
    verify(fixture.mockStatement).step()

    sut.reset()
    verify(fixture.mockStatement).reset()

    sut.clearBindings()
    verify(fixture.mockStatement).clearBindings()

    sut.close()
    verify(fixture.mockStatement).close()
  }

  private fun verifyNeverCalledRecordSpan() {
    verifyCalledRecordSpan(times = 0)
  }

  private fun verifyCalledRecordSpan(times: Int = 1) {
    verify(fixture.mockSpans, times(times)).recordSpan(any(), any(), any(), any(), anyOrNull())
  }
}
