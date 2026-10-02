package com.presyohan.app

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import java.util.WeakHashMap

object AvatarStatusHelper {

    private val runningAnimators = WeakHashMap<View, ValueAnimator>()

    /**
     * Applies tier-based circular border and glow/animation effects:
     * - Free / Default: Grey solid border, no glow.
     * - PRO: Glowing orange border.
     * - VIP: Glowing teal border with smooth continuous breathing glow animation.
     */
    fun applyStatusBorder(container: View?, tierId: String?) {
        if (container == null) return

        // Stop any currently running animation on this container
        stopAnimation(container)

        val sanitizedTier = tierId?.lowercase()?.trim() ?: "free"

        when (sanitizedTier) {
            "vip" -> {
                container.setBackgroundResource(R.drawable.bg_avatar_status_vip)
                container.alpha = 1.0f
                startVipGlowAnimation(container)
            }
            "pro" -> {
                container.setBackgroundResource(R.drawable.bg_avatar_status_pro)
                container.alpha = 1.0f
                container.background?.alpha = 255
            }
            else -> {
                // Free tier / default
                container.setBackgroundResource(R.drawable.bg_avatar_status_free)
                container.alpha = 1.0f
                container.background?.alpha = 255
            }
        }
    }

    /**
     * Convenience method to fetch cached subscription details and apply border.
     */
    fun applyCachedStatusBorder(container: View?, context: Context) {
        if (container == null) return
        val details = SubscriptionManager.getCachedSubscriptionDetails(context)
        val tierToApply = if (details.isExpired) "free" else details.tierId
        applyStatusBorder(container, tierToApply)
    }

    private fun startVipGlowAnimation(container: View) {
        val animator = ValueAnimator.ofFloat(0.40f, 1.0f).apply {
            duration = 1300L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val alphaFactor = va.animatedValue as Float
                container.background?.alpha = (alphaFactor * 255).toInt().coerceIn(0, 255)
                container.invalidate()
            }
        }

        runningAnimators[container] = animator
        animator.start()

        // Clean up when view detaches
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                stopAnimation(v)
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }

    fun stopAnimation(container: View?) {
        if (container == null) return
        runningAnimators[container]?.let {
            it.removeAllUpdateListeners()
            it.cancel()
        }
        runningAnimators.remove(container)
        container.background?.alpha = 255
    }
}
