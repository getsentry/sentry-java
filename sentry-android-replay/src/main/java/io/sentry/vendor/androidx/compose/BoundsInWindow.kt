/*
 * Adapted from AndroidX Compose UI:
 *  - the `boundsInWindow` extension is a faster copy of `LayoutCoordinates.boundsInWindow`
 *  - the `fastMinOf`, `fastMaxOf`, `fastCoerceIn`, `fastCoerceAtLeast` and `fastCoerceAtMost`
 *    helpers are copied from `androidx.compose.ui.util.MathHelpers`
 *
 * Adapted from:
 * https://github.com/androidx/androidx/blob/fc7df0dd68466ac3bb16b1c79b7a73dd0bfdd4c1/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/layout/LayoutCoordinates.kt
 * https://github.com/androidx/androidx/blob/androidx-main/compose/ui/ui-util/src/commonMain/kotlin/androidx/compose/ui/util/MathHelpers.kt
 *
 * Copyright (C) 2019, 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.sentry.vendor.androidx.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates

/**
 * Returns the smaller of the given values. If any value is NaN, returns NaN. Preferred over
 * `kotlin.comparisons.minOf()` for 4 arguments as it avoids allocating an array because of the
 * varargs.
 */
private inline fun fastMinOf(a: Float, b: Float, c: Float, d: Float): Float =
  minOf(a, minOf(b, minOf(c, d)))

/**
 * Returns the largest of the given values. If any value is NaN, returns NaN. Preferred over
 * `kotlin.comparisons.maxOf()` for 4 arguments as it avoids allocating an array because of the
 * varargs.
 */
private inline fun fastMaxOf(a: Float, b: Float, c: Float, d: Float): Float =
  maxOf(a, maxOf(b, maxOf(c, d)))

/**
 * Returns this float value clamped in the inclusive range defined by [minimumValue] and
 * [maximumValue]. Unlike [Float.coerceIn], the range is not validated: the caller must ensure that
 * [minimumValue] is less than [maximumValue].
 */
private inline fun Float.fastCoerceIn(minimumValue: Float, maximumValue: Float) =
  this.fastCoerceAtLeast(minimumValue).fastCoerceAtMost(maximumValue)

/** Ensures that this value is not less than the specified [minimumValue]. */
private inline fun Float.fastCoerceAtLeast(minimumValue: Float): Float =
  if (this < minimumValue) minimumValue else this

/** Ensures that this value is not greater than the specified [maximumValue]. */
private inline fun Float.fastCoerceAtMost(maximumValue: Float): Float =
  if (this > maximumValue) maximumValue else this

/**
 * A faster copy of
 * https://github.com/androidx/androidx/blob/fc7df0dd68466ac3bb16b1c79b7a73dd0bfdd4c1/compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/layout/LayoutCoordinates.kt#L187
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

  // pass clipBounds explicitly to avoid the `localBoundingBoxOf$default` bridge that AGP 8.13's D8
  // desugars inconsistently on minSdk < 24
  val bounds = root.localBoundingBoxOf(this, true)
  val boundsLeft = bounds.left.fastCoerceIn(0f, rootWidth)
  val boundsTop = bounds.top.fastCoerceIn(0f, rootHeight)
  val boundsRight = bounds.right.fastCoerceIn(0f, rootWidth)
  val boundsBottom = bounds.bottom.fastCoerceIn(0f, rootHeight)

  if (boundsLeft == boundsRight || boundsTop == boundsBottom) {
    return Rect(0.0f, 0.0f, 0.0f, 0.0f)
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
