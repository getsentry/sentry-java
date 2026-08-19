package io.sentry.android.buddy.ui.common

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.sentry.android.buddy.model.PerformanceCharacteristics
import kotlin.math.roundToInt

/**
 * Sentry brand palette, taken from Sentry's design system (see
 * https://blog.sentry.io/building-dark-mode/).
 */
private object SentryColor {
  val Purple300 = Color(0xFF6C5FC7) // primary accent, used only for the sample marker
  val Gray100 = Color(0xFFE7E1EC)
  val Gray200 = Color(0xFFC6BECF)
  val Gray300 = Color(0xFF9386A0)
  val Gray400 = Color(0xFF776589)
  val Gray500 = Color(0xFF2B1D38)
}

/**
 * Summary stats for a single span.op, plus the one sample being compared against them. The visible
 * range is p50-p95 — no min/max/p99, since the p50-p95 span already captures the shape that matters
 * and avoids outlier fields skewing the axis. All duration fields share [unit] (defaults to "ms").
 */
internal data class SpanDurationStats(
  val avg: Float,
  val p50: Float,
  val p75: Float,
  val p90: Float,
  val p95: Float,
  val sample: Float,
  val unit: String = "ms",
)

/**
 * The bridge sends every duration in milliseconds, and can leave any of them out. The chart needs
 * all of them, so one missing value means no chart.
 */
internal fun PerformanceCharacteristics.toSpanDurationStats(): SpanDurationStats? {
  val sample = duration ?: return null
  val avg = avg ?: return null
  val p50 = p50 ?: return null
  val p75 = p75 ?: return null
  val p90 = p90 ?: return null
  val p95 = p95 ?: return null
  return SpanDurationStats(
    avg = avg.toFloat(),
    p50 = p50.toFloat(),
    p75 = p75.toFloat(),
    p90 = p90.toFloat(),
    p95 = p95.toFloat(),
    sample = sample.toFloat(),
  )
}

/**
 * Renders a p50-p95 range chart with nested percentile bands, an avg marker, and the single
 * measurement highlighted against production stats. The scale stretches past p95 only if avg or the
 * sample itself falls beyond it.
 */
@Composable
internal fun SpanDurationRangeChart(
  stats: SpanDurationStats,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxWidth().height(150.dp).padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    Canvas(modifier = Modifier.fillMaxWidth().height(134.dp)) {
      drawRangeChart(stats)
    }
  }
}

private fun DrawScope.drawRangeChart(stats: SpanDurationStats) {
  val leftMargin = 8.dp.toPx()
  val rightMargin = 8.dp.toPx()
  val chartWidth = size.width - leftMargin - rightMargin
  // scale covers p50-p95, stretched slightly if avg or the sample falls outside it
  val scaleMin = minOf(stats.p50, stats.sample)
  val scaleMax = maxOf(stats.p95, stats.avg, stats.sample)

  val bandTop = size.height * 0.28f
  val bandBottom = size.height * 0.52f
  val centerY = (bandTop + bandBottom) / 2f
  val tickTop = bandTop - 6.dp.toPx()
  val tickBottom = bandBottom + 6.dp.toPx()

  fun xOf(value: Float): Float =
    leftMargin +
      ((value.coerceIn(scaleMin, scaleMax) - scaleMin) / (scaleMax - scaleMin)) * chartWidth

  val labelPaint = textPaint(SentryColor.Gray400, 11.sp.toPx())
  val boldLabelPaint = textPaint(SentryColor.Gray500, 13.sp.toPx(), bold = true)

  // nested percentile bands, darkening toward the tail
  val bands =
    listOf(
      Triple(stats.p50, stats.p75, SentryColor.Gray200.copy(alpha = 0.5f)),
      Triple(stats.p75, stats.p90, SentryColor.Gray300.copy(alpha = 0.5f)),
      Triple(stats.p90, stats.p95, SentryColor.Gray400.copy(alpha = 0.5f)),
    )
  bands.forEach { (from, to, color) ->
    drawRoundRect(
      color = color,
      topLeft = Offset(xOf(from), bandTop),
      size = androidx.compose.ui.geometry.Size(xOf(to) - xOf(from), bandBottom - bandTop),
      cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
    )
  }

  // percentile tick marks + labels
  val ticks =
    listOf(
      stats.p50 to "p50",
      stats.p75 to "p75",
      stats.p90 to "p90",
      stats.p95 to "p95",
    )
  ticks.forEachIndexed { i, (value, label) ->
    val x = xOf(value)
    drawLine(
      color = SentryColor.Gray400,
      start = Offset(x, tickTop),
      end = Offset(x, tickBottom),
      strokeWidth = 1.5.dp.toPx(),
    )
    // stagger alternating labels so close ticks (p90/p95) don't collide
    val labelY = tickBottom + 14.dp.toPx()
    val textAlign =
      when (i) {
        0 -> {
          Paint.Align.LEFT
        }
        ticks.size - 1 -> {
          Paint.Align.RIGHT
        }
        else -> {
          Paint.Align.CENTER
        }
      }
    labelPaint.textAlign = textAlign
    drawContext.canvas.nativeCanvas.drawText(
      "$label",
      x,
      labelY,
      labelPaint,
    )

    drawContext.canvas.nativeCanvas.drawText(
      "${value.roundToInt()}ms",
      x,
      labelY + 14.dp.toPx(),
      labelPaint,
    )
  }

  // avg marker (diamond) — divergence from p50 signals a skewed distribution
  val avgX = xOf(stats.avg)
  val diamondR = 6.dp.toPx()
  drawPath(
    path =
      androidx.compose.ui.graphics.Path().apply {
        moveTo(avgX, centerY - diamondR)
        lineTo(avgX + diamondR, centerY)
        lineTo(avgX, centerY + diamondR)
        lineTo(avgX - diamondR, centerY)
        close()
      },
    color = SentryColor.Purple300.copy(alpha = 0.55f),
  )
  drawContext.canvas.nativeCanvas.drawText(
    "avg ${stats.avg.roundToInt()}",
    avgX,
    bandTop - 10.dp.toPx(),
    labelPaint.apply { textAlign = Paint.Align.CENTER },
  )

  // the single sample — the point of the whole chart
  val sampleX = xOf(stats.sample)
  drawCircle(color = SentryColor.Purple300, radius = 8.dp.toPx(), center = Offset(sampleX, centerY))
  drawLine(
    color = SentryColor.Purple300.copy(alpha = 0.4f),
    start = Offset(sampleX, centerY),
    end = Offset(sampleX, size.height - 4.dp.toPx()),
    strokeWidth = 1.dp.toPx(),
  )
  drawContext.canvas.nativeCanvas.drawText(
    "${stats.sample.roundToInt()}${stats.unit}",
    sampleX,
    size.height,
    boldLabelPaint.apply { textAlign = Paint.Align.CENTER },
  )
}

private fun textPaint(color: Color, size: Float, bold: Boolean = false): Paint =
  Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.toArgb()
    this.textSize = size
    this.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
  }

private fun Color.toArgb(): Int =
  android.graphics.Color.argb(
    (alpha * 255).roundToInt(),
    (red * 255).roundToInt(),
    (green * 255).roundToInt(),
    (blue * 255).roundToInt(),
  )

@Preview(showBackground = true)
@Composable
private fun SpanDurationRangeChartPreview() {
  SpanDurationRangeChart(
    stats =
      SpanDurationStats(
        avg = 230f,
        p50 = 180f,
        p75 = 280f,
        p90 = 420f,
        p95 = 520f,
        sample = 120f,
      )
  )
}
