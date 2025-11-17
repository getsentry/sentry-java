package io.sentry.openfeature;

import dev.openfeature.sdk.BooleanHook;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.FlagValueType;
import dev.openfeature.sdk.HookContext;
import io.sentry.IScopes;
import io.sentry.ScopesAdapter;
import io.sentry.SentryLevel;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public final class SentryOpenFeatureHook implements BooleanHook {
  private final IScopes scopes;

  public SentryOpenFeatureHook() {
    this(ScopesAdapter.getInstance());
  }

  @VisibleForTesting
  SentryOpenFeatureHook(@NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
  }

  @Override
  public void after(
      final @Nullable HookContext<Boolean> context,
      final @Nullable FlagEvaluationDetails<Boolean> details,
      final @Nullable Map<String, Object> hints) {
    if (context == null || details == null) {
      return;
    }
    try {
      final @Nullable String flagKey = details.getFlagKey();
      final @Nullable FlagValueType type = context.getType();
      final @Nullable Object value = details.getValue();

      if (flagKey == null || type == null || value == null) {
        return;
      }

      if (!FlagValueType.BOOLEAN.equals(type)) {
        return;
      }

      if (!(value instanceof Boolean)) {
        return;
      }
      final @NotNull Boolean flagValue = (Boolean) value;

      scopes.addFeatureFlag(flagKey, flagValue);
    } catch (Exception e) {
      scopes
          .getOptions()
          .getLogger()
          .log(SentryLevel.ERROR, "Failed to capture feature flag evaluation", e);
    }
  }
}
