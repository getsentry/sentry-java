package io.sentry.util;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class HttpUtils {
  private static final List<String> SENSITIVE_HEADERS =
      Arrays.asList("X-FORWARDED-FOR", "AUTHORIZATION", "COOKIE");

  public static boolean containsSensitiveHeader(final @NotNull String header) {
    return SENSITIVE_HEADERS.contains(header.toUpperCase(Locale.ROOT));
  }
}
