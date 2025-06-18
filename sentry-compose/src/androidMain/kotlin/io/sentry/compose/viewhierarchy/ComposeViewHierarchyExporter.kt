@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals

package io.sentry.compose.viewhierarchy

import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import io.sentry.ILogger
import io.sentry.compose.SentryComposeHelper
import io.sentry.internal.viewhierarchy.ViewHierarchyExporter
import io.sentry.protocol.ViewHierarchyNode
import io.sentry.util.AutoClosableReentrantLock

public class ComposeViewHierarchyExporter public constructor(
    private val logger: ILogger,
) : ViewHierarchyExporter {
    @Volatile
    private var composeHelper: SentryComposeHelper? = null
    private val lock = AutoClosableReentrantLock()

    override fun export(
        parent: ViewHierarchyNode,
        element: Any,
    ): Boolean {
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
        addChild(composeHelper!!, parent, rootNode, rootNode)
        return true
    }

    private fun addChild(
        composeHelper: SentryComposeHelper,
        parent: ViewHierarchyNode,
        rootNode: LayoutNode,
        node: LayoutNode,
    ) {
        if (node.isPlaced) {
            val vhNode = ViewHierarchyNode()
            setTag(composeHelper, node, vhNode)
            setBounds(node, vhNode)
            vhNode.type = vhNode.tag ?: "@Composable"

            if (parent.children == null) {
                parent.children = ArrayList()
            }
            parent.children!!.add(vhNode)

            val children = node.zSortedChildren
            val childrenCount = children.size
            for (i in 0 until childrenCount) {
                val child = children[i]
                addChild(composeHelper, vhNode, rootNode, child)
            }
        }
    }

    private fun setTag(
        helper: SentryComposeHelper,
        node: LayoutNode,
        vhNode: ViewHierarchyNode,
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
        node: LayoutNode,
        vhNode: ViewHierarchyNode,
    ) {
        // layout coordinates for view hierarchy are relative to the parent node
        val bounds = node.coordinates.boundsInParent()

        vhNode.x = bounds.left.toDouble()
        vhNode.y = bounds.top.toDouble()
        vhNode.height = bounds.height.toDouble()
        vhNode.width = bounds.width.toDouble()
    }
}
