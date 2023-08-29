package io.sentry.android.core.internal.util;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import io.sentry.android.core.BuildInfoProvider;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;

/**
 * OnDrawListener that unregisters itself and invokes callback when the next draw is done. This API
 * 16+ implementation is an approximation of the initial-display-time defined by Android Vitals.
 *
 * <p>Adapted from <a
 * href="https://github.com/firebase/firebase-android-sdk/blob/master/firebase-perf/src/main/java/com/google/firebase/perf/util/FirstDrawDoneListener.java">Firebase</a>
 * under the Apache License, Version 2.0.
 */
public class FirstDrawDoneListener implements ViewTreeObserver.OnDrawListener {
  private final @NotNull Handler mainThreadHandler = new Handler(Looper.getMainLooper());
  private final @NotNull AtomicReference<View> viewReference;
  private final @NotNull Runnable callback;

  /** Registers a post-draw callback for the next draw of a view. */
  public static void registerForNextDraw(
      final @NotNull View view,
      final @NotNull Runnable drawDoneCallback,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    final FirstDrawDoneListener listener = new FirstDrawDoneListener(view, drawDoneCallback);
    // Handle bug prior to API 26 where OnDrawListener from the floating ViewTreeObserver is not
    // merged into the real ViewTreeObserver.
    // https://android.googlesource.com/platform/frameworks/base/+/9f8ec54244a5e0343b9748db3329733f259604f3
    if (buildInfoProvider.getSdkInfoVersion() < Build.VERSION_CODES.O
        && !isAliveAndAttached(view)) {
      view.addOnAttachStateChangeListener(
          new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
              view.getViewTreeObserver().addOnDrawListener(listener);
              view.removeOnAttachStateChangeListener(this);
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
              view.removeOnAttachStateChangeListener(this);
            }
          });
    } else {
      view.getViewTreeObserver().addOnDrawListener(listener);
    }
  }

  private FirstDrawDoneListener(final @NotNull View view, final @NotNull Runnable callback) {
    this.viewReference = new AtomicReference<>(view);
    this.callback = callback;
  }

  @Override
  public void onDraw() {
    // Set viewReference to null so any onDraw past the first is a no-op
    final View view = viewReference.getAndSet(null);
    if (view == null) {
      return;
    }
    // OnDrawListeners cannot be removed within onDraw, so we remove it with a
    // GlobalLayoutListener
    view.getViewTreeObserver()
        .addOnGlobalLayoutListener(() -> view.getViewTreeObserver().removeOnDrawListener(this));
    mainThreadHandler.postAtFrontOfQueue(callback);
  }

  /**
   * Helper to avoid <a
   * href="https://android.googlesource.com/platform/frameworks/base/+/9f8ec54244a5e0343b9748db3329733f259604f3">bug
   * prior to API 26</a>, where the floating ViewTreeObserver's OnDrawListeners are not merged into
   * the real ViewTreeObserver during attach.
   *
   * @return true if the View is already attached and the ViewTreeObserver is not a floating
   *     placeholder.
   */
  private static boolean isAliveAndAttached(final @NotNull View view) {
    return view.getViewTreeObserver().isAlive() && view.isAttachedToWindow();
  }
}
