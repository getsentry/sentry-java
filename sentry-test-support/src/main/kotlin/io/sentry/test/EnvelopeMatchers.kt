package io.sentry.test

import io.sentry.JsonSerializer
import io.sentry.ProfileChunk
import io.sentry.SentryEnvelope
import io.sentry.SentryEnvelopeItem
import io.sentry.SentryEvent
import io.sentry.SentryItemType
import io.sentry.SentryLogEvents
import io.sentry.SentryMetricsEvents
import io.sentry.SentryOptions
import io.sentry.protocol.FeatureFlag
import io.sentry.protocol.SentryTransaction
import java.io.PrintWriter

// Matchers return false instead of throwing, so tests can pick one envelope out of many. On a
// mismatch they print what they looked at, which is otherwise hard to see from a failed wait.

private val serializer = JsonSerializer(SentryOptions.empty())

fun SentryEnvelope.event(): SentryEvent? =
  firstItem(SentryItemType.Event) { it.getEvent(serializer) }

fun SentryEnvelope.transaction(): SentryTransaction? =
  firstItem(SentryItemType.Transaction) { it.getTransaction(serializer) }

fun SentryEnvelope.logs(): SentryLogEvents? =
  firstItem(SentryItemType.Log) { it.getLogs(serializer) }

fun SentryEnvelope.metrics(): SentryMetricsEvents? =
  firstItem(SentryItemType.TraceMetric) { it.getMetrics(serializer) }

fun SentryEnvelope.profileChunk(): ProfileChunk? =
  firstItem(SentryItemType.ProfileChunk) { item ->
    item.data.inputStream().reader().use { serializer.deserialize(it, ProfileChunk::class.java) }
  }

/** Matches the transaction's own op and, if given, a feature flag in its trace data. */
fun SentryTransaction.hasOp(op: String, featureFlag: FeatureFlag? = null): Boolean {
  val trace = contexts.trace
  if (trace?.operation != op) {
    return mismatch("Unable to find transaction with op $op:", this)
  }
  return featureFlag == null || matchesFlag(trace.data, featureFlag)
}

fun SentryTransaction.hasTraceId(traceId: String): Boolean {
  if (contexts.trace?.traceId?.toString() != traceId) {
    return mismatch("Unable to find trace ID $traceId in transaction:", this)
  }
  return true
}

/**
 * Matches a span with [op] and, if given, a feature flag in its data. With [noFeatureFlags] the
 * span must carry no flag evaluations at all.
 */
fun SentryTransaction.hasSpanWithOp(
  op: String,
  featureFlag: FeatureFlag? = null,
  noFeatureFlags: Boolean = false,
): Boolean {
  val span =
    spans.firstOrNull { it.op == op } ?: return mismatch("Unable to find span with op $op:", this)
  val data = span.data ?: emptyMap()
  if (noFeatureFlags && data.keys.any { it.startsWith("flag.evaluation.") }) {
    return mismatch("Expected no feature flags on span with op $op:", this)
  }
  return featureFlag == null || matchesFlag(data, featureFlag)
}

fun SentryTransaction.hasSpanWithOpAndDescription(op: String, description: String): Boolean {
  if (spans.none { it.op == op && it.description == description }) {
    return mismatch(
      "Unable to find span with op $op and description $description in transaction:",
      this,
    )
  }
  return true
}

fun SentryLogEvents.hasLogWithBody(body: String): Boolean {
  if (items.none { it.body == body }) {
    return mismatch("Unable to find log item with body $body in logs:", this)
  }
  return true
}

fun SentryLogEvents.hasLogWithAttribute(body: String, key: String, value: Any?): Boolean {
  val log =
    items.firstOrNull { it.body == body }
      ?: return mismatch("Unable to find log item with body $body in logs:", this)
  val attribute =
    log.attributes?.get(key)
      ?: return mismatch("Unable to find attribute $key on log with body $body:", log)
  if (attribute.value != value) {
    return mismatch("Attribute $key is ${attribute.value}, expected $value on log $body:", log)
  }
  return true
}

fun SentryMetricsEvents.hasMetric(
  name: String,
  type: String,
  value: Double,
  unit: String? = null,
): Boolean {
  val found = items.any {
    it.name == name && it.type == type && it.value == value && (unit == null || it.unit == unit)
  }
  if (!found) {
    return mismatch("Unable to find metric $name of type $type with value $value in metrics:", this)
  }
  return true
}

fun SentryMetricsEvents.hasMetricWithAttribute(name: String, key: String, value: Any?): Boolean {
  val metric =
    items.firstOrNull { it.name == name }
      ?: return mismatch("Unable to find metric with name $name in metrics:", this)
  val attribute =
    metric.attributes?.get(key)
      ?: return mismatch("Unable to find attribute $key on metric $name:", metric)
  if (attribute.value != value) {
    return mismatch(
      "Attribute $key is ${attribute.value}, expected $value on metric $name:",
      metric,
    )
  }
  return true
}

fun SentryEvent.hasExceptionMessage(message: String): Boolean {
  if (exceptions?.none { it.value == message } != false) {
    return mismatch("Unable to find exception with message $message in event:", this)
  }
  return true
}

fun SentryEvent.hasFlag(flag: String, result: Boolean): Boolean {
  if (contexts.featureFlags?.values?.none { it.flag == flag && it.result == result } != false) {
    return mismatch("Unable to find feature flag $flag=$result in event:", this)
  }
  return true
}

private inline fun <T> SentryEnvelope.firstItem(
  type: SentryItemType,
  read: (SentryEnvelopeItem) -> T?,
): T? = items.firstOrNull { it.header.type == type }?.let(read)

private fun matchesFlag(data: Map<String, Any?>?, featureFlag: FeatureFlag): Boolean {
  val result = data?.get(featureFlag.flag)
  if (result != featureFlag.result) {
    println("Feature flag ${featureFlag.flag} is $result, expected ${featureFlag.result}")
    return false
  }
  return true
}

private fun mismatch(message: String, subject: Any): Boolean {
  println(message)
  // Flush rather than close: closing the writer would close System.out.
  PrintWriter(System.out).apply {
    serializer.serialize(subject, this)
    println()
    flush()
  }
  return false
}
