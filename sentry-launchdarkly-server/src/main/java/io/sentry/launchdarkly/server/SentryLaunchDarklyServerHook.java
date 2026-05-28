package io.sentry.launchdarkly.server;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.integrations.EvaluationSeriesContext;
import com.launchdarkly.sdk.server.integrations.Hook;
import io.sentry.IScopes;
import io.sentry.ScopesAdapter;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.SentryLevel;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public final class SentryLaunchDarklyServerHook extends Hook {
  private final IScopes scopes;

  static {
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-launchdarkly-server", BuildConfig.VERSION_NAME);
  }

  public SentryLaunchDarklyServerHook() {
    this(ScopesAdapter.getInstance());
  }

  @VisibleForTesting
  SentryLaunchDarklyServerHook(@NotNull IScopes scopes) {
    super("SentryLaunchDarklyServerHook");
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    addPackageAndIntegrationInfo();
  }

  private void addPackageAndIntegrationInfo() {
    addIntegrationToSdkVersion("LaunchDarkly-Server");
  }

  @Override
  public Map<String, Object> afterEvaluation(
      EvaluationSeriesContext seriesContext,
      Map<String, Object> seriesData,
      EvaluationDetail<LDValue> evaluationDetail) {
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
    } catch (Exception e) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to capture feature flag evaluation", e);
    }

    return seriesData;
  }
}
