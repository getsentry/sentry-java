package io.sentry.android.replay.viewhierarchy

import android.annotation.TargetApi
import android.graphics.Rect
import android.text.Layout
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import io.sentry.SentryOptions
import io.sentry.android.replay.util.isRedactable
import io.sentry.android.replay.util.isVisibleToUser
import io.sentry.android.replay.util.totalPaddingTopSafe

@TargetApi(26)
sealed class ViewHierarchyNode(
    val x: Float,
    val y: Float,
    val width: Int,
    val height: Int,
    /* Elevation (in px) */
    val elevation: Float,
    /* Distance to the parent (index) */
    val distance: Int,
    val parent: ViewHierarchyNode? = null,
    val shouldRedact: Boolean = false,
    /* Whether the node is important for content capture (=non-empty container) */
    var isImportantForContentCapture: Boolean = false,
    val isVisible: Boolean = false,
    val visibleRect: Rect? = null
) {
    var children: List<ViewHierarchyNode>? = null

    class GenericViewHierarchyNode(
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        elevation: Float,
        distance: Int,
        parent: ViewHierarchyNode? = null,
        shouldRedact: Boolean = false,
        isImportantForContentCapture: Boolean = false,
        isVisible: Boolean = false,
        visibleRect: Rect? = null
    ) : ViewHierarchyNode(x, y, width, height, elevation, distance, parent, shouldRedact, isImportantForContentCapture, isVisible, visibleRect)

    class TextViewHierarchyNode(
        val layout: Layout? = null,
        val dominantColor: Int? = null,
        val paddingLeft: Int = 0,
        val paddingTop: Int = 0,
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        elevation: Float,
        distance: Int,
        parent: ViewHierarchyNode? = null,
        shouldRedact: Boolean = false,
        isImportantForContentCapture: Boolean = false,
        isVisible: Boolean = false,
        visibleRect: Rect? = null
    ) : ViewHierarchyNode(x, y, width, height, elevation, distance, parent, shouldRedact, isImportantForContentCapture, isVisible, visibleRect)

    class ImageViewHierarchyNode(
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        elevation: Float,
        distance: Int,
        parent: ViewHierarchyNode? = null,
        shouldRedact: Boolean = false,
        isImportantForContentCapture: Boolean = false,
        isVisible: Boolean = false,
        visibleRect: Rect? = null
    ) : ViewHierarchyNode(x, y, width, height, elevation, distance, parent, shouldRedact, isImportantForContentCapture, isVisible, visibleRect)

    /**
     * Traverses the view hierarchy starting from this node. The traversal is done in a depth-first
     * manner.
     *
     * @param callback a callback that will be called for each node in the hierarchy. If the callback
     * returns false, the traversal will stop for the current node and its children.
     */
    fun traverse(callback: (ViewHierarchyNode) -> Boolean) {
        val traverseChildren = callback(this)
        if (traverseChildren) {
            if (this.children != null) {
                this.children!!.forEach {
                    it.traverse(callback)
                }
            }
        }
    }

    /**
     * Checks if the given node is obscured by other nodes in the view hierarchy. A node is considered
     * obscured if it's not visible, or if it's not fully visible because it's behind another node
     * with a higher elevation or distance from the common parent.
     *
     * This method should be called on the root node of the view hierarchy.
     *
     * @param node the node to check if it's obscured by other nodes in the view hierarchy
     */
    fun isObscured(node: ViewHierarchyNode): Boolean {
        require(this.parent == null) {
            "This method should be called on the root node of the view hierarchy."
        }
        node.visibleRect ?: return false

        var isObscured = false

        traverse { otherNode ->
            // if the other node doesn't have a visible rect or the current node is already obscured
            // we can skip the traversal
            if (otherNode.visibleRect == null || isObscured) {
                return@traverse false
            }

            // if the other node is not visible, or not important for content capture (empty container)
            // or doesn't contain the node's visible rect, we can skip it
            if (!otherNode.isVisible ||
                !otherNode.isImportantForContentCapture ||
                !otherNode.visibleRect.contains(node.visibleRect)
            ) {
                return@traverse false
            }

            // if otherNode's elevation is higher, we know it's obscuring the node
            if (otherNode.elevation > node.elevation) {
                isObscured = true
                return@traverse false
            } else if (otherNode.elevation == node.elevation) {
                // if otherNode's elevation is the same, we need to find the lowest common ancestor
                // and compare the distances from the common parent
                val (lca, nodeAncestor, otherNodeAncestor) = findLCA(node, otherNode)
                // if otherNode is the LCA, this means it's a parent of the node, so it's not obscuring it
                // otherwise compare the distances from the common parent
                if (lca != otherNode && otherNodeAncestor != null && nodeAncestor != null) {
                    isObscured = otherNodeAncestor.distance > nodeAncestor.distance
                    return@traverse !isObscured
                }
            }
            return@traverse true
        }
        return isObscured
    }

    /**
     * Find the lowest common ancestor of two nodes in the view hierarchy. Given the following view
     * hierarchy:
     *
     * CoordinatorLayout
     *  -FrameLayout
     *  --TextView
     *  -BottomNavigationView
     *  --NavigationItemView
     *  --NavigationItemView
     *
     * We want to know if the TextView is obscured by anything. For that we're searching for the
     * lowest common ancestor (common parent) of the TextView and the other node. In this case it'd
     * be CoordinatorLayout.
     *
     * After that we also need to know which subtrees contain both the TextView
     * and the obscuring node. In this case it'd be FrameLayout and BottomNavigationView. Once we
     * have the subtrees, we can compare their distances (indexes) from the common parent. In this
     * case BottomNavigationView will have a higher index than FrameLayout, so we can conclude that
     * it obscures the TextView.
     *
     * This method should be called on the root node of the view hierarchy.
     */
    private fun findLCA(node: ViewHierarchyNode, otherNode: ViewHierarchyNode): LCAResult {
        var nodeSubtree: ViewHierarchyNode? = null
        var otherNodeSubtree: ViewHierarchyNode? = null
        var lca: ViewHierarchyNode? = null

        // Check if the current node is node or otherNode
        if (this == node) {
            nodeSubtree = this
        }
        if (this == otherNode) {
            otherNodeSubtree = this
        }

        // Search for nodes node and otherNode in the children subtrees
        if (children != null) {
            for (child in children!!) {
                val result = child.findLCA(node, otherNode)

                if (result.lca != null) {
                    return result // If LCA is found, propagate it up
                }
                if (result.nodeSubtree != null) {
                    nodeSubtree = child
                }
                if (result.otherNodeSubtree != null) {
                    otherNodeSubtree = child
                }
            }
        }

        // If both node and otherNode are found, and LCA is not already determined, the current node
        // is the LCA
        if (nodeSubtree != null && otherNodeSubtree != null) {
            lca = this
        }

        return LCAResult(lca, nodeSubtree, otherNodeSubtree)
    }

    private data class LCAResult(
        val lca: ViewHierarchyNode?,
        var nodeSubtree: ViewHierarchyNode?,
        var otherNodeSubtree: ViewHierarchyNode?
    )

    companion object {

        private fun Int.toOpaque() = this or 0xFF000000.toInt()

        /**
         * Basically replicating this: https://developer.android.com/reference/android/view/View#isImportantForContentCapture()
         * but for lower APIs and with less overhead. If we take a look at how it's set in Android:
         * https://cs.android.com/search?q=IMPORTANT_FOR_CONTENT_CAPTURE_YES&ss=android%2Fplatform%2Fsuperproject%2Fmain
         * we see that they just set it as important for views containing TextViews, ImageViews and WebViews.
         */
        private fun ViewHierarchyNode?.setImportantForCaptureToAncestors(isImportant: Boolean) {
            var parent = this?.parent
            while (parent != null) {
                parent.isImportantForContentCapture = isImportant
                parent = parent.parent
            }
        }

        private fun shouldRedact(view: View, options: SentryOptions): Boolean {
            return options.experimental.sessionReplay.redactClasses.contains(view.javaClass.canonicalName)
        }

        fun fromView(view: View, parent: ViewHierarchyNode?, distance: Int, options: SentryOptions): ViewHierarchyNode {
            val (isVisible, visibleRect) = view.isVisibleToUser()
            when {
                view is TextView && options.experimental.sessionReplay.redactAllText -> {
                    parent.setImportantForCaptureToAncestors(true)
                    return TextViewHierarchyNode(
                        layout = view.layout,
                        dominantColor = view.currentTextColor.toOpaque(),
                        paddingLeft = view.totalPaddingLeft,
                        paddingTop = view.totalPaddingTopSafe,
                        x = view.x,
                        y = view.y,
                        width = view.width,
                        height = view.height,
                        elevation = (parent?.elevation ?: 0f) + view.elevation,
                        shouldRedact = isVisible,
                        distance = distance,
                        parent = parent,
                        isImportantForContentCapture = true,
                        isVisible = isVisible,
                        visibleRect = visibleRect
                    )
                }

                view is ImageView && options.experimental.sessionReplay.redactAllImages -> {
                    parent.setImportantForCaptureToAncestors(true)
                    return ImageViewHierarchyNode(
                        x = view.x,
                        y = view.y,
                        width = view.width,
                        height = view.height,
                        elevation = (parent?.elevation ?: 0f) + view.elevation,
                        distance = distance,
                        parent = parent,
                        isVisible = isVisible,
                        isImportantForContentCapture = true,
                        shouldRedact = isVisible && view.drawable?.isRedactable() == true,
                        visibleRect = visibleRect
                    )
                }
            }

            return GenericViewHierarchyNode(
                view.x,
                view.y,
                view.width,
                view.height,
                (parent?.elevation ?: 0f) + view.elevation,
                distance = distance,
                parent = parent,
                shouldRedact = isVisible && shouldRedact(view, options),
                isImportantForContentCapture = false, /* will be set by children */
                isVisible = isVisible,
                visibleRect = visibleRect
            )
        }
    }
}
