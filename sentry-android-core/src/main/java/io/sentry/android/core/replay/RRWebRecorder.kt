package io.sentry.android.core.replay

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuffColorFilter
import android.view.MotionEvent
import io.sentry.Breadcrumb
import kotlin.math.roundToInt

class RREvent(
    val timestamp: Long,
    val type: Int,
    val data: Map<String, Any>
)

class RRWebRecorder : Recorder {

    companion object {
        private const val TYPE_DOMCONTENTLOADEDEVENT = 0
        private const val TYPE_LOADEVENT = 1
        private const val TYPE_METAEVENT = 4
        private const val TYPE_FULLSNAPSHOTEVENT = 2
        private const val TYPE_INCREMENTALSNAPSHOTEVENT = 3
        private const val TYPE_BREADCRUMB = 5
    }

    val recording = ArrayList<RREvent>()
    var currentFrame = RREvent(0, 0, emptyMap())
    var currentFrameCommands = ArrayList<Map<String, Any>>()

    var startTimeMs: Long = 0L
    var endTimeMs: Long = 0L

    override fun beginFrame(timestampMs: Long, width: Int, height: Int) {
        if (recording.isEmpty()) {
            startTimeMs = timestampMs

            // DOMContentLoadedEvent
            recording.add(RREvent(timestampMs, TYPE_DOMCONTENTLOADEDEVENT, emptyMap()))

            // LoadEvent
            recording.add(RREvent(timestampMs, TYPE_LOADEVENT, emptyMap()))

            // MetaEvent
            recording.add(
                RREvent(
                    timestampMs,
                    TYPE_METAEVENT,
                    mapOf<String, Any>(
                        "href" to "http://localhost",
                        "width" to width,
                        "height" to height
                    )
                )
            )

            // FullSnapshotEvent
            recording.add(
                RREvent(
                    timestampMs,
                    TYPE_FULLSNAPSHOTEVENT,
                    mapOf<String, Any>(
                        "node" to mapOf(
                            "id" to 1,
                            "type" to 0,
                            "childNodes" to listOf(
                                mapOf(
                                    "type" to 1,
                                    "name" to "html",
                                    "id" to 2,
                                    "childNodes" to emptyList<Any>()
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
                                                    "id" to 7,
                                                    "attributes" to mapOf(
                                                        "id" to "canvas",
                                                        "width" to "$width",
                                                        "height" to "$height"
                                                    ),
                                                    "childNodes" to emptyList<Any>()
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
                )
            )
        }

        currentFrameCommands = ArrayList()
        currentFrameCommands.add(
            mapOf(
                "property" to "clearRect",
                "args" to listOf(0, 0, width, height)
            )
        )
        // IncrementalSnapshotEvent
        currentFrame = RREvent(
            timestampMs,
            TYPE_INCREMENTALSNAPSHOTEVENT,
            mapOf<String, Any>(
                "source" to 9,
                "id" to 7,
                "type" to 0,
                "commands" to currentFrameCommands
            )
        )
        recording.add(currentFrame)
        endTimeMs = timestampMs
    }

    override fun save() {
        currentFrameCommands.add(
            mapOf(
                "property" to "save",
                "args" to emptyList<Any>()
            )
        )
    }

    override fun restore() {
        currentFrameCommands.add(
            mapOf(
                "property" to "restore",
                "args" to emptyList<Any>()
            )
        )
    }

    override fun restoreToCount(currentSaveCount: Int, targetSaveCount: Int) {
        val numRestores = (currentSaveCount - targetSaveCount)
        for (i in 0..numRestores) {
            currentFrameCommands.add(
                mapOf(
                    "property" to "restore",
                    "args" to emptyList<Any>()
                )
            )
        }
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
                "args" to emptyList<Any>()
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
        // TODO how to support RY ?
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
                "property" to "beginPath",
                "args" to emptyList<Any>()
            )
        )
        for (i in points.indices step 3) {
            val command = if (i == 0) {
                "moveTo"
            } else {
                "lineTo"
            }
            currentFrameCommands.add(
                mapOf(
                    "property" to command,
                    "args" to listOf(points[i + 1], points[i + 2])
                )
            )
        }
        draw(paint)
    }

    override fun onTouchEvent(timestampMs: Long, event: MotionEvent) {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_MOVE) {
            val payload = RREvent(
                timestampMs,
                TYPE_INCREMENTALSNAPSHOTEVENT,
                mapOf(
                    "positions" to listOf(
                        mapOf(
                            "x" to event.x,
                            "y" to event.y,
                            "id" to 7,
                            "timeOffset" to 0
                        )
                    )
                )
            )
            recording.add(payload)
        } else if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
            val type = if (action == MotionEvent.ACTION_DOWN) 1 else 2
            val payload = RREvent(
                timestampMs,
                TYPE_INCREMENTALSNAPSHOTEVENT,
                mapOf(
                    "source" to 2,
                    "type" to type,
                    "id" to 7,
                    "x" to event.x,
                    "y" to event.y
                )
            )
            recording.add(payload)
        }
    }

    override fun addBreadcrumb(breadcrumb: Breadcrumb) {
        val message =
            breadcrumb.message ?: breadcrumb.data["view.id"] ?: breadcrumb.data["view.class"]
                ?: breadcrumb.data["view.tag"] ?: breadcrumb.data["screen"] ?: ""

        recording.add(
            RREvent(
                breadcrumb.timestamp.time,
                TYPE_BREADCRUMB,
                mapOf(
                    "tag" to "breadcrumb",
                    "payload" to mapOf(
                        "timestamp" to breadcrumb.timestamp.time / 1000,
                        "type" to "default",
                        "category" to breadcrumb.category,
                        "message" to message,
                        "data" to breadcrumb.data
                    )
                )
            )
        )

        recording.sortBy { chunk ->
            chunk.timestamp
        }
    }

    private fun draw(paint: Paint) {
        if (paint.style == Paint.Style.FILL) {
            currentFrameCommands.add(
                mapOf(
                    "property" to "fill",
                    "args" to emptyList<Any>()
                )
            )
        } else if (paint.style == Paint.Style.STROKE) {
            currentFrameCommands.add(
                mapOf(
                    "property" to "stroke",
                    "args" to emptyList<Any>()
                )
            )
        } else {
            // fill and stroke
            currentFrameCommands.add(
                mapOf(
                    "property" to "fill",
                    "args" to emptyList<Any>()
                )
            )
            currentFrameCommands.add(
                mapOf(
                    "property" to "stroke",
                    "args" to emptyList<Any>()
                )
            )
        }
    }

    private fun setPathToRectF(left: Float, top: Float, right: Float, bottom: Float) {
        currentFrameCommands.add(
            mapOf(
                "property" to "beginPath",
                "args" to emptyList<Any>()
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

        // TODO better font support
        // font: font-style font-variant font-weight font-size/line-height font-family|caption|icon|menu|message-box|small-caption|status-bar|initial|inherit;
        // ${paint.typeface.isBold}
        val fontWeight = when {
            paint.typeface == null -> "normal"
            paint.typeface.isBold -> "bold"
            paint.typeface.isItalic -> "italic"
            else -> "normal"
        }
        currentFrameCommands.add(
            mapOf(
                "property" to "font",
                "args" to listOf("$fontWeight ${paint.textSize.roundToInt()}px sans-serif"),
                "setter" to true
            )
        )

        val textAlign = when (paint.textAlign) {
            Paint.Align.RIGHT -> "right"
            Paint.Align.CENTER -> "center"
            else -> "left"
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
