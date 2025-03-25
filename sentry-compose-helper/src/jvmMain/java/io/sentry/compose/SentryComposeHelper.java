package io.sentry.compose;

import androidx.annotation.NonNull;
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

@SuppressWarnings("KotlinInternalInJava")
public class SentryComposeHelper {

  private final @NotNull ILogger logger;
  private final @Nullable Field layoutDelegateField;
  private final @Nullable Field testTagElementField;
  private final @Nullable Field sentryTagElementField;

  @Nullable
  public String extractTag(final @NotNull Modifier modifier) {
    final @Nullable String type = modifier.getClass().getCanonicalName();
    // Newer Jetpack Compose uses TestTagElement as node elements
    // See
    // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/TestTag.kt;l=34;drc=dcaa116fbfda77e64a319e1668056ce3b032469f
    try {
      if ("androidx.compose.ui.platform.TestTagElement".equals(type)
          && testTagElementField != null) {
        final @Nullable Object value = testTagElementField.get(modifier);
        return (String) value;
      } else if ("io.sentry.compose.SentryModifier.SentryTagModifierNodeElement".equals(type)
          && sentryTagElementField != null) {
        final @Nullable Object value = sentryTagElementField.get(modifier);
        return (String) value;
      }
    } catch (Throwable e) {
      // ignored
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
    layoutDelegateField =
        loadField(logger, "androidx.compose.ui.node.LayoutNode", "layoutDelegate");
    testTagElementField = loadField(logger, "androidx.compose.ui.platform.TestTagElement", "tag");
    sentryTagElementField =
        loadField(logger, "io.sentry.compose.SentryModifier.SentryTagModifierNodeElement", "tag");
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

  @Nullable
  private static Field loadField(
      @NonNull ILogger logger, final @NotNull String className, final @NotNull String fieldName) {
    try {
      final Class<?> clazz = Class.forName(className);
      final @Nullable Field field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true);
      return field;
    } catch (Exception e) {
      logger.log(SentryLevel.WARNING, "Could not load " + className + "." + fieldName + " field");
    }
    return null;
  }
}
