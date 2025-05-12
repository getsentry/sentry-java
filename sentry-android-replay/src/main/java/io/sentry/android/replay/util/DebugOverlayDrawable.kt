package io.sentry.android.replay.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

internal class DebugOverlayDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val padding = 6f
    private val tmpRect = Rect()
    private var masks: List<Rect> = emptyList()

    companion object {
        private val maskBackgroundColor = Color.argb(64, 255, 20, 20)
        private val maskBorderColor = Color.argb(128, 255, 20, 20)
        private const val TEXT_COLOR = Color.BLACK
        private const val TEXT_OUTLINE_COLOR = Color.WHITE

        private const val STROKE_WIDTH = 6f
        private const val TEXT_SIZE = 32f
    }

    override fun draw(canvas: Canvas) {
        paint.textSize = TEXT_SIZE
        paint.setColor(Color.BLACK)

        paint.strokeWidth = STROKE_WIDTH

        for (mask in masks) {
            paint.setColor(maskBackgroundColor)
            paint.style = Paint.Style.FILL
            canvas.drawRect(mask, paint)

            paint.setColor(maskBorderColor)
            paint.style = Paint.Style.STROKE
            canvas.drawRect(mask, paint)

            val label = "${mask.left} ${mask.top}"
            paint.getTextBounds(label, 0, label.length, tmpRect)
            paint.style = Paint.Style.STROKE
            paint.setColor(TEXT_OUTLINE_COLOR)
            canvas.drawText(
                label,
                mask.left.toFloat() + padding + paint.strokeWidth / 2,
                mask.top.toFloat() + tmpRect.height() - tmpRect.bottom + padding + paint.strokeWidth / 2,
                paint
            )

            paint.style = Paint.Style.FILL
            paint.setColor(TEXT_COLOR)
            canvas.drawText(
                label,
                mask.left.toFloat() + padding + paint.strokeWidth / 2,
                mask.top.toFloat() + tmpRect.height() - tmpRect.bottom + padding + paint.strokeWidth / 2,
                paint
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        // no-op
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // no-op
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun update(masks: List<Rect>) {
        this.masks = masks

        invalidateSelf()
    }
}
