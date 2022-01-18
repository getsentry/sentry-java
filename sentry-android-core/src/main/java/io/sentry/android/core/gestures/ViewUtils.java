package io.sentry.android.core.gestures;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ScrollView;
import androidx.core.view.ScrollingView;
import io.sentry.util.Objects;
import java.util.ArrayDeque;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ViewUtils {
  static @Nullable View findTarget(
    final @NotNull View decorView,
    final float x,
    final float y,
    final @NotNull ViewTargetSelector viewTargetSelector
  ) {
    Queue<View> queue = new ArrayDeque<>();
    queue.add(decorView);

    @Nullable View target = null;
    // the coordinates variable can be method-local, but we allocate it here, to avoid allocation
    // in the while- and for-loops
    int[] coordinates = new int[2];

    while (queue.size() > 0) {
      final View view = Objects.requireNonNull(queue.poll(), "view is required");

      if (viewTargetSelector.select(view)) {
        target = view;
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
    }

    return target;
  }

  private static boolean touchWithinBounds(
    final @NotNull View view,
    final float x,
    final float y,
    final int[] coords
  ) {
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

  static boolean isViewScrollable(final @NotNull View view) {
    return (isJetpackScrollingView(view) ||
      AbsListView.class.isAssignableFrom(view.getClass()) ||
      ScrollView.class.isAssignableFrom(view.getClass())) &&
      view.getVisibility() == View.VISIBLE;
  }

  private static boolean isJetpackScrollingView(final @NotNull View view) {
    try {
      Class.forName("androidx.core.view.ScrollingView");
      return ScrollingView.class.isAssignableFrom(view.getClass());
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }

  static String getResourceId(final @NotNull View view) {
    final int viewId = view.getId();
    final Resources resources = view.getContext().getResources();
    String resourceId = "";
    try {
      if (resources != null) {
        resourceId = resources.getResourceEntryName(viewId);
      }
    } catch (Resources.NotFoundException e) {
      // fall back to hex representation of the id
      resourceId = "0x" + Integer.toString(viewId, 16);
    }
    return resourceId;
  }
}
