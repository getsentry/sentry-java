@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.sentry.android.replay.util

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.BrushPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NodesTest {

  @Test
  fun `VectorPainter is not maskable`() {
    assertThat(VectorPainter().isMaskable()).isFalse()
  }

  @Test
  fun `ColorPainter is not maskable`() {
    assertThat(ColorPainter(Color.Red).isMaskable()).isFalse()
  }

  @Test
  fun `BrushPainter is not maskable`() {
    assertThat(BrushPainter(SolidColor(Color.Red)).isMaskable()).isFalse()
  }

  @Test
  fun `BitmapPainter is maskable`() {
    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
    assertThat(BitmapPainter(bitmap).isMaskable()).isTrue()
  }

  @Test
  fun `a custom Painter is maskable even when its name contains Color, Vector, or Brush`() {
    assertThat(ColorVectorBrushPainter().isMaskable()).isTrue()
  }

  private class ColorVectorBrushPainter : Painter() {
    override val intrinsicSize: Size = Size.Unspecified

    override fun DrawScope.onDraw() = Unit
  }
}
