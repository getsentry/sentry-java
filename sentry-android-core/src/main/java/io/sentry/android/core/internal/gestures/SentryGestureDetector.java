package io.sentry.android.core.internal.gestures;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import io.sentry.ISentryLifecycleToken;
import io.sentry.util.AutoClosableReentrantLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A lightweight gesture detector that replaces {@code GestureDetectorCompat}/{@link
 * GestureDetector} to avoid ANRs caused by Handler/MessageQueue lock contention and IPC calls
 * (FrameworkStatsLog.write).
 *
 * <p>Only detects click (tap), scroll, and fling — the gestures used by {@link
 * SentryGestureListener}. Long-press, show-press, and double-tap detection (which require Handler
 * message scheduling) are intentionally omitted.
 */
@ApiStatus.Internal
public final class SentryGestureDetector {

  private final @NotNull GestureDetector.OnGestureListener listener;
  private final int touchSlopSquare;
  private final int minimumFlingVelocity;
  private final int maximumFlingVelocity;

  private boolean isInTapRegion;
  private boolean ignoreUpEvent;
  private float downX;
  private float downY;
  private float lastX;
  private float lastY;
  private @Nullable MotionEvent currentDownEvent;
  private @Nullable VelocityTracker velocityTracker;

  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  SentryGestureDetector(
      final @NotNull Context context, final @NotNull GestureDetector.OnGestureListener listener) {
    this.listener = listener;
    final ViewConfiguration config = ViewConfiguration.get(context);
    final int touchSlop = config.getScaledTouchSlop();
    this.touchSlopSquare = touchSlop * touchSlop;
    this.minimumFlingVelocity = config.getScaledMinimumFlingVelocity();
    this.maximumFlingVelocity = config.getScaledMaximumFlingVelocity();
  }

  void onTouchEvent(final @NotNull MotionEvent event) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final int action = event.getActionMasked();

      if (velocityTracker == null) {
        velocityTracker = VelocityTracker.obtain();
      }
      velocityTracker.addMovement(event);

      switch (action) {
        case MotionEvent.ACTION_DOWN:
          downX = event.getX();
          downY = event.getY();
          lastX = downX;
          lastY = downY;
          isInTapRegion = true;
          ignoreUpEvent = false;

          if (currentDownEvent != null) {
            currentDownEvent.recycle();
          }
          currentDownEvent = MotionEvent.obtain(event);

          listener.onDown(event);
          break;

        case MotionEvent.ACTION_MOVE:
          {
            final float x = event.getX();
            final float y = event.getY();
            final float dx = x - downX;
            final float dy = y - downY;
            final float distanceSquare = (dx * dx) + (dy * dy);

            if (distanceSquare > touchSlopSquare) {
              final float scrollX = lastX - x;
              final float scrollY = lastY - y;
              listener.onScroll(currentDownEvent, event, scrollX, scrollY);
              isInTapRegion = false;
              lastX = x;
              lastY = y;
            }
            break;
          }

        case MotionEvent.ACTION_POINTER_DOWN:
          // A second finger means this is not a single tap (e.g. pinch-to-zoom).
          // Also suppress the UP handler to avoid spurious fling detection when the
          // last finger lifts quickly after a pinch — mirrors GestureDetector's
          // mIgnoreNextUpEvent / cancelTaps() behavior.
          isInTapRegion = false;
          ignoreUpEvent = true;
          break;

        case MotionEvent.ACTION_UP:
          if (ignoreUpEvent) {
            recycle();
            break;
          }
          if (isInTapRegion) {
            listener.onSingleTapUp(event);
          } else {
            final int pointerId = event.getPointerId(0);
            velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
            final float velocityX = velocityTracker.getXVelocity(pointerId);
            final float velocityY = velocityTracker.getYVelocity(pointerId);

            if (Math.abs(velocityX) > minimumFlingVelocity
                || Math.abs(velocityY) > minimumFlingVelocity) {
              listener.onFling(currentDownEvent, event, velocityX, velocityY);
            }
          }
          recycle();
          break;

        case MotionEvent.ACTION_CANCEL:
          recycle();
          break;
      }
    }
  }

  void recycle() {
    final @Nullable MotionEvent capturedDownEvent;
    final @Nullable VelocityTracker capturedVelocityTracker;
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      capturedDownEvent = currentDownEvent;
      currentDownEvent = null;
      capturedVelocityTracker = velocityTracker;
      velocityTracker = null;
    }
    if (capturedDownEvent != null) {
      capturedDownEvent.recycle();
    }
    if (capturedVelocityTracker != null) {
      capturedVelocityTracker.recycle();
    }
  }
}
