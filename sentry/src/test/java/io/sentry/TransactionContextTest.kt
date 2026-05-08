package io.sentry

import io.sentry.protocol.SentryId
import io.sentry.protocol.TransactionNameSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock

class TransactionContextTest {

  @Test
  fun `when created using primary constructor, sampling decision and parent sampling are not set`() {
    val context = TransactionContext("name", "op")
    assertNull(context.sampled)
    assertNull(context.profileSampled)
    assertNull(context.parentSampled)
    assertEquals("name", context.name)
    assertEquals("op", context.op)
    assertFalse(context.isForNextAppStart)
  }

  @Test
  fun `when context is created from propagation context, parent sampling decision of false is set from trace header`() {
    val logger = mock<ILogger>()
    val propagationContext =
      PropagationContext.fromHeaders(
        logger,
        SentryTraceHeader(SentryId(), SpanId(), false).value,
        "sentry-trace_id=a,sentry-transaction=sentryTransaction,sentry-sample_rate=0.3",
        null,
      )
    val context = TransactionContext.fromPropagationContext(propagationContext)
    assertNull(context.sampled)
    assertNull(context.profileSampled)
    assertFalse(context.parentSampled!!)
    assertEquals(0.3, context.parentSamplingDecision!!.sampleRate)
    assertFalse(context.isForNextAppStart)
  }

  @Test
  fun `when context is created from propagation context, parent sampling decision of false is set from trace header if no sample rate is available`() {
    val logger = mock<ILogger>()
    val propagationContext =
      PropagationContext.fromHeaders(
        logger,
        SentryTraceHeader(SentryId(), SpanId(), false).value,
        "sentry-trace_id=a,sentry-transaction=sentryTransaction",
        null,
      )
    val context = TransactionContext.fromPropagationContext(propagationContext)
    assertNull(context.sampled)
    assertNull(context.profileSampled)
    assertFalse(context.parentSampled!!)
    assertNull(context.parentSamplingDecision!!.sampleRate)
    assertFalse(context.isForNextAppStart)
  }

  @Test
  fun `when context is created from propagation context, parent sampling decision of true is set from trace header`() {
    val logger = mock<ILogger>()
    val propagationContext =
      PropagationContext.fromHeaders(
        logger,
        SentryTraceHeader(SentryId(), SpanId(), true).value,
        "sentry-trace_id=a,sentry-transaction=sentryTransaction,sentry-sample_rate=0.3",
        null,
      )
    val context = TransactionContext.fromPropagationContext(propagationContext)
    assertNull(context.sampled)
    assertNull(context.profileSampled)
    assertTrue(context.parentSampled!!)
    assertEquals(0.3, context.parentSamplingDecision!!.sampleRate)
    assertFalse(context.isForNextAppStart)
  }

  @Test
  fun `when context is created from propagation context, parent sampling decision of true is set from trace header if no sample rate is available`() {
    val logger = mock<ILogger>()
    val propagationContext =
      PropagationContext.fromHeaders(
        logger,
        SentryTraceHeader(SentryId(), SpanId(), true).value,
        "sentry-trace_id=a,sentry-transaction=sentryTransaction",
        null,
      )
    val context = TransactionContext.fromPropagationContext(propagationContext)
    assertNull(context.sampled)
    assertNull(context.profileSampled)
    assertTrue(context.parentSampled!!)
    assertNull(context.parentSamplingDecision!!.sampleRate)
    assertFalse(context.isForNextAppStart)
  }

  @Test
  fun `fromPropagationContextAsRoot copies non trace state`() {
    val propagationBaggage = Baggage(NoOpLogger.getInstance())
    propagationBaggage.sampleRand = 0.42
    val propagationContext =
      PropagationContext(
        SentryId("75302ac48a024bde9a3b3734a82e36c8"),
        SpanId("2000000000000000"),
        SpanId("1000000000000000"),
        propagationBaggage,
        true,
      )
    val samplingDecision = TracesSamplingDecision(true, 0.3, true, 0.4)
    val transactionContext = TransactionContext("name", "op", samplingDecision)
    transactionContext.transactionNameSource = TransactionNameSource.ROUTE
    transactionContext.description = "description"
    transactionContext.status = SpanStatus.OK
    transactionContext.origin = "auto.test"
    transactionContext.instrumenter = Instrumenter.OTEL
    transactionContext.isForNextAppStart = true
    transactionContext.profilerId = SentryId("12345678123456781234567812345678")
    transactionContext.setTag("tag-key", "tag-value")
    transactionContext.setData("data-key", "data-value")
    transactionContext.unknown = mapOf("unknown-key" to "unknown-value")
    transactionContext.addFeatureFlag("feature-flag", true)

    val context =
      TransactionContext.fromPropagationContextAsRoot(propagationContext, transactionContext)

    assertEquals(propagationContext.traceId, context.traceId)
    assertEquals(transactionContext.spanId, context.spanId)
    assertNull(context.parentSpanId)
    assertEquals("name", context.name)
    assertEquals(TransactionNameSource.ROUTE, context.transactionNameSource)
    assertEquals("op", context.operation)
    assertEquals("description", context.description)
    assertEquals(SpanStatus.OK, context.status)
    assertEquals("auto.test", context.origin)
    assertEquals(Instrumenter.OTEL, context.instrumenter)
    assertTrue(context.isForNextAppStart)
    assertEquals(SentryId("12345678123456781234567812345678"), context.profilerId)
    assertEquals("tag-value", context.tags["tag-key"])
    assertEquals("data-value", context.data["data-key"])
    assertEquals("unknown-value", context.unknown!!["unknown-key"])
    assertEquals(true, context.sampled)
    assertEquals(0.3, context.samplingDecision!!.sampleRate)
    assertEquals(true, context.profileSampled)
    assertEquals(0.4, context.samplingDecision!!.profileSampleRate)
    assertEquals(0.42, context.baggage!!.sampleRand)
    assertEquals(propagationContext.traceId.toString(), context.baggage!!.traceId)
    assertNull(context.featureFlagBuffer.featureFlags)
  }

  @Test
  fun `setForNextAppStart sets the isForNextAppStart flag`() {
    val context = TransactionContext("name", "op")
    context.isForNextAppStart = true
    assertTrue(context.isForNextAppStart)
  }

  @Test
  fun `when passing null baggage creates a new one`() {
    val context = TransactionContext(SentryId(), SpanId(), null, null, null)
    assertNotNull(context.baggage)
    assertNotNull(context.baggage?.sampleRand)
  }

  @Test
  fun `when passing null baggage creates a new one and uses parent sampling decision`() {
    val context =
      TransactionContext(SentryId(), SpanId(), null, TracesSamplingDecision(true, 0.1, 0.2), null)
    assertNotNull(context.baggage)
    assertEquals(0.2, context.baggage?.sampleRand!!, 0.0001)
  }

  @Test
  fun `when using few param ctor creates a new baggage`() {
    val context = TransactionContext("name", "op")
    assertNotNull(context.baggage)
    assertNotNull(context.baggage?.sampleRand)
  }

  @Test
  fun `when using few param ctor creates a new baggage and uses sampling decision`() {
    val context =
      TransactionContext(
        "name",
        TransactionNameSource.CUSTOM,
        "op",
        TracesSamplingDecision(true, 0.1, 0.2),
      )
    assertNotNull(context.baggage)
    assertEquals(0.2, context.baggage?.sampleRand!!, 0.0001)
  }
}
