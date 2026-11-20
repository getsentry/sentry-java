package io.sentry.android.core.hang;

import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Collects data while a hang interval is open. */
@ApiStatus.Internal
public interface HangDataCollector extends Closeable {

  /** Called when an interval opens. */
  void onRecordingStarted(@NotNull HangInterval interval);

  /** Called on each tick while an interval is open. */
  void onSample(@NotNull HangInterval interval);

  /** Called when the interval ends with a record. */
  void onRecordingStopped(@NotNull HangInterval interval, @NotNull HangRecord.Builder out);

  /**
   * Called when the interval ends without a record, because a signal canceled it or the last
   * subscription was removed.
   */
  void onRecordingCanceled();

  /** Called once, when the recorder is closed. The collector is not used after this. */
  @Override
  void close() throws IOException;
}
