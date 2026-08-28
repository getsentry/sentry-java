package io.sentry.android.core.internal.profiling;

import io.sentry.SentryDate;
import io.sentry.profiling.ProfileRecordingState;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A profile chunk that was started, together with the outcome of its collection, so that spans
 * tagged with a profiler id can find out whether a profile covering them exists.
 *
 * <p>The profiler that produces the chunk writes the outcome from an OS binder thread or from the
 * executor thread, while the thread that finishes a transaction reads it. The recording state is
 * therefore guarded by the record's monitor, as two writers must not lose a reported failure
 * between them. The end timestamp only ever has one writer, so it is volatile.
 */
@ApiStatus.Internal
public final class ChunkRecord {

  private final @NotNull SentryId profilerId;
  private final @NotNull SentryDate startTimestamp;
  private volatile @Nullable SentryDate endTimestamp = null;
  private @NotNull ProfileRecordingState recordingState = ProfileRecordingState.UNKNOWN;

  public ChunkRecord(final @NotNull SentryId profilerId, final @NotNull SentryDate startTimestamp) {
    this.profilerId = profilerId;
    this.startTimestamp = startTimestamp;
  }

  /** Marks the end of the chunk. Its outcome is only known once the trace file is collected. */
  public void setEndTimestamp(final @NotNull SentryDate endTimestamp) {
    this.endTimestamp = endTimestamp;
  }

  public boolean hasEnded() {
    return endTimestamp != null;
  }

  /**
   * Marks the outcome of the chunk. {@link ProfileRecordingState#NOT_RECORDED} is final: once it is
   * known that no trace file exists, a result the OS delivers late cannot revive the chunk.
   */
  public synchronized void setRecordingState(final @NotNull ProfileRecordingState recordingState) {
    if (this.recordingState == ProfileRecordingState.NOT_RECORDED) {
      return;
    }
    this.recordingState = recordingState;
  }

  public synchronized @NotNull ProfileRecordingState getRecordingState() {
    return recordingState;
  }

  public @NotNull SentryId getProfilerId() {
    return profilerId;
  }

  public @NotNull SentryDate getStartTimestamp() {
    return startTimestamp;
  }

  public boolean overlaps(final @NotNull SentryDate startTime, final @NotNull SentryDate endTime) {
    if (endTime.isBefore(startTimestamp)) {
      return false;
    }
    // A chunk that is still running has no end yet, and covers everything from its start on
    final @Nullable SentryDate endTimestamp = this.endTimestamp;
    return endTimestamp == null || !startTime.isAfter(endTimestamp);
  }
}
