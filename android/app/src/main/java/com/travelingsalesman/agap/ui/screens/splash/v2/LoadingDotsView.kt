package com.travelingsalesman.agap.ui.screens.splash.v2

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import com.travelingsalesman.agap.R

/**
 * Four AGAP brand-colored dots that softly pulse in sequence.
 *
 * The animation is started only while the view is attached so it does not keep
 * running after the splash screen leaves the window.
 */
class LoadingDotsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dots = listOf(
        Dot(ContextCompat.getColor(context, R.color.agap_red)),
        Dot(ContextCompat.getColor(context, R.color.agap_blue)),
        Dot(ContextCompat.getColor(context, R.color.agap_orange)),
        Dot(ContextCompat.getColor(context, R.color.agap_green))
    )

    private val dotRadius = 4.2f * density
    private val dotSpacing = 14.5f * density
    private val animators = mutableListOf<ValueAnimator>()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimations()
    }

    private fun startAnimations() {
        if (animators.isNotEmpty()) return

        dots.forEachIndexed { index, dot ->
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 620L
                startDelay = index * 125L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    dot.alphaPercent = 0.35f + (0.65f * progress)
                    dot.scale = 0.78f + (0.28f * progress)
                    invalidate()
                }
                start()
                animators += this
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val totalSpacing = (dots.size - 1) * dotSpacing
        val startX = centerX - totalSpacing / 2f

        dots.forEachIndexed { index, dot ->
            dotPaint.color = dot.color
            dotPaint.alpha = (dot.alphaPercent * 255f).toInt().coerceIn(0, 255)

            canvas.drawCircle(
                startX + index * dotSpacing,
                centerY,
                dotRadius * dot.scale,
                dotPaint
            )
        }
    }

    override fun onDetachedFromWindow() {
        animators.forEach(ValueAnimator::cancel)
        animators.clear()
        super.onDetachedFromWindow()
    }

    private data class Dot(
        val color: Int,
        var alphaPercent: Float = 0.35f,
        var scale: Float = 0.78f
    )
}
