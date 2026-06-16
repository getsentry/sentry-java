package io.sentry.sqlite

import androidx.sqlite.SQLiteStatement
import io.sentry.SentryDate
import io.sentry.SpanStatus

/**
 * Wraps a [SQLiteStatement] and records a single Sentry span covering all [step] calls for the
 * statement's lifetime (until [step] iteration is complete or the statement is [reset] or
 * [closed][close]).
 *
 * Span duration is restricted to accumulated database time, i.e., each [step] call is individually
 * timed and the durations are summed. Time the application spends between steps (e.g., processing
 * rows, sleeping, or doing I/O) is intentionally excluded.
 *
 * Not thread-safe: assumes sequential access within each SQL statement (normal SQLite usage).
 */
internal class SentrySQLiteStatement(
  private val delegate: SQLiteStatement,
  private val spans: SQLiteSpanInstrumentation,
  private val sql: String,
  private val nanoTimeProvider: () -> Long = { System.nanoTime() },
) : SQLiteStatement by delegate {

  private var firstStepTimestamp: SentryDate? = null
  private var accumulatedDbNanos: Long = 0L
  private var stepsComplete = false
  private var closed = false

  @Suppress("TooGenericExceptionCaught")
  override fun step(): Boolean {
    if (stepsComplete || closed) {
      return delegate.step()
    }

    val beforeNanos = nanoTimeProvider()
    return try {
      if (firstStepTimestamp == null) {
        firstStepTimestamp = spans.startTimestamp()
      }

      stepsComplete = !delegate.step()
      accumulatedDbNanos += nanoTimeProvider() - beforeNanos
      if (stepsComplete) {
        recordSpan(SpanStatus.OK)
      }
      !stepsComplete
    } catch (e: Throwable) {
      accumulatedDbNanos += nanoTimeProvider() - beforeNanos
      recordSpan(SpanStatus.INTERNAL_ERROR, e)
      throw e
    }
  }

  override fun reset() {
    if (closed) {
      return delegate.reset()
    }

    try {
      recordSpan(SpanStatus.OK)
    } finally {
      delegate.reset()
      stepsComplete = false
    }
  }

  override fun close() {
    closed = true
    delegate.use { recordSpan(SpanStatus.OK) }
  }

  private fun recordSpan(status: SpanStatus, throwable: Throwable? = null) {
    val start = firstStepTimestamp ?: return
    val duration = accumulatedDbNanos
    firstStepTimestamp = null
    accumulatedDbNanos = 0L
    spans.recordSpan(sql, start, duration, status, throwable)
  }
}
