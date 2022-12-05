package io.sentry.compose.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsProperties.TestTag
import androidx.compose.ui.semantics.getOrNull
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.internal.gestures.GestureTargetLocator
import io.sentry.internal.gestures.UiElement
import java.lang.reflect.Method
import java.util.LinkedList
import java.util.Queue

public class ComposeGestureTargetLocator(private val options: SentryOptions) :
    GestureTargetLocator {

    private val composeLayoutNodeApiWrapper: ComposeLayoutNodeApiWrapper? by lazy {
        try {
            ComposeLayoutNodeApiWrapper(options)
        } catch (t: Throwable) {
            options.logger.log(
                SentryLevel.WARNING,
                "Could not init Compose LayoutNode API wrapper",
                t
            )
            null
        }
    }

    override fun locate(
        root: Any,
        x: Float,
        y: Float,
        targetType: UiElement.Type
    ): UiElement? {
        var targetTag: String? = null

        composeLayoutNodeApiWrapper?.let { wrapper ->
            if (!wrapper.isLayoutNodeOwner(root)) {
                return null
            }

            val rootLayoutNode = wrapper.ownerClassGetRoot(root)
            val queue: Queue<Any?> = LinkedList()
            queue.add(rootLayoutNode)

            while (!queue.isEmpty()) {
                val node = queue.poll() ?: continue

                if (wrapper.layoutNodeIsPlaced(node) && wrapper.layoutNodeBoundsContain(
                        node,
                        x,
                        y
                    )
                ) {
                    var isClickable = false
                    var isScrollable = false
                    var testTag: String? = null
                    val modifiers = wrapper.layoutNodeGetModifierInfo(node)

                    for (modifier in modifiers) {
                        if (modifier.modifier is SemanticsModifier) {
                            val semanticsModifierCore = modifier.modifier as SemanticsModifier
                            val semanticsConfiguration =
                                semanticsModifierCore.semanticsConfiguration
                            isScrollable = isScrollable ||
                                semanticsConfiguration.any {
                                    it.key.name == "ScrollBy"
                                }

                            isClickable = isClickable ||
                                semanticsConfiguration.any {
                                    it.key.name == "OnClick"
                                }

                            if (semanticsConfiguration.contains(TestTag)) {
                                val newTestTag = semanticsConfiguration.getOrNull(TestTag)
                                if (newTestTag != null) {
                                    testTag = newTestTag
                                }
                            }
                        }
                    }
                    if (isClickable && targetType == UiElement.Type.CLICKABLE) {
                        targetTag = testTag
                    } else if (isScrollable && targetType == UiElement.Type.SCROLLABLE) {
                        targetTag = testTag
                        // skip any children for scrollable targets
                        break
                    }
                }
                queue.addAll(wrapper.children(node))
            }
        }

        return if (targetTag == null) {
            null
        } else {
            UiElement(null, null, null, targetTag)
        }
    }
}

private class ComposeLayoutNodeApiWrapper(private val options: SentryOptions) {
    val ownerClass: Class<*> = Class.forName("androidx.compose.ui.node.Owner")
    val layoutNodeClass: Class<*> = Class.forName("androidx.compose.ui.node.LayoutNode")

    val ownerClassGetRoot: Method = ownerClass.getMethod("getRoot")
    val layoutNodeIsPlaced: Method = layoutNodeClass.getMethod("isPlaced")
    val layoutNodeGetChildren: Method = layoutNodeClass.getMethod("getChildren\$ui_release")
    val layoutNodeGetModifierInfo: Method = layoutNodeClass.getMethod("getModifierInfo")
    val layoutNodeGetWidth: Method = layoutNodeClass.getMethod("getWidth")
    val layoutNodeGetHeight: Method = layoutNodeClass.getMethod("getHeight")
    val layoutNodeGetCoordinates: Method = layoutNodeClass.getMethod("getCoordinates")

    fun isLayoutNodeOwner(obj: Any): Boolean {
        return try {
            return ownerClass.isAssignableFrom(obj.javaClass)
        } catch (ex: Throwable) {
            options.logger.log(SentryLevel.WARNING, "androidx.compose.ui.node.Owner failed", ex)
            false
        }
    }

    fun layoutNodeIsPlaced(obj: Any): Boolean {
        return try {
            return layoutNodeIsPlaced.invoke(obj) as Boolean
        } catch (ex: Throwable) {
            options.logger.log(SentryLevel.WARNING, "LayoutNode.getIsPlaced failed", ex)
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun layoutNodeGetModifierInfo(obj: Any): List<ModifierInfo> {
        return try {
            return layoutNodeGetModifierInfo.invoke(obj) as List<ModifierInfo>
        } catch (ex: Throwable) {
            options.logger.log(SentryLevel.WARNING, "LayoutNode.getModifierInfo failed", ex)
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun children(obj: Any): List<Any> {
        return try {
            return layoutNodeGetChildren.invoke(obj) as List<Any>
        } catch (ex: Throwable) {
            options.logger.log(SentryLevel.WARNING, "LayoutNode.children failed", ex)
            emptyList()
        }
    }

    fun width(obj: Any): Int {
        return try {
            layoutNodeGetWidth.invoke(obj) as Int
        } catch (ex: Throwable) {
            options.logger.log(SentryLevel.WARNING, "LayoutNode.width failed", ex)
            0
        }
    }

    fun height(obj: Any): Int {
        return try {
            return layoutNodeGetHeight.invoke(obj) as Int
        } catch (ex: Throwable) {
            options.logger.log(SentryLevel.WARNING, "LayoutNode.height failed", ex)
            0
        }
    }

    fun coordinates(obj: Any): LayoutCoordinates? {
        return try {
            return layoutNodeGetCoordinates.invoke(obj) as LayoutCoordinates
        } catch (ex: Throwable) {
            options.logger.log(SentryLevel.WARNING, "LayoutNode.coordinates failed", ex)
            null
        }
    }

    fun layoutNodeBoundsContain(
        node: Any,
        x: Float,
        y: Float
    ): Boolean {
        val nodeHeight = height(node)
        val nodeWidth = width(node)
        val nodePosition: Offset = coordinates(node)?.positionInWindow() ?: Offset.Unspecified

        val nodeX = nodePosition.x
        val nodeY = nodePosition.y
        return nodePosition.isValid() && x >= nodeX && x <= nodeX + nodeWidth && y >= nodeY && y <= nodeY + nodeHeight
    }
}
