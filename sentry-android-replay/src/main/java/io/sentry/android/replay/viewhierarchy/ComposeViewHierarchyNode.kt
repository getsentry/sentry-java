@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals
package io.sentry.android.replay.viewhierarchy

import android.annotation.TargetApi
import android.graphics.Rect
import android.view.View
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import io.sentry.SentryOptions
import io.sentry.SentryReplayOptions
import io.sentry.android.replay.util.ComposeTextLayout
import io.sentry.android.replay.util.toOpaque
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.GenericViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.ImageViewHierarchyNode
import io.sentry.android.replay.viewhierarchy.ViewHierarchyNode.TextViewHierarchyNode

@TargetApi(26)
internal object ComposeViewHierarchyNode {

    /**
     * Since Compose doesn't have a concept of a View class (they are all composable functions),
     * we need to map the semantics node to a corresponding old view system class.
     */
     private fun SemanticsNode?.getProxyClassName(isImage: Boolean): String {
         return when {
             isImage -> SentryReplayOptions.IMAGE_VIEW_CLASS_NAME
             this != null && (unmergedConfig.contains(SemanticsProperties.Text) ||
                 unmergedConfig.contains(SemanticsActions.SetText)) -> SentryReplayOptions.TEXT_VIEW_CLASS_NAME
             else -> "android.view.View"
         }
     }

    private fun SemanticsNode?.shouldRedact(isImage: Boolean, options: SentryOptions): Boolean {
        val className = getProxyClassName(isImage)
        if (options.experimental.sessionReplay.ignoreViewClasses.contains(className)) {
            return false
        }

        return options.experimental.sessionReplay.redactViewClasses.contains(className)
    }

    private fun LayoutNode.findPainter(): Painter? {
        val modifierInfos = getModifierInfo()
        for (modifierInfo in modifierInfos) {
            val modifier = modifierInfo.modifier
            if (modifier::class.java.name.contains("Painter")) {
                return try {
                    modifier::class.java.getDeclaredField("painter")
                        .apply { isAccessible = true }
                        .get(modifier) as? Painter
                } catch (e: Throwable) {
                    null
                }
            }
        }
        return null
    }

    private fun Painter.isRedactable(): Boolean {
        val className = this::class.java.name
        return !className.contains("Vector") &&
            !className.contains("Color") &&
            !className.contains("Brush")
    }

