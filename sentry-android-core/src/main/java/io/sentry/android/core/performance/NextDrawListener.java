package io.sentry.android.core.performance;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.view.ViewCompat;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Inspired by https://blog.p-y.wtf/tracking-android-app-launch-in-production Adapted from:
 * https://github.com/square/papa/blob/31eebb3d70908bcb1209d82f066ec4d4377183ee/papa/src/main/java/papa/internal/ViewTreeObservers.kt
 *
 * <p>Copyright 2021 Square Inc.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
@ApiStatus.Internal
public class NextDrawListener
    implements ViewTreeObserver.OnDrawListener, View.OnAttachStateChangeListener {

  private @NotNull final View view;
  private @NotNull final Runnable onDrawCallback;
  private @NotNull final Handler mainHandler;
  private boolean invoked;

  public NextDrawListener(final @NotNull View view, final @NotNull Runnable onDrawCallback) {
    this.view = view;
    this.onDrawCallback = onDrawCallback;
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.invoked = false;
  }

  @Override
  public void onDraw() {
    if (invoked) {
      return;
    }
    invoked = true;
    // ViewTreeObserver.removeOnDrawListener() throws if called from the onDraw() callback
    mainHandler.post(
        () -> {
          final ViewTreeObserver observer = view.getViewTreeObserver();
          if (observer != null && observer.isAlive()) {
            observer.removeOnDrawListener(NextDrawListener.this);
          }
        });
    onDrawCallback.run();
  }

  public void safelyRegisterForNextDraw() {
    // Prior to API 26, OnDrawListener wasn't merged back from the floating ViewTreeObserver into
    // the real ViewTreeObserver.
    // https://android.googlesource.com/platform/frameworks/base/+/9f8ec54244a5e0343b9748db3329733f259604f3
    final @Nullable ViewTreeObserver viewTreeObserver = view.getViewTreeObserver();
    if (Build.VERSION.SDK_INT >= 26
        && viewTreeObserver != null
        && (viewTreeObserver.isAlive() && ViewCompat.isAttachedToWindow(view))) {
      viewTreeObserver.addOnDrawListener(this);
    } else {
      view.addOnAttachStateChangeListener(this);
    }
  }

  @Override
  public void onViewAttachedToWindow(@NonNull View v) {
    // Backed by CopyOnWriteArrayList, ok to self remove from onViewDetachedFromWindow()
    view.removeOnAttachStateChangeListener(this);

    final @Nullable ViewTreeObserver viewTreeObserver = view.getViewTreeObserver();
    if (viewTreeObserver != null && viewTreeObserver.isAlive()) {
      viewTreeObserver.addOnDrawListener(this);
    }
  }

  @Override
  public void onViewDetachedFromWindow(@NonNull View v) {
    view.removeOnAttachStateChangeListener(this);

    final @Nullable ViewTreeObserver viewTreeObserver = view.getViewTreeObserver();
    if (viewTreeObserver != null && viewTreeObserver.isAlive()) {
      viewTreeObserver.removeOnDrawListener(this);
    }
  }
}
