package io.sentry.launchdarkly.android

import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.LDValueType
import com.launchdarkly.sdk.android.integrations.EvaluationSeriesContext
import io.sentry.ILogger
import io.sentry.IScopes
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SentryLaunchDarklyAndroidHookTest {

  private lateinit var mockScopes: IScopes
  private lateinit var mockOptions: SentryOptions
  private lateinit var mockLogger: ILogger
  private lateinit var hook: SentryLaunchDarklyAndroidHook

  @BeforeTest
  fun setUp() {
    mockScopes = mock()
    mockOptions = mock()
    mockLogger = mock()
    whenever(mockScopes.options).thenReturn(mockOptions)
    whenever(mockOptions.logger).thenReturn(mockLogger)
    hook = SentryLaunchDarklyAndroidHook(mockScopes)
  }

  @Test
  fun `afterEvaluation with boolean value calls addFeatureFlag`() {
    val flagKey = "test-flag"
    val flagValue = true

    val seriesContext = createSeriesContext(flagKey)

    val ldValue = mock<LDValue>()
    whenever(ldValue.getType()).thenReturn(LDValueType.BOOLEAN)
    whenever(ldValue.booleanValue()).thenReturn(flagValue)

    val evaluationDetail = mock<EvaluationDetail<LDValue>>()
    whenever(evaluationDetail.getValue()).thenReturn(ldValue)

    val seriesData = mutableMapOf<String, Any>()
    seriesData["existingKey"] = "existingValue"

    val result = hook.afterEvaluation(seriesContext, seriesData, evaluationDetail)

    verify(mockScopes).addFeatureFlag(eq(flagKey), eq(flagValue))
    assertEquals(seriesData, result)
    assertEquals("existingValue", result["existingKey"])
  }

  @Test
  fun `afterEvaluation with false boolean value calls addFeatureFlag`() {
    val flagKey = "test-flag"
    val flagValue = false

    val seriesContext = createSeriesContext(flagKey)

    val ldValue = mock<LDValue>()
    whenever(ldValue.getType()).thenReturn(LDValueType.BOOLEAN)
    whenever(ldValue.booleanValue()).thenReturn(flagValue)

    val evaluationDetail = mock<EvaluationDetail<LDValue>>()
    whenever(evaluationDetail.getValue()).thenReturn(ldValue)

    val seriesData = mutableMapOf<String, Any>()
    seriesData["existingKey"] = "existingValue"

    val result = hook.afterEvaluation(seriesContext, seriesData, evaluationDetail)

    verify(mockScopes).addFeatureFlag(eq(flagKey), eq(flagValue))
    assertEquals(seriesData, result)
    assertEquals("existingValue", result["existingKey"])
  }

  @Test
  fun `afterEvaluation with non-boolean value does not call addFeatureFlag`() {
    val flagKey = "test-flag"

    val seriesContext = createSeriesContext(flagKey)

    val ldValue = mock<LDValue>()
    whenever(ldValue.getType()).thenReturn(LDValueType.STRING)

    val evaluationDetail = mock<EvaluationDetail<LDValue>>()
    whenever(evaluationDetail.getValue()).thenReturn(ldValue)

    val seriesData = mutableMapOf<String, Any>()
    seriesData["existingKey"] = "existingValue"

    val result = hook.afterEvaluation(seriesContext, seriesData, evaluationDetail)

    verify(mockScopes, never()).addFeatureFlag(any(), any())
    assertEquals(seriesData, result)
    assertEquals("existingValue", result["existingKey"])
  }

  @Test
  fun `afterEvaluation with null seriesContext returns seriesData`() {
    val evaluationDetail = mock<EvaluationDetail<LDValue>>()
    val seriesData = mutableMapOf<String, Any>()
    seriesData["existingKey"] = "existingValue"

    val result = hook.afterEvaluation(null, seriesData, evaluationDetail)

    verify(mockScopes, never()).addFeatureFlag(any(), any())
    assertEquals(seriesData, result)
    assertEquals("existingValue", result["existingKey"])
  }

  @Test
  fun `afterEvaluation with null evaluationDetail returns seriesData`() {
    val seriesContext = mock<EvaluationSeriesContext>()
    val seriesData = mutableMapOf<String, Any>()
    seriesData["existingKey"] = "existingValue"

    val result = hook.afterEvaluation(seriesContext, seriesData, null)

    verify(mockScopes, never()).addFeatureFlag(any(), any())
    assertEquals(seriesData, result)
    assertEquals("existingValue", result["existingKey"])
  }

  @Test
  fun `afterEvaluation with null flagKey returns seriesData`() {
    val seriesContext = createSeriesContext(null)

    val ldValue = mock<LDValue>()
    whenever(ldValue.getType()).thenReturn(LDValueType.BOOLEAN)

    val evaluationDetail = mock<EvaluationDetail<LDValue>>()
    whenever(evaluationDetail.getValue()).thenReturn(ldValue)

    val seriesData = mutableMapOf<String, Any>()
    seriesData["existingKey"] = "existingValue"

    val result = hook.afterEvaluation(seriesContext, seriesData, evaluationDetail)

    verify(mockScopes, never()).addFeatureFlag(any(), any())
    assertEquals(seriesData, result)
    assertEquals("existingValue", result["existingKey"])
  }

  @Test
  fun `afterEvaluation with null value returns seriesData`() {
    val flagKey = "test-flag"

    val seriesContext = createSeriesContext(flagKey)

    val evaluationDetail = mock<EvaluationDetail<LDValue>>()
    whenever(evaluationDetail.getValue()).thenReturn(null)

    val seriesData = mutableMapOf<String, Any>()
    seriesData["existingKey"] = "existingValue"

    val result = hook.afterEvaluation(seriesContext, seriesData, evaluationDetail)

    verify(mockScopes, never()).addFeatureFlag(any(), any())
    assertEquals(seriesData, result)
    assertEquals("existingValue", result["existingKey"])
  }

  @Test
  fun `afterEvaluation with exception logs error`() {
    val flagKey = "test-flag"

    val seriesContext = createSeriesContext(flagKey)

    val ldValue = mock<LDValue>()
    whenever(ldValue.getType()).thenThrow(RuntimeException("Test exception"))

    val evaluationDetail = mock<EvaluationDetail<LDValue>>()
    whenever(evaluationDetail.getValue()).thenReturn(ldValue)

    val seriesData = mutableMapOf<String, Any>()
    seriesData["existingKey"] = "existingValue"

    val result = hook.afterEvaluation(seriesContext, seriesData, evaluationDetail)

    verify(mockLogger).log(eq(SentryLevel.ERROR), eq("Failed to capture feature flag evaluation"), any())
    verify(mockScopes, never()).addFeatureFlag(any(), any())
    assertEquals(seriesData, result)
    assertEquals("existingValue", result["existingKey"])
  }

  @Test
  fun `afterEvaluation returns original seriesData`() {
    val flagKey = "test-flag"
    val flagValue = true

    val seriesContext = createSeriesContext(flagKey)

    val ldValue = mock<LDValue>()
    whenever(ldValue.getType()).thenReturn(LDValueType.BOOLEAN)
    whenever(ldValue.booleanValue()).thenReturn(flagValue)

    val evaluationDetail = mock<EvaluationDetail<LDValue>>()
    whenever(evaluationDetail.getValue()).thenReturn(ldValue)

    val seriesData = mutableMapOf<String, Any>()
    seriesData["key"] = "value"

    val result = hook.afterEvaluation(seriesContext, seriesData, evaluationDetail)

    verify(mockScopes).addFeatureFlag(eq(flagKey), eq(flagValue))
    assertEquals(seriesData, result)
    assertEquals("value", result["key"])
  }

  private fun createSeriesContext(flagKey: String?): EvaluationSeriesContext {
    val seriesContext = mock<EvaluationSeriesContext>()
    try {
      val field = EvaluationSeriesContext::class.java.getField("flagKey")
      field.isAccessible = true
      field.set(seriesContext, flagKey)
    } catch (e: Exception) {
      throw RuntimeException("Failed to set flagKey field", e)
    }
    return seriesContext
  }
}

