package io.sentry.android.core.internal.gestures;

import android.content.res.Resources;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ScrollView;
import androidx.core.view.ScrollingView;
import io.sentry.android.core.internal.util.ClassUtil;
import io.sentry.internal.gestures.GestureTargetLocator;
import io.sentry.internal.gestures.UiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AndroidViewGestureTargetLocator implements GestureTargetLocator {

  private static final String ORIGIN = "old_view_system";

  private final boolean isAndroidXAvailable;
  private final int[] coordinates = new int[2];

  public AndroidViewGestureTargetLocator(final boolean isAndroidXAvailable) {
    this.isAndroidXAvailable = isAndroidXAvailable;
  }

  @Override
  public @Nullable UiElement locate(
      @Nullable Object root, float x, float y, UiElement.Type targetType) {
    if (!(root instanceof View)) {
      return null;
    }
    final View view = (View) root;
    if (touchWithinBounds(view, x, y)) {
      if (targetType == UiElement.Type.CLICKABLE && isViewTappable(view)) {
        return createUiElement(view);
      } else if (targetType == UiElement.Type.SCROLLABLE
          && isViewScrollable(view, isAndroidXAvailable)) {
        return createUiElement(view);
      }
    }
    return null;
  }

  private UiElement createUiElement(final @NotNull View targetView) {
    try {
      final String resourceName = ViewUtils.getResourceId(targetView);
      @Nullable String className = ClassUtil.getClassName(targetView);
      return new UiElement(targetView, className, resourceName, null, ORIGIN);
    } catch (Resources.NotFoundException ignored) {
      return null;
    }
  }

  private boolean touchWithinBounds(final @NotNull View view, final float x, final float y) {
    view.getLocationOnScreen(coordinates);
    int vx = coordinates[0];
    int vy = coordinates[1];

    int w = view.getWidth();
    int h = view.getHeight();

    return !(x < vx || x > vx + w || y < vy || y > vy + h);
  }

  private static boolean isViewTappable(final @NotNull View view) {
    return view.isClickable() && view.getVisibility() == View.VISIBLE;
  }

  private static boolean isViewScrollable(
      final @NotNull View view, final boolean isAndroidXAvailable) {
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
}
