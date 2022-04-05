package io.sentry.android.core.internal.gestures;

import android.app.Activity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import io.sentry.Breadcrumb;
import io.sentry.IHub;
import io.sentry.ITransaction;
import io.sentry.Scope;
import io.sentry.SentryLevel;
import io.sentry.android.core.SentryAndroidOptions;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class SentryGestureListener implements GestureDetector.OnGestureListener {

  static final String UI_ACTION = "ui.action";

  private final @NotNull WeakReference<Window> windowRef;
  private final @NotNull WeakReference<Activity> currentActivity;
  private final @NotNull IHub hub;
  private final @NotNull SentryAndroidOptions options;
  private final boolean isAndroidXAvailable;
  private final boolean performanceEnabled;

  private @Nullable ITransaction activeTransaction = null;

  private final ScrollState scrollState = new ScrollState();

  public SentryGestureListener(
      final @NotNull WeakReference<Window> windowRef,
      final @NotNull WeakReference<Activity> currentActivity,
      final @NotNull IHub hub,
      final @NotNull SentryAndroidOptions options,
      final boolean isAndroidXAvailable,
      final boolean performanceEnabled) {
    this.windowRef = windowRef;
    this.hub = hub;
    this.options = options;
    this.isAndroidXAvailable = isAndroidXAvailable;
    this.performanceEnabled = performanceEnabled;
    this.currentActivity = currentActivity;
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
    addBreadcrumb(scrollTarget, scrollState.type, Collections.singletonMap("direction", direction));
    startTracing(scrollTarget, scrollState.type);
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
        ViewUtils.findTarget(
            decorView,
            motionEvent.getX(),
            motionEvent.getY(),
            view -> ViewUtils.isViewTappable(view));

    if (target == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Unable to find click target. No breadcrumb captured.");
      return false;
    }

    addBreadcrumb(target, "click", Collections.emptyMap());
    startTracing(target, "click");
    return false;
  }

  @Override
  public boolean onScroll(
      final @Nullable MotionEvent firstEvent,
      final @Nullable MotionEvent currentEvent,
      final float distX,
      final float distY) {
    final View decorView = ensureWindowDecorView("onScroll");
    if (decorView == null || firstEvent == null) {
      return false;
    }

    if (scrollState.type == null) {
      final @Nullable View target =
          ViewUtils.findTarget(
              decorView,
              firstEvent.getX(),
              firstEvent.getY(),
              new ViewTargetSelector() {
                @Override
                public boolean select(@NotNull View view) {
                  return ViewUtils.isViewScrollable(view, isAndroidXAvailable);
                }

                @Override
                public boolean skipChildren() {
                  return true;
                }
              });

      if (target == null) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Unable to find scroll target. No breadcrumb captured.");
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
      final float v1) {
    scrollState.type = "swipe";
    return false;
  }

  @Override
  public void onShowPress(MotionEvent motionEvent) {}

  @Override
  public void onLongPress(MotionEvent motionEvent) {}

  // region utils
  private void addBreadcrumb(
      final @NotNull View target,
      final @NotNull String eventType,
      final @NotNull Map<String, Object> additionalData) {
    @NotNull String className;
    @Nullable String canonicalName = target.getClass().getCanonicalName();
    if (canonicalName != null) {
      className = canonicalName;
    } else {
      className = target.getClass().getSimpleName();
    }

    hub.addBreadcrumb(
        Breadcrumb.userInteraction(
            eventType, ViewUtils.getResourceId(target), className, additionalData));
  }

  private void startTracing(
    final @NotNull View target,
    final @NotNull String eventType
  ) {
    if (!performanceEnabled) {
      return;
    }

    final Activity activity = currentActivity.get();
    if (activity == null) {
      options
        .getLogger()
        .log(SentryLevel.DEBUG, "Activity is null, no transaction captured");
      return;
    }

    // as we allow a single UI transaction running on the bound Scope, we finish the previous one
    if (activeTransaction != null) {
      activeTransaction.finish();
    }

    // we can only bind to the scope if there's no running transaction
    // TODO: if view.id is not specified on the view, do not create a transaction, document this
    final String name = getActivityName(activity) + "." + ViewUtils.getResourceId(target);
    final String op = UI_ACTION + "." + eventType;
    final ITransaction transaction =
      hub.startTransaction(
        name,
        op,
        true,
        options.getIdleTimeout()
      );

    hub.configureScope(
      scope -> {
        applyScope(scope, transaction);
      });

    activeTransaction = transaction;
  }

  @VisibleForTesting
  void applyScope(final @NotNull Scope scope, final @NotNull ITransaction transaction) {
    scope.withTransaction(
      scopeTransaction -> {
        // we'd not like to overwrite existent transactions bound to the Scope manually
        if (scopeTransaction == null) {
          scope.setTransaction(transaction);
        } else {
          options
            .getLogger()
            .log(
              SentryLevel.DEBUG,
              "Transaction '%s' won't be bound to the Scope since there's one already in there.",
              transaction.getName());
        }
      });
  }

  private @NotNull String getActivityName(final @NotNull Activity activity) {
    return activity.getClass().getSimpleName();
  }

  private @Nullable View ensureWindowDecorView(final @NotNull String caller) {
    final Window window = windowRef.get();
    if (window == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Window is null in " + caller + ". No breadcrumb captured.");
      return null;
    }

    final View decorView = window.getDecorView();
    if (decorView == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "DecorView is null in " + caller + ". No breadcrumb captured.");
      return null;
    }
    return decorView;
  }
  // endregion

  // region scroll logic
  private static final class ScrollState {
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
