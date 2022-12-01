package io.sentry.android.core;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class CurrentActivityHolder {

  private static final @NotNull CurrentActivityHolder instance = new CurrentActivityHolder();

  private CurrentActivityHolder() {}

  private @Nullable WeakReference<Activity> currentActivity;

  public static @NonNull CurrentActivityHolder getInstance() {
    return instance;
  }

  public @Nullable Activity getActivity() {
    if (currentActivity != null) {
      return currentActivity.get();
    }
    return null;
  }

  public void setActivity(final @NonNull Activity activity) {
    if (currentActivity != null && currentActivity.get() == activity) {
      return;
    }

    currentActivity = new WeakReference<>(activity);
  }

  public void clearActivity() {
    currentActivity = null;
  }
}
