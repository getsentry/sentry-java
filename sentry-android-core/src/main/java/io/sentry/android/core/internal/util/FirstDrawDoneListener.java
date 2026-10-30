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
  // The BuildInfoProvider param is kept for compatibility, as hybrid SDKs call this method.
  public static void registerForNextDraw(
      final @NotNull View view,
      final @NotNull Runnable drawDoneCallback,
      final @NotNull BuildInfoProvider buildInfoProvider) {
    final FirstDrawDoneListener listener = new FirstDrawDoneListener(view, drawDoneCallback);
    view.getViewTreeObserver().addOnDrawListener(listener);
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
    // OnDrawListeners cannot be removed within onDraw. Posting the removal instead of waiting for
    // the next global layout guarantees it happens, even if no layout pass follows the draw.
    mainThreadHandler.post(
        () -> {
          final ViewTreeObserver observer = view.getViewTreeObserver();
          if (observer.isAlive()) {
            observer.removeOnDrawListener(this);
          }
        });
    mainThreadHandler.postAtFrontOfQueue(callback);
  }
}
