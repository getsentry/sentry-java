package io.sentry;

import java.net.URI;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DsnUtil {

  public static boolean urlContainsDsnHost(@Nullable SentryOptions options, @Nullable String url) {
    if (options == null) {
      return false;
    }

    if (url == null) {
      return false;
    }

    final @Nullable String dsnString = options.getDsn();
    if (dsnString == null) {
      return false;
    }

    final @NotNull Dsn dsn = options.retrieveParsedDsn();
    final @NotNull URI sentryUri = dsn.getSentryUri();
    final @Nullable String dsnHost = sentryUri.getHost();

    if (dsnHost == null) {
      return false;
    }

    final @NotNull String lowerCaseHost = dsnHost.toLowerCase(Locale.ROOT);
    final int dsnPort = sentryUri.getPort();

    if (dsnPort > 0) {
      return url.toLowerCase(Locale.ROOT).contains(lowerCaseHost + ":" + dsnPort);
    } else {
      return url.toLowerCase(Locale.ROOT).contains(lowerCaseHost);
    }
  }
}
