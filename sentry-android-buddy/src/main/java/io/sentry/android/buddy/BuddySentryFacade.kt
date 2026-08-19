package io.sentry.android.buddy

import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.ITransaction
import io.sentry.SamplingContext
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.TracesSamplingDecision
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.android.buddy.model.BuddyObservedBreadcrumb
import io.sentry.android.buddy.model.BuddyObservedEvent
import io.sentry.android.buddy.model.BuddyObservedSpan
import io.sentry.android.buddy.model.BuddyObservedTransaction
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal interface BuddySentryFacade {
  val dsn: String?

  val release: String?

  val environment: String?

  fun setTag(key: String, value: String)

  fun removeTag(key: String)

  fun startTransaction(
    name: String,
    operation: String,
    tags: Map<String, String>,
  ): BuddySentryTransaction
}

internal interface BuddySentryTransaction {
  val traceId: String?

  val spanId: String?

  val spanCount: Int

  fun makeCurrent()

  fun observedSpans(): List<BuddyObservedSpan>

  fun finish()
}

internal class RealBuddySentryFacade : BuddySentryFacade {
  override val dsn: String?
    get() = Sentry.getCurrentScopes().options.dsn

  override val release: String?
    get() = Sentry.getCurrentScopes().options.release

  override val environment: String?
    get() = Sentry.getCurrentScopes().options.environment

  override fun setTag(key: String, value: String) {
    Sentry.setTag(key, value)
  }

  override fun removeTag(key: String) {
    Sentry.removeTag(key)
  }

  override fun startTransaction(
    name: String,
    operation: String,
    tags: Map<String, String>,
  ): BuddySentryTransaction {
    val options = TransactionOptions()
    options.setBindToScope(true)
    val transaction =
      Sentry.startTransaction(
        TransactionContext(name, operation, TracesSamplingDecision(true)),
        options,
      )
    tags.forEach { (key, value) -> transaction.setTag(key, value) }
    return RealBuddySentryTransaction(transaction)
  }

  companion object {
    fun eventObserver(
      recorder: BuddyRecorder,
      original: SentryOptions.BeforeSendCallback?,
    ): SentryOptions.BeforeSendCallback = SentryOptions.BeforeSendCallback { event, hint ->
      val processed = original?.execute(event, hint) ?: event.takeIf { original == null }
      processed
        ?.takeIf { it.isUsefulForBuddy() }
        ?.let {
          recorder.recordEvent(it.toBuddyObservedEvent())
        }
      processed
    }

    fun breadcrumbObserver(
      recorder: BuddyRecorder,
      original: SentryOptions.BeforeBreadcrumbCallback?,
    ): SentryOptions.BeforeBreadcrumbCallback =
      SentryOptions.BeforeBreadcrumbCallback { breadcrumb, hint ->
        val processed =
          original?.execute(breadcrumb, hint) ?: breadcrumb.takeIf { original == null }
        processed
          ?.takeIf { it.isUsefulForBuddy() }
          ?.let {
            recorder.recordBreadcrumb(it.toBuddyObservedBreadcrumb(hint))
          }
        processed
      }

    fun transactionObserver(
      recorder: BuddyRecorder,
      original: SentryOptions.BeforeSendTransactionCallback?,
    ): SentryOptions.BeforeSendTransactionCallback =
      SentryOptions.BeforeSendTransactionCallback { transaction, hint ->
        val processed =
          original?.execute(transaction, hint) ?: transaction.takeIf { original == null }
        processed?.let { recorder.recordTransaction(it.toBuddyObservedTransaction()) }
        processed
      }

    fun tracesSampler(
      recorder: BuddyRecorder,
      original: SentryOptions.TracesSamplerCallback?,
    ): SentryOptions.TracesSamplerCallback =
      SentryOptions.TracesSamplerCallback { samplingContext: SamplingContext ->
        if (recorder.isRecording()) {
          1.0
        } else {
          original?.sample(samplingContext)
        }
      }
  }
}

private fun SentryEvent.isUsefulForBuddy(): Boolean =
  isErrored || level == SentryLevel.ERROR || level == SentryLevel.FATAL

private fun SentryEvent.toBuddyObservedEvent(): BuddyObservedEvent {
  val primaryException = exceptions?.lastOrNull()
  val throwable = throwable
  val title =
    primaryException?.type
      ?: throwable?.javaClass?.name
      ?: message?.formatted
      ?: message?.message
      ?: transaction
      ?: eventId?.toString()

  return BuddyObservedEvent(
    timestamp = timestamp,
    title = title,
    data =
      linkedMapOf<String, Any?>(
          "event_id" to eventId?.toString(),
          "level" to level?.name,
          "transaction" to transaction,
          "message" to (message?.formatted ?: message?.message),
          "logger" to logger,
          "is_crashed" to isCrashed,
          "is_errored" to isErrored,
          "exception_count" to exceptions?.size,
          "exception_type" to primaryException?.type,
          "exception_value" to primaryException?.value,
          "throwable_type" to throwable?.javaClass?.name,
          "throwable_message" to throwable?.message,
          "trace_id" to contexts.trace?.traceId?.toString(),
          "span_id" to contexts.trace?.spanId?.toString(),
          "breadcrumb_count" to breadcrumbs?.size,
        )
        .apply {
          tags?.takeIf { it.isNotEmpty() }?.let { put("tags", it) }
        },
  )
}

