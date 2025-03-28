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
    @Volatile
    private var composeHelper: SentryComposeHelper? = null
    private val lock = AutoClosableReentrantLock()

    init {
        SentryIntegrationPackageStorage.getInstance().addPackage("maven:io.sentry:sentry-compose", BuildConfig.VERSION_NAME)
    }

    override fun locate(
        root: Any?,
        x: Float,
        y: Float,
        targetType: UiElement.Type
    ): UiElement? {
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

        val queue: Queue<LayoutNode> = LinkedList()
        queue.add(rootLayoutNode)

        // the final tag to return
        var targetTag: String? = null

        // the last known tag when iterating the node tree
        var lastKnownTag: String? = null
        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue
            if (node.isPlaced && layoutNodeBoundsContain(
                    rootLayoutNode,
                    node,
                    x,
                    y
                )
            ) {
                var isClickable = false
                var isScrollable = false

                val modifiers = node.getModifierInfo()
                for (modifierInfo in modifiers) {
                    val tag = composeHelper!!.extractTag(modifierInfo.modifier)
                    if (tag != null) {
                        lastKnownTag = tag
                    }

                    if (modifierInfo.modifier is SemanticsModifier) {
                        val semanticsModifierCore =
                            modifierInfo.modifier as SemanticsModifier
                        val semanticsConfiguration =
                            semanticsModifierCore.semanticsConfiguration

                        for (item in semanticsConfiguration) {
                            val key: String = item.key.name
                            if ("ScrollBy" == key) {
                                isScrollable = true
                            } else if ("OnClick" == key) {
                                isClickable = true
                            }
                        }
                    } else {
                        val modifier = modifierInfo.modifier
                        // Newer Jetpack Compose 1.5 uses Node modifiers for clicks/scrolls
                        val type = modifier.javaClass.canonicalName
                        if ("androidx.compose.foundation.ClickableElement" == type ||
                            "androidx.compose.foundation.CombinedClickableElement" == type
                        ) {
                            isClickable = true
                        } else if ("androidx.compose.foundation.ScrollingLayoutElement" == type) {
                            isScrollable = true
                        }
                    }
                }

                if (isClickable && targetType == UiElement.Type.CLICKABLE) {
                    targetTag = lastKnownTag
                }
                if (isScrollable && targetType == UiElement.Type.SCROLLABLE) {
                    targetTag = lastKnownTag
                    // skip any children for scrollable targets
                    break
                }
            }
            queue.addAll(node.zSortedChildren.asMutableList())
        }

        return if (targetTag == null) {
            null
        } else {
            UiElement(
                null,
                null,
                null,
                targetTag,
                ORIGIN
            )
        }
    }

    private fun layoutNodeBoundsContain(
        root: LayoutNode,
        node: LayoutNode,
        x: Float,
        y: Float
    ): Boolean {
        val bounds = node.coordinates.boundsInWindow(root.coordinates)
        return bounds.contains(Offset(x, y))
    }

    public companion object {
        private const val ORIGIN = "jetpack_compose"
    }
}
