package io.sentry.android.core.hang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface HangRecorderListener {

  void onHangRecorded(@NotNull HangRecord record);
}
