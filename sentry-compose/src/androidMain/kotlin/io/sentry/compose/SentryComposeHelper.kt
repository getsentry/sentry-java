@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals

package io.sentry.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.LayoutNodeLayoutDelegate
import androidx.compose.ui.semantics.SemanticsModifier
import io.sentry.ILogger
import io.sentry.SentryLevel
import java.lang.reflect.Field

internal class SentryComposeHelper(private val logger: ILogger) {

    private val layoutDelegateField: Field? =
        loadField(logger, "androidx.compose.ui.node.LayoutNode", "layoutDelegate")
    private val testTagElementField: Field? =
        loadField(logger, "androidx.compose.ui.platform.TestTagElement", "tag")
    private val sentryTagElementField: Field? =
        loadField(logger, "io.sentry.compose.SentryModifier.SentryTagModifierNodeElement", "tag")

    fun extractTag(modifier: Modifier): String? {
        val type = modifier.javaClass.canonicalName
        // Newer Jetpack Compose uses TestTagElement as node elements
        // See
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/platform/TestTag.kt;l=34;drc=dcaa116fbfda77e64a319e1668056ce3b032469f
        try {
            if ("androidx.compose.ui.platform.TestTagElement" == type &&
                testTagElementField != null
            ) {
                val value = testTagElementField.get(modifier)
                return value as String?
            } else if ("io.sentry.compose.SentryModifier.SentryTagModifierNodeElement" == type &&
                sentryTagElementField != null
            ) {
                val value = sentryTagElementField.get(modifier)
                return value as String?
            }
        } catch (e: Throwable) {
            // ignored
        }

        // Older versions use SemanticsModifier
        if (modifier is SemanticsModifier) {
            val semanticsConfiguration =
                modifier.semanticsConfiguration
            for ((item, value) in semanticsConfiguration) {
                val key = item.name
                if ("SentryTag" == key || "TestTag" == key) {
                    if (value is String) {
                        return value
                    }
                }
            }
        }
        return null
    }

    fun getLayoutNodeBoundsInWindow(node: LayoutNode): Rect? {
        if (layoutDelegateField != null) {
            try {
                val delegate =
                    layoutDelegateField[node] as LayoutNodeLayoutDelegate
                return delegate.outerCoordinator.coordinates.boundsInWindow()
            } catch (e: Exception) {
                logger.log(SentryLevel.WARNING, "Could not fetch position for LayoutNode", e)
            }
        }
        return null
    }

    companion object {
        private fun loadField(
            logger: ILogger,
            className: String,
            fieldName: String
        ): Field? {
            try {
                val clazz = Class.forName(className)
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                return field
            } catch (e: Exception) {
                logger.log(SentryLevel.WARNING, "Could not load $className.$fieldName field")
            }
            return null
        }
    }
}
