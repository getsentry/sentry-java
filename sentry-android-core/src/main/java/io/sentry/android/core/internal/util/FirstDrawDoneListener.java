/*
 * https://github.com/firebase/firebase-android-sdk/blob/master/firebase-perf/src/main/java/com/google/firebase/perf/util/FirstDrawDoneListener.java
 *
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sentry.android.core.internal.util;

import android.app.Activity;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import androidx.annotation.Nullable;
import io.sentry.android.core.BuildInfoProvider;
import io.sentry.android.core.internal.gestures.NoOpWindowCallback;
import io.sentry.android.core.performance.WindowContentChangedCallback;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;

/**
 * OnDrawListener that unregisters itself and invokes callback when the next draw is done. This API
 * 16+ implementation is an approximation of the initial-display-time defined by Android Vitals.
 */
public class FirstDrawDoneListener implements ViewTreeObserver.OnDrawListener {
  private final @NotNull Handler mainThreadHandler = new Handler(Looper.getMainLooper());
  private final @NotNull AtomicReference<View> viewReference;
  private final @NotNull Runnable callback;

  public static void registerForNextDraw(
      final @NotNull Activity activity,
      final @NotNull Runnable drawDoneCallback,
      final @NotNull BuildInfoProvider buildInfoProvider) {

    @Nullable Window window = activity.getWindow();
    if (window != null) {
      @Nullable View decorView = window.peekDecorView();
      if (decorView != null) {
        registerForNextDraw(decorView, drawDoneCallback, buildInfoProvider);
      } else {
        final @Nullable Window.Callback oldCallback = window.getCallback();
        window.setCallback(
            new WindowContentChangedCallback(
                oldCallback != null ? oldCallback : new NoOpWindowCallback(),
                () -> {
                  @Nullable View newDecorView = window.peekDecorView();
                  if (newDecorView != null) {
                    // let's set the old callback again, so we don't intercept anymore
                    window.setCallback(oldCallback);
                    registerForNextDraw(newDecorView, drawDoneCallback, buildInfoProvider);
                  }
                }));
      }
    }
  }

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
