@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals

package io.sentry.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.semantics.SemanticsModifier
import io.sentry.ILogger
import io.sentry.SentryLevel
import java.lang.reflect.Field

internal class SentryComposeHelper(logger: ILogger) {

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

/**
 * Copied from sentry-android-replay/src/main/java/io/sentry/android/replay/util/Nodes.kt
 *
 * A faster copy of https://github.com/androidx/androidx/blob/fc7df0dd68466ac3bb16b1c79b7a73dd0bfdd4c1/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/layout/LayoutCoordinates.kt#L187
 *
 * Since we traverse the tree from the root, we don't need to find it again from the leaf node and
 * just pass it as an argument.
 *
 * @return boundaries of this layout relative to the window's origin.
 */
public fun LayoutCoordinates.boundsInWindow(rootCoordinates: LayoutCoordinates?): Rect {
    val root = rootCoordinates ?: findRootCoordinates()

    val rootWidth = root.size.width.toFloat()
    val rootHeight = root.size.height.toFloat()

    val bounds = root.localBoundingBoxOf(this)
    val boundsLeft = bounds.left.fastCoerceIn(0f, rootWidth)
    val boundsTop = bounds.top.fastCoerceIn(0f, rootHeight)
    val boundsRight = bounds.right.fastCoerceIn(0f, rootWidth)
    val boundsBottom = bounds.bottom.fastCoerceIn(0f, rootHeight)

    if (boundsLeft == boundsRight || boundsTop == boundsBottom) {
        return Rect.Zero
    }

    val topLeft = root.localToWindow(Offset(boundsLeft, boundsTop))
    val topRight = root.localToWindow(Offset(boundsRight, boundsTop))
    val bottomRight = root.localToWindow(Offset(boundsRight, boundsBottom))
    val bottomLeft = root.localToWindow(Offset(boundsLeft, boundsBottom))

    val topLeftX = topLeft.x
    val topRightX = topRight.x
    val bottomLeftX = bottomLeft.x
    val bottomRightX = bottomRight.x

    val left = fastMinOf(topLeftX, topRightX, bottomLeftX, bottomRightX)
    val right = fastMaxOf(topLeftX, topRightX, bottomLeftX, bottomRightX)

    val topLeftY = topLeft.y
    val topRightY = topRight.y
    val bottomLeftY = bottomLeft.y
    val bottomRightY = bottomRight.y

    val top = fastMinOf(topLeftY, topRightY, bottomLeftY, bottomRightY)
    val bottom = fastMaxOf(topLeftY, topRightY, bottomLeftY, bottomRightY)

    return Rect(left, top, right, bottom)
}

/**
 * Returns the smaller of the given values. If any value is NaN, returns NaN. Preferred over
 * `kotlin.comparisons.minOf()` for 4 arguments as it avoids allocating an array because of the
 * varargs.
 */
private fun fastMinOf(a: Float, b: Float, c: Float, d: Float): Float {
    return minOf(a, minOf(b, minOf(c, d)))
}

/**
 * Returns the largest of the given values. If any value is NaN, returns NaN. Preferred over
 * `kotlin.comparisons.maxOf()` for 4 arguments as it avoids allocating an array because of the
 * varargs.
 */
private fun fastMaxOf(a: Float, b: Float, c: Float, d: Float): Float {
    return maxOf(a, maxOf(b, maxOf(c, d)))
}

/**
 * Returns this float value clamped in the inclusive range defined by [minimumValue] and
 * [maximumValue]. Unlike [Float.coerceIn], the range is not validated: the caller must ensure that
 * [minimumValue] is less than [maximumValue].
 */
private fun Float.fastCoerceIn(minimumValue: Float, maximumValue: Float) =
    this.fastCoerceAtLeast(minimumValue).fastCoerceAtMost(maximumValue)

/** Ensures that this value is not less than the specified [minimumValue]. */
private fun Float.fastCoerceAtLeast(minimumValue: Float): Float {
    return if (this < minimumValue) minimumValue else this
}

/** Ensures that this value is not greater than the specified [maximumValue]. */
private fun Float.fastCoerceAtMost(maximumValue: Float): Float {
    return if (this > maximumValue) maximumValue else this
}
