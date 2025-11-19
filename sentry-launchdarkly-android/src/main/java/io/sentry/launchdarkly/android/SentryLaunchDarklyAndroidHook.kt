package io.sentry.launchdarkly.android

import com.launchdarkly.sdk.EvaluationDetail
import com.launchdarkly.sdk.LDValue
import com.launchdarkly.sdk.LDValueType
import com.launchdarkly.sdk.android.integrations.EvaluationSeriesContext
import com.launchdarkly.sdk.android.integrations.Hook
import io.sentry.IScopes
import io.sentry.ScopesAdapter
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion

public class SentryLaunchDarklyAndroidHook : Hook {
  private val scopes: IScopes

  private companion object {
    init {
      SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-launchdarkly-android", BuildConfig.VERSION_NAME)
    }
  }

  public constructor() : this(ScopesAdapter.getInstance())

  public constructor(scopes: IScopes) : super("SentryLaunchDarklyAndroidHook") {
    this.scopes = scopes
    addPackageAndIntegrationInfo()
  }

  private fun addPackageAndIntegrationInfo() {
    addIntegrationToSdkVersion("LaunchDarkly-Android")
  }

  @Suppress("TooGenericExceptionCaught")
  override fun afterEvaluation(
    seriesContext: EvaluationSeriesContext?,
    seriesData: Map<String, Any>?,
    evaluationDetail: EvaluationDetail<LDValue>?,
  ): Map<String, Any>? {
    try {
      val flagKey: String? = seriesContext?.flagKey
      val value: LDValue? = evaluationDetail?.value

      if (flagKey != null && value != null && LDValueType.BOOLEAN == value.type) {
        val flagValue: Boolean = value.booleanValue()
        scopes.addFeatureFlag(flagKey, flagValue)
      }
    } catch (e: Throwable) {
      scopes.options.logger.log(SentryLevel.ERROR, "Failed to capture feature flag evaluation", e)
    }

    return seriesData
  }
}
