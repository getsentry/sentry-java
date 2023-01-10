package io.sentry.android.core.internal.util;

import android.os.Looper;
import io.sentry.util.thread.IMainThreadChecker;
import org.jetbrains.annotations.ApiStatus;

/** Class that checks if a given thread is the Android Main/UI thread */
@ApiStatus.Internal
public final class AndroidMainThreadChecker implements IMainThreadChecker {

  private static final AndroidMainThreadChecker instance = new AndroidMainThreadChecker();

  public static AndroidMainThreadChecker getInstance() {
    return instance;
  }

  private AndroidMainThreadChecker() {}

  @Override
  public boolean isMainThread(final long threadId) {
    return Looper.getMainLooper().getThread().getId() == threadId;
  }
}
