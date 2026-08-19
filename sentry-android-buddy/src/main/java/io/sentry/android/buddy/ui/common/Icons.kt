package io.sentry.android.buddy.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object Icons {
  @Suppress("CheckReturnValue")
  val check: ImageVector
    get() {
      if (_check != null) {
        return _check!!
      }
      _check =
        ImageVector.Builder(
            name = "check",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
          )
          .apply {
            path(
              fill = SolidColor(Color.Black),
              fillAlpha = 1f,
              stroke = null,
              strokeAlpha = 1f,
              strokeLineWidth = 1f,
              strokeLineCap = StrokeCap.Butt,
              strokeLineJoin = StrokeJoin.Bevel,
              strokeLineMiter = 1f,
              pathFillType = PathFillType.Companion.NonZero,
            ) {
              moveTo(9.55f, 18f)
              lineTo(3.85f, 12.3f)
              lineTo(5.28f, 10.88f)
              lineToRelative(4.28f, 4.28f)
              lineTo(18.73f, 5.97f)
              lineTo(20.15f, 7.4f)
              lineTo(9.55f, 18f)
              close()
            }
          }
          .build()
      return _check!!
    }

  private var _check: ImageVector? = null

  @Suppress("CheckReturnValue")
  val open_in_new: ImageVector
    get() {
      if (_open_in_new != null) {
        return _open_in_new!!
      }
      _open_in_new =
        ImageVector.Builder(
            name = "open_in_new",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
          )
          .apply {
            path(
              fill = SolidColor(Color.Black),
              fillAlpha = 1f,
              stroke = null,
              strokeAlpha = 1f,
              strokeLineWidth = 1f,
              strokeLineCap = StrokeCap.Butt,
              strokeLineJoin = StrokeJoin.Bevel,
              strokeLineMiter = 1f,
              pathFillType = PathFillType.Companion.NonZero,
            ) {
              moveTo(5f, 21f)
              quadTo(4.18f, 21f, 3.59f, 20.41f)
              reflectiveQuadTo(3f, 19f)
              verticalLineTo(5f)
              quadTo(3f, 4.17f, 3.59f, 3.59f)
              reflectiveQuadTo(5f, 3f)
              horizontalLineToRelative(7f)
              verticalLineTo(5f)
              horizontalLineTo(5f)
              verticalLineTo(19f)
              horizontalLineTo(19f)
              verticalLineTo(12f)
              horizontalLineToRelative(2f)
              verticalLineToRelative(7f)
              quadToRelative(0f, 0.82f, -0.59f, 1.41f)
              reflectiveQuadTo(19f, 21f)
              horizontalLineTo(5f)
              close()
              moveTo(9.7f, 15.7f)
              lineTo(8.3f, 14.3f)
              lineTo(17.6f, 5f)
              horizontalLineTo(14f)
              verticalLineTo(3f)
              horizontalLineToRelative(7f)
              verticalLineToRelative(7f)
              horizontalLineTo(19f)
              verticalLineTo(6.4f)
              lineTo(9.7f, 15.7f)
              close()
            }
          }
          .build()
      return _open_in_new!!
    }

  private var _open_in_new: ImageVector? = null
}
