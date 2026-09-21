package io.sentry.samples.android.navigation

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import io.sentry.Sentry
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import io.sentry.samples.android.SampleBeforeSendTransactionHook

/** State holder backing the [Nav2TransactionHistorySheet]. */
internal class Nav2TransactionHistory(private val isActive: () -> Boolean) {

  val transactions = mutableStateListOf<Nav2TransactionTrace>()

  private val mainHandler = Handler(Looper.getMainLooper())
  private val transactionListener: (SentryTransaction, String?) -> Unit = { transaction, dsn ->
    add(transaction, dsn)
  }

  fun install() {
    val options = Sentry.getCurrentScopes().options
    SampleBeforeSendTransactionHook.installIfNeeded(options)
    SampleBeforeSendTransactionHook.addListener(transactionListener)
  }

  fun uninstall() {
    clear()
    SampleBeforeSendTransactionHook.removeListener(transactionListener)
  }

  fun clear() {
    transactions.clear()
  }

  private fun add(transaction: SentryTransaction, dsn: String?) {
    if (!isActive()) {
      return
    }

    val trace = transaction.toNav2TransactionTrace(dsn)
    if (Looper.myLooper() == Looper.getMainLooper()) {
      transactions.addMostRecent(trace)
    } else {
      mainHandler.post {
        if (isActive()) {
          transactions.addMostRecent(trace)
        }
      }
    }
  }
}

internal data class Nav2TransactionTrace(
  val name: String,
  val operation: String,
  val eventId: String,
  val traceId: String,
  val status: String?,
  val tab: String,
  val durationMillis: Double,
  val sentryUrl: String?,
  val spans: List<Nav2TraceSpan>,
)

internal data class Nav2TraceSpan(
  val spanId: String,
  val parentSpanId: String?,
  val operation: String,
  val description: String?,
  val startOffsetMillis: Double,
  val durationMillis: Double,
  val children: List<Nav2TraceSpan> = emptyList(),
)

private fun SentryTransaction.toNav2TransactionTrace(dsn: String?): Nav2TransactionTrace {
  val trace = contexts.trace
  val startTimestamp = startTimestamp
  val endTimestamp = timestamp ?: startTimestamp
  val durationMillis = ((endTimestamp - startTimestamp) * 1_000.0).coerceAtLeast(0.0)
  val rootSpanId = trace?.spanId?.toString()
  val traceId = trace?.traceId?.toString().orEmpty()
  val eventId = eventId?.toString().orEmpty()
  val rawSpans = spans.map { it.toNav2TraceSpan(startTimestamp) }
  val spanIds = rawSpans.map { it.spanId }.toSet()
  val spansByParentId = rawSpans.groupBy { span -> span.parentSpanId }
  val topLevelSpans =
    rawSpans
      .filter { span ->
        span.parentSpanId == null ||
          span.parentSpanId == rootSpanId ||
          span.parentSpanId !in spanIds
      }
      .sortedBy { span -> span.startOffsetMillis }

  return Nav2TransactionTrace(
    name = transaction ?: "<unnamed transaction>",
    operation = trace?.operation ?: "transaction",
    eventId = eventId,
    traceId = traceId,
    status = status?.name,
    tab = nav2ScenarioLabel(),
    durationMillis = durationMillis,
    sentryUrl = sentryTransactionUrl(dsn, traceId, rootSpanId, eventId, endTimestamp),
    spans = topLevelSpans.withChildren(spansByParentId),
  )
}

private fun MutableList<Nav2TransactionTrace>.addMostRecent(transaction: Nav2TransactionTrace) {
  add(0, transaction)
  while (size > TRANSACTION_HISTORY_LIMIT) {
    removeAt(lastIndex)
  }
}

private fun SentrySpan.toNav2TraceSpan(transactionStartTimestamp: Double): Nav2TraceSpan =
  Nav2TraceSpan(
    spanId = spanId.toString(),
    parentSpanId = parentSpanId?.toString(),
    operation = op,
    description = description,
    startOffsetMillis = ((startTimestamp - transactionStartTimestamp) * 1_000.0).coerceAtLeast(0.0),
    durationMillis =
      (((timestamp ?: startTimestamp) - startTimestamp) * 1_000.0).coerceAtLeast(0.0),
  )

private fun List<Nav2TraceSpan>.withChildren(
  spansByParentId: Map<String?, List<Nav2TraceSpan>>
): List<Nav2TraceSpan> = map { span -> span.withChildren(spansByParentId) }

private fun Nav2TraceSpan.withChildren(
  spansByParentId: Map<String?, List<Nav2TraceSpan>>
): Nav2TraceSpan =
  copy(
    children =
      spansByParentId[spanId]
        .orEmpty()
        .sortedBy { span -> span.startOffsetMillis }
        .withChildren(spansByParentId)
  )

private fun sentryTransactionUrl(
  dsn: String?,
  traceId: String,
  rootSpanId: String?,
  eventId: String,
  timestampSeconds: Double,
): String? {
  val projectId = dsn?.projectIdFromDsn() ?: return null
  if (traceId.isEmpty() || rootSpanId.isNullOrEmpty() || eventId.isEmpty()) {
    return null
  }
  return "https://$SENTRY_SAMPLE_ORG_SLUG.sentry.io/explore/traces/trace/$traceId/" +
    "?node=span-$rootSpanId" +
    "&project=$projectId" +
    "&source=traces" +
    "&statsPeriod=14d" +
    "&targetId=$eventId" +
    "&timestamp=${timestampSeconds.toLong()}"
}

private fun String.projectIdFromDsn(): String? =
  substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/').takeIf { projectId
    ->
    projectId.isNotEmpty() && projectId.all { it.isDigit() }
  }

private const val TRANSACTION_HISTORY_LIMIT = 10
private const val SENTRY_SAMPLE_ORG_SLUG = "sentry-sdks"
