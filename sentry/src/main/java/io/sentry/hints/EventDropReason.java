package io.sentry.hints;

import org.jetbrains.annotations.ApiStatus;

/** A reason for which an event was dropped, used for (not to confuse with ClientReports) */
@ApiStatus.Internal
public enum EventDropReason {
  MULTITHREADED_DEDUPLICATION
}
