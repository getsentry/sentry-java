package io.sentry.android.core.internal.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class ClassUtil {

  public static @Nullable String getClassName(final @Nullable Object object) {
    if (object == null) {
      return null;
    }
    final @Nullable String canonicalName = object.getClass().getCanonicalName();
    if (canonicalName != null) {
      return canonicalName;
    }
    return object.getClass().getSimpleName();
  }
}
