package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface HangRecorderListener {

  /**
   * Called on the recorder thread when a hang starts, once for each subscriber. Must be fast, no
   * IO.
   *
   * @return what this subscriber needs from the recording of this hang, or null for nothing
   */
  @Nullable
  HangRecordingRequest onHangStarted();

  /**
   * Called on the SDK executor, only if the request of this subscriber for the hang records and the
   * subscriber is still subscribed when the hang ends.
   */
  void onHangRecorded(@NotNull HangRecord record);
}
