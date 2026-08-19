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

  @Suppress("CheckReturnValue")
  val dashboard: ImageVector
    get() {
      if (_dashboard != null) {
        return _dashboard!!
      }
      _dashboard =
        ImageVector.Builder(
            name = "dashboard",
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
              moveTo(4f, 4f)
              horizontalLineTo(11f)
              verticalLineTo(11f)
              horizontalLineTo(4f)
              close()
              moveTo(13f, 4f)
              horizontalLineTo(20f)
              verticalLineTo(11f)
              horizontalLineTo(13f)
              close()
              moveTo(4f, 13f)
              horizontalLineTo(11f)
              verticalLineTo(20f)
              horizontalLineTo(4f)
              close()
              moveTo(13f, 13f)
              horizontalLineTo(20f)
              verticalLineTo(20f)
              horizontalLineTo(13f)
              close()
            }
          }
          .build()
      return _dashboard!!
    }

  private var _dashboard: ImageVector? = null

  @Suppress("CheckReturnValue")
  val monitor: ImageVector
    get() {
      if (_monitor != null) {
        return _monitor!!
      }
      _monitor =
        ImageVector.Builder(
            name = "monitor",
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
              pathFillType = PathFillType.Companion.EvenOdd,
            ) {
              moveTo(3f, 4f)
              horizontalLineTo(21f)
              verticalLineTo(16f)
              horizontalLineTo(3f)
              close()
              moveTo(5f, 6f)
              horizontalLineTo(19f)
              verticalLineTo(14f)
              horizontalLineTo(5f)
              close()
              moveTo(11f, 16f)
              horizontalLineTo(13f)
              verticalLineTo(19f)
              horizontalLineTo(11f)
              close()
              moveTo(8f, 19f)
              horizontalLineTo(16f)
              verticalLineTo(21f)
              horizontalLineTo(8f)
              close()
            }
          }
          .build()
      return _monitor!!
    }

  private var _monitor: ImageVector? = null

  @Suppress("CheckReturnValue")
  val bug: ImageVector
    get() {
      if (_bug != null) {
        return _bug!!
      }
      _bug =
        ImageVector.Builder(
            name = "bug",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
          )
          .apply {
            // Legs and antennae.
            path(
              fill = null,
              stroke = SolidColor(Color.Black),
              strokeAlpha = 1f,
              strokeLineWidth = 1.6f,
              strokeLineCap = StrokeCap.Round,
              strokeLineJoin = StrokeJoin.Round,
              strokeLineMiter = 1f,
              pathFillType = PathFillType.Companion.NonZero,
            ) {
              moveTo(6.6f, 11.2f)
              lineTo(2.6f, 8.6f)
              moveTo(6.2f, 14.4f)
              lineTo(1.9f, 14.4f)
              moveTo(6.6f, 17.4f)
              lineTo(2.6f, 19.8f)
              moveTo(17.4f, 11.2f)
              lineTo(21.4f, 8.6f)
              moveTo(17.8f, 14.4f)
              lineTo(22.1f, 14.4f)
              moveTo(17.4f, 17.4f)
              lineTo(21.4f, 19.8f)
              moveTo(10.3f, 4.2f)
              lineTo(8.5f, 1.8f)
              moveTo(13.7f, 4.2f)
              lineTo(15.5f, 1.8f)
            }
            // Body.
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
              moveTo(6.5f, 14.4f)
              arcTo(5.5f, 6.6f, 0f, true, true, 17.5f, 14.4f)
              arcTo(5.5f, 6.6f, 0f, true, true, 6.5f, 14.4f)
              close()
            }
            // Head, with the two eyes cut out of it.
            path(
              fill = SolidColor(Color.Black),
              fillAlpha = 1f,
              stroke = null,
              strokeAlpha = 1f,
              strokeLineWidth = 1f,
              strokeLineCap = StrokeCap.Butt,
              strokeLineJoin = StrokeJoin.Bevel,
              strokeLineMiter = 1f,
              pathFillType = PathFillType.Companion.EvenOdd,
            ) {
              moveTo(8.8f, 6.6f)
              arcTo(3.2f, 3.2f, 0f, true, true, 15.2f, 6.6f)
              arcTo(3.2f, 3.2f, 0f, true, true, 8.8f, 6.6f)
              close()
              moveTo(9.9f, 6.2f)
              arcTo(0.85f, 0.85f, 0f, true, true, 11.6f, 6.2f)
              arcTo(0.85f, 0.85f, 0f, true, true, 9.9f, 6.2f)
              close()
              moveTo(12.4f, 6.2f)
              arcTo(0.85f, 0.85f, 0f, true, true, 14.1f, 6.2f)
              arcTo(0.85f, 0.85f, 0f, true, true, 12.4f, 6.2f)
              close()
            }
          }
          .build()
      return _bug!!
    }

  private var _bug: ImageVector? = null

  @Suppress("CheckReturnValue")
  val bolt: ImageVector
    get() {
      if (_bolt != null) {
        return _bolt!!
      }
      _bolt =
        ImageVector.Builder(
            name = "bolt",
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
              moveTo(13.2f, 2f)
              lineTo(4.5f, 13.6f)
              lineTo(10.6f, 13.6f)
              lineTo(9.2f, 22f)
              lineTo(19.5f, 9.6f)
              lineTo(13.1f, 9.6f)
              close()
            }
          }
          .build()
      return _bolt!!
    }

  private var _bolt: ImageVector? = null

  @Suppress("CheckReturnValue")
  public val wand_shine: ImageVector
    get() {
      if (_wand_shine != null) {
        return _wand_shine!!
      }
      _wand_shine =
        ImageVector.Builder(
            name = "wand_shine",
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
              moveTo(8.28f, 7.72f)
              lineToRelative(-3f, -3f)
              lineTo(6.7f, 3.3f)
              lineToRelative(3f, 3f)
              lineTo(8.28f, 7.72f)
              close()
              moveTo(12f, 5.35f)
              verticalLineTo(1.1f)
              horizontalLineToRelative(2f)
              verticalLineTo(5.35f)
              horizontalLineTo(12f)
              close()
              moveToRelative(7.28f, 13.38f)
              lineToRelative(-3f, -3f)
              lineTo(17.7f, 14.3f)
              lineToRelative(3f, 3f)
              lineToRelative(-1.43f, 1.43f)
              close()
              moveTo(17.7f, 7.72f)
              lineTo(16.28f, 6.3f)
              lineToRelative(3f, -3f)
              lineTo(20.7f, 4.72f)
              lineToRelative(-3f, 3f)
              close()
              moveTo(18.65f, 12f)
              verticalLineTo(10f)
              horizontalLineTo(22.9f)
              verticalLineToRelative(2f)
              horizontalLineTo(18.65f)
              close()
              moveTo(5.13f, 21.7f)
              lineTo(2.3f, 18.88f)
              quadTo(2f, 18.58f, 2f, 18.18f)
              quadToRelative(0f, -0.4f, 0.3f, -0.7f)
              lineToRelative(9.07f, -9.1f)
              quadTo(12.25f, 7.5f, 13.5f, 7.5f)
              reflectiveQuadToRelative(2.13f, 0.88f)
              reflectiveQuadTo(16.5f, 10.5f)
              reflectiveQuadToRelative(-0.88f, 2.13f)
              lineTo(6.53f, 21.7f)
              quadTo(6.23f, 22f, 5.83f, 22f)
              reflectiveQuadTo(5.13f, 21.7f)
              close()
              moveTo(12.1f, 13.33f)
              quadToRelative(0f, 0f, -0.36f, -0.35f)
              reflectiveQuadTo(11.38f, 12.63f)
              reflectiveQuadTo(11.03f, 12.27f)
              quadTo(10.68f, 11.93f, 10.68f, 11.93f)
              lineToRelative(0.7f, 0.7f)
              lineToRelative(0.73f, 0.7f)
              close()
              moveTo(5.83f, 19.6f)
              lineTo(12.1f, 13.33f)
              lineToRelative(-1.43f, -1.4f)
              lineTo(4.43f, 18.18f)
              lineToRelative(1.4f, 1.43f)
              close()
            }
          }
          .build()
      return _wand_shine!!
    }

  private var _wand_shine: ImageVector? = null
}
