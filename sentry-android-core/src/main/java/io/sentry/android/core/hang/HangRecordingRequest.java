package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** What a subscriber needs from the recording of one hang interval. Immutable. */
@ApiStatus.Internal
public final class HangRecordingRequest {

  /** Values in memory only. */
  public static final @NotNull HangRecordingRequest DEFAULT = new Builder().build();

  /** This subscriber needs nothing from this interval: no record, no values. */
  public static final @NotNull HangRecordingRequest NONE = new HangRecordingRequest(false, false);

  private final boolean record;
  private final boolean persist;

  private HangRecordingRequest(final boolean record, final boolean persist) {
    this.record = record;
    this.persist = persist;
  }

  /** Whether this subscriber needs the record of this interval. False only for {@link #NONE}. */
  public boolean shouldRecord() {
    return record;
  }

  /** Whether the values of this interval go to the stores. Implies {@link #shouldRecord()}. */
  public boolean shouldPersist() {
    return persist;
  }

  /** Builds requests that record the interval. */
  public static final class Builder {
    private boolean persist;

    public @NotNull Builder setPersist(final boolean persist) {
      this.persist = persist;
      return this;
    }

    public @NotNull HangRecordingRequest build() {
      return new HangRecordingRequest(true, persist);
    }
  }
}
