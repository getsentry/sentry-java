package io.sentry.android.core.internal.gestures;

import android.view.View;
import android.widget.AbsListView;
import android.widget.ScrollView;
import androidx.core.view.ScrollingView;
import io.sentry.internal.gestures.GestureTargetLocator;
import io.sentry.internal.gestures.UiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AndroidViewGestureTargetLocator implements GestureTargetLocator {

  private final boolean isAndroidXAvailable;
  private final int[] coordinates = new int[2];

  public AndroidViewGestureTargetLocator(final boolean isAndroidXAvailable) {
    this.isAndroidXAvailable = isAndroidXAvailable;
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

  static boolean touchWithinBounds(
      final @NotNull View view, final float x, final float y, final int[] coords) {
    view.getLocationOnScreen(coords);
    int vx = coords[0];
    int vy = coords[1];

    int w = view.getWidth();
    int h = view.getHeight();

    return !(x < vx || x > vx + w || y < vy || y > vy + h);
  }

  @Override
  public @Nullable UiElement locate(
      @NotNull Object root, float x, float y, UiElement.Type targetType) {
    if (!(root instanceof View)) {
      return null;
    }
    final View view = (View) root;
    if (touchWithinBounds(view, x, y, coordinates)) {
      if (targetType == UiElement.Type.CLICKABLE && isViewTappable(view)) {
        return createUiElement(view);
      } else if (targetType == UiElement.Type.SCROLLABLE
          && isViewScrollable(view, isAndroidXAvailable)) {
        return createUiElement(view);
      }
    }
    return null;
  }

  private UiElement createUiElement(@NotNull View targetView) {
    final String resourceName = ViewUtils.getResourceIdWithFallback(targetView);
    @Nullable String className = targetView.getClass().getCanonicalName();
    if (className == null) {
      className = targetView.getClass().getSimpleName();
    }
    return new UiElement(targetView, className, resourceName, null);
  }
}
