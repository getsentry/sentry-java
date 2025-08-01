package io.sentry.android.core;

import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import io.sentry.ILogger;
import io.sentry.ISentryLifecycleToken;
import io.sentry.NoOpLogger;
import io.sentry.SentryLevel;
import io.sentry.android.core.internal.util.AndroidThreadChecker;
import io.sentry.util.AutoClosableReentrantLock;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** AppState holds the state of the App, e.g. whether the app is in background/foreground, etc. */
@ApiStatus.Internal
public final class AppState implements Closeable {
  private static @NotNull AppState instance = new AppState();
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  volatile LifecycleObserver lifecycleObserver;
  MainLooperHandler handler = new MainLooperHandler();

  private AppState() {}

  public static @NotNull AppState getInstance() {
    return instance;
  }

  private volatile @Nullable Boolean inBackground = null;

  @TestOnly
  void resetInstance() {
    instance = new AppState();
  }

  public @Nullable Boolean isInBackground() {
    return inBackground;
  }

  void setInBackground(final boolean inBackground) {
    this.inBackground = inBackground;
  }

  void addAppStateListener(final @NotNull AppStateListener listener) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      ensureLifecycleObserver(NoOpLogger.getInstance());

      lifecycleObserver.listeners.add(listener);
    }
  }

  void removeAppStateListener(final @NotNull AppStateListener listener) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      if (lifecycleObserver != null) {
        lifecycleObserver.listeners.remove(listener);
      }
    }
  }

  void registerLifecycleObserver(final @Nullable SentryAndroidOptions options) {
    if (lifecycleObserver != null) {
      return;
    }

    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      ensureLifecycleObserver(options != null ? options.getLogger() : NoOpLogger.getInstance());
    }
  }

  private void ensureLifecycleObserver(final @NotNull ILogger logger) {
    if (lifecycleObserver != null) {
      return;
    }
    try {
      Class.forName("androidx.lifecycle.ProcessLifecycleOwner");
      // create it right away, so it's available in addAppStateListener in case it's posted to main
      // thread
      lifecycleObserver = new LifecycleObserver();

      if (AndroidThreadChecker.getInstance().isMainThread()) {
        addObserverInternal(logger);
      } else {
        // some versions of the androidx lifecycle-process require this to be executed on the main
        // thread.
        handler.post(() -> addObserverInternal(logger));
      }
    } catch (ClassNotFoundException e) {
      logger.log(
          SentryLevel.WARNING,
          "androidx.lifecycle is not available, some features might not be properly working,"
              + "e.g. Session Tracking, Network and System Events breadcrumbs, etc.");
    } catch (Throwable e) {
      logger.log(SentryLevel.ERROR, "AppState could not register lifecycle observer", e);
    }
  }

  private void addObserverInternal(final @NotNull ILogger logger) {
    final @Nullable LifecycleObserver observerRef = lifecycleObserver;
    try {
      // might already be unregistered/removed so we have to check for nullability
      if (observerRef != null) {
        ProcessLifecycleOwner.get().getLifecycle().addObserver(observerRef);
      }
    } catch (Throwable e) {
      // This is to handle a potential 'AbstractMethodError' gracefully. The error is triggered in
      // connection with conflicting dependencies of the androidx.lifecycle.
      // //See the issue here: https://github.com/getsentry/sentry-java/pull/2228
      lifecycleObserver = null;
      logger.log(
          SentryLevel.ERROR,
          "AppState failed to get Lifecycle and could not install lifecycle observer.",
          e);
    }
  }

  void unregisterLifecycleObserver() {
    if (lifecycleObserver == null) {
      return;
    }

    final @Nullable LifecycleObserver ref;
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      ref = lifecycleObserver;
      lifecycleObserver.listeners.clear();
      lifecycleObserver = null;
    }

    if (AndroidThreadChecker.getInstance().isMainThread()) {
      removeObserverInternal(ref);
    } else {
      // some versions of the androidx lifecycle-process require this to be executed on the main
      // thread.
      // avoid method refs on Android due to some issues with older AGP setups
      // noinspection Convert2MethodRef
      handler.post(() -> removeObserverInternal(ref));
    }
  }

  private void removeObserverInternal(final @Nullable LifecycleObserver ref) {
    if (ref != null) {
      ProcessLifecycleOwner.get().getLifecycle().removeObserver(ref);
    }
  }

  @Override
  public void close() throws IOException {
    unregisterLifecycleObserver();
  }

  final class LifecycleObserver implements DefaultLifecycleObserver {
    final List<AppStateListener> listeners =
        new CopyOnWriteArrayList<AppStateListener>() {
          @Override
          public boolean add(AppStateListener appStateListener) {
            // notify the listeners immediately to let them "catch up" with the current state
            // (mimics the behavior of androidx.lifecycle)
            if (Boolean.FALSE.equals(inBackground)) {
              appStateListener.onForeground();
            } else if (Boolean.TRUE.equals(inBackground)) {
              appStateListener.onBackground();
            }
            return super.add(appStateListener);
          }
        };

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
      for (AppStateListener listener : listeners) {
        listener.onForeground();
      }
      setInBackground(false);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
      for (AppStateListener listener : listeners) {
        listener.onBackground();
      }
      setInBackground(true);
    }
  }

  // If necessary, we can adjust this and add other callbacks in the future
  public interface AppStateListener {
    void onForeground();

    void onBackground();
  }
}
