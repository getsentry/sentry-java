package io.sentry;

import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryCrashLastRunState {

  private static final SentryCrashLastRunState INSTANCE = new SentryCrashLastRunState();

  private final @NotNull AtomicBoolean readCrashedLastRun = new AtomicBoolean(false);
  private @Nullable Boolean crashedLastRun;

  private SentryCrashLastRunState() {}

  public static SentryCrashLastRunState getInstance() {
    return INSTANCE;
  }

  public @Nullable Boolean isCrashedLastRun() {
    return crashedLastRun;
  }

  public void setCrashedLastRun(final boolean crashedLastRun) {
    if (readCrashedLastRun.compareAndSet(false, true)) {
      this.crashedLastRun = crashedLastRun;
    }
  }
}
