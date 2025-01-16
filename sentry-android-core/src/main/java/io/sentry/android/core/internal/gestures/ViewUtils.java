package io.sentry.android.core.internal.gestures;

import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.internal.gestures.GestureTargetLocator;
import io.sentry.internal.gestures.UiElement;
import java.util.LinkedList;
import java.util.Queue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ViewUtils {

  private static final int[] coordinates = new int[2];

  /**
   * Verifies if the given touch coordinates are within the bounds of the given view.
   *
   * @param view the view to check if the touch coordinates are within its bounds
   * @param x - the x coordinate of a {@link MotionEvent}
   * @param y - the y coordinate of {@link MotionEvent}
   * @return true if the touch coordinates are within the bounds of the view, false otherwise
   */
  private static boolean touchWithinBounds(
      final @Nullable View view, final float x, final float y) {
    if (view == null) {
      return false;
    }

    view.getLocationOnScreen(coordinates);
    int vx = coordinates[0];
    int vy = coordinates[1];

    int w = view.getWidth();
    int h = view.getHeight();

    return !(x < vx || x > vx + w || y < vy || y > vy + h);
  }

  /**
   * Finds a target view, that has been selected/clicked by the given coordinates x and y and the
   * given {@code viewTargetSelector}.
   *
   * @param decorView - the root view of this window
   * @param x - the x coordinate of a {@link MotionEvent}
   * @param y - the y coordinate of {@link MotionEvent}
   * @param targetType - the type of target to find
   * @return the {@link View} that contains the touch coordinates and complements the {@code
   *     viewTargetSelector}
   */
  static @Nullable UiElement findTarget(
      final @NotNull SentryAndroidOptions options,
      final @NotNull View decorView,
      final float x,
      final float y,
      final UiElement.Type targetType) {

    final Queue<View> queue = new LinkedList<>();
    queue.add(decorView);

    @Nullable UiElement target = null;
    while (queue.size() > 0) {
      final View view = queue.poll();

      if (!touchWithinBounds(view, x, y)) {
        // if the touch is not hitting the view, skip traversal of its children
        continue;
      }

      if (view instanceof ViewGroup) {
        final ViewGroup viewGroup = (ViewGroup) view;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
          queue.add(viewGroup.getChildAt(i));
        }
      }

      for (GestureTargetLocator locator : options.getGestureTargetLocators()) {
        final @Nullable UiElement newTarget = locator.locate(view, x, y, targetType);
        if (newTarget != null) {
          if (targetType == UiElement.Type.CLICKABLE) {
            target = newTarget;
          } else if (targetType == UiElement.Type.SCROLLABLE) {
            return newTarget;
          }
        }
      }
    }
    return target;
  }

  /**
   * Retrieves the human-readable view id based on {@code view.getContext().getResources()}, falls
   * back to a hexadecimal id representation in case the view id is not available in the resources.
   *
   * @param view - the view that the id is being retrieved for.
   * @return human-readable view id
   */
  static String getResourceIdWithFallback(final @NotNull View view) {
    final int viewId = view.getId();
    try {
      return getResourceId(view);
    } catch (Resources.NotFoundException e) {
      // fall back to hex representation of the id
      return "0x" + Integer.toString(viewId, 16);
    }
  }

  /**
   * Retrieves the human-readable view id based on {@code view.getContext().getResources()}.
   *
   * @param view - the view whose id is being retrieved
   * @return human-readable view id
   * @throws Resources.NotFoundException in case the view id was not found
   */
  public static String getResourceId(final @NotNull View view) throws Resources.NotFoundException {
    final int viewId = view.getId();
    if (viewId == View.NO_ID || isViewIdGenerated(viewId)) {
      throw new Resources.NotFoundException();
    }
    final Resources resources = view.getContext().getResources();
    if (resources != null) {
      return resources.getResourceEntryName(viewId);
    }
    return "";
  }

  private static boolean isViewIdGenerated(int id) {
    return (id & 0xFF000000) == 0 && (id & 0x00FFFFFF) != 0;
  }
}
