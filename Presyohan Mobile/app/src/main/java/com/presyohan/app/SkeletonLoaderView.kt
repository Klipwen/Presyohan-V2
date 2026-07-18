package com.presyohan.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class SkeletonLoaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#EAEAEA") // Soft gray color
        style = Paint.Style.FILL
    }

    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Relative widths of the lines for a natural reading pattern
    private val lineWidthFractions = floatArrayOf(0.40f, 0.75f, 0.55f, 0.50f, 0.85f, 0.45f)
    private val density = resources.displayMetrics.density
    private val lineHeight = 24f * density
    private val lineSpacing = 16f * density
    private val stepY = lineHeight + lineSpacing
    private val cornerRadius = 6f * density

    private var scrollYOffset = 0f
    private var shimmerXOffset = 0f

    private var scrollAnimator: ValueAnimator? = null
    private var shimmerAnimator: ValueAnimator? = null

    private var shimmerShader: LinearGradient? = null

    init {
        startAnimations()
    }

    private fun startAnimations() {
        val patternHeight = lineWidthFractions.size * stepY
        
        // Upward scroll animation: from 0 to total pattern height (moves up, slow and smooth, loops)
        scrollAnimator = ValueAnimator.ofFloat(0f, patternHeight).apply {
            duration = 5000 // Slow scroll speed
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                scrollYOffset = animator.animatedValue as Float
                postInvalidateOnAnimation()
            }
        }

        // Shimmer animation: moves from left to right continuously
        shimmerAnimator = ValueAnimator.ofFloat(-1.5f, 2.5f).apply {
            duration = 2000 // Smooth shimmering
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                shimmerXOffset = fraction * width
                postInvalidateOnAnimation()
            }
        }
        
        scrollAnimator?.start()
        shimmerAnimator?.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            val shimmerWidth = w * 0.6f
            // Gradient: Transparent -> Soft White Highlight -> Transparent
            shimmerShader = LinearGradient(
                0f, 0f, shimmerWidth, 0f,
                intArrayOf(Color.TRANSPARENT, Color.parseColor("#4DFFFFFF"), Color.TRANSPARENT),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            shimmerPaint.shader = shimmerShader
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val patternHeight = lineWidthFractions.size * stepY

        // Position the shimmer gradient
        shimmerShader?.let { shader ->
            val matrix = Matrix()
            matrix.setTranslate(shimmerXOffset - (w * 0.3f), 0f)
            shader.setLocalMatrix(matrix)
        }

        for (i in lineWidthFractions.indices) {
            val fraction = lineWidthFractions[i]
            val lineWidth = (w - paddingLeft - paddingRight) * fraction
            val left = paddingLeft.toFloat()
            val right = left + lineWidth

            // Current scrolling position
            var y = i * stepY - scrollYOffset

            // Wrap vertical coordinate so the lines loop seamlessly
            while (y < -stepY) {
                y += patternHeight
            }

            var drawY = y
            while (drawY < h + stepY) {
                val top = drawY
                val bottom = top + lineHeight

                // Draw base gray rounded rectangle
                canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, basePaint)

                // Draw shimmer highlight on top
                canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, shimmerPaint)

                drawY += patternHeight
            }
        }
    }

    fun stopAnimations() {
        scrollAnimator?.cancel()
        shimmerAnimator?.cancel()
    }

    fun resumeAnimations() {
        if (scrollAnimator?.isStarted != true) scrollAnimator?.start()
        if (shimmerAnimator?.isStarted != true) shimmerAnimator?.start()
    }

    override fun onDetachedFromWindow() {
        stopAnimations()
        super.onDetachedFromWindow()
    }
}
