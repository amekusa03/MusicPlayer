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
    }
    
    private val barSpacing = 4f

    fun updateMagnitudes(fft: ByteArray) {
        if (fft.isEmpty()) return
        
        // FFT data format: [real0, imag0, real1, imag1, ...]
        // We calculate magnitude as sqrt(real^2 + imag^2)
        val n = fft.size / 2
        if (magnitudes.size != n) {
            magnitudes = FloatArray(n)
        }
        
        for (i in 0 until n) {
            val r = fft[2 * i].toFloat()
            val img = fft[2 * i + 1].toFloat()
            magnitudes[i] = Math.sqrt((r * r + img * img).toDouble()).toFloat()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (magnitudes.isEmpty()) return

        val width = width.toFloat()
        val height = height.toFloat()
        val barWidth = (width - (magnitudes.size - 1) * barSpacing) / magnitudes.size

        for (i in magnitudes.indices) {
            val barHeight = (magnitudes[i] / 128f) * height
            val left = i * (barWidth + barSpacing)
            val top = height - barHeight
            val right = left + barWidth
            val bottom = height
            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
}
