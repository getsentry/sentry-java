package io.sentry.samples.android.replay

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuffColorFilter
import android.view.MotionEvent

class RRWebRecorder : Recorder {

    val recording = ArrayList<Any>()
    var currentFrame = emptyMap<String, Any>()
    var currentFrameCommands = ArrayList<Map<String, Any>>()

    override fun beginFrame(timestampMs: Long, width: Int, height: Int) {
        if (recording.isEmpty()) {
            val initialScreen = mapOf(
                "timestamp" to timestampMs,
                "type" to 2,
                "data" to mapOf<String, Any>(
                    "node" to mapOf(
                        "id" to 1,
                        "type" to 0,
                        "childNodes" to listOf(
                            mapOf(
                                "type" to 1,
                                "name" to "html",
                                "id" to 2,
                            ),
                            mapOf(
                                "id" to 3,
                                "type" to 2,
                                "tagName" to "html",
                                "childNodes" to listOf(
                                    mapOf(
                                        "id" to 5,
                                        "type" to 2,
                                        "tagName" to "body",
                                        "childNodes" to listOf(
                                            mapOf(
                                                "type" to 2,
                                                "tagName" to "canvas",
                                                "id" to 7
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    ),
                    "initialOffset" to mapOf(
                        "left" to 0,
                        "top" to 0
                    )
                )
            )
            recording.add(initialScreen)
        }

        currentFrameCommands = ArrayList()
        currentFrameCommands.add(
            mapOf(
                "property" to "clearRect",
                "args" to listOf(0, 0, width, height)
            )
        )
        currentFrame = mapOf(
            "timestamp" to timestampMs,
            "type" to 3,
            "data" to mapOf<String, Any>(
                "source" to 9,
                "id" to 7,
                "type" to 0,
                "commands" to currentFrameCommands
            )
        )
        recording.add(currentFrame)
    }

    override fun save() {
        currentFrameCommands.add(
            mapOf(
                "property" to "save"
            )
        )
    }

    override fun restore() {
        currentFrameCommands.add(
            mapOf(
                "property" to "restore"
            )
        )
    }

    override fun restoreToCount(saveCount: Int) {
        // TODO how often?
        currentFrameCommands.add(
            mapOf(
                "property" to "restore"
            )
        )
    }

    override fun translate(dx: Float, dy: Float) {
        currentFrameCommands.add(
            mapOf(
                "property" to "translate",
                "args" to listOf(dx, dy)
            )
        )
    }

    override fun clipRectF(left: Float, top: Float, right: Float, bottom: Float) {
        setPathToRectF(left, top, right, bottom)
        currentFrameCommands.add(
            mapOf(
                "property" to "clip",
            )
        )
    }

    override fun drawRoundRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        rx: Float,
        ry: Float,
        paint: Paint
    ) {
        setupPaint(paint)
        // TODO how to support RY
        currentFrameCommands.add(
            mapOf(
                "property" to "roundRect",
                "args" to listOf(left, top, right - left, bottom - top, rx)
            )
        )
        draw(paint)
    }


    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        setupPaint(paint)

        // arc(x, y, radius, startAngle, endAngle)
        currentFrameCommands.add(
            mapOf(
                "property" to "arc",
                "args" to listOf(cx, cy, 0.0, 360.0, radius)
            )
        )

        draw(paint)
    }

    override fun drawText(
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        y: Float,
        paint: Paint
    ) {
        setupPaint(paint)

        // TODO font support
        // ctx.font = "50px serif";
        // ctx.fillText("Hello world", 50, 90);

        val relevantText = text.subSequence(start, end).toString()
        currentFrameCommands.add(
            mapOf(
                "property" to "fillText",
                "args" to listOf(relevantText, x, y)
            )
        )
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        setupPaint(paint)
        currentFrameCommands.add(
            mapOf(
                "property" to "fillRect",
                "args" to listOf(left, top, right - left, bottom - top)
            )
        )
    }

    override fun concat(matrix: Matrix) {
        // TODO implement ??
    }

    override fun scale(sx: Float, sy: Float) {
        // scale(x, y)
        currentFrameCommands.add(
            mapOf(
                "property" to "scale",
                "args" to listOf(sx, sy)
            )
        )
    }

    override fun rotate(degrees: Float) {
        // rotate(angle)
        currentFrameCommands.add(
            mapOf(
                "property" to "rotate",
                "args" to listOf(degrees)
            )
        )
    }

    override fun skew(sx: Float, sy: Float) {
        // TODO how to implement
    }

    override fun setMatrix(matrix: Matrix?) {
        // https://developer.mozilla.org/en-US/docs/Web/API/CanvasRenderingContext2D/setTransform
        // ctx.setTransform(1, 0, 0, 1, 0, 0);
        // Android has a 3x3 matrix
    }

    override fun onTouchEvent(timestampMs: Long, event: MotionEvent) {
        // TODO("Not yet implemented")
    }

    override fun drawPath(path: Path, paint: Paint) {
        setupPaint(paint)

        val points = path.approximate(1f)
        val type = when (path.fillType) {
            Path.FillType.WINDING -> "nonzero"
            Path.FillType.EVEN_ODD -> "evenodd"
            Path.FillType.INVERSE_EVEN_ODD -> "??"
            Path.FillType.INVERSE_WINDING -> "??"
        }

        currentFrameCommands.add(
            mapOf(
                "property" to "beginPath"
            )
        )
        for (i in points.indices step 3) {
            val command = if (i == 0)
                "moveTo" else "lineTo"
            currentFrameCommands.add(
                mapOf(
                    "property" to command,
                    "args" to listOf(points[i + 1], points[i + 2])
                )
            )
        }
        draw(paint)
    }

    private fun draw(paint: Paint) {
        if (paint.style == Paint.Style.FILL) {
            currentFrameCommands.add(
                mapOf(
                    "property" to "fill"
                )
            )
        } else if (paint.style == Paint.Style.STROKE) {
            currentFrameCommands.add(
                mapOf(
                    "property" to "stroke"
                )
            )
        } else {
            // fill and stroke
            currentFrameCommands.add(
                mapOf(
                    "property" to "fill"
                )
            )
            currentFrameCommands.add(
                mapOf(
                    "property" to "stroke"
                )
            )
        }
    }

    private fun setPathToRectF(left: Float, top: Float, right: Float, bottom: Float) {
        currentFrameCommands.add(
            mapOf(
                "property" to "beginPath",
            )
        )
        currentFrameCommands.add(
            mapOf(
                "property" to "moveTo",
                "args" to listOf(left, top)
            )
        )
        currentFrameCommands.add(
            mapOf(
                "property" to "lineTo",
                "args" to listOf(right, top)
            )
        )
        currentFrameCommands.add(
            mapOf(
                "property" to "lineTo",
                "args" to listOf(right, bottom)
            )
        )
        currentFrameCommands.add(
            mapOf(
                "property" to "lineTo",
                "args" to listOf(left, bottom)
            )
        )
    }

    private fun setupPaint(paint: Paint) {
        val r = Color.red(paint.color)
        val g = Color.green(paint.color)
        val b = Color.blue(paint.color)
        val a = Color.alpha(paint.color)
        val color = "rgba($r, $g, $b, $a)"

        currentFrameCommands.add(
            mapOf(
                "property" to "fillStyle",
                "args" to listOf(color),
                "setter" to true
            )
        )

        currentFrameCommands.add(
            mapOf(
                "property" to "strokeStyle",
                "args" to listOf(color),
                "setter" to true
            )
        )

        currentFrameCommands.add(
            mapOf(
                "property" to "font",
                "args" to listOf("${paint.textSize}px"),
                "setter" to true
            )
        )

        val textAlign = when (paint.textAlign) {
            Paint.Align.RIGHT -> "right"
            Paint.Align.LEFT -> "left"
            Paint.Align.CENTER -> "center"
        }

        currentFrameCommands.add(
            mapOf(
                "property" to "textAlign",
                "args" to listOf(textAlign),
                "setter" to true
            )
        )

        currentFrameCommands.add(
            mapOf(
                "property" to "lineWidth",
                "args" to listOf(paint.strokeWidth),
                "setter" to true
            )
        )

        currentFrameCommands.add(
            mapOf(
                "property" to "globalAlpha",
                "args" to listOf(paint.alpha / 255.0f),
                "setter" to true
            )
        )

        val colorFilter = paint.colorFilter
        colorFilter?.let {
            if (it is PorterDuffColorFilter) {
                ViewHelper.decodePorterDuffcolorFilter(it)?.let { (color, mode) ->
                    currentFrameCommands.add(
                        mapOf(
                            "property" to "fillStyle",
                            "args" to listOf(color),
                            "setter" to true
                        )
                    )

                    currentFrameCommands.add(
                        mapOf(
                            "property" to "strokeStyle",
                            "args" to listOf(color),
                            "setter" to true
                        )
                    )
                }
            }
        }
    }
}
