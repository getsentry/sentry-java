package io.sentry.android.core.gestures;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.SentryLevel;
import io.sentry.android.core.SentryAndroidOptions;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryGestureListener implements GestureDetector.OnGestureListener {

  private final @NotNull WeakReference<Window> windowRef;
  private final @NotNull IHub hub;
  private final @NotNull SentryAndroidOptions options;

  private final ScrollState scrollState = new ScrollState();

  public SentryGestureListener(
    final @NotNull WeakReference<Window> windowRef,
    final @NotNull IHub hub,
    final @NotNull SentryAndroidOptions options
  ) {
    this.windowRef = windowRef;
    this.hub = hub;
    this.options = options;
  }

  public void onUp(final @NotNull MotionEvent motionEvent) {
    final View decorView = ensureWindowDecorView("onUp");
    final View scrollTarget = scrollState.targetRef.get();
    if (decorView == null || scrollTarget == null) {
      return;
    }

    if (scrollState.type == null) {
      options
        .getLogger()
        .log(SentryLevel.DEBUG, "Unable to define scroll type. No breadcrumb captured.");
      return;
    }

    final String direction = scrollState.calculateDirection(motionEvent);
    addBreadcrumb(scrollTarget, scrollState.type,
      Collections.singletonMap("direction", direction));
    scrollState.reset();
  }

  @Override
  public boolean onDown(final @Nullable MotionEvent motionEvent) {
    if (motionEvent == null) {
      return false;
    }
    scrollState.reset();
    scrollState.startX = motionEvent.getX();
    scrollState.startY = motionEvent.getY();
    return false;
  }

  @Override
  public boolean onSingleTapUp(final @Nullable MotionEvent motionEvent) {
    final View decorView = ensureWindowDecorView("onSingleTapUp");
    if (decorView == null || motionEvent == null) {
      return false;
    }

    @SuppressWarnings("Convert2MethodRef")
    final @Nullable View target =
      ViewUtils.findTarget(decorView, motionEvent.getX(), motionEvent.getY(),
        view -> ViewUtils.isViewTappable(view));

    if (target == null) {
      options
        .getLogger()
        .log(SentryLevel.INFO, "Unable to find click target. No breadcrumb captured.");
      return false;
    }

    addBreadcrumb(target, "click");
    return false;
  }

  @Override
  public boolean onScroll(
    final @Nullable MotionEvent firstEvent,
    final @Nullable MotionEvent currentEvent,
    final float distX,
    final float distY
  ) {
    final View decorView = ensureWindowDecorView("onScroll");
    if (decorView == null || firstEvent == null) {
      return false;
    }

    if (scrollState.type == null) {
      @SuppressWarnings("Convert2MethodRef")
      final @Nullable View target =
        ViewUtils.findTarget(decorView, firstEvent.getX(), firstEvent.getY(),
          view -> ViewUtils.isViewScrollable(view));

      if (target == null) {
        options
          .getLogger()
          .log(SentryLevel.INFO, "Unable to find scroll target. No breadcrumb captured.");
        return false;
      }

      scrollState.setTarget(target);
      scrollState.type = "scroll";
    }
    return false;
  }

  @Override
  public boolean onFling(
    final @Nullable MotionEvent motionEvent,
    final @Nullable MotionEvent motionEvent1,
    final float v,
    final float v1
  ) {
    scrollState.type = "swipe";
    return false;
  }

  @Override
  public void onShowPress(MotionEvent motionEvent) {
  }

  @Override
  public void onLongPress(MotionEvent motionEvent) {
  }

  // region utils
  private void addBreadcrumb(
    final @NotNull View target,
    final @NotNull String eventType
  ) {
    addBreadcrumb(target, eventType, Collections.emptyMap());
  }

  private void addBreadcrumb(
    final @NotNull View target,
    final @NotNull String eventType,
    final @NotNull Map<String, Object> additionalData
  ) {
    @NotNull String className;
    if (target.getClass().getCanonicalName() != null) {
      className = target.getClass().getCanonicalName();
    } else {
      className = target.getClass().getSimpleName();
    }

    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setType("user");
    breadcrumb.setCategory("ui." + eventType);
    breadcrumb.setData("view.id", ViewUtils.getResourceId(target));
    breadcrumb.setData("view.class", className);
    for (final Map.Entry<String, Object> entry : additionalData.entrySet()) {
      breadcrumb.getData().put(entry.getKey(), entry.getValue());
    }
    breadcrumb.setLevel(SentryLevel.INFO);
    hub.addBreadcrumb(breadcrumb);
  }

  private @Nullable View ensureWindowDecorView(final @NotNull String caller) {
    final Window window = windowRef.get();
    if (window == null) {
      options
        .getLogger()
        .log(SentryLevel.INFO, "Window is null in " + caller + ". No breadcrumb captured.");
      return null;
    }

    final View decorView = window.getDecorView();
    if (decorView == null) {
      options
        .getLogger()
        .log(SentryLevel.INFO, "DecorView is null in " + caller + ". No breadcrumb captured.");
      return null;
    }
    return decorView;
  }
  // endregion

  // region scroll logic
  private static class ScrollState {
    private @Nullable String type = null;
    private WeakReference<View> targetRef = new WeakReference<>(null);
    private float startX = 0f;
    private float startY = 0f;

    private void setTarget(final @NotNull View target) {
      targetRef = new WeakReference<>(target);
    }

    /**
     * Calculates the direction of the scroll/swipe based on startX and startY and a given event
     *
     * @param endEvent - the event which notifies when the scroll/swipe ended
     * @return String, one of (left|right|up|down)
     */
    private @NotNull String calculateDirection(MotionEvent endEvent) {
      final float diffX = endEvent.getX() - startX;
      final float diffY = endEvent.getY() - startY;
      final String direction;
      if (Math.abs(diffX) > Math.abs(diffY)) {
        if (diffX > 0f) {
          direction = "right";
        } else {
          direction = "left";
        }
      } else {
        if (diffY > 0) {
          direction = "down";
        } else {
          direction = "up";
        }
      }
      return direction;
    }

    private void reset() {
      targetRef.clear();
      type = null;
      startX = 0f;
      startY = 0f;
    }
  }
  // endregion
}
