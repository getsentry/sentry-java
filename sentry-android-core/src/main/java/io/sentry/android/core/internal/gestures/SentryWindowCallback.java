package io.sentry.android.core.internal.gestures;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.Window;
import androidx.core.view.GestureDetectorCompat;
import io.sentry.SentryLevel;
import io.sentry.SentryOptions;
import io.sentry.SpanStatus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryWindowCallback extends WindowCallbackAdapter {

  private final @NotNull Window.Callback delegate;
  private final @NotNull SentryGestureListener gestureListener;
  private final @NotNull GestureDetectorCompat gestureDetector;
  private final @Nullable SentryOptions options;
  private final @NotNull MotionEventObtainer motionEventObtainer;

  public SentryWindowCallback(
      final @NotNull Window.Callback delegate,
      final @NotNull Context context,
      final @NotNull SentryGestureListener gestureListener,
      final @Nullable SentryOptions options) {
    this(
        delegate,
        new GestureDetectorCompat(context, gestureListener, new Handler(Looper.getMainLooper())),
        gestureListener,
        options,
        new MotionEventObtainer() {});
  }

  SentryWindowCallback(
      final @NotNull Window.Callback delegate,
      final @NotNull GestureDetectorCompat gestureDetector,
      final @NotNull SentryGestureListener gestureListener,
      final @Nullable SentryOptions options,
      final @NotNull MotionEventObtainer motionEventObtainer) {
    super(delegate);
    this.delegate = delegate;
    this.gestureListener = gestureListener;
    this.options = options;
    this.gestureDetector = gestureDetector;
    this.motionEventObtainer = motionEventObtainer;
  }

  @Override
  public boolean dispatchTouchEvent(final @Nullable MotionEvent motionEvent) {
    if (motionEvent != null) {
      final MotionEvent copy = motionEventObtainer.obtain(motionEvent);
      try {
        handleTouchEvent(copy);
      } catch (Throwable e) {
        if (options != null) {
          options.getLogger().log(SentryLevel.ERROR, "Error dispatching touch event", e);
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

  public void stopTracking() {
    gestureListener.stopTracing(SpanStatus.CANCELLED);
  }

  public @NotNull Window.Callback getDelegate() {
    return delegate;
  }

  interface MotionEventObtainer {
    @NotNull
    default MotionEvent obtain(@NotNull MotionEvent origin) {
      return MotionEvent.obtain(origin);
    }
  }
}
