package io.sentry.systemtest.util

import com.apollographql.apollo3.api.ApolloResponse
import com.apollographql.apollo3.api.Operation
import io.sentry.JsonSerializer
import io.sentry.SentryEnvelopeHeader
import io.sentry.SentryEvent
import io.sentry.SentryItemType
import io.sentry.SentryLogEvents
import io.sentry.SentryOptions
import io.sentry.protocol.SentrySpan
import io.sentry.protocol.SentryTransaction
import io.sentry.systemtest.graphql.GraphqlTestClient
import java.io.File
import java.io.PrintWriter
import java.util.concurrent.TimeUnit
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
    val deserializeEnvelope = jsonSerializer.deserializeEnvelope(envelopeString.byteInputStream())
    if (deserializeEnvelope == null) {
      return false
    }

    val envelopeHeader = deserializeEnvelope.header

    val logsItem = deserializeEnvelope.items.firstOrNull { it.header.type == SentryItemType.Log }
    if (logsItem == null) {
      return false
    }

    val logs = logsItem.getLogs(jsonSerializer)
    if (logs == null) {
      return false
    }

    return callback(logs, envelopeHeader)
  }

  fun doesContainLogWithBody(logs: SentryLogEvents, body: String): Boolean {
    val logItem = logs.items.firstOrNull { logItem -> logItem.body == body }
    if (logItem == null) {
      println("Unable to find log item with body $body in logs:")
      logObject(logs)
      return false
    }

    return true
  }

  private fun checkIfTransactionMatches(
    envelopeString: String,
    callback: ((SentryTransaction, SentryEnvelopeHeader) -> Boolean),
  ): Boolean {
    val deserializeEnvelope = jsonSerializer.deserializeEnvelope(envelopeString.byteInputStream())
    if (deserializeEnvelope == null) {
      return false
    }

    val envelopeHeader = deserializeEnvelope.header

    val transactionItem =
      deserializeEnvelope.items.firstOrNull { it.header.type == SentryItemType.Transaction }
    if (transactionItem == null) {
      return false
    }

    val transaction = transactionItem.getTransaction(jsonSerializer)
    if (transaction == null) {
      return false
    }

    return callback(transaction, envelopeHeader)
  }

  fun ensureErrorReceived(callback: ((SentryEvent) -> Boolean)) {
    ensureEnvelopeReceived { envelopeString ->
      val deserializeEnvelope = jsonSerializer.deserializeEnvelope(envelopeString.byteInputStream())
      if (deserializeEnvelope == null) {
        return@ensureEnvelopeReceived false
      }

      val errorItem =
        deserializeEnvelope.items.firstOrNull { it.header.type == SentryItemType.Event }
      if (errorItem == null) {
        return@ensureEnvelopeReceived false
      }

      val error = errorItem.getEvent(jsonSerializer)
      if (error == null) {
        return@ensureEnvelopeReceived false
      }

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
    PrintWriter(System.out).use { jsonSerializer.serialize(obj, it) }
    println()
  }

  fun <T : Operation.Data> ensureNoErrors(response: ApolloResponse<T>?) {
    response ?: throw RuntimeException("no response")
    assertFalse(response.hasErrors())
  }

  fun <T : Operation.Data> ensureErrorCount(response: ApolloResponse<T>?, errorCount: Int) {
    response ?: throw RuntimeException("no response")
    assertEquals(errorCount, response.errors?.size)
  }

  fun doesTransactionContainSpanWithOp(transaction: SentryTransaction, op: String): Boolean {
    val span = transaction.spans.firstOrNull { span -> span.op == op }
    if (span == null) {
      println("Unable to find span with op $op in transaction:")
      logObject(transaction)
      return false
    }

    return true
  }

  fun doesTransactionContainSpanWithOpAndDescription(
    transaction: SentryTransaction,
    op: String,
    description: String,
  ): Boolean {
    val span =
      transaction.spans.firstOrNull { span -> span.op == op && span.description == description }
    if (span == null) {
      println("Unable to find span with op $op and description $description in transaction:")
      logObject(transaction)
      return false
    }

    return true
  }

  fun doesTransactionContainSpanWithDescription(
    transaction: SentryTransaction,
    description: String,
  ): Boolean {
    val span = transaction.spans.firstOrNull { span -> span.description == description }
    if (span == null) {
      println("Unable to find span with description $description in transaction:")
      logObject(transaction)
      return false
    }

    return true
  }

  fun doesTransactionHaveTraceId(transaction: SentryTransaction, traceId: String): Boolean {
    val spanContext = transaction.contexts.trace
    if (spanContext?.traceId?.toString() != traceId) {
      println("Unable to find trace ID $traceId in transaction:")
      logObject(transaction)
      return false
    }

    return true
  }

  fun doesTransactionHaveOp(transaction: SentryTransaction, op: String): Boolean {
    val matches = transaction.contexts.trace?.operation == op
    if (!matches) {
      println("Unable to find transaction with op $op:")
      logObject(transaction)
      return false
    }

    return true
  }

  fun findJar(prefix: String, inDir: String = "build/libs"): File {
    val buildDir = File(inDir)
    val jarFiles =
      buildDir
        .listFiles { _, name -> name.startsWith(prefix) && name.endsWith(".jar") }
        ?.toList() ?: emptyList()

    if (jarFiles.isEmpty()) {
      throw AssertionError("No JAR found in ${buildDir.absolutePath}")
    }

    return jarFiles.maxOf { it }
  }

  fun launch(jar: File, env: Map<String, String>): Process {
    val processBuilder = ProcessBuilder("java", "-jar", jar.absolutePath)
      .inheritIO() // forward i/o to current process

    processBuilder.environment().putAll(env)

    return processBuilder.start()
  }
}
