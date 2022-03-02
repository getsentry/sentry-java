package io.sentry.util;

import io.sentry.hints.ApplyScopeData;
import io.sentry.hints.Cached;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Util class for Applying or not scope's data to an event */
@ApiStatus.Internal
public final class HintUtils {

  private HintUtils() {}

  /**
   * Scope's data should be applied if: Hint is of the type ApplyScopeData or Hint is not Cached
   * (this includes a null hint)
   *
   * @param hint the hint
   * @return true if it should apply scope's data or false otherwise
   */
  public static boolean shouldApplyScopeData(final @Nullable Map<String, Object> hint) {
    Object SentrySdkHint = getSentrySdkHint(hint);
    return (!(SentrySdkHint instanceof Cached) || (SentrySdkHint instanceof ApplyScopeData));
  }

  public static @Nullable Object getSentrySdkHint(final @Nullable Map<String, Object> hint) {
    if (hint == null) {
      return null;
    }
    return hint.get("Sentry:TypeCheckHint");
  }
}