    private fun androidx.compose.ui.geometry.Rect.toRect(): Rect {
        return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals
    private fun LayoutNode.traverse(parentNode: ViewHierarchyNode, semanticsNodes: Map<Int, SemanticsNode>, options: SentryOptions) {
        val children = this.children
        if (children.isEmpty()) {
            return
        }

        val childNodes = ArrayList<ViewHierarchyNode>(children.size)
        for (index in children.indices) {
            val child = children[index]
            val semanticsNode = semanticsNodes[child.semanticsId]
            val childNode = fromComposeNode(child, semanticsNode, parentNode, child.depth, options)
            if (childNode != null) {
                childNodes.add(childNode)
                child.traverse(childNode, semanticsNodes, options)
            }
        }
        parentNode.children = childNodes
    }

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals
    private fun fromComposeNode(
        node: LayoutNode,
        semanticsNode: SemanticsNode?,
        parent: ViewHierarchyNode?,
        distance: Int,
        options: SentryOptions
    ): ViewHierarchyNode? {
        val isInTree = node.isPlaced && node.isAttached
        if (!isInTree) {
            return null
        }
        val isVisible = semanticsNode == null || (!semanticsNode.isTransparent && !semanticsNode.unmergedConfig.contains(SemanticsProperties.InvisibleToUser))
        val painter: Painter? = node.findPainter()
        val shouldRedact = isVisible && semanticsNode.shouldRedact(painter != null, options)
        val isEditable = semanticsNode?.unmergedConfig?.contains(SemanticsActions.SetText) == true
        val positionInWindow = node.coordinates.positionInWindow()
        val boundsInWindow = node.coordinates.boundsInWindow()
        when {
            semanticsNode?.unmergedConfig?.contains(SemanticsProperties.Text) == true || isEditable -> {
                parent?.setImportantForCaptureToAncestors(true)
                val textLayoutResults = mutableListOf<TextLayoutResult>()
                semanticsNode?.unmergedConfig?.getOrNull(SemanticsActions.GetTextLayoutResult)
                    ?.action
                    ?.invoke(textLayoutResults)
                // TODO: support multiple text layouts
                // TODO: support editable text (currently there's no way to get @Composable's padding, and we can't reliably mask input fields based on TextLayout, so we mask the whole view instead)
                return TextViewHierarchyNode(
                    layout = if (textLayoutResults.isNotEmpty() && !isEditable) ComposeTextLayout(textLayoutResults.first()) else null,
                    dominantColor = textLayoutResults.firstOrNull()?.layoutInput?.style?.color?.toArgb()?.toOpaque(),
                    x = positionInWindow.x,
                    y = positionInWindow.y,
                    width = node.width,
                    height = node.height,
                    elevation = (parent?.elevation ?: 0f),
                    distance = distance,
                    parent = parent,
                    shouldRedact = shouldRedact,
                    isImportantForContentCapture = true,
                    isVisible = isVisible,
                    visibleRect = boundsInWindow.toRect()
                )
            }
            painter != null -> {
                parent?.setImportantForCaptureToAncestors(true)
                return ImageViewHierarchyNode(
                    x = positionInWindow.x,
                    y = positionInWindow.y,
                    width = node.width,
                    height = node.height,
                    elevation = (parent?.elevation ?: 0f),
                    distance = distance,
                    parent = parent,
                    isVisible = isVisible,
                    isImportantForContentCapture = true,
                    shouldRedact = shouldRedact && painter.isRedactable(),
                    visibleRect = boundsInWindow.toRect()
                )
            }
        }

        return GenericViewHierarchyNode(
            x = positionInWindow.x,
            y = positionInWindow.y,
            width = node.width,
            height = node.height,
            elevation = (parent?.elevation ?: 0f),
            distance = distance,
            parent = parent,
            shouldRedact = shouldRedact, // TODO: use custom modifier to mark views that should be redacted/ignored
            isImportantForContentCapture = false, /* will be set by children */
            isVisible = isVisible,
            visibleRect = boundsInWindow.toRect()
        )
    }

    fun fromView(view: View, parent: ViewHierarchyNode?, options: SentryOptions): Boolean {
        if (!view::class.java.name.contains("AndroidComposeView")) {
            return false
        }

        if (parent == null) {
            return false
        }

        val semanticsNodes = (view as? RootForTest)?.semanticsOwner?.getAllSemanticsNodesToMap(true) ?: return false
        val rootNode = (view as? Owner)?.root ?: return false
        rootNode.traverse(parent, semanticsNodes, options)
        return true
    }

    /**
     * Backport of https://github.com/androidx/androidx/blob/d0b13cd790006c94a2665474a91e465af4beb094/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/semantics/SemanticsOwner.kt#L81-L100
     * which got changed in newer versions
     */
    private fun SemanticsOwner.getAllSemanticsNodesToMap(
        useUnmergedTree: Boolean = false,
    ): Map<Int, SemanticsNode> {
        val nodes = mutableMapOf<Int, SemanticsNode>()

        fun findAllSemanticNodesRecursive(currentNode: SemanticsNode) {
            nodes[currentNode.id] = currentNode
            val children = currentNode.children
            for (index in children.indices) {
                val node = children[index]
                findAllSemanticNodesRecursive(node)
            }
        }

        val root = if (useUnmergedTree) unmergedRootSemanticsNode else rootSemanticsNode
        findAllSemanticNodesRecursive(root)
        return nodes
    }
}
