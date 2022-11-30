package io.sentry.android.core.internal.gestures;

import android.view.View;
import androidx.compose.ui.layout.LayoutCoordinatesKt;
import androidx.compose.ui.layout.ModifierInfo;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.platform.AndroidComposeView;
import androidx.compose.ui.semantics.SemanticsActions;
import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsModifier;
import androidx.compose.ui.semantics.SemanticsProperties;
import io.sentry.util.Objects;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("KotlinInternalInJava")
final class AndroidComposeViewUtils {
  static boolean isComposeView(View view) {
    return view instanceof AndroidComposeView;
  }

  static @Nullable ViewUtils.UiElement findTarget(
      final @NotNull View view,
      final float x,
      final float y,
      final ViewUtils.TargetType targetType) {
    @Nullable String targetTag = null;

    if (!isComposeView(view)) {
      return null;
    }

    final AndroidComposeView androidComposeView = (AndroidComposeView) view;
    final LayoutNode root = androidComposeView.getRoot();

    final Queue<LayoutNode> queue = new LinkedList<>();
    queue.add(root);

    while (!queue.isEmpty()) {
      final LayoutNode node = Objects.requireNonNull(queue.poll(), "layoutnode is required");

      if (node.isPlaced() && nodeBoundsContains(node, x, y)) {
        boolean isClickable = false;
        boolean isScrollable = false;
        @Nullable String testTag = null;

        final List<ModifierInfo> modifiers = node.getModifierInfo();
        for (ModifierInfo modifier : modifiers) {
          if (modifier.getModifier() instanceof SemanticsModifier) {
            final SemanticsModifier semanticsModifierCore =
                ((SemanticsModifier) modifier.getModifier());
            final SemanticsConfiguration semanticsConfiguration =
                semanticsModifierCore.getSemanticsConfiguration();

            isScrollable =
                isScrollable
                    || semanticsConfiguration.contains(SemanticsActions.INSTANCE.getScrollBy());
            isClickable =
                isClickable
                    || semanticsConfiguration.contains(SemanticsActions.INSTANCE.getOnClick());

            if (semanticsConfiguration.contains(SemanticsProperties.INSTANCE.getTestTag())) {
              final String newTestTag =
                  semanticsConfiguration.get(SemanticsProperties.INSTANCE.getTestTag());
              if (newTestTag != null) {
                testTag = newTestTag;
              }
            }
          }
        }

        if (isClickable && targetType == ViewUtils.TargetType.CLICKABLE) {
          targetTag = testTag;
        } else if (isScrollable && targetType == ViewUtils.TargetType.SCROLLABLE) {
          targetTag = testTag;
          // skip any children for scrollable targets
          break;
        }
      }
      queue.addAll(node.getChildren$ui_release());
    }

    return ViewUtils.UiElement.create(null, targetTag);
  }

  private static boolean nodeBoundsContains(
      @NotNull LayoutNode node, final float x, final float y) {
    final int nodeHeight = node.getHeight();
    final int nodeWidth = node.getWidth();

    // Offset is a Kotlin value class, packing x/y into a long
    // TODO find a way to use the existing APIs
    final long nodePosition = LayoutCoordinatesKt.positionInWindow(node.getCoordinates());
    final int nodeX = (int) Float.intBitsToFloat((int) (nodePosition >> 32));
    final int nodeY = (int) Float.intBitsToFloat((int) (nodePosition));

    return x >= nodeX && x <= (nodeX + nodeWidth) && y >= nodeY && y <= (nodeY + nodeHeight);
  }
}
