@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals and classes

package io.sentry.android.replay.util

import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.roundToInt

internal class ComposeTextLayout(internal val layout: TextLayoutResult, private val hasFillModifier: Boolean) : TextLayout {
    override val lineCount: Int get() = layout.lineCount
    override val dominantTextColor: Int? get() = null
    override fun getPrimaryHorizontal(line: Int, offset: Int): Float {
        val horizontalPos = layout.getHorizontalPosition(offset, usePrimaryDirection = true)
        // when there's no `fill` modifier on a Text composable, compose still thinks that there's
        // one and wrongly calculates horizontal position relative to node's start, not text's start
        // for some reason. This is only the case for single-line text (multiline works fien).
        // So we subtract line's left to get the correct position
        return if (!hasFillModifier && lineCount == 1) {
            horizontalPos - layout.getLineLeft(line)
        } else {
            horizontalPos
        }
    }
    override fun getEllipsisCount(line: Int): Int = if (layout.isLineEllipsized(line)) 1 else 0
    override fun getLineVisibleEnd(line: Int): Int = layout.getLineEnd(line, visibleEnd = true)
    override fun getLineTop(line: Int): Int = layout.getLineTop(line).roundToInt()
    override fun getLineBottom(line: Int): Int = layout.getLineBottom(line).roundToInt()
    override fun getLineStart(line: Int): Int = layout.getLineStart(line)
}

// TODO: probably most of the below we can do via bytecode instrumentation and speed up at runtime

/**
 * This method is necessary to mask images in Compose.
 *
 * We heuristically look up for classes that have a [Painter] modifier, usually they all have a
 * `Painter` string in their name, e.g. PainterElement, PainterModifierNodeElement or
 * ContentPainterModifier for Coil.
 *
 * That's not going to cover all cases, but probably 90%.
 *
 * We also add special proguard rules to keep the `Painter` class names and their `painter` member.
 */
internal fun LayoutNode.findPainter(): Painter? {
    val modifierInfos = getModifierInfo()
    for (index in modifierInfos.indices) {
        val modifier = modifierInfos[index].modifier
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

/**
 * We heuristically check the known classes that are coming from local assets usually:
 * [androidx.compose.ui.graphics.vector.VectorPainter]
 * [androidx.compose.ui.graphics.painter.ColorPainter]
 * [androidx.compose.ui.graphics.painter.BrushPainter]
 *
 * In theory, [androidx.compose.ui.graphics.painter.BitmapPainter] can also come from local assets,
 * but it can as well come from a network resource, so we preemptively mask it.
 */
internal fun Painter.isMaskable(): Boolean {
    val className = this::class.java.name
    return !className.contains("Vector") &&
        !className.contains("Color") &&
        !className.contains("Brush")
}

internal data class TextAttributes(val color: Color?, val hasFillModifier: Boolean)

/**
 * This method is necessary to mask text in Compose.
 *
 * We heuristically look up for classes that have a [Text] modifier, usually they all have a
 * `Text` string in their name, e.g. TextStringSimpleElement or TextAnnotatedStringElement. We then
 * get the color from the modifier, to be able to mask it with the correct color.
 *
 * We also look up for classes that have a [Fill] modifier, usually they all have a `Fill` string in
 * their name, e.g. FillElement. This is necessary to workaround a Compose bug where single-line
 * text composable without a `fill` modifier still thinks that there's one and wrongly calculates
 * horizontal position.
 *
 * We also add special proguard rules to keep the `Text` class names and their `color` member.
 */
internal fun LayoutNode.findTextAttributes(): TextAttributes {
    val modifierInfos = getModifierInfo()
    var color: Color? = null
    var hasFillModifier = false
    for (index in modifierInfos.indices) {
        val modifier = modifierInfos[index].modifier
        val modifierClassName = modifier::class.java.name
        if (modifierClassName.contains("Text")) {
            color = try {
                (
                    modifier::class.java.getDeclaredField("color")
                        .apply { isAccessible = true }
                        .get(modifier) as? ColorProducer
                    )
                    ?.invoke()
            } catch (e: Throwable) {
                null
            }
        } else if (modifierClassName.contains("Fill")) {
            hasFillModifier = true
        }
    }
    return TextAttributes(color, hasFillModifier)
}

/**
 * Returns the smaller of the given values. If any value is NaN, returns NaN. Preferred over
 * `kotlin.comparisons.minOf()` for 4 arguments as it avoids allocating an array because of the
 * varargs.
 */
private inline fun fastMinOf(a: Float, b: Float, c: Float, d: Float): Float {
    return minOf(a, minOf(b, minOf(c, d)))
}

/**
 * Returns the largest of the given values. If any value is NaN, returns NaN. Preferred over
 * `kotlin.comparisons.maxOf()` for 4 arguments as it avoids allocating an array because of the
 * varargs.
 */
private inline fun fastMaxOf(a: Float, b: Float, c: Float, d: Float): Float {
    return maxOf(a, maxOf(b, maxOf(c, d)))
}

/**
 * Returns this float value clamped in the inclusive range defined by [minimumValue] and
 * [maximumValue]. Unlike [Float.coerceIn], the range is not validated: the caller must ensure that
 * [minimumValue] is less than [maximumValue].
 */
private inline fun Float.fastCoerceIn(minimumValue: Float, maximumValue: Float) =
    this.fastCoerceAtLeast(minimumValue).fastCoerceAtMost(maximumValue)

/** Ensures that this value is not less than the specified [minimumValue]. */
private inline fun Float.fastCoerceAtLeast(minimumValue: Float): Float {
    return if (this < minimumValue) minimumValue else this
}

/** Ensures that this value is not greater than the specified [maximumValue]. */
private inline fun Float.fastCoerceAtMost(maximumValue: Float): Float {
    return if (this > maximumValue) maximumValue else this
}

/**
 * A faster copy of https://github.com/androidx/androidx/blob/fc7df0dd68466ac3bb16b1c79b7a73dd0bfdd4c1/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/layout/LayoutCoordinates.kt#L187
 *
 * Since we traverse the tree from the root, we don't need to find it again from the leaf node and
 * just pass it as an argument.
 *
 * @return boundaries of this layout relative to the window's origin.
 */
internal fun LayoutCoordinates.boundsInWindow(rootCoordinates: LayoutCoordinates?): Rect {
    val root = rootCoordinates ?: findRootCoordinates()

    val rootWidth = root.size.width.toFloat()
    val rootHeight = root.size.height.toFloat()

    val bounds = root.localBoundingBoxOf(this)
    val boundsLeft = bounds.left.fastCoerceIn(0f, rootWidth)
    val boundsTop = bounds.top.fastCoerceIn(0f, rootHeight)
    val boundsRight = bounds.right.fastCoerceIn(0f, rootWidth)
    val boundsBottom = bounds.bottom.fastCoerceIn(0f, rootHeight)

    if (boundsLeft == boundsRight || boundsTop == boundsBottom) {
        return Rect()
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

    return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
}
