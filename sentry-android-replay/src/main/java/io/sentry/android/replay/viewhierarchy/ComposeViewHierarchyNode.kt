@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals

package io.sentry.android.replay.viewhierarchy

import android.annotation.TargetApi
import android.view.View
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.SentryReplayOptions
import io.sentry.android.replay.SentryReplayModifiers
import io.sentry.android.replay.util.ComposeTextLayout
import io.sentry.android.replay.util.boundsInWindow
import io.sentry.android.replay.util.findPainter
import io.sentry.android.replay.util.findTextAttributes
import io.sentry.android.replay.util.isMaskable
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
    private fun LayoutNode.getProxyClassName(isImage: Boolean): String {
        return when {
            isImage -> SentryReplayOptions.IMAGE_VIEW_CLASS_NAME
            collapsedSemantics?.contains(SemanticsProperties.Text) == true ||
                collapsedSemantics?.contains(SemanticsActions.SetText) == true -> SentryReplayOptions.TEXT_VIEW_CLASS_NAME
            else -> "android.view.View"
        }
    }

    private fun LayoutNode.shouldMask(isImage: Boolean, options: SentryOptions): Boolean {
        val sentryPrivacyModifier = collapsedSemantics?.getOrNull(SentryReplayModifiers.SentryPrivacy)
        if (sentryPrivacyModifier == "unmask") {
            return false
        }

        if (sentryPrivacyModifier == "mask") {
            return true
        }

        val className = getProxyClassName(isImage)
        if (options.experimental.sessionReplay.unmaskViewClasses.contains(className)) {
            return false
        }

        return options.experimental.sessionReplay.maskViewClasses.contains(className)
    }

    private var _rootCoordinates: LayoutCoordinates? = null

    private fun fromComposeNode(
        node: LayoutNode,
        parent: ViewHierarchyNode?,
        distance: Int,
        isComposeRoot: Boolean,
        options: SentryOptions
    ): ViewHierarchyNode? {
        val isInTree = node.isPlaced && node.isAttached
        if (!isInTree) {
            return null
        }

        if (isComposeRoot) {
            _rootCoordinates = node.coordinates.findRootCoordinates()
        }

        val semantics = node.collapsedSemantics
        val visibleRect = node.coordinates.boundsInWindow(_rootCoordinates)
        val isVisible = !node.outerCoordinator.isTransparent() &&
            (semantics == null || !semantics.contains(SemanticsProperties.InvisibleToUser)) &&
            visibleRect.height() > 0 && visibleRect.width() > 0
        val isEditable = semantics?.contains(SemanticsActions.SetText) == true
        val positionInWindow = node.coordinates.positionInWindow()
        return when {
            semantics?.contains(SemanticsProperties.Text) == true || isEditable -> {
                val shouldMask = isVisible && node.shouldMask(isImage = false, options)

                parent?.setImportantForCaptureToAncestors(true)
                val textLayoutResults = mutableListOf<TextLayoutResult>()
                semantics?.getOrNull(SemanticsActions.GetTextLayoutResult)
                    ?.action
                    ?.invoke(textLayoutResults)

                val (color, hasFillModifier) = node.findTextAttributes()
                var textColor = textLayoutResults.firstOrNull()?.layoutInput?.style?.color
                if (textColor?.isUnspecified == true) {
                    textColor = color
                }
                // TODO: support multiple text layouts
                // TODO: support editable text (currently there's a way to get @Composable's padding only via reflection, and we can't reliably mask input fields based on TextLayout, so we mask the whole view instead)
                TextViewHierarchyNode(
                    layout = if (textLayoutResults.isNotEmpty() && !isEditable) ComposeTextLayout(textLayoutResults.first(), hasFillModifier) else null,
                    dominantColor = textColor?.toArgb()?.toOpaque(),
                    x = positionInWindow.x,
                    y = positionInWindow.y,
                    width = node.width,
                    height = node.height,
                    elevation = (parent?.elevation ?: 0f),
                    distance = distance,
                    parent = parent,
                    shouldMask = shouldMask,
                    isImportantForContentCapture = true,
                    isVisible = isVisible,
                    visibleRect = visibleRect
                )
            }
            else -> {
                val painter = node.findPainter()
                if (painter != null) {
                    val shouldMask = isVisible && node.shouldMask(isImage = true, options)

                    parent?.setImportantForCaptureToAncestors(true)
                    ImageViewHierarchyNode(
                        x = positionInWindow.x,
                        y = positionInWindow.y,
                        width = node.width,
                        height = node.height,
                        elevation = (parent?.elevation ?: 0f),
                        distance = distance,
                        parent = parent,
                        isVisible = isVisible,
                        isImportantForContentCapture = true,
                        shouldMask = shouldMask && painter.isMaskable(),
                        visibleRect = visibleRect
                    )
                } else {
                    val shouldMask = isVisible && node.shouldMask(isImage = false, options)

                    // TODO: this currently does not support embedded AndroidViews, we'd have to
                    // TODO: traverse the ViewHierarchyNode here again. For now we can recommend
                    // TODO: using custom modifiers to obscure the entire node if it's sensitive
                    GenericViewHierarchyNode(
                        x = positionInWindow.x,
                        y = positionInWindow.y,
                        width = node.width,
                        height = node.height,
                        elevation = (parent?.elevation ?: 0f),
                        distance = distance,
                        parent = parent,
                        shouldMask = shouldMask,
                        isImportantForContentCapture = false, /* will be set by children */
                        isVisible = isVisible,
                        visibleRect = visibleRect
                    )
                }
            }
        }
    }

    fun fromView(view: View, parent: ViewHierarchyNode?, options: SentryOptions): Boolean {
        if (!view::class.java.name.contains("AndroidComposeView")) {
            return false
        }

        if (parent == null) {
            return false
        }

        try {
            val rootNode = (view as? Owner)?.root ?: return false
            rootNode.traverse(parent, isComposeRoot = true, options)
        } catch (e: Throwable) {
            options.logger.log(
                SentryLevel.ERROR,
                e,
                """
                Error traversing Compose tree. Most likely you're using an unsupported version of
                androidx.compose.ui:ui. The minimum supported version is 1.5.0. If it's a newer
                version, please open a github issue with the version you're using, so we can add
                support for it.
                """.trimIndent()
            )
            return false
        }

        return true
    }

    private fun LayoutNode.traverse(parentNode: ViewHierarchyNode, isComposeRoot: Boolean, options: SentryOptions) {
        val children = this.children
        if (children.isEmpty()) {
            return
        }

        val childNodes = ArrayList<ViewHierarchyNode>(children.size)
        for (index in children.indices) {
            val child = children[index]
            val childNode = fromComposeNode(child, parentNode, index, isComposeRoot, options)
            if (childNode != null) {
                childNodes.add(childNode)
                child.traverse(childNode, isComposeRoot = false, options)
            }
        }
        parentNode.children = childNodes
    }
}
