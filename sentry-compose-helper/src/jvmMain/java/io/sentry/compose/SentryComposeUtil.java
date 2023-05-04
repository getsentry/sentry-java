package io.sentry.compose;

import androidx.compose.ui.layout.LayoutCoordinatesKt;
import androidx.compose.ui.node.LayoutNode;
import org.jetbrains.annotations.NotNull;

public class SentryComposeUtil {
  public static final int[] getLayoutNodeXY(@NotNull final LayoutNode node) {
    // Offset is a Kotlin value class, packing x/y into a long
    // TODO find a way to use the existing APIs
    final long nodePosition = LayoutCoordinatesKt.positionInWindow(node.getCoordinates());
    final int nodeX = (int) Float.intBitsToFloat((int) (nodePosition >> 32));
    final int nodeY = (int) Float.intBitsToFloat((int) (nodePosition));
    return new int[] {nodeX, nodeY};
  }
}
