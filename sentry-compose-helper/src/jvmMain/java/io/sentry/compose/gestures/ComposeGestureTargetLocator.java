package io.sentry.compose.gestures;

import androidx.compose.ui.Modifier;
import androidx.compose.ui.geometry.Rect;
import androidx.compose.ui.layout.ModifierInfo;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.Owner;
import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsModifier;
import androidx.compose.ui.semantics.SemanticsPropertyKey;
import io.sentry.ILogger;
import io.sentry.ISentryLifecycleToken;
import io.sentry.SentryIntegrationPackageStorage;
import io.sentry.compose.SentryComposeHelper;
import io.sentry.compose.helper.BuildConfig;
import io.sentry.internal.gestures.GestureTargetLocator;
import io.sentry.internal.gestures.UiElement;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("KotlinInternalInJava")
public final class ComposeGestureTargetLocator implements GestureTargetLocator {

  private static final String ORIGIN = "jetpack_compose";

  private final @NotNull ILogger logger;
  private volatile @Nullable SentryComposeHelper composeHelper;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public ComposeGestureTargetLocator(final @NotNull ILogger logger) {
    this.logger = logger;
    SentryIntegrationPackageStorage.getInstance().addIntegration("ComposeUserInteraction");
    SentryIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.sentry:sentry-compose", BuildConfig.VERSION_NAME);
  }

  @Override
  public @Nullable UiElement locate(
      @Nullable Object root, float x, float y, UiElement.Type targetType) {

    // lazy init composeHelper as it's using some reflection under the hood
    if (composeHelper == null) {
      try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
        if (composeHelper == null) {
          composeHelper = new SentryComposeHelper(logger);
        }
      }
    }

    if (!(root instanceof Owner)) {
      return null;
    }

    final @NotNull Queue<LayoutNode> queue = new LinkedList<>();
    queue.add(((Owner) root).getRoot());

    // the final tag to return
    @Nullable String targetTag = null;

    // the last known tag when iterating the node tree
    @Nullable String lastKnownTag = null;
    while (!queue.isEmpty()) {
      final @Nullable LayoutNode node = queue.poll();
      if (node == null) {
        continue;
      }

      if (node.isPlaced() && layoutNodeBoundsContain(composeHelper, node, x, y)) {
        boolean isClickable = false;
        boolean isScrollable = false;

        final List<ModifierInfo> modifiers = node.getModifierInfo();
        for (ModifierInfo modifierInfo : modifiers) {
          final @Nullable String tag = SentryComposeHelper.extractTag(modifierInfo.getModifier());
          if (tag != null) {
            lastKnownTag = tag;
          }

          if (modifierInfo.getModifier() instanceof SemanticsModifier) {
            final SemanticsModifier semanticsModifierCore =
                (SemanticsModifier) modifierInfo.getModifier();
            final SemanticsConfiguration semanticsConfiguration =
                semanticsModifierCore.getSemanticsConfiguration();
            for (Map.Entry<? extends SemanticsPropertyKey<?>, ?> entry : semanticsConfiguration) {
              final @Nullable String key = entry.getKey().getName();
              if ("ScrollBy".equals(key)) {
                isScrollable = true;
              } else if ("OnClick".equals(key)) {
                isClickable = true;
              }
            }
          } else {
            final @NotNull Modifier modifier = modifierInfo.getModifier();
            // Newer Jetpack Compose 1.5 uses Node modifiers for clicks/scrolls
            final @Nullable String type = modifier.getClass().getCanonicalName();
            if ("androidx.compose.foundation.ClickableElement".equals(type)
                || "androidx.compose.foundation.CombinedClickableElement".equals(type)) {
              isClickable = true;
            } else if ("androidx.compose.foundation.ScrollingLayoutElement".equals(type)) {
              isScrollable = true;
            }
          }
        }

        if (isClickable && targetType == UiElement.Type.CLICKABLE) {
          targetTag = lastKnownTag;
        }
        if (isScrollable && targetType == UiElement.Type.SCROLLABLE) {
          targetTag = lastKnownTag;
          // skip any children for scrollable targets
          break;
        }
      }
      queue.addAll(node.getZSortedChildren().asMutableList());
    }

    if (targetTag == null) {
      return null;
    } else {
      return new UiElement(null, null, null, targetTag, ORIGIN);
    }
  }

  private static boolean layoutNodeBoundsContain(
      @NotNull SentryComposeHelper composeHelper,
      @NotNull LayoutNode node,
      final float x,
      final float y) {

    final @Nullable Rect bounds = composeHelper.getLayoutNodeBoundsInWindow(node);
    if (bounds == null) {
      return false;
    } else {
      return x >= bounds.getLeft()
          && x <= bounds.getRight()
          && y >= bounds.getTop()
          && y <= bounds.getBottom();
    }
  }
}
