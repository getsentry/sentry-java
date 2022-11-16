package io.sentry.android.core;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class CurrentActivityHolder {

  private static @Nullable CurrentActivityHolder instance;

  private @Nullable WeakReference<Activity> currentActivity;

  public static CurrentActivityHolder getInstance() {
    if (instance != null) {
      return instance;
    }
    instance = new CurrentActivityHolder();
    return instance;
  }

  public @Nullable Activity getActivity() {
    if (currentActivity != null) {
      return currentActivity.get();
    }
    return null;
  }

  public void setActivity(@NonNull Activity activity) {
    if (currentActivity != null && currentActivity.get() == activity) {
      return;
    }

    currentActivity = new WeakReference<>(activity);
  }

  public void clearActivity() {
    currentActivity = null;
  }
}
