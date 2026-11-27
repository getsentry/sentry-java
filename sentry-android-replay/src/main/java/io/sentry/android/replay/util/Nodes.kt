@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // to access internal vals and classes

package io.sentry.android.replay.util

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal class ComposeTextLayout(internal val layout: TextLayoutResult) : TextLayout {
  override val lineCount: Int
    get() = layout.lineCount

  override val dominantTextColor: Int?
    get() = null

  /**
   * The paragraph may be laid out with a wider width (constraint maxWidth) than the actual node
   * (layout result size). When that happens, getLineLeft/getLineRight return positions in the
   * paragraph coordinate system, which don't match the node's bounds. In that case, text alignment
   * has no visible effect, so we fall back to using line width starting from x=0.
   */
  private val paragraphWidthExceedsNode: Boolean
    get() = layout.multiParagraph.width > layout.size.width

  override fun getLineLeft(line: Int): Float {
    if (paragraphWidthExceedsNode) {
      return 0f
    }
    return layout.getLineLeft(line)
  }

  override fun getLineRight(line: Int): Float {
    if (paragraphWidthExceedsNode) {
      return layout.multiParagraph.getLineWidth(line)
    }
    return layout.getLineRight(line)
  }

  override fun getLineTop(line: Int): Int = layout.getLineTop(line).roundToInt()

  override fun getLineBottom(line: Int): Int = layout.getLineBottom(line).roundToInt()
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
 * Returns null if the node has no such modifier, otherwise whether the image should be masked (see
 * [isMaskable]). If the modifier has no readable `painter` member, e.g. Coil 3.1+ keeps the painter
 * on its Modifier.Node instead, we can't tell what it draws, so we treat it as maskable. For Coil
 * that's the same result as reading it: its painter is always the `AsyncImagePainter` wrapper,
 * which [isMaskable] can't tell apart from a remote image.
 *
 * We also add special proguard rules to keep the `Painter` class names and their `painter` member.
 */
internal fun LayoutNode.isMaskableImage(): Boolean? {
  val modifierInfos = getModifierInfo()
  for (index in modifierInfos.indices) {
    val modifier = modifierInfos[index].modifier
    if (modifier::class.java.name.contains("Painter")) {
      // a lookup by name instead of getDeclaredField, which throws on every frame for Coil 3.1+
      val painter =
        modifier::class
          .java
          .declaredFields
          .firstOrNull { it.name == "painter" }
          ?.apply { isAccessible = true }
          ?.get(modifier) as? Painter
      return painter?.isMaskable() ?: true
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

/**
 * This method is necessary to mask text in Compose.
 *
 * We heuristically look up for classes that have a [Text] modifier, usually they all have a `Text`
 * string in their name, e.g. TextStringSimpleElement or TextAnnotatedStringElement. We then get the
 * color from the modifier, to be able to mask it with the correct color.
 *
 * We also add special proguard rules to keep the `Text` class names and their `color` member.
 */
internal fun LayoutNode.findTextColor(): Color? {
  val modifierInfos = getModifierInfo()
  for (index in modifierInfos.indices) {
    val modifier = modifierInfos[index].modifier
    val modifierClassName = modifier::class.java.name
    if (modifierClassName.contains("Text")) {
      return try {
        (modifier::class.java.getDeclaredField("color").apply { isAccessible = true }.get(modifier)
            as? ColorProducer)
          ?.invoke()
      } catch (e: Throwable) {
        null
      }
    }
  }
  return null
}

internal fun Rect.toRect(): android.graphics.Rect {
  // Round outward (floor min edges, ceil max edges) so that a sub-pixel but non-empty Rect doesn't
  // collapse to a zero-width/height android.graphics.Rect. Otherwise a node could be marked visible
  // and maskable based on the float bounds, while the integer rect the MaskRenderer draws has zero
  // area, leaving sensitive content unmasked. Rounding outward also biases toward over-masking.
  return android.graphics.Rect(
    floor(left).toInt(),
    floor(top).toInt(),
    ceil(right).toInt(),
    ceil(bottom).toInt(),
  )
}
