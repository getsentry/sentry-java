package io.sentry.android.core.gestures;

import android.content.Context;
import android.view.MotionEvent;
import android.view.Window;
import androidx.core.view.GestureDetectorCompat;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryWindowCallback extends WindowCallbackAdapter {

  private final @NotNull Window.Callback delegate;
  private final @NotNull SentryGestureListener gestureListener;
  private final @NotNull GestureDetectorCompat gestureDetector;
  private final @Nullable SentryOptions options;

  public SentryWindowCallback(
    final @NotNull Window.Callback delegate,
    final @NotNull Context context,
    final @NotNull SentryGestureListener gestureListener,
    final @Nullable SentryOptions options
  ) {
    super(delegate);
    this.delegate = delegate;
    this.gestureListener = gestureListener;
    this.options = options;
    gestureDetector = new GestureDetectorCompat(context, gestureListener);
  }

  @Override
  public boolean dispatchTouchEvent(final @Nullable MotionEvent motionEvent) {
    if (motionEvent != null) {
      final MotionEvent copy = MotionEvent.obtain(motionEvent);
      try {
        handleTouchEvent(motionEvent);
      } catch (Throwable e) {
        if (options != null) {
          options
            .getLogger()
            .log(SentryLevel.ERROR, "Error dispatching touch event", e);
        }
      } finally {
        copy.recycle();
      }
    }
    return super.dispatchTouchEvent(motionEvent);
  }

  private void handleTouchEvent(final @NotNull MotionEvent motionEvent) {
    gestureDetector.onTouchEvent(motionEvent);
    int action = motionEvent.getActionMasked();
    if (action == MotionEvent.ACTION_UP) {
      gestureListener.onUp(motionEvent);
    }
  }

  public @NotNull Window.Callback getDelegate() {
    return delegate;
  }
}
