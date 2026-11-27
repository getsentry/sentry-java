package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;

/** What a subscriber needs from the recording of one hang. Immutable. */
@ApiStatus.Internal
public final class HangRecordingRequest {

  private final boolean record;
  private final boolean persist;

  public HangRecordingRequest(final boolean record, final boolean persist) {
    this.record = record;
    this.persist = persist;
  }

  /** Whether this subscriber gets the record of this hang. */
  public boolean shouldRecord() {
    return record;
  }

  /** Whether the values of this hang also go to the stores. Has no effect without a record. */
  public boolean shouldPersist() {
    return persist;
  }
}
