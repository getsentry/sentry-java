package io.sentry.android.core;

import android.content.Context;
import androidx.annotation.WorkerThread;
import io.sentry.BackfillingEventProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** originating from the Native SDK. */
@ApiStatus.Internal
@WorkerThread
public class TombstoneEventProcessor implements BackfillingEventProcessor {

  @NotNull private final Context context;
  @NotNull private final SentryAndroidOptions options;
  @NotNull private final BuildInfoProvider buildInfoProvider;

  public TombstoneEventProcessor(
      @NotNull Context context,
      @NotNull SentryAndroidOptions options,
      @NotNull BuildInfoProvider buildInfoProvider) {
    this.context = context;
    this.options = options;
    this.buildInfoProvider = buildInfoProvider;
  }
}
