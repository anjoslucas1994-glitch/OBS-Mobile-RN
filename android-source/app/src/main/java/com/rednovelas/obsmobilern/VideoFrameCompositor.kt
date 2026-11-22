package com.rednovelas.obsmobilern

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Compositor de quadros de teste para a futura saída codificada.
 *
 * Recebe um Bitmap de vídeo já decodificado e desenha logo/letreiro sobre uma
 * cópia. Não captura ExoPlayer, não inclui áudio e não publica RTMP.
 * Não executar no thread principal durante transmissão.
 */
internal data class LogoLayout(
    val widthPercent: Int = 25,
    val corner: Corner = Corner.TOP_RIGHT
) {
    enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    init { require(widthPercent in 8..35) }
}

internal class VideoFrameCompositor {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 130, 14, 30)
    }

    fun compose(
        frame: Bitmap,
        logo: Bitmap?,
        lowerThird: String,
        ticker: String,
        logoLayout: LogoLayout = LogoLayout()
    ): Bitmap {
        require(!frame.isRecycled) { "Quadro inválido" }
        val output = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val width = frame.width.toFloat()
        val height = frame.height.toFloat()
        canvas.drawBitmap(frame, 0f, 0f, paint)

        if (logo != null && !logo.isRecycled && logo.width > 0 && logo.height > 0) {
            val maxWidth = width * logoLayout.widthPercent / 100f
            val maxHeight = height * 0.22f
            val scale = minOf(maxWidth / logo.width, maxHeight / logo.height)
            val targetWidth = logo.width * scale
            val targetHeight = logo.height * scale
            val isLeft = logoLayout.corner == LogoLayout.Corner.TOP_LEFT ||
                logoLayout.corner == LogoLayout.Corner.BOTTOM_LEFT
            val isBottom = logoLayout.corner == LogoLayout.Corner.BOTTOM_LEFT ||
                logoLayout.corner == LogoLayout.Corner.BOTTOM_RIGHT
            val left = if (isLeft) width * 0.035f else width - targetWidth - width * 0.035f
            // Manter a região inferior das tarjas de texto livre.
            val top = if (isBottom) height * 0.77f - targetHeight else height * 0.045f
            // Uma base discreta dá contraste a logos brancas/transparentes
            // sobre imagens claras. Ela também confirma visualmente a região
            // realmente composta nos quadros codificados, não só na prévia.
            val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(175, 10, 10, 10)
            }
            val pad = width * 0.012f
            canvas.drawRoundRect(
                RectF(left - pad, top - pad, left + targetWidth + pad, top + targetHeight + pad),
                pad, pad, plate
            )
            canvas.drawBitmap(
                logo,
                Rect(0, 0, logo.width, logo.height),
                RectF(left, top, left + targetWidth, top + targetHeight),
                paint
            )
        }

        val textSize = (height * 0.045f).coerceAtLeast(12f)
        textPaint.textSize = textSize
        if (lowerThird.isNotBlank()) {
            val barTop = height * 0.79f
            val barBottom = height * 0.89f
            canvas.drawRect(0f, barTop, width, barBottom, barPaint)
            drawFittedText(canvas, lowerThird, width * 0.035f, barTop + textSize * 1.35f, width * 0.93f)
        }
        if (ticker.isNotBlank()) {
            val barTop = height * 0.90f
            canvas.drawRect(0f, barTop, width, height, barPaint)
            drawFittedText(canvas, ticker, width * 0.035f, barTop + textSize * 1.35f, width * 0.93f)
        }
        return output
    }

    private fun drawFittedText(canvas: Canvas, value: String, x: Float, baseline: Float, maxWidth: Float) {
        val clean = value.replace("\n", " ").trim()
        if (clean.isEmpty()) return
        val count = textPaint.breakText(clean, true, maxWidth, null)
        canvas.drawText(clean.take(count), x, baseline, textPaint)
    }
}
