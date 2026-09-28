package io.sentry

import com.google.common.truth.Truth.assertThat
import io.sentry.clientreport.DiscardReason
import io.sentry.exception.ExceptionMechanismException
import io.sentry.exception.SentryCallbackError
import io.sentry.exception.SentryCallbackException
import io.sentry.internal.eventprocessor.SentryEventProcessor
import io.sentry.protocol.Feedback
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryId
import io.sentry.protocol.SentryTransaction
import io.sentry.util.CallbackUtils
import io.sentry.util.SentryCallbackReentrancyGuard
import java.util.concurrent.CompletionException
import kotlin.test.Test
import kotlin.test.assertFails
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
class StrictCallbackModeTest(private val fatal: Boolean) {
  companion object {
    @JvmStatic @Parameterized.Parameters(name = "fatal={0}") fun data() = listOf(false, true)
  }

  private val failure: Throwable =
    if (fatal) LinkageError("private callback data")
    else IllegalStateException("private callback data")
  private val fixture = SentryClientTest.Fixture()
  private val options =
    fixture.sentryOptions.apply {
      isStrictCallbackMode = true
      eventProcessors.clear()
      logs.isEnabled = true
      metrics.isEnabled = true
    }
  private val client = fixture.getSut()
  private val scope = Scope(options).apply { bindClient(this@StrictCallbackModeTest.client) }
  private val scopes = Scopes(scope, Scope(options), Scope(options), "strict-callback-mode-test")

  private fun assertFailure(block: () -> Unit): Throwable {
    val thrown = assertFails(block = block)
    assertThat(thrown)
      .isInstanceOf(
        if (fatal) SentryCallbackError::class.java else SentryCallbackException::class.java
      )
    assertThat(thrown.cause).isSameInstanceAs(failure)
    assertThat(SentryCallbackReentrancyGuard.isActive()).isFalse()
    return thrown
  }

  @Test
  fun `options configuration propagates marked failures`() {
    assertFailure {
      Sentry.init { configured ->
        configured.isStrictCallbackMode = true
        throw failure
      }
    }
  }

  @Test
  fun `transaction finished callback propagates marked failures`() {
    options.tracesSampleRate = 1.0
    val transactionOptions =
      TransactionOptions().apply {
        setTransactionFinishedCallback { throw failure }
      }
    val transaction = scopes.startTransaction(TransactionContext("test", "op"), transactionOptions)
    assertFailure { transaction.finish() }
  }

  @Test
  fun `beforeSend escapes scopes and does not report a loss`() {
    val onDiscard = mock<SentryOptions.OnDiscardCallback>()
    options.onDiscard = onDiscard
    options.setBeforeSend { _, _ -> throw failure }
    assertFailure { scopes.captureEvent(SentryEvent()) }
    verifyNoInteractions(onDiscard, fixture.transport)
  }

  @Test
  fun `callback markers escape before logging even after strict callback mode is disabled`() {
    val logger = mock<ILogger>()
    val onDiscard = mock<SentryOptions.OnDiscardCallback>()
    options.isDebug = true
    options.setLogger(logger)
    options.onDiscard = onDiscard
    val marker =
      if (fatal) SentryCallbackError(failure as Error) else SentryCallbackException(failure)
    options.setBeforeSend { _, _ ->
      clearInvocations(logger)
      options.isStrictCallbackMode = false
      throw marker
    }
    assertThat(assertFails { scopes.captureEvent(SentryEvent()) }).isSameInstanceAs(marker)
    verifyNoInteractions(logger, onDiscard, fixture.transport)
  }

  @Test
  fun `beforeSend escapes captureException and captureMessage`() {
    options.setBeforeSend { _, _ -> throw failure }
    assertFailure { scopes.captureException(IllegalArgumentException()) }
    assertFailure { scopes.captureMessage("message") }
  }

