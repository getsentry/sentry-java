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

    override fun draw(canvas: Canvas) {
        paint.textSize = 32f
        paint.setColor(Color.BLACK)

        paint.strokeWidth = 4f

        for (mask in masks) {
            paint.setColor(Color.argb(128, 255, 20, 20))
            paint.style = Paint.Style.STROKE
            canvas.drawRect(mask, paint)

            paint.style = Paint.Style.FILL
            val label = "${mask.left} ${mask.top}"
            paint.getTextBounds(label, 0, label.length, tmpRect)

            paint.setColor(Color.argb(128, 255, 255, 255))
            canvas.drawRect(
                mask.left.toFloat() + paint.strokeWidth / 2,
                mask.top.toFloat() + paint.strokeWidth / 2,
                mask.left.toFloat() + tmpRect.width() + padding + padding,
                mask.top.toFloat() + tmpRect.height() + padding + padding,
                paint
            )
            paint.setColor(Color.BLACK)
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
