package io.sentry;

import io.sentry.util.UUIDGenerator;
import io.sentry.util.UUIDStringUtils;

/**
 * SentryUUID is a utility class for generating Sentry-specific ID Strings. It provides methods for
 * generating Sentry IDs and Sentry Span IDs.
 */
public final class SentryUUID {

  private SentryUUID() {
    // A private constructor prevents callers from accidentally instantiating FastUUID objects
  }

  public static String generateSentryId() {
    return UUIDStringUtils.toSentryIdString(UUIDGenerator.randomUUID());
  }

  public static String generateSpanId() {
    return UUIDStringUtils.toSentrySpanIdString(UUIDGenerator.randomHalfLengthUUID());
  }
}
