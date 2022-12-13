package io.sentry.compose.gestures;

import androidx.compose.ui.layout.LayoutCoordinatesKt;
import androidx.compose.ui.layout.ModifierInfo;
import androidx.compose.ui.node.LayoutNode;
import androidx.compose.ui.node.Owner;
import androidx.compose.ui.semantics.SemanticsConfiguration;
import androidx.compose.ui.semantics.SemanticsModifier;
import androidx.compose.ui.semantics.SemanticsPropertyKey;
import io.sentry.internal.gestures.GestureTargetLocator;
import io.sentry.internal.gestures.UiElement;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("KotlinInternalInJava")
public final class ComposeGestureTargetLocator implements GestureTargetLocator {

  @Override
  public @Nullable UiElement locate(
      @NotNull Object root, float x, float y, UiElement.Type targetType) {
    @Nullable String targetTag = null;

    if (!(root instanceof Owner)) {
      return null;
    }

    final @NotNull Queue<LayoutNode> queue = new LinkedList<>();
    queue.add(((Owner) root).getRoot());

    while (!queue.isEmpty()) {
      final @Nullable LayoutNode node = queue.poll();
      if (node == null) {
        continue;
      }

      if (node.isPlaced() && layoutNodeBoundsContain(node, x, y)) {
        boolean isClickable = false;
        boolean isScrollable = false;
        @Nullable String testTag = null;

        final List<ModifierInfo> modifiers = node.getModifierInfo();
        for (ModifierInfo modifierInfo : modifiers) {
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
              } else if ("TestTag".equals(key)) {
                if (entry.getValue() instanceof String) {
                  testTag = (String) entry.getValue();
                }
              }
            }
          }
        }

        if (isClickable && targetType == UiElement.Type.CLICKABLE) {
          targetTag = testTag;
        }
        if (isScrollable && targetType == UiElement.Type.SCROLLABLE) {
          targetTag = testTag;
          // skip any children for scrollable targets
          break;
        }
      }
      queue.addAll(node.getZSortedChildren().asMutableList());
    }

    if (targetTag == null) {
      return null;
    } else {
      return new UiElement(null, null, null, targetTag);
    }
  }

  private static boolean layoutNodeBoundsContain(
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
