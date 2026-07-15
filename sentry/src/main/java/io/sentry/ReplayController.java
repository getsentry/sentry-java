package io.sentry;

import io.sentry.protocol.SentryId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface ReplayController extends IReplayApi {
  void start();

  void stop();

  void pause();

  void resume();

  boolean isRecording();

  void captureReplay(@Nullable Boolean isTerminating);

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
