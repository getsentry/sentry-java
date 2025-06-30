package io.sentry

import io.sentry.util.SentryRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class TracesSamplerTest {
  class Fixture {
    internal fun getSut(
      tracesSampleRate: Double? = null,
      profilesSampleRate: Double? = null,
      profileSessionSampleRate: Double? = null,
      tracesSamplerCallback: SentryOptions.TracesSamplerCallback? = null,
      profilesSamplerCallback: SentryOptions.ProfilesSamplerCallback? = null,
      logger: ILogger? = null,
    ): TracesSampler {
      val options = SentryOptions()
      if (tracesSampleRate != null) {
        options.tracesSampleRate = tracesSampleRate
      }
      if (profilesSampleRate != null) {
        options.profilesSampleRate = profilesSampleRate
      }
      if (profileSessionSampleRate != null) {
        options.profileSessionSampleRate = profileSessionSampleRate
      }
      if (tracesSamplerCallback != null) {
        options.tracesSampler = tracesSamplerCallback
      }
      if (profilesSamplerCallback != null) {
        options.profilesSampler = profilesSamplerCallback
      }
      if (logger != null) {
        options.isDebug = true
        options.setLogger(logger)
      }
      return TracesSampler(options)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `when tracesSampleRate is set and random returns greater number returns false`() {
    val sampler = fixture.getSut(tracesSampleRate = 0.2, profilesSampleRate = 0.2)
    val samplingDecision =
      sampler.sample(SamplingContext(TransactionContext("name", "op"), null, 0.9, null))
    assertFalse(samplingDecision.sampled)
    assertEquals(0.2, samplingDecision.sampleRate)
    assertEquals(0.9, samplingDecision.sampleRand)
  }

  @Test
  fun `when tracesSampleRate is set and random returns lower number returns true`() {
    val sampler = fixture.getSut(tracesSampleRate = 0.2, profilesSampleRate = 0.2)
    val samplingDecision =
      sampler.sample(SamplingContext(TransactionContext("name", "op"), null, 0.1, null))
    assertTrue(samplingDecision.sampled)
    assertEquals(0.2, samplingDecision.sampleRate)
    assertEquals(0.1, samplingDecision.sampleRand)
  }

  @Test
  fun `when profilesSampleRate is set and random returns greater number returns false`() {
    val sampler = fixture.getSut(tracesSampleRate = 1.0, profilesSampleRate = 0.2)
    val samplingDecision =
      sampler.sample(SamplingContext(TransactionContext("name", "op"), null, 0.9, null))
    assertTrue(samplingDecision.sampled)
    assertFalse(samplingDecision.profileSampled)
    assertEquals(0.2, samplingDecision.profileSampleRate)
    assertEquals(0.9, samplingDecision.sampleRand)
  }

  @Test
  fun `when profilesSampleRate is set and random returns lower number returns true`() {
    val sampler = fixture.getSut(tracesSampleRate = 1.0, profilesSampleRate = 0.2)
    val samplingDecision =
      sampler.sample(SamplingContext(TransactionContext("name", "op"), null, 0.1, null))
    assertTrue(samplingDecision.sampled)
    assertTrue(samplingDecision.profileSampled)
    assertEquals(0.2, samplingDecision.profileSampleRate)
    assertEquals(0.1, samplingDecision.sampleRand)
  }

  @Test
  fun `when trace is not sampled, profile is not sampled`() {
    val sampler = fixture.getSut(tracesSampleRate = 0.0, profilesSampleRate = 1.0)
    val samplingDecision =
      sampler.sample(SamplingContext(TransactionContext("name", "op"), null, 0.3, null))
    assertFalse(samplingDecision.sampled)
    assertFalse(samplingDecision.profileSampled)
    assertEquals(1.0, samplingDecision.profileSampleRate)
    assertEquals(0.3, samplingDecision.sampleRand)
  }

  @Test
  fun `when tracesSampleRate is not set, tracesSampler is set and random returns lower number returns true`() {
    val sampler = fixture.getSut(tracesSamplerCallback = { 0.2 }, profilesSamplerCallback = { 0.2 })
    val samplingDecision =
      sampler.sample(
        SamplingContext(TransactionContext("name", "op"), CustomSamplingContext(), 0.1, null)
      )
    assertTrue(samplingDecision.sampled)
    assertEquals(0.2, samplingDecision.sampleRate)
    assertEquals(0.1, samplingDecision.sampleRand)
  }

  @Test
  fun `when profilesSampleRate is not set, profilesSampler is set and random returns lower number returns true`() {
    val sampler = fixture.getSut(tracesSampleRate = 1.0, profilesSamplerCallback = { 0.2 })
    val samplingDecision =
      sampler.sample(
        SamplingContext(TransactionContext("name", "op"), CustomSamplingContext(), 0.1, null)
      )
    assertTrue(samplingDecision.sampled)
    assertTrue(samplingDecision.profileSampled)
    assertEquals(0.2, samplingDecision.profileSampleRate)
    assertEquals(0.1, samplingDecision.sampleRand)
  }

  @Test
  fun `when tracesSampleRate is not set, tracesSampler is set and random returns greater number returns false`() {
    val sampler = fixture.getSut(tracesSamplerCallback = { 0.2 })
    val samplingDecision =
      sampler.sample(
        SamplingContext(TransactionContext("name", "op"), CustomSamplingContext(), 0.9, null)
      )
    assertFalse(samplingDecision.sampled)
    assertEquals(0.2, samplingDecision.sampleRate)
    assertEquals(0.9, samplingDecision.sampleRand)
  }

  @Test
  fun `when profilesSampleRate is not set, profilesSampler is set and random returns greater number returns false`() {
    val sampler = fixture.getSut(tracesSampleRate = 1.0, profilesSamplerCallback = { 0.2 })
    val samplingDecision =
      sampler.sample(
        SamplingContext(TransactionContext("name", "op"), CustomSamplingContext(), 0.9, null)
      )
    assertTrue(samplingDecision.sampled)
    assertFalse(samplingDecision.profileSampled)
    assertEquals(0.2, samplingDecision.profileSampleRate)
    assertEquals(0.9, samplingDecision.sampleRand)
  }

  @Test
  fun `when profileSessionSampleRate is not set returns false`() {
    val sampler = fixture.getSut()
    val sampled = sampler.sampleSessionProfile(1.0)
    assertFalse(sampled)
  }

  @Test
  fun `when profileSessionSampleRate is set and random returns lower number returns true`() {
    SentryRandom.current().nextDouble()
    val sampler = fixture.getSut(profileSessionSampleRate = 0.2)
    val sampled = sampler.sampleSessionProfile(0.1)
    assertTrue(sampled)
  }

  @Test
  fun `when profileSessionSampleRate is set and random returns greater number returns false`() {
    val sampler = fixture.getSut(profileSessionSampleRate = 0.2)
    val sampled = sampler.sampleSessionProfile(0.9)
    assertFalse(sampled)
  }

  @Test
  fun `when tracesSampler returns null and parentSampled is set sampler uses it as a sampling decision`() {
    val sampler = fixture.getSut(tracesSamplerCallback = null)
    val transactionContextParentSampled = TransactionContext("name", "op")
    transactionContextParentSampled.parentSampled = true
    val samplingDecision =
      sampler.sample(
        SamplingContext(transactionContextParentSampled, CustomSamplingContext(), 0.1, null)
      )
    assertTrue(samplingDecision.sampled)
    assertNull(samplingDecision.sampleRate)
    assertNotNull(samplingDecision.sampleRand)
  }

  @Test
  fun `when profilesSampler returns null and parentSampled is set sampler uses it as a sampling decision`() {
    val sampler = fixture.getSut(tracesSampleRate = 1.0, profilesSamplerCallback = null)
    val transactionContextParentSampled = TransactionContext("name", "op")
    transactionContextParentSampled.setParentSampled(true, true)
    val samplingDecision =
      sampler.sample(
        SamplingContext(transactionContextParentSampled, CustomSamplingContext(), 0.1, null)
      )
    assertTrue(samplingDecision.sampled)
    assertTrue(samplingDecision.profileSampled)
    assertNull(samplingDecision.profileSampleRate)
    assertNotNull(samplingDecision.sampleRand)
  }

  @Test
  fun `when tracesSampler returns null and tracesSampleRate is set sampler uses it as a sampling decision`() {
    val sampler = fixture.getSut(tracesSampleRate = 0.2, tracesSamplerCallback = null)
    val samplingDecision =
      sampler.sample(
        SamplingContext(TransactionContext("name", "op"), CustomSamplingContext(), 0.1, null)
      )
    assertTrue(samplingDecision.sampled)
    assertEquals(0.2, samplingDecision.sampleRate)
    assertEquals(0.1, samplingDecision.sampleRand)
  }

  @Test
  fun `when profilesSampler returns null and profilesSampleRate is set sampler uses it as a sampling decision`() {
    val sampler =
      fixture.getSut(
        tracesSampleRate = 1.0,
        profilesSampleRate = 0.2,
        profilesSamplerCallback = null,
      )
    val samplingDecision =
      sampler.sample(
        SamplingContext(TransactionContext("name", "op"), CustomSamplingContext(), 0.1, null)
      )
    assertTrue(samplingDecision.sampled)
    assertTrue(samplingDecision.profileSampled)
    assertEquals(0.2, samplingDecision.profileSampleRate)
  }

  @Test
  fun `when tracesSampleRate is not set, and tracesSampler is not set returns false`() {
    val sampler = fixture.getSut()
    val samplingDecision =
      sampler.sample(
        SamplingContext(TransactionContext("name", "op"), CustomSamplingContext(), 0.1, null)
      )
    assertFalse(samplingDecision.sampled)
    assertNull(samplingDecision.sampleRate)
    assertEquals(0.1, samplingDecision.sampleRand)
  }

  @Test
  fun `when profilesSampleRate is not set, and profilesSampler is not set returns false`() {
    val sampler = fixture.getSut(tracesSampleRate = 1.0)
    val samplingDecision =
      sampler.sample(
        SamplingContext(TransactionContext("name", "op"), CustomSamplingContext(), 0.1, null)
      )
    assertTrue(samplingDecision.sampled)
    assertFalse(samplingDecision.profileSampled)
    assertNull(samplingDecision.profileSampleRate)
    assertEquals(0.1, samplingDecision.sampleRand)
  }

  @Test
  fun `when parentSampled is set, sampler uses it as a sampling decision`() {
    val sampler = fixture.getSut()
    val transactionContextParentNotSampled = TransactionContext("name", "op")
    transactionContextParentNotSampled.parentSampled = false
    val samplingDecision =
      sampler.sample(
        SamplingContext(transactionContextParentNotSampled, CustomSamplingContext(), 0.1, null)
      )
    assertFalse(samplingDecision.sampled)
    assertNull(samplingDecision.sampleRate)
    assertNotNull(samplingDecision.sampleRand)
    assertFalse(samplingDecision.profileSampled)
    assertNull(samplingDecision.profileSampleRate)

    val transactionContextParentSampled = TransactionContext("name", "op")
    transactionContextParentSampled.setParentSampled(true, true)
    val samplingDecisionParentSampled =
      sampler.sample(
        SamplingContext(transactionContextParentSampled, CustomSamplingContext(), 0.1, null)
      )
    assertTrue(samplingDecisionParentSampled.sampled)
    assertNull(samplingDecisionParentSampled.sampleRate)
    assertNotNull(samplingDecisionParentSampled.sampleRand)
    assertTrue(samplingDecisionParentSampled.profileSampled)
    assertNull(samplingDecisionParentSampled.profileSampleRate)
  }

  @Test
  fun `when parentSampled is not set and parentProfileSampled is set, profile is not sampled`() {
    val sampler = fixture.getSut()
    val transactionContextParentUnsampled = TransactionContext("name", "op")
    transactionContextParentUnsampled.setParentSampled(false, true)
    val samplingDecisionParentSampled =
      sampler.sample(SamplingContext(transactionContextParentUnsampled, CustomSamplingContext()))
    assertFalse(samplingDecisionParentSampled.sampled)
    assertNull(samplingDecisionParentSampled.sampleRate)
    assertFalse(samplingDecisionParentSampled.profileSampled)
    assertNull(samplingDecisionParentSampled.profileSampleRate)
  }

  @Test
  fun `when tracing decision is set on SpanContext, sampler uses it as a sampling decision`() {
    val sampler = fixture.getSut()
    val transactionContextNotSampled = TransactionContext("name", "op")
    transactionContextNotSampled.sampled = false
    val samplingDecision =
      sampler.sample(
        SamplingContext(transactionContextNotSampled, CustomSamplingContext(), 0.1, null)
      )
    assertFalse(samplingDecision.sampled)
    assertNull(samplingDecision.sampleRate)
    assertNotNull(samplingDecision.sampleRand)
    assertFalse(samplingDecision.profileSampled)
    assertNull(samplingDecision.profileSampleRate)

    val transactionContextSampled = TransactionContext("name", "op")
    transactionContextSampled.setSampled(true, true)
    val samplingDecisionContextSampled =
      sampler.sample(SamplingContext(transactionContextSampled, CustomSamplingContext(), 0.1, null))
    assertTrue(samplingDecisionContextSampled.sampled)
    assertNull(samplingDecisionContextSampled.sampleRate)
    assertNotNull(samplingDecisionContextSampled.sampleRand)
    assertTrue(samplingDecisionContextSampled.profileSampled)
    assertNull(samplingDecisionContextSampled.profileSampleRate)

    val transactionContextUnsampledWithProfile = TransactionContext("name", "op")
    transactionContextUnsampledWithProfile.setSampled(false, true)
    val samplingDecisionContextUnsampledWithProfile =
      sampler.sample(
        SamplingContext(transactionContextUnsampledWithProfile, CustomSamplingContext(), 0.1, null)
      )
    assertFalse(samplingDecisionContextUnsampledWithProfile.sampled)
    assertNull(samplingDecisionContextUnsampledWithProfile.sampleRate)
    assertNotNull(samplingDecisionContextUnsampledWithProfile.sampleRand)
    assertFalse(samplingDecisionContextUnsampledWithProfile.profileSampled)
    assertNull(samplingDecisionContextUnsampledWithProfile.profileSampleRate)
  }

  @Test
  fun `when ProfilesSamplerCallback throws an exception then profiling is disabled and an error is logged`() {
    val logger = mock<ILogger>()

    val exception = Exception("faulty ProfilesSamplerCallback")
    val sampler =
      fixture.getSut(
        tracesSampleRate = 1.0,
        profilesSamplerCallback = { throw exception },
        logger = logger,
      )
    val decision =
      sampler.sample(SamplingContext(TransactionContext("name", "op"), null, 0.1, null))
    assertFalse(decision.profileSampled)
    verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
  }

  @Test
  fun `when a profilingRate and a ProfilesSamplerCallback is set but the callback throws an exception then profiling should still be enabled`() {
    val exception = Exception("faulty ProfilesSamplerCallback")
    val sampler =
      fixture.getSut(
        tracesSampleRate = 1.0,
        profilesSampleRate = 1.0,
        profilesSamplerCallback = { throw exception },
      )
    val decision =
      sampler.sample(SamplingContext(TransactionContext("name", "op"), null, 0.0, null))
    assertTrue(decision.profileSampled)
    assertEquals(0.0, decision.sampleRand)
  }

  @Test
  fun `when TracesSamplerCallback throws an exception then tracing is disabled and an error is logged`() {
    val logger = mock<ILogger>()

    val exception = Exception("faulty TracesSamplerCallback")
    val sampler = fixture.getSut(tracesSamplerCallback = { throw exception }, logger = logger)
    val decision =
      sampler.sample(SamplingContext(TransactionContext("name", "op"), null, 0.1, null))
    assertFalse(decision.sampled)
    assertEquals(0.1, decision.sampleRand)
    verify(logger).log(eq(SentryLevel.ERROR), any(), eq(exception))
  }

  @Test
  fun `when a tracesSampleRate and a TracesSamplerCallback is set but the callback throws an exception then tracing should still be enabled`() {
    val exception = Exception("faulty TracesSamplerCallback")
    val sampler =
      fixture.getSut(tracesSampleRate = 1.0, tracesSamplerCallback = { throw exception })
    val decision =
      sampler.sample(SamplingContext(TransactionContext("name", "op"), null, 0.0, null))
    assertTrue(decision.sampled)
    assertEquals(0.0, decision.sampleRand)
  }

  @Test
  fun `attributes can be accessed in callback`() {
    var attributeValue: Any? = null
    val sampler =
      fixture.getSut(
        tracesSamplerCallback = { samplingContext ->
          attributeValue = samplingContext.getAttribute("attr")
          1.0
        }
      )
    val decision =
      sampler.sample(
        SamplingContext(TransactionContext("name", "op"), null, 0.0, mapOf("attr" to "123"))
      )
    assertTrue(decision.sampled)
    assertEquals("123", attributeValue)
  }

  @Test
  fun `non existing attribute returns null in callback`() {
    var attributeValue: Any? = null
    val sampler =
      fixture.getSut(
        tracesSamplerCallback = { samplingContext ->
          attributeValue = samplingContext.getAttribute("i-do-not-exist")
          1.0
        }
      )
    val decision =
      sampler.sample(
        SamplingContext(TransactionContext("name", "op"), null, 0.0, mapOf("attr" to "123"))
      )
    assertTrue(decision.sampled)
    assertNull(attributeValue)
  }

  @Test
  fun `null attributes return null`() {
    var attributeValue: Any? = null
    val sampler =
      fixture.getSut(
        tracesSamplerCallback = { samplingContext ->
          attributeValue = samplingContext.getAttribute("i-do-not-exist")
          1.0
        }
      )
    val decision =
      sampler.sample(SamplingContext(TransactionContext("name", "op"), null, 0.0, null))
    assertTrue(decision.sampled)
    assertNull(attributeValue)
  }
}
