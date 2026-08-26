package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface ReplayController extends IReplayApi {
  /**
   * Handles app foregrounding. When a new app session begins, stops any previous replay and starts
   * a newly sampled one.
   */
  void onAppForegrounded(boolean startNewSession);

  /**
   * Handles app backgrounding with a temporary lifecycle pause. Unlike {@link #pause()}, this pause
   * is automatically resumed on foreground and does not override an explicit user pause.
   */
  void onAppBackgrounded();

  boolean isRecording();

  /**
   * Captures replay data for an event and returns its ID, or {@link SentryId#EMPTY_ID} if no replay
   * was captured. In buffer mode, sends the buffered replay and continues in session mode. In
   * session mode, the replay is already uploaded continuously, so this does not force an immediate
   * segment upload; use {@link #flush()} for that.
   */
  @NotNull
  SentryId captureReplay(@Nullable Boolean isTerminating);

  @NotNull
  SentryId getReplayId();

  void setBreadcrumbConverter(@NotNull ReplayBreadcrumbConverter converter);

  @NotNull
  ReplayBreadcrumbConverter getBreadcrumbConverter();

  boolean isDebugMaskingOverlayEnabled();

  /**
   * Registers a trace ID to be associated with the current replay. This is called when a
   * transaction is captured while replay is recording, to enable searching for replays by trace ID.
   *
   * @param traceId the trace ID to associate with the current replay
   */
  void registerTraceId(@NotNull SentryId traceId);

  /** Registers a segment name to be associated with the current replay segment. */
  void registerSegmentName(@NotNull String segmentName);
}
