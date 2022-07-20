package io.sentry.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SampleRateUtil {

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
    if (tracesSampleRate == null) {
      return allowNull;
    }

    return !(tracesSampleRate.isNaN() || tracesSampleRate > 1.0 || tracesSampleRate < 0.0);
  }

  public static boolean isValidProfilesSampleRate(@Nullable Double profilesSampleRate) {
    return isValidProfilesSampleRate(profilesSampleRate, true);
  }

  public static boolean isValidProfilesSampleRate(
      @Nullable Double profilesSampleRate, boolean allowNull) {
    if (profilesSampleRate == null) {
      return allowNull;
    }

    return !(profilesSampleRate.isNaN() || profilesSampleRate > 1.0 || profilesSampleRate < 0.0);
  }
}
