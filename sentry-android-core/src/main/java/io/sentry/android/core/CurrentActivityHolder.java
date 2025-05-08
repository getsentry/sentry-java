package io.sentry.android.core;

import android.app.Activity;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class CurrentActivityHolder {

  private static final @NotNull CurrentActivityHolder instance = new CurrentActivityHolder();

  private CurrentActivityHolder() {}

  private @Nullable WeakReference<Activity> currentActivity;

  public static @NotNull CurrentActivityHolder getInstance() {
    return instance;
  }

  public @Nullable Activity getActivity() {
    if (currentActivity != null) {
      return currentActivity.get();
    }
    return null;
  }

  public void setActivity(final @NotNull Activity activity) {
    if (currentActivity != null && currentActivity.get() == activity) {
      return;
    }

    currentActivity = new WeakReference<>(activity);
  }

  public void clearActivity() {
    currentActivity = null;
  }

  public void clearActivity(final @NotNull Activity activity) {
    if (currentActivity != null && currentActivity.get() != activity) {
      return;
    }
    currentActivity = null;
  }
}
