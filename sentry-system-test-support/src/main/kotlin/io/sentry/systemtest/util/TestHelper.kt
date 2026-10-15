package io.sentry.systemtest.util

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import io.sentry.JsonSerializer
import io.sentry.ProfileChunk
import io.sentry.SentryEnvelope
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryEvent
import io.sentry.SentryLogEvents
import io.sentry.SentryMetricsEvents
import io.sentry.SentryOptions
import io.sentry.protocol.FeatureFlag
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import io.sentry.systemtest.graphql.GraphqlTestClient
import io.sentry.test.event
import io.sentry.test.hasExceptionMessage
import io.sentry.test.hasFlag
import io.sentry.test.hasLogWithAttribute
import io.sentry.test.hasLogWithBody
import io.sentry.test.hasMetric
import io.sentry.test.hasMetricWithAttribute
import io.sentry.test.hasOp
import io.sentry.test.hasSpanWithOp
import io.sentry.test.hasSpanWithOpAndDescription
import io.sentry.test.hasTraceId
import io.sentry.test.logs
import io.sentry.test.metrics
import io.sentry.test.profileChunk
import io.sentry.test.transaction
import java.io.File
import java.io.PrintWriter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestHelper(backendUrl: String) {
  val restClient: RestTestClient
  val graphqlClient: GraphqlTestClient
  val sentryClient: SentryMockServerClient
  val jsonSerializer: JsonSerializer
  val dsn = "http://502f25099c204a2fbf4cb16edc5975d1@localhost:8000/0"

  var envelopeCounts: EnvelopeCounts? = null

  init {
    restClient = RestTestClient(backendUrl)
    sentryClient = SentryMockServerClient("http://localhost:8000")
    graphqlClient = GraphqlTestClient(backendUrl)
    jsonSerializer = JsonSerializer(SentryOptions.empty())
  }

  fun snapshotEnvelopeCount() {
    envelopeCounts = sentryClient.getEnvelopeCount()
  }

  fun ensureEnvelopeCountIncreased() {
    Thread.sleep(1000)
    val envelopeCountsAfter = sentryClient.getEnvelopeCount()
    assertTrue(envelopeCountsAfter!!.envelopes!! > envelopeCounts!!.envelopes!!)
  }

  fun ensureEnvelopeReceived(retryCount: Int = 1, callback: ((String) -> Boolean)) {
    val envelopes = sentryClient.getEnvelopes()
    assertNotNull(envelopes.envelopes)
    envelopes.envelopes.forEach { envelopeString ->
      val didMatch = callback(envelopeString)
      if (didMatch) {
        return
      }
    }
    if (retryCount <= 0) {
      throw RuntimeException("Unable to find matching envelope received by relay")
    } else {
      Thread.sleep(10000)
      ensureEnvelopeReceived(retryCount - 1, callback)
    }
  }

  fun ensureNoEnvelopeReceived(callback: ((String) -> Boolean)) {
    Thread.sleep(10000)
    val envelopes = sentryClient.getEnvelopes()

    if (envelopes.envelopes.isNullOrEmpty()) {
      return
    }

    envelopes.envelopes.forEach { envelopeString ->
      val didMatch = callback(envelopeString)
      if (didMatch) {
        throw RuntimeException("Found unexpected matching envelope received by relay")
      }
    }
  }

  fun ensureTransactionReceived(callback: ((SentryTransaction, SentryEnvelopeHeader) -> Boolean)) {
    ensureEnvelopeReceived { envelopeString -> checkIfTransactionMatches(envelopeString, callback) }
  }

  fun ensureProfileChunkReceived(callback: ((ProfileChunk, SentryEnvelopeHeader) -> Boolean)) {
    ensureEnvelopeReceived { envelopeString -> checkIfProfileMatches(envelopeString, callback) }
  }

  fun ensureNoTransactionReceived(
    callback: ((SentryTransaction, SentryEnvelopeHeader) -> Boolean)
  ) {
    ensureNoEnvelopeReceived { envelopeString ->
      checkIfTransactionMatches(envelopeString, callback)
    }
  }

  fun ensureLogsReceived(callback: ((SentryLogEvents, SentryEnvelopeHeader) -> Boolean)) {
    ensureEnvelopeReceived { envelopeString -> checkIfLogsMatch(envelopeString, callback) }
  }

  private fun checkIfLogsMatch(
    envelopeString: String,
    callback: ((SentryLogEvents, SentryEnvelopeHeader) -> Boolean),
  ): Boolean {
    val envelope = deserialize(envelopeString) ?: return false
    val logs = envelope.logs() ?: return false
    return callback(logs, envelope.header)
  }

  fun doesContainMetric(
    metrics: SentryMetricsEvents,
    name: String,
    type: String,
    value: Double,
    unit: String? = null,
  ): Boolean = metrics.hasMetric(name, type, value, unit)

  fun ensureMetricsReceived(callback: ((SentryMetricsEvents, SentryEnvelopeHeader) -> Boolean)) {
    ensureEnvelopeReceived { envelopeString -> checkIfMetricsMatch(envelopeString, callback) }
  }

  private fun checkIfMetricsMatch(
    envelopeString: String,
    callback: ((SentryMetricsEvents, SentryEnvelopeHeader) -> Boolean),
  ): Boolean {
    val envelope = deserialize(envelopeString) ?: return false
    val metrics = envelope.metrics() ?: return false
    return callback(metrics, envelope.header)
  }

  fun doesContainLogWithBody(logs: SentryLogEvents, body: String): Boolean =
    logs.hasLogWithBody(body)

  fun doesLogWithBodyHaveAttribute(
    logs: SentryLogEvents,
    body: String,
    attributeKey: String,
    attributeValue: Any?,
  ): Boolean = logs.hasLogWithAttribute(body, attributeKey, attributeValue)

  fun doesMetricHaveAttribute(
    metrics: SentryMetricsEvents,
    metricName: String,
    attributeKey: String,
    attributeValue: Any?,
  ): Boolean = metrics.hasMetricWithAttribute(metricName, attributeKey, attributeValue)

  private fun checkIfTransactionMatches(
    envelopeString: String,
    callback: ((SentryTransaction, SentryEnvelopeHeader) -> Boolean),
  ): Boolean {
    val envelope = deserialize(envelopeString) ?: return false
    val transaction = envelope.transaction() ?: return false
    return callback(transaction, envelope.header)
  }

  private fun checkIfProfileMatches(
    envelopeString: String,
    callback: ((ProfileChunk, SentryEnvelopeHeader) -> Boolean),
  ): Boolean {
    val envelope = deserialize(envelopeString) ?: return false
    val chunk = envelope.profileChunk() ?: return false
    return callback(chunk, envelope.header)
  }

  fun ensureErrorReceived(callback: ((SentryEvent) -> Boolean)) {
    ensureEnvelopeReceived(retryCount = 3) { envelopeString ->
      val error = deserialize(envelopeString)?.event() ?: return@ensureEnvelopeReceived false
      val callbackResult = callback(error)
      if (!callbackResult) {
        println("found an error event but it did not match:")
        logObject(error)
      }
      callbackResult
    }
  }

  fun ensureTransactionWithSpanReceived(callback: ((SentrySpan) -> Boolean)) {
    ensureTransactionReceived { transaction, envelopeHeader ->
      transaction.spans.forEach { span ->
        val callbackResult = callback(span)
        if (callbackResult) {
          return@ensureTransactionReceived true
        }
      }
      false
    }
  }

  fun reset() {
    sentryClient.reset()
  }

  fun logObject(obj: Any?) {
    obj ?: return
    // Flush rather than close: closing the writer would close System.out.
    PrintWriter(System.out).apply {
      jsonSerializer.serialize(obj, this)
      flush()
    }
    println()
  }

  private fun deserialize(envelopeString: String): SentryEnvelope? =
    jsonSerializer.deserializeEnvelope(envelopeString.byteInputStream())

  fun <T : Operation.Data> ensureNoErrors(response: ApolloResponse<T>?) {
    response ?: throw RuntimeException("no response")
    assertFalse(response.hasErrors())
  }

  fun <T : Operation.Data> ensureErrorCount(response: ApolloResponse<T>?, errorCount: Int) {
    response ?: throw RuntimeException("no response")
    assertEquals(errorCount, response.errors?.size)
  }

  fun doesTransactionContainSpanWithOp(transaction: SentryTransaction, op: String): Boolean =
    transaction.hasSpanWithOp(op)

  fun doesTransactionContainSpanWithOpAndDescription(
    transaction: SentryTransaction,
    op: String,
    description: String,
  ): Boolean = transaction.hasSpanWithOpAndDescription(op, description)

  fun doesTransactionHaveTraceId(transaction: SentryTransaction, traceId: String): Boolean =
    transaction.hasTraceId(traceId)

  fun doesTransactionHaveOp(transaction: SentryTransaction, op: String): Boolean =
    transaction.hasOp(op)

  fun doesTransactionHave(
    transaction: SentryTransaction,
    op: String,
    featureFlag: FeatureFlag? = null,
  ): Boolean = transaction.hasOp(op, featureFlag)

  fun doesTransactionHaveSpanWith(
    transaction: SentryTransaction,
    op: String,
    featureFlag: FeatureFlag? = null,
    noFeatureFlags: Boolean = false,
  ): Boolean = transaction.hasSpanWithOp(op, featureFlag, noFeatureFlags)

  fun doesEventHaveExceptionMessage(event: SentryEvent, expectedMessage: String): Boolean =
    event.hasExceptionMessage(expectedMessage)

  fun doesEventHaveFlag(event: SentryEvent, flag: String, result: Boolean): Boolean =
    event.hasFlag(flag, result)

  fun findJar(prefix: String, inDir: String = "build/libs"): File {
    val buildDir = File(inDir)
    val jarFiles =
      buildDir.listFiles { _, name -> name.startsWith(prefix) && name.endsWith(".jar") }?.toList()
        ?: emptyList()

    if (jarFiles.isEmpty()) {
      throw AssertionError("No JAR found in ${buildDir.absolutePath}")
    }

    return jarFiles.maxOf { it }
  }

  fun launch(jar: File, env: Map<String, String>, enableOtelAutoConfig: Boolean = false): Process {
    val processBuilderList = mutableListOf("java", "--add-opens", "java.base/java.lang=ALL-UNNAMED")

    if (enableOtelAutoConfig) {
      processBuilderList.add("-Dotel.java.global-autoconfigure.enabled=true")
    }

    processBuilderList.add("-jar")
    processBuilderList.add(jar.absolutePath)

    val processBuilder =
      ProcessBuilder(processBuilderList).inheritIO() // forward i/o to current process

    processBuilder.environment().putAll(env)

    return processBuilder.start()
  }
}
