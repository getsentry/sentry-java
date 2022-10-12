package io.sentry.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@ApiStatus.Internal
public final class HttpHeadersUtils {
    private static final List<String> SENSITIVE_HEADERS =
            Arrays.asList("X-FORWARDED-FOR", "AUTHORIZATION", "COOKIE");

    public static boolean containsSensitiveHeader(final @NotNull String header) {
        return SENSITIVE_HEADERS.contains(header.toUpperCase(Locale.ROOT));
    }
}
