package io.sentry.compose;

import androidx.compose.ui.Modifier;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.layout.LayoutCoordinatesKt;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.LayoutNodeLayoutDelegate;
import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsModifier;
import androidx.compose.ui.semantics.SemanticsPropertyKey;
import io.sentry.ILogger;
import io.sentry.SentryLevel;
import java.lang.reflect.Field;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SentryComposeHelper {

  private final @NotNull ILogger logger;
  private Field layoutDelegateField = null;

  @Nullable
  public static String extractTag(final @NotNull Modifier modifier) {
    final @Nullable String type = modifier.getClass().getCanonicalName();
    // Newer Jetpack Compose uses TestTagElement as node elements
    // See
    // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/TestTag.kt;l=34;drc=dcaa116fbfda77e64a319e1668056ce3b032469f
    if ("androidx.compose.ui.platform.TestTagElement".equals(type)
        || "io.sentry.compose.SentryModifier.SentryTagModifierNodeElement".equals(type)) {
      try {
        final Field tagField = modifier.getClass().getDeclaredField("tag");
        tagField.setAccessible(true);
        final @Nullable Object value = tagField.get(modifier);
        return (String) value;
      } catch (Throwable e) {
        // ignored
      }
    }

    // Older versions use SemanticsModifier
    if (modifier instanceof SemanticsModifier) {
      final SemanticsConfiguration semanticsConfiguration =
          ((SemanticsModifier) modifier).getSemanticsConfiguration();
      for (Map.Entry<? extends SemanticsPropertyKey<?>, ?> entry : semanticsConfiguration) {
        final @Nullable String key = entry.getKey().getName();
        if ("SentryTag".equals(key) || "TestTag".equals(key)) {
          if (entry.getValue() instanceof String) {
            return (String) entry.getValue();
          }
        }
      }
    }
    return null;
  }

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
