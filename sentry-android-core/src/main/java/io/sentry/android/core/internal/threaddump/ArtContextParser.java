package io.sentry.android.core.internal.threaddump;

import io.sentry.protocol.ArtContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ArtContextParser {

  private static final long KB = 1024;
  private static final long MB = 1024 * KB;
  private static final long GB = 1024 * MB;

  private static final String FREE_MEMORY_PREFIX = "Free memory ";
  private static final String FREE_MEMORY_UNTIL_GC_PREFIX = "Free memory until GC ";
  private static final String FREE_MEMORY_UNTIL_OOME_PREFIX = "Free memory until OOME ";
  private static final String TOTAL_MEMORY_PREFIX = "Total memory ";
  private static final String MAX_MEMORY_PREFIX = "Max memory ";
  private static final String TOTAL_TIME_WAITING_FOR_GC_PREFIX =
      "Total time waiting for GC to complete: ";
  private static final String TOTAL_GC_COUNT_PREFIX = "Total GC count: ";
  private static final String TOTAL_GC_TIME_PREFIX = "Total GC time: ";
  private static final String TOTAL_BLOCKING_GC_COUNT_PREFIX = "Total blocking GC count: ";
  private static final String TOTAL_BLOCKING_GC_TIME_PREFIX = "Total blocking GC time: ";
  private static final String TOTAL_PRE_OOME_GC_COUNT_PREFIX = "Total pre-OOME GC count: ";

  private @Nullable ArtContext artContext;

  @Nullable
  ArtContext getArtContext() {
    return artContext;
  }

  void parseLine(final @NotNull String text) {
    if (text.startsWith(FREE_MEMORY_UNTIL_OOME_PREFIX)) {
      getOrCreateArtContext()
          .setFreeMemoryUntilOome(
              parsePrettySize(text.substring(FREE_MEMORY_UNTIL_OOME_PREFIX.length())));
    } else if (text.startsWith(FREE_MEMORY_UNTIL_GC_PREFIX)) {
      getOrCreateArtContext()
          .setFreeMemoryUntilGc(
              parsePrettySize(text.substring(FREE_MEMORY_UNTIL_GC_PREFIX.length())));
    } else if (text.startsWith(FREE_MEMORY_PREFIX)) {
      getOrCreateArtContext()
          .setFreeMemory(parsePrettySize(text.substring(FREE_MEMORY_PREFIX.length())));
    } else if (text.startsWith(TOTAL_MEMORY_PREFIX)) {
      getOrCreateArtContext()
          .setTotalMemory(parsePrettySize(text.substring(TOTAL_MEMORY_PREFIX.length())));
    } else if (text.startsWith(MAX_MEMORY_PREFIX)) {
      getOrCreateArtContext()
          .setMaxMemory(parsePrettySize(text.substring(MAX_MEMORY_PREFIX.length())));
    } else if (text.startsWith(TOTAL_TIME_WAITING_FOR_GC_PREFIX)) {
      getOrCreateArtContext()
          .setGcWaitingTime(parseTimeMs(text.substring(TOTAL_TIME_WAITING_FOR_GC_PREFIX.length())));
    } else if (text.startsWith(TOTAL_GC_TIME_PREFIX)) {
      getOrCreateArtContext()
          .setGcTotalTime(parseTimeMs(text.substring(TOTAL_GC_TIME_PREFIX.length())));
    } else if (text.startsWith(TOTAL_GC_COUNT_PREFIX)) {
      getOrCreateArtContext()
          .setGcTotalCount(parseLongOrNull(text.substring(TOTAL_GC_COUNT_PREFIX.length())));
    } else if (text.startsWith(TOTAL_BLOCKING_GC_TIME_PREFIX)) {
      getOrCreateArtContext()
          .setGcBlockingTime(parseTimeMs(text.substring(TOTAL_BLOCKING_GC_TIME_PREFIX.length())));
    } else if (text.startsWith(TOTAL_BLOCKING_GC_COUNT_PREFIX)) {
      getOrCreateArtContext()
          .setGcBlockingCount(
              parseLongOrNull(text.substring(TOTAL_BLOCKING_GC_COUNT_PREFIX.length())));
    } else if (text.startsWith(TOTAL_PRE_OOME_GC_COUNT_PREFIX)) {
      getOrCreateArtContext()
          .setGcPreOomeCount(
              parseLongOrNull(text.substring(TOTAL_PRE_OOME_GC_COUNT_PREFIX.length())));
    }
  }

  private @NotNull ArtContext getOrCreateArtContext() {
    if (artContext == null) {
      artContext = new ArtContext();
    }
    return artContext;
  }

  /**
   * Matches Android's PrettySize output: number followed by unit with no space, e.g. "3107KB".
   *
   * <p>Counterpart to
   * https://cs.android.com/android/platform/superproject/+/android-latest-release:art/libartbase/base/utils.cc;l=232-251;drc=d0d3deb269b1e14de2ec2707815e38bc95de570c
   */
  private @Nullable Long parsePrettySize(final @NotNull String sizeString) {
    final String trimmed = sizeString.trim();
    try {
      if (trimmed.endsWith("GB")) {
        return Long.parseLong(trimmed.substring(0, trimmed.length() - 2)) * GB;
      } else if (trimmed.endsWith("MB")) {
        return Long.parseLong(trimmed.substring(0, trimmed.length() - 2)) * MB;
      } else if (trimmed.endsWith("KB")) {
        return Long.parseLong(trimmed.substring(0, trimmed.length() - 2)) * KB;
      } else if (trimmed.endsWith("B")) {
        return Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
      }
    } catch (NumberFormatException e) {
      return null;
    }
    return null;
  }

  private static @Nullable Double parseTimeMs(final @NotNull String timeString) {
    final String trimmed = timeString.trim();
    if (trimmed.endsWith("ms")) {
      try {
        // Double.parseDouble is locale-independent (always uses '.' as decimal separator),
        // which matches the ART runtime output format.
        return Double.parseDouble(trimmed.substring(0, trimmed.length() - 2));
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  private static @Nullable Long parseLongOrNull(final @NotNull String value) {
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