  @Test
  fun `beforeSendTransaction escapes scopes`() {
    options.setBeforeSendTransaction { _, _ -> throw failure }
    val transaction =
      SentryTracer(TransactionContext("test", "op", TracesSamplingDecision(true)), scopes)
    assertFailure { transaction.finish() }
  }

  @Test
  fun `beforeSendFeedback escapes scopes`() {
    options.setBeforeSendFeedback { _, _ -> throw failure }
    assertFailure { scopes.captureFeedback(Feedback("feedback")) }
  }

  @Test
  fun `beforeSendReplay escapes scopes`() {
    options.setBeforeSendReplay { _, _ -> throw failure }
    assertFailure { scopes.captureReplay(SentryReplayEvent(), Hint()) }
  }

  @Test
  fun `beforeSendLog escapes logger API`() {
    options.logs.setBeforeSend { throw failure }
    assertFailure { scopes.logger().log(SentryLogLevel.INFO, "message") }
  }

  @Test
  fun `beforeSendMetric escapes metrics API`() {
    options.metrics.setBeforeSend { _, _ -> throw failure }
    assertFailure { scopes.metrics().count("metric") }
  }

  @Test
  fun `beforeEnvelope escapes scopes`() {
    options.setBeforeEnvelopeCallback { _, _ -> throw failure }
    assertFailure { scopes.captureEvent(SentryEvent()) }
    verifyNoInteractions(fixture.transport)
  }

  @Test
  fun `beforeBreadcrumb escapes and is not added`() {
    options.setBeforeBreadcrumb { _, _ -> throw failure }
    assertFailure { scopes.addBreadcrumb(Breadcrumb("private")) }
    assertThat(scope.breadcrumbs).isEmpty()
  }

  @Test
  fun `tracesSampler and profilesSampler escape without fallback`() {
    options.tracesSampleRate = 1.0
    options.setTracesSampler { throw failure }
    assertFailure { scopes.startTransaction("test", "op") }
    options.tracesSampler = null
    options.setProfilesSampler { throw failure }
    assertFailure { scopes.startTransaction("test", "op") }
  }

  @Test
  fun `beforeErrorSampling escapes without capturing replay`() {
    options.sessionReplay.setBeforeErrorSampling { _, _ -> throw failure }
    val event =
      SentryEvent().apply {
        exceptions =
          listOf(io.sentry.protocol.SentryException().apply { type = "IllegalArgumentException" })
      }
    assertFailure { scopes.captureEvent(event) }
  }

