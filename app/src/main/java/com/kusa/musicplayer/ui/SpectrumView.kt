package com.kusa.musicplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class SpectrumView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var magnitudes = FloatArray(0)
    private val paint = Paint().apply {
        color = Color.parseColor("#4285F4") // Google Blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val barSpacing = 4f
    private val maxBars = 64

    fun updateMagnitudes(fft: ByteArray) {
        if (fft.isEmpty()) return
        
        // FFT data format: [real0, imag0, real1, imag1, ...]
        val n = Math.min(fft.size / 2, maxBars)
        if (magnitudes.size != n) {
            magnitudes = FloatArray(n)
        }
        
        for (i in 0 until n) {
            val r = fft[2 * i].toInt()
            val img = fft[2 * i + 1].toInt()
            val mag = Math.sqrt((r * r + img * img).toDouble()).toFloat()
            
            // Apply simple smoothing
            magnitudes[i] = magnitudes[i] * 0.5f + mag * 0.5f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (magnitudes.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val barWidth = (w - (magnitudes.size - 1) * barSpacing) / magnitudes.size

        for (i in magnitudes.indices) {
            // Scale bar height. Magnitudes can be large, so we normalize.
            // Adjust the divisor (e.g., 60f) to control sensitivity.
            val barHeight = Math.min(h, (magnitudes[i] / 60f) * h)
            
            val left = i * (barWidth + barSpacing)
            val top = h - barHeight
            val right = left + barWidth
            val bottom = h
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
}
