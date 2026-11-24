package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface HangRecordingListener {

  /**
   * A hang started. Must be fast, no IO.
   *
   * @return what this listener needs from the recording of this hang, or null for nothing
   */
  @Nullable
  HangRecordingRequest onHangStarted();

  /**
   * The record of a hang for which this listener asked for a record in {@link #onHangStarted()}.
   *
   * <p>The listener gets the record also if it unsubscribed during the hang or before the delivery.
   * Only the last {@link HangManager#unsubscribe(HangRecordingListener)} drops an open hang without
   * a record.
   */
  void onHangRecorded(@NotNull HangRecord record);
}
