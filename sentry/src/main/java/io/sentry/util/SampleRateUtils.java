package io.sentry.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SampleRateUtils {

  public static boolean isValidSampleRate(@Nullable Double sampleRate) {
    return isValidSampleRate(sampleRate, true);
  }

  public static boolean isValidSampleRate(@Nullable Double sampleRate, boolean allowNull) {
    if (sampleRate == null) {
      return allowNull;
    }

    return !(sampleRate.isNaN() || sampleRate > 1.0 || sampleRate <= 0.0);
  }

  public static boolean isValidTracesSampleRate(@Nullable Double tracesSampleRate) {
    return isValidTracesSampleRate(tracesSampleRate, true);
  }

  public static boolean isValidTracesSampleRate(
      @Nullable Double tracesSampleRate, boolean allowNull) {
    return isValidRate(tracesSampleRate, allowNull);
  }

  public static boolean isValidProfilesSampleRate(@Nullable Double profilesSampleRate) {
    return isValidRate(profilesSampleRate, true);
  }

  private static boolean isValidRate(final @Nullable Double rate, final boolean allowNull) {
    if (rate == null) {
      return allowNull;
    }
    return !rate.isNaN() && rate >= 0.0 && rate <= 1.0;
  }
}
