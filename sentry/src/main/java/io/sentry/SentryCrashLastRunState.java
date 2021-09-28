package io.sentry;

import io.sentry.cache.EnvelopeCache;
import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryCrashLastRunState {

  private static final SentryCrashLastRunState INSTANCE = new SentryCrashLastRunState();

  private boolean readCrashedLastRun = false;
  private @Nullable Boolean crashedLastRun;

  private final @NotNull Object crashedLastRunLock = new Object();

  private SentryCrashLastRunState() {}

  public static SentryCrashLastRunState getInstance() {
    return INSTANCE;
  }

  public @Nullable Boolean isCrashedLastRun(
      final @Nullable String cacheDirPath, final boolean deleteFile) {
    synchronized (crashedLastRunLock) {
      if (checkReadAndAssign()) {
        return crashedLastRun;
      }

      if (cacheDirPath == null) {
        return null;
      }
      final File javaMarker = new File(cacheDirPath, EnvelopeCache.CRASH_MARKER_FILE);
      final File nativeMarker = new File(cacheDirPath, EnvelopeCache.NATIVE_CRASH_MARKER_FILE);
      boolean exists = false;
      try {
        if (javaMarker.exists()) {
          exists = true;

          javaMarker.delete();
        } else if (nativeMarker.exists()) {
          exists = true;
          if (deleteFile) {
            nativeMarker.delete();
          }
        }
      } catch (Exception e) {
        // ignore
      }

      crashedLastRun = exists;
    }

    return crashedLastRun;
  }

  public void setCrashedLastRun(final boolean crashedLastRun) {
    synchronized (crashedLastRunLock) {
      if (!checkReadAndAssign()) {
        this.crashedLastRun = crashedLastRun;
      }
    }
  }

  private boolean checkReadAndAssign() {
    if (readCrashedLastRun) {
      return true;
    }
    readCrashedLastRun = true;
    return false;
  }
}
