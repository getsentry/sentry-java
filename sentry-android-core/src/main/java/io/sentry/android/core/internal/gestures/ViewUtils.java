package io.sentry.android.core.internal.gestures;

import android.content.res.Resources;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ScrollView;
import androidx.core.view.ScrollingView;
import io.sentry.util.Objects;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ViewUtils {

  static class UiElement {
    final @NotNull WeakReference<View> viewRef;
    final @Nullable String className;
    final @Nullable String resourceName;
    final @Nullable String tag;

    public static @Nullable UiElement create(@Nullable View view, @Nullable String tag) {
      if (view == null && tag == null) {
        return null;
      } else {
        return new UiElement(view, tag);
      }
    }

    private UiElement(@Nullable View view, @Nullable String tag) {
      this.viewRef = new WeakReference<>(view);
      if (view != null) {
        this.resourceName = getResourceIdWithFallback(view);
        @Nullable String canonicalName = view.getClass().getCanonicalName();
        if (canonicalName != null) {
          this.className = canonicalName;
        } else {
          this.className = view.getClass().getSimpleName();
        }
      } else {
        this.resourceName = null;
        this.className = null;
      }
      this.tag = tag;
    }

    public @Nullable String getClassName() {
      return className;
    }

    public @Nullable String getResourceName() {
      return resourceName;
    }

    public @Nullable String getTag() {
      return tag;
    }

    public @NotNull String getIdentifier() {
      if (resourceName != null) {
        return resourceName;
      } else {
        return tag;
      }
    }

    public @Nullable View getView() {
      return viewRef.get();
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(viewRef, resourceName, tag);
    }
  }

  enum TargetType {
    CLICKABLE,
    SCROLLABLE
  }

  /**
   * Finds a target view, that has been selected/clicked by the given coordinates x and y and the
   * given {@code viewTargetSelector}.
   *
   * @param isAndroidXAvailable - true if androidx is available at runtime
   * @param decorView - the root view of this window
   * @param x - the x coordinate of a {@link MotionEvent}
   * @param y - the y coordinate of {@link MotionEvent}
   * @param targetType - the type of target to find
   * @return the {@link View} that contains the touch coordinates and complements the {@code
   *     viewTargetSelector}
   */
  static @Nullable ViewUtils.UiElement findTarget(
      boolean isAndroidXAvailable,
      final @NotNull View decorView,
      final float x,
      final float y,
      final TargetType targetType) {
    final Queue<View> queue = new LinkedList<>();
    queue.add(decorView);

    @Nullable View targetView = null;

    // the coordinates variable can be method-local, but we allocate it here, to avoid allocation
    // in the while- and for-loops
    int[] coordinates = new int[2];

    while (queue.size() > 0) {
      final View view = Objects.requireNonNull(queue.poll(), "view is required");
      if (targetType == TargetType.SCROLLABLE
          && ViewUtils.isViewScrollable(view, isAndroidXAvailable)) {
        targetView = view;
        // skip any children for scrollable targets
        break;
      } else if (targetType == TargetType.CLICKABLE && ViewUtils.isViewTappable(view)) {
        targetView = view;
      }

      if (view instanceof ViewGroup) {
        final ViewGroup viewGroup = (ViewGroup) view;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
          final View child = viewGroup.getChildAt(i);
          if (touchWithinBounds(child, x, y, coordinates)) {
            queue.add(child);
          }
        }
      }

      if (AndroidComposeViewUtils.isComposeView(view)) {
        final UiElement composeElement = AndroidComposeViewUtils.findTarget(view, x, y, targetType);
        if (composeElement != null) {
          return composeElement;
        }
      }
    }
    return UiElement.create(targetView, null);
  }

  private static boolean touchWithinBounds(
      final @NotNull View view, final float x, final float y, final int[] coords) {
    view.getLocationOnScreen(coords);
    int vx = coords[0];
    int vy = coords[1];

    int w = view.getWidth();
    int h = view.getHeight();

    return !(x < vx || x > vx + w || y < vy || y > vy + h);
  }

  static boolean isViewTappable(final @NotNull View view) {
    return view.isClickable() && view.getVisibility() == View.VISIBLE;
  }

  static boolean isViewScrollable(final @NotNull View view, final boolean isAndroidXAvailable) {
    return (isJetpackScrollingView(view, isAndroidXAvailable)
            || AbsListView.class.isAssignableFrom(view.getClass())
            || ScrollView.class.isAssignableFrom(view.getClass()))
        && view.getVisibility() == View.VISIBLE;
  }

  private static boolean isJetpackScrollingView(
      final @NotNull View view, final boolean isAndroidXAvailable) {
    if (!isAndroidXAvailable) {
      return false;
    }
    return ScrollingView.class.isAssignableFrom(view.getClass());
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
  static String getResourceId(final @NotNull View view) throws Resources.NotFoundException {
    final int viewId = view.getId();
    final Resources resources = view.getContext().getResources();
    String resourceId = "";
    if (resources != null) {
      resourceId = resources.getResourceEntryName(viewId);
    }
    return resourceId;
  }
}