  @Test
  fun `onDiscard escapes its enclosing recorder catches`() {
    options.setOnDiscard { _, _, _ -> throw failure }
    assertFailure {
      options.clientReportRecorder.recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Error)
    }
    options.setBeforeSend { _, _ -> null }
    assertFailure { scopes.captureEvent(SentryEvent()) }
  }

  @Test
  fun `scope callbacks escape and restore current scopes`() {
    val previous = Sentry.getCurrentScopes()
    assertFailure { scopes.withScope { throw failure } }
    assertThat(Sentry.getCurrentScopes()).isSameInstanceAs(previous)
    assertFailure { scopes.withIsolationScope { throw failure } }
    assertThat(Sentry.getCurrentScopes()).isSameInstanceAs(previous)
    assertFailure { scopes.configureScope { throw failure } }
    assertFailure { scopes.captureEvent(SentryEvent(), ScopeCallback { throw failure }) }
  }

  @Test
  fun `onOversizedEvent escapes the size limiter and scopes catches`() {
    options.setOnOversizedEvent { _, _ -> throw failure }
    options.isEnableEventSizeLimiting = true
    val event =
      SentryEvent().apply {
        setExtra("large", "x".repeat(SentryOptions.MAX_EVENT_SIZE_BYTES.toInt() + 1))
      }
    assertFailure { scopes.captureEvent(event) }
  }

  @Test
  fun `all customer processor types propagate failures`() {
    val processor = mock<EventProcessor>()
    doAnswer { throw failure }.whenever(processor).process(any<SentryEvent>(), any())
    doAnswer { throw failure }.whenever(processor).process(any<SentryTransaction>(), any())
    doAnswer { throw failure }.whenever(processor).process(any<SentryReplayEvent>(), any())
    doAnswer { throw failure }.whenever(processor).process(any<SentryLogEvent>())
    doAnswer { throw failure }.whenever(processor).process(any<SentryMetricsEvent>(), any())
    for (onScope in listOf(false, true)) {
      if (onScope) scope.addEventProcessor(processor) else options.addEventProcessor(processor)
      assertFailure { scopes.captureEvent(SentryEvent()) }
      assertFailure { scopes.captureFeedback(Feedback("feedback")) }
      // Replays only run options-level processors.
      if (!onScope) assertFailure { scopes.captureReplay(SentryReplayEvent(), Hint()) }
      assertFailure {
        client.captureTransaction(SentryTransaction(fixture.sentryTracer), scope, null)
      }
      assertFailure { scopes.logger().log(SentryLogLevel.INFO, "message") }
      assertFailure { scopes.metrics().count("metric") }
      options.eventProcessors.clear()
    }
  }

  @Test
  fun `SDK processor failures are not converted but nested callback markers escape`() {
    val processor = mock<SentryEventProcessor>()
    doAnswer { throw failure }.whenever(processor).process(any<SentryEvent>(), any())
    options.addEventProcessor(processor)
    val event = SentryEvent()
    assertThat(scopes.captureEvent(event)).isEqualTo(event.eventId)
    val marker = assertFailure { CallbackUtils.rethrowIfStrictCallbackMode(options, failure) }
    doAnswer { throw marker }.whenever(processor).process(any<SentryEvent>(), any())
    assertThat(assertFails { scopes.captureEvent(SentryEvent()) }).isSameInstanceAs(marker)
  }

  @Test
  fun `capture silently excludes nested callback failures even with strict callback mode disabled`() {
    val marker = assertFailure { CallbackUtils.rethrowIfStrictCallbackMode(options, failure) }
    val throwable = CompletionException(IllegalArgumentException("outer", marker))
    val mechanismWrapper =
      ExceptionMechanismException(Mechanism(), throwable, Thread.currentThread())
    val callback = mock<ScopeCallback>()
    val processor = mock<EventProcessor>()
    val beforeSend = mock<SentryOptions.BeforeSendCallback>()
    val onDiscard = mock<SentryOptions.OnDiscardCallback>()
    val logger = mock<ILogger>()
    options.addEventProcessor(processor)
    options.beforeSend = beforeSend
    options.onDiscard = onDiscard
    options.setLogger(logger)
    options.isStrictCallbackMode = false
    clearInvocations(logger, fixture.transport)
    for (wrapped in listOf(marker, throwable, mechanismWrapper)) {
      assertThat(scopes.captureException(wrapped, callback)).isEqualTo(SentryId.EMPTY_ID)
      assertThat(scopes.captureEvent(SentryEvent(wrapped), callback)).isEqualTo(SentryId.EMPTY_ID)
      assertThat(client.captureEvent(SentryEvent(wrapped), scope)).isEqualTo(SentryId.EMPTY_ID)
    }
    verifyNoInteractions(callback, processor, beforeSend, onDiscard, logger, fixture.transport)
  }

  @Test
  fun `default behavior still isolates failures and intentional drops remain drops`() {
    options.isStrictCallbackMode = false
    options.setBeforeSend { _, _ -> throw failure }
    assertThat(scopes.captureEvent(SentryEvent())).isEqualTo(SentryId.EMPTY_ID)
    options.isStrictCallbackMode = true
    options.setBeforeSend { _, _ -> null }
    assertThat(scopes.captureEvent(SentryEvent())).isEqualTo(SentryId.EMPTY_ID)
  }
}
