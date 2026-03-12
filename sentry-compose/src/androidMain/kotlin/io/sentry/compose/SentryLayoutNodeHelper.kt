@file:Suppress(
  "INVISIBLE_MEMBER",
  "INVISIBLE_REFERENCE",
  "EXPOSED_PARAMETER_TYPE",
  "EXPOSED_RETURN_TYPE",
  "EXPOSED_FUNCTION_RETURN_TYPE",
)

package io.sentry.compose

import androidx.compose.ui.node.LayoutNode

/**
 * Provides access to internal LayoutNode members that are subject to Kotlin name-mangling.
 *
 * LayoutNode.children and LayoutNode.outerCoordinator are Kotlin `internal`, so their getters are
 * mangled with the module name: getChildren$ui_release() in Compose < 1.10 vs getChildren$ui() in
 * Compose >= 1.10. This class detects the version on first use and delegates to the correct
 * accessor.
 */
public object SentryLayoutNodeHelper {
  @Volatile private var compose110Helper: Compose110Helper? = null
  @Volatile private var useCompose110: Boolean? = null

  public fun getChildren(node: LayoutNode): List<LayoutNode> {
    return if (useCompose110 == true) {
      compose110Helper!!.getChildren(node)
    } else {
      val helper = Compose110Helper()
      try {
        helper.getChildren(node).also {
          compose110Helper = helper
          useCompose110 = true
        }
      } catch (_: NoSuchMethodError) {
        useCompose110 = false
        node.children
      }
    }
  }

  public fun isTransparent(node: LayoutNode): Boolean {
    return if (useCompose110 == true) {
      compose110Helper!!.getOuterCoordinator(node).isTransparent()
    } else {
      val helper = Compose110Helper()
      try {
        helper.getOuterCoordinator(node).isTransparent().also {
          compose110Helper = helper
          useCompose110 = true
        }
      } catch (_: NoSuchMethodError) {
        useCompose110 = false
        node.outerCoordinator.isTransparent()
      }
    }
  }
}
