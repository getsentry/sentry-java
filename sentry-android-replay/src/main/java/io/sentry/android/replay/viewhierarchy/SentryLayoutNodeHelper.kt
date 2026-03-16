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
import java.lang.reflect.Method

/**
 * Provides access to internal LayoutNode members that are subject to Kotlin name-mangling.
 *
 * This class is not thread-safe, as Compose UI operations are expected to be performed on the main
 * thread.
 *
 * Compiled against Compose >= 1.10 where the mangled names use the "ui" module suffix (e.g.
 * getChildren$ui()). For apps still on Compose < 1.10 (where the suffix is "$ui_release"), the
 * direct call will throw [NoSuchMethodError] and we fall back to reflection-based accessors that
 * are resolved and cached on first use.
 */
internal object SentryLayoutNodeHelper {
  private class Fallback(val getChildren: Method?, val getOuterCoordinator: Method?)

  private var useFallback: Boolean? = null
  private var fallback: Fallback? = null

  private fun tryResolve(clazz: Class<*>, name: String): Method? {
    return try {
      clazz.getDeclaredMethod(name).apply { isAccessible = true }
    } catch (_: NoSuchMethodException) {
      null
    }
  }

  @Suppress("UNCHECKED_CAST")
  fun getChildren(node: LayoutNode): List<LayoutNode> {
    when (useFallback) {
      false -> return node.children
      true -> {
        return getFallback().getChildren!!.invoke(node) as List<LayoutNode>
      }
      null -> {
        try {
          return node.children.also { useFallback = false }
        } catch (_: NoSuchMethodError) {
          useFallback = true
          return getFallback().getChildren!!.invoke(node) as List<LayoutNode>
        }
      }
    }
  }

  fun isTransparent(node: LayoutNode): Boolean {
    when (useFallback) {
      false -> return node.outerCoordinator.isTransparent()
      true -> {
        val fb = getFallback()
        val coordinator = fb.getOuterCoordinator!!.invoke(node) as NodeCoordinator
        return coordinator.isTransparent()
      }
      null -> {
        try {
          return node.outerCoordinator.isTransparent().also { useFallback = false }
        } catch (_: NoSuchMethodError) {
          useFallback = true
          val fb = getFallback()
          val coordinator = fb.getOuterCoordinator!!.invoke(node) as NodeCoordinator
          return coordinator.isTransparent()
        }
      }
    }
  }

  private fun getFallback(): Fallback {
    fallback?.let {
      return it
    }

    val layoutNodeClass = LayoutNode::class.java
    val getChildren = tryResolve(layoutNodeClass, "getChildren\$ui_release")
    val getOuterCoordinator = tryResolve(layoutNodeClass, "getOuterCoordinator\$ui_release")

    return Fallback(getChildren, getOuterCoordinator).also { fallback = it }
  }
}
