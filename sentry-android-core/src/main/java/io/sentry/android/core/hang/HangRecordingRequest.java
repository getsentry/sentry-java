package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** What a subscriber needs from the recording of one hang interval. Immutable. */
@ApiStatus.Internal
public final class HangRecordingRequest {

  /** Values in memory only. */
  public static final @NotNull HangRecordingRequest DEFAULT = new Builder().build();

  private final boolean persist;

  private HangRecordingRequest(final @NotNull Builder builder) {
    this.persist = builder.persist;
  }

  /** Whether the values of this interval go to the stores. */
  public boolean shouldPersist() {
    return persist;
  }

  public static final class Builder {
    private boolean persist;

    public @NotNull Builder setPersist(final boolean persist) {
      this.persist = persist;
      return this;
    }

    public @NotNull HangRecordingRequest build() {
      return new HangRecordingRequest(this);
    }
  }
}
