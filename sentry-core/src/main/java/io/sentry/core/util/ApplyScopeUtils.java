package io.sentry.core.util;

import io.sentry.core.hints.ApplyScopeData;
import io.sentry.core.hints.Cached;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Util class for Applying or not scope's data to an event */
@ApiStatus.Internal
public final class ApplyScopeUtils {

  /**
   * Scope's data should be applied if: Hint is of the type ApplyScopeData or Hint is not Cached
   * (this includes a null hint)
   *
   * @param hint the hint
   * @return true if it should apply scope's data or false otherwise
   */
  public static boolean shouldApplyScopeData(final @Nullable Object hint) {
    return (!(hint instanceof Cached) || (hint instanceof ApplyScopeData));
  }
}
