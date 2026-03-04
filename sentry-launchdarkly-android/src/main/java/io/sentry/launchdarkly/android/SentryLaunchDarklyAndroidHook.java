package io.sentry.launchdarkly.android;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.android.integrations.EvaluationSeriesContext;
import com.launchdarkly.sdk.android.integrations.Hook;
import io.sentry.IScopes;
import io.sentry.ScopesAdapter;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import io.sentry.util.IntegrationUtils;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryLaunchDarklyAndroidHook extends Hook {
  private final IScopes scopes;

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-launchdarkly-android", BuildConfig.VERSION_NAME);
  }

  public SentryLaunchDarklyAndroidHook() {
    this(ScopesAdapter.getInstance());
  }

  public SentryLaunchDarklyAndroidHook(final @NotNull IScopes scopes) {
    super("SentryLaunchDarklyAndroidHook");
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    addPackageAndIntegrationInfo();
  }

  private void addPackageAndIntegrationInfo() {
    IntegrationUtils.addIntegrationToSdkVersion("LaunchDarkly-Android");
  }

  @Override
  public Map<String, Object> afterEvaluation(
      final EvaluationSeriesContext seriesContext,
      final Map<String, Object> seriesData,
      final EvaluationDetail<LDValue> evaluationDetail) {
    if (evaluationDetail == null || seriesContext == null) {
      return seriesData;
    }

    try {
      final @Nullable String flagKey = seriesContext.flagKey;
      final @Nullable LDValue value = evaluationDetail.getValue();

      if (flagKey == null || value == null) {
        return seriesData;
      }

      if (LDValueType.BOOLEAN.equals(value.getType())) {
        final boolean flagValue = value.booleanValue();
        scopes.addFeatureFlag(flagKey, flagValue);
      }
    } catch (final Exception e) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to capture feature flag evaluation", e);
    }

    return seriesData;
  }
}
