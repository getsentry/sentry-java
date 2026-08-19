package io.sentry.android.buddy.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.sentry.android.buddy.ui.common.theme.BuddyBorder
import io.sentry.android.buddy.ui.common.theme.BuddyPurple
import io.sentry.android.buddy.ui.preview.BuddyPreviewSurface
import kotlin.math.ceil
import kotlin.math.sin

private const val WAVE_SCROLL_DURATION_MS = 1400
private const val SWEEP_DURATION_MS = 1900
private const val SWEEP_WINDOW_FRACTION = 0.45f
private const val WAVE_SEGMENT_PX = 2f
private const val TAU = (2.0 * Math.PI).toFloat()

/**
 * An endless wavy progress bar. Material 3 grows one of these in 1.5.0-alpha, but that release
 * drags the whole Compose 1.12 stack in with it and the SDK still ships against minSdk 21 and Java
 * 8 - so Buddy draws its own.
 */
@Composable
internal fun BuddyWavyProgress(
  modifier: Modifier = Modifier,
  color: Color = BuddyPurple,
  trackColor: Color = BuddyBorder,
  strokeWidth: Dp = 4.dp,
  wavelength: Dp = 26.dp,
  amplitude: Dp = 3.dp,
) {
  val transition = rememberInfiniteTransition(label = "buddy-wavy-progress")
  val phase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          tween(WAVE_SCROLL_DURATION_MS, easing = LinearEasing),
          RepeatMode.Restart,
        ),
      label = "wave-phase",
    )
  val sweep by
    transition.animateFloat(
      initialValue = -SWEEP_WINDOW_FRACTION,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(tween(SWEEP_DURATION_MS, easing = LinearEasing), RepeatMode.Restart),
      label = "wave-sweep",
    )

  Canvas(modifier = modifier.fillMaxWidth().height(strokeWidth + amplitude * 2)) {
    val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
    val wave = wavePath(size.width, size.height / 2f, wavelength.toPx(), amplitude.toPx(), phase)
    drawPath(wave, trackColor, style = stroke)
    clipRect(left = sweep * size.width, right = (sweep + SWEEP_WINDOW_FRACTION) * size.width) {
      drawPath(wave, color, style = stroke)
    }
  }
}

/** A sine wave sampled across [width], shifted along its own axis by [phase] of a wavelength. */
private fun wavePath(
  width: Float,
  centerY: Float,
  wavelength: Float,
  amplitude: Float,
  phase: Float,
): Path {
  val path = Path()
  val steps = ceil(width / WAVE_SEGMENT_PX).toInt().coerceAtLeast(1)
  for (step in 0..steps) {
    val x = (step * WAVE_SEGMENT_PX).coerceAtMost(width)
    val y = centerY + amplitude * sin(TAU * (x / wavelength + phase))
    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
  }
  return path
}

@Preview(showBackground = true, widthDp = 380)
@Composable
private fun BuddyWavyProgressPreview() {
  BuddyPreviewSurface { BuddyWavyProgress() }
}
