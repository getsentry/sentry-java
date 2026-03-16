@file:Suppress(
  "INVISIBLE_MEMBER",
  "INVISIBLE_REFERENCE",
  "EXPOSED_PARAMETER_TYPE",
  "EXPOSED_RETURN_TYPE",
  "EXPOSED_FUNCTION_RETURN_TYPE",
)

package io.sentry.android.replay.viewhierarchy

import androidx.compose.ui.node.LayoutNode

/**
 * Provides access to internal LayoutNode members that are subject to Kotlin name-mangling.
 *
 * LayoutNode.children and LayoutNode.outerCoordinator are Kotlin `internal`, so their getters are
 * mangled with the module name: getChildren$ui_release() in Compose < 1.10 vs getChildren$ui() in
 * Compose >= 1.10. This class detects the version on first use and delegates to the correct
 * accessor.
 */
internal object SentryLayoutNodeHelper {
  @Volatile private var compose110Helper: Compose110Helper? = null
  @Volatile private var useCompose110: Boolean? = null

  private fun getHelper(): Compose110Helper {
    compose110Helper?.let {
      return it
    }
    val helper = Compose110Helper()
    compose110Helper = helper
    return helper
  }

  fun getChildren(node: LayoutNode): List<LayoutNode> {
    return if (useCompose110 == false) {
      node.children
    } else {
      try {
        getHelper().getChildren(node).also { useCompose110 = true }
      } catch (_: NoSuchMethodError) {
        useCompose110 = false
        node.children
      }
    }
  }

  fun isTransparent(node: LayoutNode): Boolean {
    return if (useCompose110 == false) {
      node.outerCoordinator.isTransparent()
    } else {
      try {
        getHelper().getOuterCoordinator(node).isTransparent().also { useCompose110 = true }
      } catch (_: NoSuchMethodError) {
        useCompose110 = false
        node.outerCoordinator.isTransparent()
      }
    }
  }
}
