package io.sentry.samples.android.navigation.nav3

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import io.sentry.Sentry
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import io.sentry.samples.android.SampleBeforeSendTransactionHook

/** Process-wide state holder backing every Nav3 transaction history sheet. */
internal object NavigationTransactionHistory {

  private val store = NavigationTransactionHistoryStore()
  @Volatile private var sessionGeneration = 0L

  val transactions: List<NavigationTransactionTrace>
    get() = store.transactions

  val sessionId: String
    get() = sessionGeneration.toString()

  private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
  private val transactionListener: (SentryTransaction, String?) -> Unit = { transaction, dsn ->
    add(transaction, dsn)
  }

  fun install() {
    val options = Sentry.getCurrentScopes().options
    SampleBeforeSendTransactionHook.installIfNeeded(options)
    SampleBeforeSendTransactionHook.addListener(transactionListener)
  }

  fun startSession() {
    install()
    sessionGeneration++
    clear()
  }

  fun clear() {
    store.clear()
  }

  private fun add(transaction: SentryTransaction, dsn: String?) {
    val transactionSessionId = transaction.getTag(NAVIGATION_SAMPLE_SESSION_TAG)
    if (transaction.getTag(NAV3_SCENARIO_TAG) == null || transactionSessionId != sessionId) {
      return
    }

    val trace = transaction.toNavigationTransactionTrace(dsn)
    if (Looper.myLooper() == Looper.getMainLooper()) {
      store.add(trace)
    } else {
      mainHandler.post {
        // The session may have changed while this was queued.
        if (transactionSessionId == sessionId) {
          store.add(trace)
        }
      }
    }
  }
}

internal class NavigationTransactionHistoryStore(
  private val transactionLimit: Int = TRANSACTION_HISTORY_LIMIT
) {

  val transactions = mutableStateListOf<NavigationTransactionTrace>()

  fun add(transaction: NavigationTransactionTrace) {
    transactions.add(0, transaction)
    while (transactions.size > transactionLimit) {
      transactions.removeAt(transactions.lastIndex)
    }
  }

  fun clear() {
    transactions.clear()
  }
}

internal data class NavigationTransactionTrace(
  val activity: String,
  val name: String,
  val operation: String,
  val eventId: String,
  val traceId: String,
  val status: String?,
  val tab: String,
  val durationMillis: Double,
  val sentryUrl: String?,
  val spans: List<NavigationTraceSpan>,
)

internal data class NavigationTraceSpan(
  val spanId: String,
  val parentSpanId: String?,
  val operation: String,
  val description: String?,
  val startOffsetMillis: Double,
  val durationMillis: Double,
  val children: List<NavigationTraceSpan> = emptyList(),
)

internal fun SentryTransaction.toNavigationTransactionTrace(
  dsn: String?
): NavigationTransactionTrace {
  val trace = contexts.trace
  val startTimestamp = startTimestamp
  val endTimestamp =
    timestamp ?: spans.mapNotNull { span -> span.timestamp }.maxOrNull() ?: startTimestamp
  val durationMillis = ((endTimestamp - startTimestamp) * 1_000.0).coerceAtLeast(0.0)
  val rootSpanId = trace?.spanId?.toString()
  val traceId = trace?.traceId?.toString().orEmpty()
  val eventId = eventId?.toString().orEmpty()
  val rawSpans = spans.map { it.toNavigationTraceSpan(startTimestamp) }
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

  return NavigationTransactionTrace(
    activity = getTag(NAVIGATION_SAMPLE_ACTIVITY_TAG) ?: UNKNOWN_NAVIGATION_ACTIVITY,
    name = transaction ?: "<unnamed transaction>",
    operation = trace?.operation ?: "transaction",
    eventId = eventId,
    traceId = traceId,
    status = status?.name,
    tab = navigationSampleScenarioLabel(),
    durationMillis = durationMillis,
    sentryUrl = sentryTransactionUrl(dsn, traceId, rootSpanId, eventId, endTimestamp),
    spans = topLevelSpans.withChildren(spansByParentId),
  )
}

private fun SentrySpan.toNavigationTraceSpan(
  transactionStartTimestamp: Double
): NavigationTraceSpan =
  NavigationTraceSpan(
    spanId = spanId.toString(),
    parentSpanId = parentSpanId?.toString(),
    operation = op,
    description = description,
    startOffsetMillis = ((startTimestamp - transactionStartTimestamp) * 1_000.0).coerceAtLeast(0.0),
    durationMillis =
      (((timestamp ?: startTimestamp) - startTimestamp) * 1_000.0).coerceAtLeast(0.0),
  )

private fun List<NavigationTraceSpan>.withChildren(
  spansByParentId: Map<String?, List<NavigationTraceSpan>>
): List<NavigationTraceSpan> = map { span -> span.withChildren(spansByParentId) }

private fun NavigationTraceSpan.withChildren(
  spansByParentId: Map<String?, List<NavigationTraceSpan>>
): NavigationTraceSpan =
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

private const val TRANSACTION_HISTORY_LIMIT = 25
private const val SENTRY_SAMPLE_ORG_SLUG = "sentry-sdks"
private const val UNKNOWN_NAVIGATION_ACTIVITY = "Unknown"
