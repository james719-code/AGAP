package com.travelingsalesman.agap.ui.screens.splash.v2

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.travelingsalesman.agap.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Soft, layered AGAP brand waves for the bottom of the splash screen.
 *
 * The geometry is based on the view width rather than raw pixels, so the waves
 * look consistent on phones with different densities and resolutions.
 */
class WaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val wavePath = Path()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val softPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(14f * density, BlurMaskFilter.Blur.NORMAL)
    }

    private var phase = 0f

    private val animator = ValueAnimator.ofFloat(0f, (2f * PI).toFloat()).apply {
        duration = 6200L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    private val waveLayers = listOf(
        WaveLayer(
            color = ContextCompat.getColor(context, R.color.agap_blue),
            baseYPercent = 0.18f,
            amplitudeDp = 18f,
            cycles = 0.78f,
            phaseOffset = 0.0f,
            speed = 0.48f,
            alpha = 0.16f
        ),
        WaveLayer(
            color = ContextCompat.getColor(context, R.color.agap_green),
            baseYPercent = 0.42f,
            amplitudeDp = 16f,
            cycles = 0.92f,
            phaseOffset = 1.7f,
            speed = 0.56f,
            alpha = 0.17f
        ),
        WaveLayer(
            color = ContextCompat.getColor(context, R.color.agap_orange),
            baseYPercent = 0.63f,
            amplitudeDp = 14f,
            cycles = 0.84f,
            phaseOffset = 3.2f,
            speed = 0.43f,
            alpha = 0.18f
        ),
        WaveLayer(
            color = ContextCompat.getColor(context, R.color.agap_red),
            baseYPercent = 0.82f,
            amplitudeDp = 12f,
            cycles = 0.76f,
            phaseOffset = 4.4f,
            speed = 0.50f,
            alpha = 0.17f
        )
    )

    init {
        // BlurMaskFilter is rendered reliably using a software layer. This view
        // exists for only a short splash animation, so the cost is bounded.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!animator.isStarted) animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        waveLayers.forEach { layer ->
            drawWave(canvas, w, h, layer)
        }
    }

    private fun drawWave(
        canvas: Canvas,
        width: Float,
        height: Float,
        layer: WaveLayer
    ) {
        wavePath.reset()

        val baseY = height * layer.baseYPercent
        val amplitude = layer.amplitudeDp * density
        val twoPi = (2f * PI).toFloat()

        wavePath.moveTo(0f, baseY)

        var x = 0f
        val step = 5f * density
        while (x <= width + step) {
            val normalizedX = x / width
            val angle = normalizedX * layer.cycles * twoPi +
                (phase * layer.speed) +
                layer.phaseOffset

            val y = baseY + amplitude * sin(angle)
            wavePath.lineTo(x, y)
            x += step
        }

        wavePath.lineTo(width, height)
        wavePath.lineTo(0f, height)
        wavePath.close()

        val fillAlpha = (layer.alpha * 255f).toInt().coerceIn(0, 255)
        val blurAlpha = (layer.alpha * 0.65f * 255f).toInt().coerceIn(0, 255)

        softPaint.color = layer.color
        softPaint.alpha = blurAlpha
        canvas.drawPath(wavePath, softPaint)

        fillPaint.color = layer.color
        fillPaint.alpha = fillAlpha
        canvas.drawPath(wavePath, fillPaint)
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    private data class WaveLayer(
        val color: Int,
        val baseYPercent: Float,
        val amplitudeDp: Float,
        val cycles: Float,
        val phaseOffset: Float,
        val speed: Float,
        val alpha: Float
    )
}
