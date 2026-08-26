package io.sentry.hints;

import org.jetbrains.annotations.ApiStatus;

/** A reason for which an event was dropped, used for (not to confuse with ClientReports) */
@ApiStatus.Internal
public enum EventDropReason {
  MULTITHREADED_DEDUPLICATION,
  /** The event matched {@code ignoredExceptionsForType} or {@code ignoredErrors}. */
  IGNORED,
  /** The {@code beforeSend} callback returned {@code null} or threw. */
  BEFORE_SEND,
  /** The event lost the {@code sampleRate} draw. */
  SAMPLE_RATE
}
