package io.sentry.android.core;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.sentry.IHub;
import io.sentry.Integration;
import io.sentry.SentryOptions;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class CurrentActivityIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private final @NotNull Application application;

  public CurrentActivityIntegration(@NotNull Application application) {
    this.application = application;
  }

  @Override
  public void register(@NotNull IHub hub, @NotNull SentryOptions options) {
    application.registerActivityLifecycleCallbacks(this);
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    setCurrentActivity(activity);
  }

  @Override
  public void onActivityStarted(@NonNull Activity activity) {
    setCurrentActivity(activity);
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity) {
    setCurrentActivity(activity);
  }

  @Override
  public void onActivityPaused(@NonNull Activity activity) {
    cleanCurrentActivity(activity);
  }

  @Override
  public void onActivityStopped(@NonNull Activity activity) {
    cleanCurrentActivity(activity);
  }

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {
    cleanCurrentActivity(activity);
  }

  @Override
  public void close() throws IOException {
    application.unregisterActivityLifecycleCallbacks(this);
    CurrentActivityHolder.getInstance().clearActivity();
  }

  private void cleanCurrentActivity(@NonNull Activity activity) {
    if (CurrentActivityHolder.getInstance().getActivity() == activity) {
      CurrentActivityHolder.getInstance().clearActivity();
    }
  }

  private void setCurrentActivity(@NonNull Activity activity) {
    CurrentActivityHolder.getInstance().setActivity(activity);
  }
}
