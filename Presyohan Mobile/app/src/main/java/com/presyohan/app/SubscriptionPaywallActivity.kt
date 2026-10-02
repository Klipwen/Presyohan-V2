package com.presyohan.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
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
    private lateinit var btnTabPro: TextView
    private lateinit var btnTabVip: TextView
    private lateinit var viewPagerCards: ViewPager2
    private lateinit var btnPaywallCta: AppCompatButton
    private lateinit var tvPaywallFooterSubtitle: TextView

    private lateinit var adapter: PaywallCardAdapter
    private var selectedTier: String = "pro"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription_paywall)

        selectedTier = intent.getStringExtra("INITIAL_TIER")?.lowercase() ?: "pro"
        if (selectedTier != "vip") selectedTier = "pro"

        btnClosePaywall = findViewById(R.id.btnClosePaywall)
        btnTabPro = findViewById(R.id.btnTabPro)
        btnTabVip = findViewById(R.id.btnTabVip)
        viewPagerCards = findViewById(R.id.viewPagerCards)
        btnPaywallCta = findViewById(R.id.btnPaywallCta)
        tvPaywallFooterSubtitle = findViewById(R.id.tvPaywallFooterSubtitle)

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
            viewPagerCards.setCurrentItem(1, true)
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
                adapter.updateTiers(listOf(proTier, vipTier))
                updateSelectedState(viewPagerCards.currentItem)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupCarousel() {
        val proTier = SubscriptionManager.getTierInfo("pro")
        val vipTier = SubscriptionManager.getTierInfo("vip")
        val tierList = listOf(proTier, vipTier)

        adapter = PaywallCardAdapter(tierList) { tierId ->
            PlanBenefitsDialog.show(this, tierId)
        }

        viewPagerCards.adapter = adapter
        viewPagerCards.offscreenPageLimit = 1

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
        val initialPosition = if (selectedTier == "vip") 1 else 0
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
        selectedTier = if (position == 0) "pro" else "vip"
        val tierInfo = SubscriptionManager.getTierInfo(selectedTier)

        if (position == 0) { // PRO Tier Selected
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
        lifecycleScope.launch {
            try {
                val success = SubscriptionManager.updateUserSubscription(this@SubscriptionPaywallActivity, selectedTier)
                if (success) {
                    val tierInfo = SubscriptionManager.getTierInfo(selectedTier)
                    Toast.makeText(
                        this@SubscriptionPaywallActivity,
                        "Welcome to Presyohan ${tierInfo.name}!",
                        Toast.LENGTH_LONG
                    ).show()
                    setResult(Activity.RESULT_OK, Intent().putExtra("SUBSCRIBED_TIER", selectedTier))
                    finish()
                } else {
                    Toast.makeText(this@SubscriptionPaywallActivity, "Unable to complete subscription. Please try again.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                Toast.makeText(this@SubscriptionPaywallActivity, "Unable to process subscription. Please try again.", Toast.LENGTH_SHORT).show()
            }
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
