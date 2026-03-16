@file:Suppress(
  "INVISIBLE_MEMBER",
  "INVISIBLE_REFERENCE",
  "EXPOSED_PARAMETER_TYPE",
  "EXPOSED_RETURN_TYPE",
  "EXPOSED_FUNCTION_RETURN_TYPE",
)

package io.sentry.android.replay.viewhierarchy

import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.NodeCoordinator

/**
 * Compiled against Compose >= 1.10 where internal LayoutNode accessors are mangled with the module
 * name "ui" (e.g. getChildren$ui(), getOuterCoordinator$ui()) instead of "ui_release" used in
 * earlier versions.
 */
public class Compose110Helper {
  public fun getChildren(node: LayoutNode): List<LayoutNode> = node.children

  public fun getOuterCoordinator(node: LayoutNode): NodeCoordinator = node.outerCoordinator
}
