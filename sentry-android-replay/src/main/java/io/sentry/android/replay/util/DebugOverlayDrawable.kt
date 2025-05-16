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
        private val maskBackgroundColor = Color.argb(32, 255, 20, 20)
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

            val topLeftLabel = "${mask.left}/${mask.top}"
            paint.getTextBounds(topLeftLabel, 0, topLeftLabel.length, tmpRect)
            drawTextWithOutline(
                canvas,
                topLeftLabel,
                mask.left.toFloat(),
                mask.top.toFloat()
            )

            val bottomRightLabel = "${mask.right}/${mask.bottom}"
            paint.getTextBounds(bottomRightLabel, 0, bottomRightLabel.length, tmpRect)
            drawTextWithOutline(
                canvas,
                bottomRightLabel,
                mask.right.toFloat() - tmpRect.width(),
                mask.bottom.toFloat() + tmpRect.height()
            )
        }
    }

    private fun drawTextWithOutline(
        canvas: Canvas,
        bottomRightLabel: String,
        x: Float,
        y: Float
    ) {
        paint.setColor(TEXT_OUTLINE_COLOR)
        paint.style = Paint.Style.STROKE
        canvas.drawText(
            bottomRightLabel,
            x,
            y,
            paint
        )

        paint.setColor(TEXT_COLOR)
        paint.style = Paint.Style.FILL
        canvas.drawText(
            bottomRightLabel,
            x,
            y,
            paint
        )
    }

    override fun setAlpha(alpha: Int) {
        // no-op
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // no-op
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    fun updateMasks(masks: List<Rect>) {
        this.masks = masks
        invalidateSelf()
    }
}
