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
import org.jetbrains.annotations.NotNull;

// Inspired by https://blog.p-y.wtf/tracking-android-app-launch-in-production
// https://github.com/square/papa/blob/main/papa/src/main/java/papa/internal/ViewTreeObservers.kt

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
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
