package io.sentry;

import io.sentry.cache.EnvelopeCache;
import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class SentryCrashLastRunState {

  private static final SentryCrashLastRunState INSTANCE = new SentryCrashLastRunState();

  private boolean readCrashedLastRun;
  private @Nullable Boolean crashedLastRun;

  private final @NotNull Object crashedLastRunLock = new Object();

  private SentryCrashLastRunState() {}

  public static SentryCrashLastRunState getInstance() {
    return INSTANCE;
  }

  public @Nullable Boolean isCrashedLastRun(
      final @Nullable String cacheDirPath, final boolean deleteFile) {
    synchronized (crashedLastRunLock) {
      if (readCrashedLastRun) {
        return crashedLastRun;
      }

      if (cacheDirPath == null) {
        return null;
      }
      readCrashedLastRun = true;

      final File javaMarker = new File(cacheDirPath, EnvelopeCache.CRASH_MARKER_FILE);
      final File nativeMarker = new File(cacheDirPath, EnvelopeCache.NATIVE_CRASH_MARKER_FILE);
      boolean exists = false;
      try {
        if (javaMarker.exists()) {
          exists = true;

          javaMarker.delete();
        } else if (nativeMarker.exists()) {
          exists = true;
          // only delete if release health is disabled, otherwise the session crashed by the native
          // side won't be marked as crashed correctly.
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
      if (!readCrashedLastRun) {
        this.crashedLastRun = crashedLastRun;
        // mark readCrashedLastRun as true since its being set directly
        readCrashedLastRun = true;
      }
    }
  }

  @TestOnly
  public void reset() {
    synchronized (crashedLastRunLock) {
      readCrashedLastRun = false;
      crashedLastRun = null;
    }
  }
}
