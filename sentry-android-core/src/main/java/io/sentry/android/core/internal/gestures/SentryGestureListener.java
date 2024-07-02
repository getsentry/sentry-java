package io.sentry.android.core.internal.gestures;

import static io.sentry.TypeCheckHint.ANDROID_MOTION_EVENT;
import static io.sentry.TypeCheckHint.ANDROID_VIEW;

import android.app.Activity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import io.sentry.Breadcrumb;
import io.sentry.Hint;
import io.sentry.IScope;
import io.sentry.IScopes;
import io.sentry.ITransaction;
import io.sentry.SentryLevel;
import io.sentry.SpanStatus;
import io.sentry.TransactionContext;
import io.sentry.TransactionOptions;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.internal.gestures.UiElement;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.util.TracingUtils;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class SentryGestureListener implements GestureDetector.OnGestureListener {

  private enum GestureType {
    Click,
    Scroll,
    Swipe,
    Unknown
  }

  static final String UI_ACTION = "ui.action";
  private static final String TRACE_ORIGIN = "auto.ui.gesture_listener";

  private final @NotNull WeakReference<Activity> activityRef;
  private final @NotNull IScopes scopes;
  private final @NotNull SentryAndroidOptions options;

  private @Nullable UiElement activeUiElement = null;
  private @Nullable ITransaction activeTransaction = null;
  private @NotNull GestureType activeEventType = GestureType.Unknown;

  private final ScrollState scrollState = new ScrollState();

  public SentryGestureListener(
      final @NotNull Activity currentActivity,
      final @NotNull IScopes scopes,
      final @NotNull SentryAndroidOptions options) {
    this.activityRef = new WeakReference<>(currentActivity);
    this.scopes = scopes;
    this.options = options;
  }

  public void onUp(final @NotNull MotionEvent motionEvent) {
    final View decorView = ensureWindowDecorView("onUp");
    final UiElement scrollTarget = scrollState.target;
    if (decorView == null || scrollTarget == null) {
      return;
    }

    if (scrollState.type == GestureType.Unknown) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Unable to define scroll type. No breadcrumb captured.");
      return;
    }

    final String direction = scrollState.calculateDirection(motionEvent);
    addBreadcrumb(
        scrollTarget,
        scrollState.type,
        Collections.singletonMap("direction", direction),
        motionEvent);
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

    final @Nullable UiElement target =
        ViewUtils.findTarget(
            options, decorView, motionEvent.getX(), motionEvent.getY(), UiElement.Type.CLICKABLE);

    if (target == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Unable to find click target. No breadcrumb captured.");
      return false;
    }

    addBreadcrumb(target, GestureType.Click, Collections.emptyMap(), motionEvent);
    startTracing(target, GestureType.Click);
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

    if (scrollState.type == GestureType.Unknown) {
      final @Nullable UiElement target =
          ViewUtils.findTarget(
              options, decorView, firstEvent.getX(), firstEvent.getY(), UiElement.Type.SCROLLABLE);

      if (target == null) {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Unable to find scroll target. No breadcrumb captured.");
        return false;
      } else {
        options
            .getLogger()
            .log(SentryLevel.DEBUG, "Scroll target found: " + target.getIdentifier());
      }

      scrollState.setTarget(target);
      scrollState.type = GestureType.Scroll;
    }
    return false;
  }

  @Override
  public boolean onFling(
      final @Nullable MotionEvent motionEvent,
      final @Nullable MotionEvent motionEvent1,
      final float v,
      final float v1) {
    scrollState.type = GestureType.Swipe;
    return false;
  }

  @Override
  public void onShowPress(MotionEvent motionEvent) {}

  @Override
  public void onLongPress(MotionEvent motionEvent) {}

  // region utils
  private void addBreadcrumb(
      final @NotNull UiElement target,
      final @NotNull GestureType eventType,
      final @NotNull Map<String, Object> additionalData,
      final @NotNull MotionEvent motionEvent) {

    if (!options.isEnableUserInteractionBreadcrumbs()) {
      return;
    }

    final String type = getGestureType(eventType);

    final Hint hint = new Hint();
    hint.set(ANDROID_MOTION_EVENT, motionEvent);
    hint.set(ANDROID_VIEW, target.getView());

    scopes.addBreadcrumb(
        Breadcrumb.userInteraction(
            type, target.getResourceName(), target.getClassName(), target.getTag(), additionalData),
        hint);
  }

  private void startTracing(final @NotNull UiElement target, final @NotNull GestureType eventType) {

    final boolean isNewGestureSameAsActive =
        (eventType == activeEventType && target.equals(activeUiElement));
    final boolean isClickGesture = eventType == GestureType.Click;
    // we always want to start new transaction/traces for clicks, for swipe/scroll only if the
    // target changed
    final boolean isNewInteraction = isClickGesture || !isNewGestureSameAsActive;

    if (!(options.isTracingEnabled() && options.isEnableUserInteractionTracing())) {
      if (isNewInteraction) {
        TracingUtils.startNewTrace(scopes);
        activeUiElement = target;
        activeEventType = eventType;
      }
      return;
    }

    final Activity activity = activityRef.get();
    if (activity == null) {
      options.getLogger().log(SentryLevel.DEBUG, "Activity is null, no transaction captured.");
      return;
    }

    final @Nullable String viewIdentifier = target.getIdentifier();

    if (activeTransaction != null) {
      if (!isNewInteraction && !activeTransaction.isFinished()) {
        options
            .getLogger()
            .log(
                SentryLevel.DEBUG,
                "The view with id: "
                    + viewIdentifier
                    + " already has an ongoing transaction assigned. Rescheduling finish");

        final Long idleTimeout = options.getIdleTimeout();
        if (idleTimeout != null) {
          // reschedule the finish task for the idle transaction, so it keeps running for the same
          // view
          activeTransaction.scheduleFinish();
        }
        return;
      } else {
        // as we allow a single UI transaction running on the bound Scope, we finish the previous
        // one, if it's a new view
        stopTracing(SpanStatus.OK);
      }
    }

    // we can only bind to the scope if there's no running transaction
    final String name = getActivityName(activity) + "." + viewIdentifier;
    final String op = UI_ACTION + "." + getGestureType(eventType);

    final TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setWaitForChildren(true);
    transactionOptions.setDeadlineTimeout(
        TransactionOptions.DEFAULT_DEADLINE_TIMEOUT_AUTO_TRANSACTION);
    transactionOptions.setIdleTimeout(options.getIdleTimeout());
    transactionOptions.setTrimEnd(true);
    transactionOptions.setOrigin(TRACE_ORIGIN + "." + target.getOrigin());

    final ITransaction transaction =
        scopes.startTransaction(
            new TransactionContext(name, TransactionNameSource.COMPONENT, op), transactionOptions);

    scopes.configureScope(
        scope -> {
          applyScope(scope, transaction);
        });

    activeTransaction = transaction;
    activeUiElement = target;
    activeEventType = eventType;
  }

  void stopTracing(final @NotNull SpanStatus status) {
    if (activeTransaction != null) {
      final SpanStatus currentStatus = activeTransaction.getStatus();
      // status might be set by other integrations, let's not overwrite it
      if (currentStatus == null) {
        activeTransaction.finish(status);
      } else {
        activeTransaction.finish();
      }
    }
    scopes.configureScope(
        scope -> {
          // avoid method refs on Android due to some issues with older AGP setups
          // noinspection Convert2MethodRef
          clearScope(scope);
        });
    activeTransaction = null;
    if (activeUiElement != null) {
      activeUiElement = null;
    }
    activeEventType = GestureType.Unknown;
  }

  @VisibleForTesting
  void clearScope(final @NotNull IScope scope) {
    scope.withTransaction(
        transaction -> {
          if (transaction == activeTransaction) {
            scope.clearTransaction();
          }
        });
  }

  @VisibleForTesting
  void applyScope(final @NotNull IScope scope, final @NotNull ITransaction transaction) {
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
    final Activity activity = activityRef.get();
    if (activity == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Activity is null in " + caller + ". No breadcrumb captured.");
      return null;
    }

    final Window window = activity.getWindow();
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

  @NotNull
  private static String getGestureType(final @NotNull GestureType eventType) {
    final @NotNull String type;
    switch (eventType) {
      case Click:
        type = "click";
        break;
      case Scroll:
        type = "scroll";
        break;
      case Swipe:
        type = "swipe";
        break;
      default:
      case Unknown:
        type = "unknown";
        break;
    }
    return type;
  }
  // endregion

  // region scroll logic
  private static final class ScrollState {
    private @NotNull GestureType type = GestureType.Unknown;
    private @Nullable UiElement target;
    private float startX = 0f;
    private float startY = 0f;

    private void setTarget(final @NotNull UiElement target) {
      this.target = target;
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
      target = null;
      type = GestureType.Unknown;
      startX = 0f;
      startY = 0f;
    }
  }
  // endregion
}
