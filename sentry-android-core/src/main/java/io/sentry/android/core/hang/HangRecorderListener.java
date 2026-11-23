package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface HangRecorderListener {

  /**
   * Called on the recorder thread when an interval opens, once for each subscriber. Must be fast,
   * no IO. The request is valid for the whole interval.
   */
  @NotNull
  HangRecordingRequest onHangStarted(@NotNull HangInterval interval);

  /**
   * Called on the SDK executor, only if the request of this subscriber for the interval {@link
   * HangRecordingRequest#shouldRecord() records}.
   */
  void onHangRecorded(@NotNull HangRecord record);
}