private fun Breadcrumb.isUsefulForBuddy(): Boolean {
  val normalizedCategory = category?.lowercase(Locale.ROOT)
  val normalizedType = type?.lowercase(Locale.ROOT)
  return normalizedCategory == "navigation" ||
    normalizedCategory == "http" ||
    normalizedCategory?.startsWith("ui.") == true ||
    normalizedType == "navigation" ||
    normalizedType == "http" ||
    normalizedType == "user"
}

private fun Breadcrumb.toBuddyObservedBreadcrumb(hint: Hint): BuddyObservedBreadcrumb =
  BuddyObservedBreadcrumb(
    timestamp = timestamp,
    type = type,
    category = category,
    data =
      linkedMapOf<String, Any?>(
          "breadcrumb_type" to type,
          "category" to category,
          "message" to message,
          "level" to level?.name,
          "origin" to origin,
        )
        .apply {
          if (data.isNotEmpty()) {
            put("data", data)
          }
          hintSummary(hint)?.let { put("hint", it) }
        },
  )

private fun hintSummary(hint: Hint): String? {
  val knownHints =
    listOf(
      "sentry:typeCheckHint",
      "android:fragment",
      "android:navigationDestination",
      "android:motionEvent",
      "android:view",
    )
  return knownHints.firstOrNull { hint.get(it) != null }
}

internal class RealBuddySentryTransaction(private val transaction: ITransaction) :
  BuddySentryTransaction {
  override val traceId: String?
    get() = transaction.spanContext.traceId.toString()

  override val spanId: String?
    get() = transaction.spanContext.spanId.toString()

  override val spanCount: Int
    get() = transaction.spans.size

  override fun makeCurrent() {
    transaction.makeCurrent()
  }

  override fun observedSpans(): List<BuddyObservedSpan> =
    transaction.spans.map { span ->
      BuddyObservedSpan(
        id = span.spanId.toString(),
        timestamp = Date(TimeUnit.NANOSECONDS.toMillis(span.startDate.nanoTimestamp())),
        operation = span.operation,
        description = span.description,
        data =
          spanData(
            operation = span.operation,
            description = span.description,
            status = span.status?.name,
            origin = span.spanContext.origin,
            traceId = span.traceId.toString(),
            spanId = span.spanId.toString(),
            parentSpanId = span.parentSpanId?.toString(),
            durationMs = span.finishDate?.let { span.startDate.diff(it) / -1_000_000 },
            transactionName = null,
            extraData = span.data,
            tags = span.tags,
          ),
      )
    }

  override fun finish() {
    transaction.finish()
  }
}

private fun SentryTransaction.toBuddyObservedTransaction(): BuddyObservedTransaction {
  val trace = contexts.trace
  return BuddyObservedTransaction(
    recordingId = tags?.get("sentry.buddy.recording_id"),
    operation = trace?.operation,
    transactionName = transaction,
    spans = spans.map { it.toBuddyObservedSpan(transaction) },
    timestamp = Date((startTimestamp * 1000).toLong()),
  )
}

private fun SentrySpan.toBuddyObservedSpan(transactionName: String?): BuddyObservedSpan {
  val durationMs = timestamp?.let { ((it - startTimestamp) * 1000).toLong() }
  return BuddyObservedSpan(
    id = spanId.toString(),
    timestamp = Date((startTimestamp * 1000).toLong()),
    operation = op,
    description = description,
    data =
      spanData(
        operation = op,
        description = description,
        status = status?.name,
        origin = origin,
        traceId = traceId.toString(),
        spanId = spanId.toString(),
        parentSpanId = parentSpanId?.toString(),
        durationMs = durationMs,
        transactionName = transactionName,
        extraData = data,
        tags = tags,
      ),
  )
}

private fun spanData(
  operation: String,
  description: String?,
  status: String?,
  origin: String?,
  traceId: String,
  spanId: String,
  parentSpanId: String?,
  durationMs: Long?,
  transactionName: String?,
  extraData: Map<String, Any?>?,
  tags: Map<String, String>,
): Map<String, Any?> =
  linkedMapOf<String, Any?>(
      "op" to operation,
      "description" to description,
      "status" to status,
      "origin" to origin,
      "trace_id" to traceId,
      "span_id" to spanId,
      "parent_span_id" to parentSpanId,
      "duration_ms" to durationMs,
      "transaction" to transactionName,
    )
    .apply {
      if (!extraData.isNullOrEmpty()) {
        put("data", extraData)
      }
      if (tags.isNotEmpty()) {
        put("tags", tags)
      }
    }
