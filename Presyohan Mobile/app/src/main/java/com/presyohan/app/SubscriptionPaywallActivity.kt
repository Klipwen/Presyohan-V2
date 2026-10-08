package com.presyohan.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.presyohan.app.adapter.PaywallCardAdapter
import kotlinx.coroutines.launch
import kotlin.math.abs

class SubscriptionPaywallActivity : AppCompatActivity() {

    private lateinit var btnClosePaywall: FrameLayout
    private lateinit var layoutPaywallTabsContainer: LinearLayout
    private lateinit var btnTabPro: TextView
    private lateinit var btnTabVip: TextView
    private lateinit var viewPagerCards: ViewPager2
    private lateinit var btnPaywallCta: AppCompatButton
    private lateinit var tvPaywallFooterSubtitle: TextView
    private lateinit var paywallLoadingOverlay: View

    private lateinit var adapter: PaywallCardAdapter
    private var selectedTier: String = "pro"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription_paywall)

        selectedTier = if (SubscriptionConfig.IS_VIP_VISIBLE && intent.getStringExtra("INITIAL_TIER")?.lowercase() == "vip") "vip" else "pro"

        btnClosePaywall = findViewById(R.id.btnClosePaywall)
        layoutPaywallTabsContainer = findViewById(R.id.layoutPaywallTabsContainer)
        btnTabPro = findViewById(R.id.btnTabPro)
        btnTabVip = findViewById(R.id.btnTabVip)
        viewPagerCards = findViewById(R.id.viewPagerCards)
        btnPaywallCta = findViewById(R.id.btnPaywallCta)
        tvPaywallFooterSubtitle = findViewById(R.id.tvPaywallFooterSubtitle)
        paywallLoadingOverlay = findViewById(R.id.paywallLoadingOverlay)

        // Adjust only the close button top margin to safely sit below the phone status bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(btnClosePaywall) { view, insets ->
            val statusBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            val params = view.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            if (params != null && statusBarHeight > 0) {
                params.topMargin = statusBarHeight + (8 * resources.displayMetrics.density).toInt()
                view.layoutParams = params
            }
            insets
        }

        setupCarousel()

        btnClosePaywall.setOnClickListener {
            finish()
        }

        btnTabPro.setOnClickListener {
            viewPagerCards.setCurrentItem(0, true)
        }

        btnTabVip.setOnClickListener {
            if (SubscriptionConfig.IS_VIP_VISIBLE) {
                viewPagerCards.setCurrentItem(1, true)
            }
        }

        btnPaywallCta.setOnClickListener {
            handleSubscriptionPurchase()
        }

        // Fetch live admin tier configs from Supabase and refresh carousel
        lifecycleScope.launch {
            try {
                SubscriptionManager.fetchLiveTierConfigs()
                val proTier = SubscriptionManager.getTierInfo("pro")
                val vipTier = SubscriptionManager.getTierInfo("vip")
                val liveList = if (SubscriptionConfig.IS_VIP_VISIBLE) listOf(proTier, vipTier) else listOf(proTier)
                adapter.updateTiers(liveList)
                updateSelectedState(viewPagerCards.currentItem)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupCarousel() {
        val proTier = SubscriptionManager.getTierInfo("pro")
        val vipTier = SubscriptionManager.getTierInfo("vip")
        val tierList = if (SubscriptionConfig.IS_VIP_VISIBLE) listOf(proTier, vipTier) else listOf(proTier)

        adapter = PaywallCardAdapter(tierList) { tierId ->
            PlanBenefitsDialog.show(this, tierId)
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

        // Set initial selected tab and card position
        val initialPosition = if (selectedTier == "vip" && SubscriptionConfig.IS_VIP_VISIBLE) 1 else 0
        viewPagerCards.setCurrentItem(initialPosition, false)
        updateSelectedState(initialPosition)

        viewPagerCards.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateSelectedState(position)
            }
        })
    }

    private fun updateSelectedState(position: Int) {
        selectedTier = if (position == 0 || !SubscriptionConfig.IS_VIP_VISIBLE) "pro" else "vip"
        val tierInfo = SubscriptionManager.getTierInfo(selectedTier)

        if (position == 0 || !SubscriptionConfig.IS_VIP_VISIBLE) { // PRO Tier Selected
            btnTabPro.setBackgroundResource(R.drawable.bg_paywall_tab_active_orange)
            btnTabPro.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

            btnTabVip.setBackgroundResource(0)
            btnTabVip.setTextColor(Color.parseColor("#64748B"))

            btnPaywallCta.backgroundTintList = ContextCompat.getColorStateList(this, R.color.presyo_orange)
        } else { // VIP Tier Selected
            btnTabVip.setBackgroundResource(R.drawable.bg_paywall_tab_active_teal)
            btnTabVip.setTextColor(ContextCompat.getColor(this, R.color.presyo_teal))

            btnTabPro.setBackgroundResource(0)
            btnTabPro.setTextColor(Color.parseColor("#64748B"))

            btnPaywallCta.backgroundTintList = ContextCompat.getColorStateList(this, R.color.presyo_teal)
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

    private fun handleSubscriptionPurchase() {
        if (SubscriptionConfig.BILLING_MODE == BillingMode.PROMO_CLAIM_FREE) {
            paywallLoadingOverlay.findViewById<TextView>(R.id.loadingText)?.text = "Activating PRO Tier..."
            paywallLoadingOverlay.visibility = View.VISIBLE
            btnPaywallCta.isEnabled = false
            btnClosePaywall.isEnabled = false

            lifecycleScope.launch {
                try {
                    val startTime = System.currentTimeMillis()
                    val claimResult = SubscriptionManager.claimPromotionalProTier(this@SubscriptionPaywallActivity)
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed < 2500L) {
                        kotlinx.coroutines.delay(2500L - elapsed)
                    }

                    paywallLoadingOverlay.visibility = View.GONE
                    btnPaywallCta.isEnabled = true
                    btnClosePaywall.isEnabled = true

                    if (claimResult.isSuccess) {
                        val activePro = SubscriptionManager.getTierInfo("pro")
                        SubscriptionSuccessDialogHelper.showProClaimSuccessDialog(this@SubscriptionPaywallActivity, activePro) {
                            setResult(Activity.RESULT_OK, Intent().putExtra("SUBSCRIBED_TIER", "pro"))
                            finish()
                        }
                    } else {
                        Toast.makeText(
                            this@SubscriptionPaywallActivity,
                            "Unable to activate PRO Tier. Please check your connection and try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) return@launch
                    paywallLoadingOverlay.visibility = View.GONE
                    btnPaywallCta.isEnabled = true
                    btnClosePaywall.isEnabled = true
                    Toast.makeText(this@SubscriptionPaywallActivity, "Unable to process subscription. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            SubscriptionManager.openWebCheckout(this, selectedTier)
            finish()
        }
    }

    companion object {
        fun launch(context: Context, initialTierId: String = "pro") {
            val intent = Intent(context, SubscriptionPaywallActivity::class.java).apply {
                putExtra("INITIAL_TIER", initialTierId)
            }
            context.startActivity(intent)
        }
    }
}
