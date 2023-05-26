package io.sentry.compose;

import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.layout.LayoutCoordinatesKt;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.LayoutNodeLayoutDelegate;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.lang.reflect.Field;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SentryComposeHelper {

  private final @NotNull ILogger logger;
  private Field layoutDelegateField = null;

  public SentryComposeHelper(final @NotNull ILogger logger) {
    this.logger = logger;
    try {
      final Class<?> clazz = Class.forName("androidx.compose.ui.node.LayoutNode");
      layoutDelegateField = clazz.getDeclaredField("layoutDelegate");
      layoutDelegateField.setAccessible(true);
    } catch (Exception e) {
      logger.log(SentryLevel.WARNING, "Could not find LayoutNode.layoutDelegate field");
    }
  }

  public @Nullable Rect getLayoutNodeBoundsInWindow(@NotNull final LayoutNode node) {
    if (layoutDelegateField != null) {
      try {
        final LayoutNodeLayoutDelegate delegate =
            (LayoutNodeLayoutDelegate) layoutDelegateField.get(node);
        return LayoutCoordinatesKt.boundsInWindow(delegate.getOuterCoordinator().getCoordinates());
      } catch (Exception e) {
        logger.log(SentryLevel.WARNING, "Could not fetch position for LayoutNode", e);
      }
    }
    return null;
  }
}
