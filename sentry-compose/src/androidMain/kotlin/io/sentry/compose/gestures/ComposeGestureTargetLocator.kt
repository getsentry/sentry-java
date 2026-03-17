@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals

package io.sentry.compose.gestures

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.semantics.SemanticsModifier
import io.sentry.ILogger
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.compose.BuildConfig
import io.sentry.compose.SentryComposeHelper
import io.sentry.compose.boundsInWindow
import io.sentry.internal.gestures.GestureTargetLocator
import io.sentry.internal.gestures.UiElement
import io.sentry.util.AutoClosableReentrantLock
import java.util.LinkedList
import java.util.Queue

@OptIn(InternalComposeUiApi::class)
public class ComposeGestureTargetLocator(private val logger: ILogger) : GestureTargetLocator {
  @Volatile private var composeHelper: SentryComposeHelper? = null
  private val lock = AutoClosableReentrantLock()

  init {
    SentryIntegrationPackageStorage.getInstance()
      .addPackage("maven:io.sentry:sentry-compose", BuildConfig.VERSION_NAME)
  }

  override fun locate(root: Any?, x: Float, y: Float, targetType: UiElement.Type): UiElement? {
    if (root !is Owner) {
      return null
    }

    // lazy init composeHelper as it's using some reflection under the hood
    if (composeHelper == null) {
      lock.acquire().use {
        if (composeHelper == null) {
          composeHelper = SentryComposeHelper(logger)
        }
      }
    }

    val rootLayoutNode = root.root

    // Pair<Node, ParentTag>
    val queue: Queue<Pair<LayoutNode, String?>> = LinkedList()
    queue.add(Pair(rootLayoutNode, null))

    // the final tag to return, only relevant for clicks
    // as for scrolls, we return the first matching element
    var targetTag: String? = null

    while (!queue.isEmpty()) {
      val (node, parentTag) = queue.poll() ?: continue
      if (node.isPlaced && layoutNodeBoundsContain(rootLayoutNode, node, x, y)) {
        val tag = extractTag(composeHelper!!, node) ?: parentTag
        if (tag != null) {
          val modifiers = node.getModifierInfo()
          for (index in modifiers.indices) {
            val modifierInfo = modifiers[index]
            if (modifierInfo.modifier is SemanticsModifier) {
              val semanticsModifierCore = modifierInfo.modifier as SemanticsModifier
              val semanticsConfiguration = semanticsModifierCore.semanticsConfiguration

              for (item in semanticsConfiguration) {
                val key: String = item.key.name
                if (targetType == UiElement.Type.SCROLLABLE && "ScrollBy" == key) {
                  return UiElement(null, null, null, tag, ORIGIN)
                } else if (targetType == UiElement.Type.CLICKABLE && "OnClick" == key) {
                  targetTag = tag
                }
              }
            } else {
              // Jetpack Compose 1.5+: uses Node modifiers elements for clicks/scrolls
              val modifier = modifierInfo.modifier
              val type = modifier.javaClass.name
              if (
                targetType == UiElement.Type.CLICKABLE &&
                  ("androidx.compose.foundation.ClickableElement" == type ||
                    "androidx.compose.foundation.CombinedClickableElement" == type)
              ) {
                targetTag = tag
              } else if (
                targetType == UiElement.Type.SCROLLABLE &&
                  ("androidx.compose.foundation.ScrollingLayoutElement" == type ||
                    "androidx.compose.foundation.ScrollingContainerElement" == type)
              ) {
                return UiElement(null, null, null, tag, ORIGIN)
              }
            }
          }
        }
        queue.addAll(node.zSortedChildren.asMutableList().map { Pair(it, tag) })
      }
    }

    return if (targetTag == null) {
      null
    } else {
      UiElement(null, null, null, targetTag, ORIGIN)
    }
  }

  private fun layoutNodeBoundsContain(
    root: LayoutNode,
    node: LayoutNode,
    x: Float,
    y: Float,
  ): Boolean {
    val bounds = node.coordinates.boundsInWindow(root.coordinates)
    return bounds.contains(Offset(x, y))
  }

  private fun extractTag(composeHelper: SentryComposeHelper, node: LayoutNode): String? {
    val modifiers = node.getModifierInfo()
    for (index in modifiers.indices) {
      val modifierInfo = modifiers[index]
      composeHelper.extractTag(modifierInfo.modifier).also {
        return it
      }
    }
    return null
  }

  public companion object {
    private const val ORIGIN = "jetpack_compose"
  }
}
