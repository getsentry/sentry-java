package io.sentry.android.buddy

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.SentryLogEvent
import io.sentry.android.buddy.bridge.BuddyLogItem
import io.sentry.android.buddy.bridge.SentryBuddyLogIngestApi
import io.sentry.protocol.MeasurementValue
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Forwards captured errors, transactions and logs to the buddy logs endpoint, one item per capture.
 *
 * The bridge call blocks, and `beforeSend` may run on any thread, so every item is posted on a
 * single background [executor]. A failed post is swallowed — a debug bridge must not disturb the
 * host app.
 */
internal class BuddyLogForwarder(
  private val logIngestApi: SentryBuddyLogIngestApi,
  private val sessionId: String,
  private val executor: Executor = defaultExecutor(),
) {
  fun forwardEvent(event: SentryEvent) = submit(event.toLogItem())

  fun forwardTransaction(transaction: SentryTransaction) = submit(transaction.toLogItem())

  fun forwardBreadcrumb(breadcrumb: Breadcrumb) = submit(breadcrumb.toLogItem())

  fun forwardLog(log: SentryLogEvent) = submit(log.toLogItem())

  private fun submit(item: BuddyLogItem) {
    executor.execute {
      try {
        logIngestApi.ingest(sessionId, listOf(item))
      } catch (_: IllegalStateException) {
        // The host is unreachable; a debug bridge must not disturb the app.
      }
    }
  }

  fun close() {
    (executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
  }

  private fun SentryEvent.toLogItem(): BuddyLogItem {
    val exception = exceptions?.lastOrNull()
    return BuddyLogItem(
      type = "error",
      timestamp = timestamp.time,
      data =
        compact(
          "event_id" to eventId?.toString(),
          "level" to level?.name,
          "message" to (message?.formatted ?: message?.message),
          "transaction" to transaction,
          "exception_type" to (exception?.type ?: throwable?.javaClass?.name),
          "exception_value" to (exception?.value ?: throwable?.message),
          "trace_id" to contexts.trace?.traceId?.toString(),
        ),
    )
  }

  /**
   * Carries the whole trace, not only a summary: every child span with its timing, status and
   * attributes, so the data can be analyzed on the host without a round trip to Sentry.
   */
  private fun SentryTransaction.toLogItem(): BuddyLogItem {
    val trace = contexts.trace
    return BuddyLogItem(
      type = "transaction",
      timestamp = startTimestamp.toMillis(),
      data =
        compact(
          "event_id" to eventId?.toString(),
          "transaction" to transaction,
          "op" to trace?.operation,
          "description" to trace?.description,
          "status" to (status ?: trace?.status)?.apiName(),
          "origin" to trace?.origin,
          "trace_id" to trace?.traceId?.toString(),
          "span_id" to trace?.spanId?.toString(),
          "parent_span_id" to trace?.parentSpanId?.toString(),
          "release" to release,
          "environment" to environment,
          "start_timestamp_ms" to startTimestamp.toMillis(),
          "end_timestamp_ms" to timestamp?.toMillis(),
          "duration_ms" to durationMs(startTimestamp, timestamp),
          "tags" to tags?.takeIf { it.isNotEmpty() },
          "data" to trace?.data?.takeIf { it.isNotEmpty() },
          "measurements" to measurements.toLogData(),
          "span_count" to spans.size,
          "spans" to spans.sortedBy { it.startTimestamp }.map { it.toLogData(startTimestamp) },
        ),
    )
  }

  private fun SentrySpan.toLogData(transactionStart: Double): Map<String, Any?> =
    compact(
      "span_id" to spanId.toString(),
      "parent_span_id" to parentSpanId?.toString(),
      "op" to op,
      "description" to description,
      "status" to status?.apiName(),
      "origin" to origin,
      "start_timestamp_ms" to startTimestamp.toMillis(),
      "end_timestamp_ms" to timestamp?.toMillis(),
      "offset_ms" to durationMs(transactionStart, startTimestamp),
      "duration_ms" to durationMs(startTimestamp, timestamp),
      "tags" to tags.takeIf { it.isNotEmpty() },
      "data" to data?.takeIf { it.isNotEmpty() },
      "measurements" to measurements.toLogData(),
    )

  private fun Map<String, MeasurementValue>.toLogData(): Map<String, Any?>? =
    takeIf { it.isNotEmpty() }
      ?.mapValues { (_, measurement) ->
        compact("value" to measurement.value, "unit" to measurement.unit)
      }

  private fun Double.toMillis(): Long = (this * 1000).toLong()

  private fun durationMs(start: Double, end: Double?): Long? = end?.let {
    ((it - start) * 1000).toLong()
  }

  private fun Breadcrumb.toLogItem(): BuddyLogItem {
    return BuddyLogItem(
      type = "breadcrumb",
      timestamp = timestamp.time,
      data =
        compact(
          "type" to type,
          "data" to data,
        ),
    )
  }

  private fun SentryLogEvent.toLogItem(): BuddyLogItem =
    BuddyLogItem(
      type = "log",
      timestamp = (timestamp * 1000).toLong(),
      data =
        compact(
          "level" to level.name,
          "body" to body,
          "trace_id" to traceId.toString(),
        ),
    )

  private fun compact(vararg pairs: Pair<String, Any?>): Map<String, Any?> =
    pairs.filter { it.second != null }.toMap()

  private companion object {
    fun defaultExecutor(): Executor = Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "sentry-buddy-log-forwarder").apply { isDaemon = true }
    }
  }
}
