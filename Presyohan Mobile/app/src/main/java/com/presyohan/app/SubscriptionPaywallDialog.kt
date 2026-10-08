package com.presyohan.app

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.presyohan.app.adapter.PaywallCardAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

object SubscriptionPaywallDialog {

    fun show(
        context: Context,
        initialTierId: String = "pro",
        onSubscribed: ((String) -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.activity_subscription_paywall, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.let { window ->
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        val btnClose = view.findViewById<FrameLayout>(R.id.btnClosePaywall)
        val layoutPaywallTabsContainer = view.findViewById<LinearLayout>(R.id.layoutPaywallTabsContainer)
        val btnTabPro = view.findViewById<TextView>(R.id.btnTabPro)
        val btnTabVip = view.findViewById<TextView>(R.id.btnTabVip)
        val viewPagerCards = view.findViewById<ViewPager2>(R.id.viewPagerCards)
        val btnPaywallCta = view.findViewById<AppCompatButton>(R.id.btnPaywallCta)
        val tvPaywallFooterSubtitle = view.findViewById<TextView>(R.id.tvPaywallFooterSubtitle)
        val paywallLoadingOverlay = view.findViewById<View>(R.id.paywallLoadingOverlay)

        var selectedTier = if (SubscriptionConfig.IS_VIP_VISIBLE && initialTierId.lowercase() == "vip") "vip" else "pro"

        val proTier = SubscriptionManager.getTierInfo("pro")
        val vipTier = SubscriptionManager.getTierInfo("vip")
        val initialTierList = if (SubscriptionConfig.IS_VIP_VISIBLE) listOf(proTier, vipTier) else listOf(proTier)

        val adapter = PaywallCardAdapter(initialTierList) { tierId ->
            PlanBenefitsDialog.show(context, tierId)
        }

        viewPagerCards.adapter = adapter
        viewPagerCards.offscreenPageLimit = 1

        if (!SubscriptionConfig.IS_VIP_VISIBLE) {
            layoutPaywallTabsContainer.visibility = View.GONE
            viewPagerCards.isUserInputEnabled = false
        }

        // Custom Scale Transformer: Selected card 1.0x, inactive side card 0.88x with smooth peek
        viewPagerCards.setPageTransformer { page, position ->
            val absPos = abs(position)
            if (absPos >= 1) {
                page.scaleY = 0.88f
                page.scaleX = 0.88f
                page.alpha = 0.75f
            } else {
                val scale = 0.88f + (1 - absPos) * 0.12f
                page.scaleY = scale
                page.scaleX = scale
                page.alpha = 0.75f + (1 - absPos) * 0.25f
            }
        }

        fun updateSelectedState(position: Int) {
            selectedTier = if (position == 0 || !SubscriptionConfig.IS_VIP_VISIBLE) "pro" else "vip"
            val tierInfo = SubscriptionManager.getTierInfo(selectedTier)

            if (position == 0 || !SubscriptionConfig.IS_VIP_VISIBLE) { // PRO Tier Selected
                btnTabPro.setBackgroundResource(R.drawable.bg_paywall_tab_active_orange)
                btnTabPro.setTextColor(ContextCompat.getColor(context, R.color.presyo_orange))

                btnTabVip.setBackgroundResource(0)
                btnTabVip.setTextColor(Color.parseColor("#64748B"))

                btnPaywallCta.backgroundTintList = ContextCompat.getColorStateList(context, R.color.presyo_orange)
            } else { // VIP Tier Selected
                btnTabVip.setBackgroundResource(R.drawable.bg_paywall_tab_active_teal)
                btnTabVip.setTextColor(ContextCompat.getColor(context, R.color.presyo_teal))

                btnTabPro.setBackgroundResource(0)
                btnTabPro.setTextColor(Color.parseColor("#64748B"))

                btnPaywallCta.backgroundTintList = ContextCompat.getColorStateList(context, R.color.presyo_teal)
            }

            // Dynamic CTA Button Text from Admin configuration
            val adminCta = tierInfo.ctaButtonText?.trim()
            btnPaywallCta.text = if (!adminCta.isNullOrBlank()) {
                adminCta
            } else if (tierInfo.trialDays > 0) {
                "START ${tierInfo.trialDays}-DAY FREE TRIAL"
            } else {
                "UPGRADE TO ${tierInfo.name.uppercase()} (${tierInfo.effectivePriceText}/MO)"
            }

            tvPaywallFooterSubtitle.text = "Cancel Anytime"
        }

        val initialPosition = if (selectedTier == "vip" && SubscriptionConfig.IS_VIP_VISIBLE) 1 else 0
        viewPagerCards.setCurrentItem(initialPosition, false)
        updateSelectedState(initialPosition)

        viewPagerCards.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateSelectedState(position)
            }
        })

        btnTabPro.setOnClickListener {
            viewPagerCards.setCurrentItem(0, true)
        }

        btnTabVip.setOnClickListener {
            if (SubscriptionConfig.IS_VIP_VISIBLE) {
                viewPagerCards.setCurrentItem(1, true)
            }
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        btnPaywallCta.setOnClickListener {
            if (SubscriptionConfig.BILLING_MODE == BillingMode.PROMO_CLAIM_FREE) {
                // Show realistic loading state (2.5 seconds)
                paywallLoadingOverlay.findViewById<TextView>(R.id.loadingText)?.text = "Activating PRO Tier..."
                paywallLoadingOverlay.visibility = View.VISIBLE
                btnPaywallCta.isEnabled = false
                btnClose.isEnabled = false

                CoroutineScope(Dispatchers.Main).launch {
                    val startTime = System.currentTimeMillis()
                    val claimResult = SubscriptionManager.claimPromotionalProTier(context)
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed < 2500L) {
                        kotlinx.coroutines.delay(2500L - elapsed)
                    }

                    paywallLoadingOverlay.visibility = View.GONE
                    btnPaywallCta.isEnabled = true
                    btnClose.isEnabled = true

                    if (claimResult.isSuccess) {
                        dialog.dismiss()
                        val activePro = SubscriptionManager.getTierInfo("pro")
                        SubscriptionSuccessDialogHelper.showProClaimSuccessDialog(context, activePro) {
                            onSubscribed?.invoke("pro")
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Unable to activate PRO Tier. Please check your connection and try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                SubscriptionManager.openWebCheckout(context, selectedTier)
                dialog.dismiss()
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                SubscriptionManager.fetchLiveTierConfigs()
                val livePro = SubscriptionManager.getTierInfo("pro")
                val liveVip = SubscriptionManager.getTierInfo("vip")
                val liveList = if (SubscriptionConfig.IS_VIP_VISIBLE) listOf(livePro, liveVip) else listOf(livePro)
                adapter.updateTiers(liveList)
                updateSelectedState(viewPagerCards.currentItem)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        dialog.show()
        return dialog
    }
}
