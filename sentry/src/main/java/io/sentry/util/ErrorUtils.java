package io.sentry.util;

import io.sentry.FilterString;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ErrorUtils {

  /** Checks if an error has been ignored. */
  @ApiStatus.Internal
  public static boolean isIgnored(
      final @Nullable List<FilterString> ignoredErrors, final @NotNull SentryEvent event) {
    if (event == null || ignoredErrors == null || ignoredErrors.isEmpty()) {
      return false;
    }

    final @NotNull Set<String> possibleMessages = new HashSet<>();

    final @Nullable Message eventMessage = event.getMessage();
    if (eventMessage != null) {
      final @Nullable String stringMessage = eventMessage.getMessage();
      if (stringMessage != null) {
        possibleMessages.add(stringMessage);
      }
      final @Nullable String formattedMessage = eventMessage.getFormatted();
      if (formattedMessage != null) {
        possibleMessages.add(formattedMessage);
      }
    }
    final @Nullable Throwable throwable = event.getThrowable();
    if (throwable != null) {
      possibleMessages.add(throwable.toString());
    }

    for (final @NotNull FilterString filter : ignoredErrors) {
      if (possibleMessages.contains(filter.getFilterString())) {
        return true;
      }
    }

    for (final @NotNull FilterString filter : ignoredErrors) {
      for (final @NotNull String message : possibleMessages) {
        if (filter.matches(message)) {
          return true;
        }
      }
    }

    return false;
  }
}
