package io.sentry.android.core;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.Window;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import io.sentry.IScopes;
import io.sentry.Integration;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.android.core.internal.gestures.NoOpWindowCallback;
import io.sentry.android.core.internal.gestures.SentryGestureListener;
import io.sentry.android.core.internal.gestures.SentryWindowCallback;
import io.sentry.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UserInteractionIntegration
    implements Integration, Closeable, Application.ActivityLifecycleCallbacks {

  private final @NotNull Application application;
  private @Nullable IScopes scopes;
  private @Nullable SentryAndroidOptions options;

  private final boolean isAndroidxLifecycleAvailable;

  // WeakReference value, because the callback chain strongly references the wrapper — a strong
  // value would prevent the window from ever being GC'd.
  //
  // All access must be guarded by wrappedWindowsLock — lifecycle callbacks fire on the main
  // thread, but close() may be called from a background thread (e.g. Sentry.close()).
  private final @NotNull WeakHashMap<Window, WeakReference<SentryWindowCallback>> wrappedWindows =
      new WeakHashMap<>();

  private final @NotNull Object wrappedWindowsLock = new Object();

  public UserInteractionIntegration(
      final @NotNull Application application, final @NotNull io.sentry.util.LoadClass classLoader) {
    this.application = Objects.requireNonNull(application, "Application is required");
    isAndroidxLifecycleAvailable =
        classLoader.isClassAvailable("androidx.lifecycle.Lifecycle", options);
  }

  private void startTracking(final @NotNull Activity activity) {
    final Window window = activity.getWindow();
    if (window == null) {
      if (options != null) {
        options.getLogger().log(SentryLevel.INFO, "Window was null in startTracking");
      }
      return;
    }

    if (scopes != null && options != null) {
      synchronized (wrappedWindowsLock) {
        final @Nullable WeakReference<SentryWindowCallback> cached = wrappedWindows.get(window);
        if (cached != null && cached.get() != null) {
          return;
        }
      }

      Window.Callback delegate = window.getCallback();
      if (delegate == null) {
        delegate = new NoOpWindowCallback();
      }

      final SentryGestureListener gestureListener =
          new SentryGestureListener(activity, scopes, options);
      final SentryWindowCallback wrapper =
          new SentryWindowCallback(delegate, activity, gestureListener, options);
      window.setCallback(wrapper);
      synchronized (wrappedWindowsLock) {
        wrappedWindows.put(window, new WeakReference<>(wrapper));
      }
    }
  }

  private void stopTracking(final @NotNull Activity activity) {
    final Window window = activity.getWindow();
    if (window == null) {
      if (options != null) {
        options.getLogger().log(SentryLevel.INFO, "Window was null in stopTracking");
      }
      return;
    }
    unwrapWindow(window);
  }

  private void unwrapWindow(final @NotNull Window window) {
    final Window.Callback current = window.getCallback();
    if (current instanceof SentryWindowCallback) {
      ((SentryWindowCallback) current).stopTracking();
      if (((SentryWindowCallback) current).getDelegate() instanceof NoOpWindowCallback) {
        window.setCallback(null);
      } else {
        window.setCallback(((SentryWindowCallback) current).getDelegate());
      }
      synchronized (wrappedWindowsLock) {
        wrappedWindows.remove(window);
      }
      return;
    }

    // Another wrapper (e.g. Session Replay) sits on top of ours — cutting it out of the chain
    // would break its instrumentation, so we leave the chain alone and only release our
    // resources. The inert wrapper gets GC'd when the window is destroyed.
    final @Nullable SentryWindowCallback ours;
    synchronized (wrappedWindowsLock) {
      final @Nullable WeakReference<SentryWindowCallback> cached = wrappedWindows.get(window);
      ours = cached != null ? cached.get() : null;
    }
    if (ours != null) {
      ours.stopTracking();
    }
  }

  @Override
  public void onActivityCreated(@NotNull Activity activity, @Nullable Bundle bundle) {}

  @Override
  public void onActivityStarted(@NotNull Activity activity) {}

  @Override
  public void onActivityResumed(@NotNull Activity activity) {
    startTracking(activity);
  }

  @Override
  public void onActivityPaused(@NotNull Activity activity) {
    stopTracking(activity);
  }

  @Override
  public void onActivityStopped(@NotNull Activity activity) {}

  @Override
  public void onActivitySaveInstanceState(@NotNull Activity activity, @NotNull Bundle bundle) {}

  @Override
  public void onActivityDestroyed(@NotNull Activity activity) {}

  @Override
  public void register(@NotNull IScopes scopes, @NotNull SentryOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof SentryAndroidOptions) ? (SentryAndroidOptions) options : null,
            "SentryAndroidOptions is required");

    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");

    final boolean integrationEnabled =
        this.options.isEnableUserInteractionBreadcrumbs()
            || this.options.isEnableUserInteractionTracing();
    this.options
        .getLogger()
        .log(SentryLevel.DEBUG, "UserInteractionIntegration enabled: %s", integrationEnabled);

    if (integrationEnabled) {
      application.registerActivityLifecycleCallbacks(this);
      this.options.getLogger().log(SentryLevel.DEBUG, "UserInteractionIntegration installed.");
      addIntegrationToSdkVersion("UserInteraction");

      // In case of a deferred init, we hook into any resumed activity
      if (isAndroidxLifecycleAvailable) {
        final @Nullable Activity activity = CurrentActivityHolder.getInstance().getActivity();
        if (activity instanceof LifecycleOwner) {
          if (((LifecycleOwner) activity).getLifecycle().getCurrentState()
              == Lifecycle.State.RESUMED) {
            startTracking(activity);
          }
        }
      }
    }
  }

  @Override
  public void close() throws IOException {
    application.unregisterActivityLifecycleCallbacks(this);

    // Restore original callbacks so a subsequent Sentry.init() starts from a clean chain instead
    // of wrapping on top of our orphaned callback.
    final ArrayList<Window> snapshot;
    synchronized (wrappedWindowsLock) {
      snapshot = new ArrayList<>(wrappedWindows.keySet());
    }
    for (final Window window : snapshot) {
      if (window != null) {
        unwrapWindow(window);
      }
    }
    synchronized (wrappedWindowsLock) {
      wrappedWindows.clear();
    }

    if (options != null) {
      options.getLogger().log(SentryLevel.DEBUG, "UserInteractionIntegration removed.");
    }
  }
}
