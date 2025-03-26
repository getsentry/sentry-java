@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals

package io.sentry.compose.viewhierarchy

import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import io.sentry.ILogger
import io.sentry.compose.SentryComposeHelper
import io.sentry.internal.viewhierarchy.ViewHierarchyExporter
import io.sentry.protocol.ViewHierarchyNode
import io.sentry.util.AutoClosableReentrantLock

public class ComposeViewHierarchyExporter public constructor(private val logger: ILogger) :
    ViewHierarchyExporter {
    @Volatile
    private var composeHelper: SentryComposeHelper? = null
    private val lock = AutoClosableReentrantLock()

    override fun export(parent: ViewHierarchyNode, element: Any): Boolean {
        if (element !is Owner) {
            return false
        }

        // lazy init composeHelper as it's using some reflection under the hood
        if (composeHelper == null) {
            lock.acquire().use {
                if (composeHelper == null) {
                    composeHelper = SentryComposeHelper(logger)
                }
            }
        }

        val rootNode = element.root
        addChild(composeHelper!!, parent, null, rootNode)
        return true
    }

    public companion object {
        private fun addChild(
            composeHelper: SentryComposeHelper,
            parent: ViewHierarchyNode,
            parentNode: LayoutNode?,
            node: LayoutNode
        ) {
            if (node.isPlaced) {
                val vhNode = ViewHierarchyNode()
                setTag(composeHelper, node, vhNode)
                setBounds(composeHelper, node, parentNode, vhNode)

                if (vhNode.tag != null) {
                    vhNode.type = vhNode.tag
                } else {
                    vhNode.type = "@Composable"
                }

                if (parent.children == null) {
                    parent.children = ArrayList()
                }
                parent.children!!.add(vhNode)

                val children = node.zSortedChildren
                val childrenCount = children.size
                for (i in 0 until childrenCount) {
                    val child = children[i]
                    addChild(composeHelper, vhNode, node, child)
                }
            }
        }

        private fun setTag(
            helper: SentryComposeHelper,
            node: LayoutNode,
            vhNode: ViewHierarchyNode
        ) {
            // needs to be in-sync with ComposeGestureTargetLocator
            val modifiers = node.getModifierInfo()
            for (modifierInfo in modifiers) {
                val tag = helper.extractTag(modifierInfo.modifier)
                if (tag != null) {
                    vhNode.tag = tag
                }
            }
        }

        private fun setBounds(
            composeHelper: SentryComposeHelper,
            node: LayoutNode,
            parentNode: LayoutNode?,
            vhNode: ViewHierarchyNode
        ) {
            val nodeHeight = node.height
            val nodeWidth = node.width

            vhNode.height = nodeHeight.toDouble()
            vhNode.width = nodeWidth.toDouble()

            val bounds = composeHelper.getLayoutNodeBoundsInWindow(node)
            if (bounds != null) {
                var x = bounds.left.toDouble()
                var y = bounds.top.toDouble()
                // layout coordinates for view hierarchy are relative to the parent node
                parentNode?.let {
                    val parentBounds = composeHelper.getLayoutNodeBoundsInWindow(it)
                    if (parentBounds != null) {
                        x -= parentBounds.left.toDouble()
                        y -= parentBounds.top.toDouble()
                    }
                }
                vhNode.x = x
                vhNode.y = y
            }
        }
    }
}
