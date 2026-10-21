package io.sentry.android.core.internal.gestures;

import android.content.res.Resources;
import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.internal.gestures.GestureTargetLocator;
import io.sentry.internal.gestures.UiElement;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ViewUtils {

  /**
   * Verifies if the given touch coordinates, expressed in the view's own local coordinate space,
   * are within the bounds of the given view.
   *
   * @param view the view to check if the touch coordinates are within its bounds
   * @param localX - the x coordinate of the touch, relative to the view's top-left corner
   * @param localY - the y coordinate of the touch, relative to the view's top-left corner
   * @return true if the touch coordinates are within the bounds of the view, false otherwise
   */
  private static boolean touchWithinBounds(
      final @Nullable View view, final float localX, final float localY) {
    if (view == null) {
      return false;
    }

    final int w = view.getWidth();
    final int h = view.getHeight();

    return !(localX < 0 || localX > w || localY < 0 || localY > h);
  }

  /**
   * Maps a touch point expressed in the parent's local coordinate space into the child's local
   * coordinate space. This mirrors how {@link ViewGroup} dispatches touch events to its children
   * and lets us hit-test the whole tree with a single downward traversal, instead of calling {@link
   * View#getLocationOnScreen(int[])} (which walks up to the root) for every view.
   */
  private static @NotNull ViewWithLocation mapToChild(
      final @NotNull View child,
      final float parentX,
      final float parentY,
      final int parentScrollX,
      final int parentScrollY) {
    float childX = parentX + parentScrollX - child.getLeft();
    float childY = parentY + parentScrollY - child.getTop();

    final @Nullable Matrix matrix = child.getMatrix();
    if (matrix != null && !matrix.isIdentity()) {
      final Matrix inverse = new Matrix();
      if (matrix.invert(inverse)) {
        final float[] point = {childX, childY};
        inverse.mapPoints(point);
        childX = point[0];
        childY = point[1];
      }
    }
    return new ViewWithLocation(child, childX, childY);
  }

  /**
   * Finds a target view, that has been selected/clicked by the given coordinates x and y and the
   * given {@code viewTargetSelector}.
   *
   * @param decorView - the root view of this window
   * @param x - the x coordinate of a {@link MotionEvent}, relative to the decor view
   * @param y - the y coordinate of {@link MotionEvent}, relative to the decor view
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

    final List<GestureTargetLocator> locators = options.getGestureTargetLocators();
    final Queue<ViewWithLocation> queue = new ArrayDeque<>();
    // The touch coordinates from the MotionEvent are already relative to the decor view, i.e. in
    // its local coordinate space.
    queue.add(new ViewWithLocation(decorView, x, y));

    @Nullable UiElement target = null;
    while (!queue.isEmpty()) {
      final ViewWithLocation current = queue.poll();
      final View view = current.view;

      if (!touchWithinBounds(view, current.x, current.y)) {
        // if the touch is not hitting the view, skip traversal of its children
        continue;
      }

      if (view instanceof ViewGroup) {
        final ViewGroup viewGroup = (ViewGroup) view;
        final int scrollX = viewGroup.getScrollX();
        final int scrollY = viewGroup.getScrollY();
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
          final @Nullable View child = viewGroup.getChildAt(i);
          if (child != null) {
            queue.add(mapToChild(child, current.x, current.y, scrollX, scrollY));
          }
        }
      }

      // Locators receive the original decor-view-relative coordinates, as the Compose locator
      // hit-tests against window coordinates.
      for (int i = 0; i < locators.size(); i++) {
        final GestureTargetLocator locator = locators.get(i);
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

  private static final class ViewWithLocation {
    final @NotNull View view;
    final float x;
    final float y;

    ViewWithLocation(final @NotNull View view, final float x, final float y) {
      this.view = view;
      this.x = x;
      this.y = y;
    }
  }

  /**
   * Retrieves the human-readable view id based on {@code view.getContext().getResources()}, falls
   * back to a hexadecimal id representation in case the view id is not available in the resources.
   *
   * @param view - the view that the id is being retrieved for.
   * @return human-readable view id
   */
  static String getResourceIdWithFallback(final @NotNull View view) {
    final @Nullable String resourceId = getResourceIdOrNull(view);
    if (resourceId == null) {
      // fall back to hex representation of the id
      return "0x" + Integer.toString(view.getId(), 16);
    }
    return resourceId;
  }

  /**
   * Retrieves the human-readable view id based on {@code view.getContext().getResources()}, or
   * {@code null} when the view has no resource-backed id. Returning {@code null} rather than
   * throwing avoids exception-driven control flow on hot, main-thread paths such as view-hierarchy
   * snapshots and gesture target resolution.
   *
   * @param view - the view whose id is being retrieved
   * @return human-readable view id, or {@code null} if it cannot be resolved
   */
  public static @Nullable String getResourceIdOrNull(final @NotNull View view) {
    final int viewId = view.getId();
    if (viewId == View.NO_ID || isViewIdGenerated(viewId)) {
      return null;
    }
    final Resources resources = view.getContext().getResources();
    if (resources == null) {
      return "";
    }
    try {
      return resources.getResourceEntryName(viewId);
    } catch (Resources.NotFoundException e) {
      return null;
    }
  }

  private static boolean isViewIdGenerated(int id) {
    return (id & 0xFF000000) == 0 && (id & 0x00FFFFFF) != 0;
  }
}
