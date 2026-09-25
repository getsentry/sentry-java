package io.sentry.opentelemetry

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.trace.samplers.SamplingDecision
import io.sentry.DataCategory
import io.sentry.IScopes
import io.sentry.SamplingContext
import io.sentry.SentryOptions
import io.sentry.SentryTraceHeader
import io.sentry.SpanId
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import io.sentry.clientreport.DiscardReason
import io.sentry.protocol.SentryId
import kotlin.test.AfterTest
import kotlin.test.Test
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class SentrySamplerTest {
  private val onDiscard = mock<SentryOptions.OnDiscardCallback>()
  private val options =
    SentryOptions().apply {
      tracesSampleRate = 1.0
      profilesSampleRate = 1.0
      tracesSampler = SentryOptions.TracesSamplerCallback { throw IllegalStateException("sampler") }
      this.onDiscard = this@SentrySamplerTest.onDiscard
    }
  private val scopes = mock<IScopes>().also { whenever(it.options).thenReturn(options) }
  private val sampler = SentrySampler(scopes)

  @AfterTest
  fun tearDown() {
    SentryWeakSpanStorage.getInstance().clear()
  }

  @Test
  fun `throwing tracesSampler drops root and reports callback errors alongside sample rate losses`() {
    for (parentSampled in listOf(null, false, true)) {
      val traceId = SentryId()
      val context =
        if (parentSampled == null) Context.root()
        else
          Context.root()
            .with(
              SentryOtelKeys.SENTRY_TRACE_KEY,
              SentryTraceHeader(traceId, SpanId(), parentSampled),
            )
      val result =
        sampler.shouldSample(
          context,
          traceId.toString(),
          "root",
          SpanKind.INTERNAL,
          Attributes.empty(),
          emptyList(),
        ) as SentrySamplingResult

      assertThat(result.decision).isEqualTo(SamplingDecision.RECORD_ONLY)
      assertThat(result.sentryDecision.sampled).isFalse()
      assertThat(result.sentryDecision.profileSampled).isFalse()
    }

    verify(onDiscard, times(3)).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Transaction, 1)
    verify(onDiscard, times(3)).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 1)
    verify(onDiscard, times(3)).execute(DiscardReason.SAMPLE_RATE, DataCategory.Transaction, 1)
    verify(onDiscard, times(3)).execute(DiscardReason.SAMPLE_RATE, DataCategory.Span, 1)
    verifyNoMoreInteractions(onDiscard)
  }

  @Test
  fun `children of failed sampling decisions retain sample rate accounting`() {
    val rootResult =
      sampler.shouldSample(
        Context.root(),
        SentryId().toString(),
        "root",
        SpanKind.INTERNAL,
        Attributes.empty(),
        emptyList(),
      ) as SentrySamplingResult
    val restored = OtelSamplingUtil.extractSamplingDecision(rootResult.attributes)!!
    assertThat(restored.sampled).isFalse()
    assertThat(restored.sampleRand).isEqualTo(rootResult.sentryDecision.sampleRand)

    val parentContext =
      io.opentelemetry.api.trace.SpanContext.create(
        SentryId().toString(),
        SpanId().toString(),
        TraceFlags.getDefault(),
        TraceState.getDefault(),
      )
    val parent =
      mock<IOtelSpanWrapper>().also {
        whenever(it.samplingDecision).thenReturn(restored)
      }
    SentryWeakSpanStorage.getInstance().storeSentrySpan(parentContext, parent)
    val childResult =
      sampler.shouldSample(
        Span.wrap(parentContext).storeInContext(Context.root()),
        parentContext.traceId,
        "child",
        SpanKind.INTERNAL,
        Attributes.empty(),
        emptyList(),
      ) as SentrySamplingResult

    assertThat(childResult.decision).isEqualTo(SamplingDecision.RECORD_ONLY)
    assertThat(childResult.sentryDecision.sampled).isFalse()
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Transaction, 1)
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 1)
    verify(onDiscard).execute(DiscardReason.SAMPLE_RATE, DataCategory.Transaction, 1)
    verify(onDiscard, times(2)).execute(DiscardReason.SAMPLE_RATE, DataCategory.Span, 1)
    verifyNoMoreInteractions(onDiscard)
  }

  @Test
  fun `Sentry API sampling failure reports before forwarding through span factory`() {
    val context = TransactionContext("root", "op")
    context.samplingDecision =
      options.internalTracesSampler.sample(SamplingContext(context, null, 0.0, null))
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Transaction, 1)
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 1)
    verifyNoMoreInteractions(onDiscard)
    val recordingSampler = mock<Sampler>(defaultAnswer = delegatesTo(sampler))
    SdkTracerProvider.builder().setSampler(recordingSampler).build().use { provider ->
      val openTelemetry =
        mock<OpenTelemetry>().also {
          whenever(it.tracerProvider).thenReturn(provider)
        }
      OtelSpanFactory(openTelemetry).createTransaction(context, scopes, TransactionOptions(), null)
      val attributes = argumentCaptor<Attributes>()
      verify(recordingSampler).shouldSample(any(), any(), any(), any(), attributes.capture(), any())
      val restored = OtelSamplingUtil.extractSamplingDecision(attributes.firstValue)!!
      assertThat(restored.sampled).isFalse()
      assertThat(restored.profileSampled).isFalse()
    }

    verifyNoMoreInteractions(onDiscard)
  }

  @Test
  fun `null tracesSampler result uses normal sample rate accounting`() {
    options.tracesSampler = SentryOptions.TracesSamplerCallback { null }
    options.tracesSampleRate = 0.0
    val result =
      sampler.shouldSample(
        Context.root(),
        SentryId().toString(),
        "root",
        SpanKind.INTERNAL,
        Attributes.empty(),
        emptyList(),
      ) as SentrySamplingResult

    assertThat(result.sentryDecision.sampled).isFalse()
    verify(onDiscard).execute(DiscardReason.SAMPLE_RATE, DataCategory.Transaction, 1)
    verify(onDiscard).execute(DiscardReason.SAMPLE_RATE, DataCategory.Span, 1)
    verifyNoMoreInteractions(onDiscard)
  }
}
