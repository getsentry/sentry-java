package io.sentry;

import io.sentry.profiling.ProfileRecordingState;
import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Used for performing operations when a transaction is started or ended. */
@ApiStatus.Internal
public interface IContinuousProfiler {
  boolean isRunning();

  void startProfiler(
      final @NotNull ProfileLifecycle profileLifecycle, final @NotNull TracesSampler tracesSampler);

  void stopProfiler(final @NotNull ProfileLifecycle profileLifecycle);

  /**
   * Cancel the profiler and stops it.
   *
   * @param isTerminating whether the profiler is terminating and won't be restarted or not.
   */
  void close(final boolean isTerminating);

  void reevaluateSampling();

  @NotNull
  SentryId getProfilerId();

  @NotNull
  SentryId getChunkId();

  /**
   * Tells whether a profile exists for the given profiler id, covering the given time window.
   *
   * <p>The result of a profiling request can arrive long after a span was tagged with the profiler
   * id, so callers are expected to ask again when they are about to send the data.
   *
   * <p>An implementation that keeps a history of profiling requests answers as follows:
   *
   * <ul>
   *   <li>{@link ProfileRecordingState#RECORDED} if a profile covering part of the window exists
   *   <li>{@link ProfileRecordingState#NOT_RECORDED} if every profiling request covering the window
   *       is known to have produced nothing
   *   <li>{@link ProfileRecordingState#UNKNOWN} if no history is kept, if a request covering the
   *       window has no outcome yet, or if no request covers the window at all
   * </ul>
   *
   * <p>A window no request covers is unknown rather than not recorded, as the window may reach
   * further back than the history, or a back-dated span may read as outside the request it ran in.
   *
   * @param profilerId the profiler id the caller was tagged with
   * @param startTime start of the time window to check
   * @param endTime end of the time window to check
   * @return the state of the profile recording for that window
   */
  @NotNull
  ProfileRecordingState getProfileRecordingState(
      final @NotNull SentryId profilerId,
      final @NotNull SentryDate startTime,
      final @NotNull SentryDate endTime);
}
