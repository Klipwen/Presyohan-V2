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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SubscriptionPaywallActivity : AppCompatActivity() {

    private var selectedTier: String = "pro"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription_paywall)

        selectedTier = intent.getStringExtra("INITIAL_TIER")?.lowercase() ?: "pro"
        if (selectedTier != "vip") selectedTier = "pro"

        val btnClose = findViewById<FrameLayout>(R.id.btnClosePaywall)
        val btnTabPro = findViewById<TextView>(R.id.btnTabPro)
        val btnTabVip = findViewById<TextView>(R.id.btnTabVip)

        val tvMerchantStoresSub = findViewById<TextView>(R.id.tvMerchantStoresSub)
        val tvMerchantItemsSub = findViewById<TextView>(R.id.tvMerchantItemsSub)
        val tvMerchantStaffSub = findViewById<TextView>(R.id.tvMerchantStaffSub)
        val tvMerchantAiSub = findViewById<TextView>(R.id.tvMerchantAiSub)
        val tvMerchantPhotoSub = findViewById<TextView>(R.id.tvMerchantPhotoSub)

        val tvCustomerSukiSub = findViewById<TextView>(R.id.tvCustomerSukiSub)
        val tvCustomerStoresSub = findViewById<TextView>(R.id.tvCustomerStoresSub)

        val btnViewFullBenefits = findViewById<TextView>(R.id.btnViewFullBenefits)
        val layoutFullBenefits = findViewById<LinearLayout>(R.id.layoutFullBenefits)

        val tvPaywallPrice = findViewById<TextView>(R.id.tvPaywallPrice)
        val tvPaywallPriceSubtitle = findViewById<TextView>(R.id.tvPaywallPriceSubtitle)
        val tvCurrentPlanDropdown = findViewById<TextView>(R.id.tvCurrentPlanDropdown)
        val btnStartTrial = findViewById<AppCompatButton>(R.id.btnStartTrial)

        val cardPaywallMerchant = findViewById<LinearLayout>(R.id.cardPaywallMerchant)
        val cardPaywallCustomer = findViewById<LinearLayout>(R.id.cardPaywallCustomer)
        val tvMerchantSectionHeader = findViewById<TextView>(R.id.tvMerchantSectionHeader)
        val tvCustomerSectionHeader = findViewById<TextView>(R.id.tvCustomerSectionHeader)

        val checkViews = listOf(
            R.id.tvCheckMerchant1, R.id.tvCheckMerchant2, R.id.tvCheckMerchant3,
            R.id.tvCheckMerchant4, R.id.tvCheckMerchant5, R.id.tvCheckMerchant6,
            R.id.tvCheckCustomer1, R.id.tvCheckCustomer2, R.id.tvCheckCustomer3
        ).mapNotNull { findViewById<TextView>(it) }

        val titleViews = listOf(
            R.id.tvMerchantStoresTitle, R.id.tvMerchantItemsTitle, R.id.tvMerchantStaffTitle,
            R.id.tvMerchantAiTitle, R.id.tvMerchantPhotoTitle, R.id.tvMerchantSukiTitle,
            R.id.tvCustomerSukiTitle, R.id.tvCustomerStoresTitle, R.id.tvCustomerAiSearchTitle
        ).mapNotNull { findViewById<TextView>(it) }

        val subViews = listOf(
            tvMerchantStoresSub, tvMerchantItemsSub, tvMerchantStaffSub,
            tvMerchantAiSub, tvMerchantPhotoSub, tvCustomerSukiSub, tvCustomerStoresSub
        )

        fun updateUiForTier(tier: String) {
            selectedTier = tier
            val info = SubscriptionManager.getTierInfo(tier)

            tvMerchantStoresSub.text = info.merchantBenefits.getOrNull(0) ?: (if (info.storeLimit > 900) "Unlimited Stores" else "Up to ${info.storeLimit} Stores")
            tvMerchantItemsSub.text = info.merchantBenefits.getOrNull(1) ?: (if (info.itemsPerStoreLimit > 9000) "Unlimited items / store" else "${info.itemsPerStoreLimit} items / store branch")
            tvMerchantStaffSub.text = info.merchantBenefits.getOrNull(2) ?: (if (info.membersPerStoreLimit > 900) "Unlimited staff / store" else "${info.membersPerStoreLimit} staffs / store branch")
            tvMerchantAiSub.text = info.merchantBenefits.getOrNull(3) ?: "${info.aiQuotaDaily} AI parses / day"
            tvMerchantPhotoSub.text = info.merchantBenefits.getOrNull(4) ?: "${info.aiQuotaDaily} photo scans / day"

            tvCustomerSukiSub.text = info.customerBenefits.getOrNull(0) ?: (if (info.sukiLimit > 900) "Unlimited partner stores" else "${info.sukiLimit} partner stores")
            tvCustomerStoresSub.text = info.customerBenefits.getOrNull(1) ?: (if (info.sukiLimit > 900) "Unlimited Presyohan Stores" else "${info.sukiLimit} Presyohan Stores")

            tvPaywallPrice.text = "₱${info.priceValue.toInt()} / month"
            tvPaywallPriceSubtitle.text = if (info.trialDays > 0) "Includes ${info.trialDays}-Day Free Trial" else if (info.hasPrioritySupport || tier == "vip") "Includes Priority VIP Support" else "Cancel Anytime"
            btnStartTrial.text = if (info.trialDays > 0) {
                "START ${info.trialDays}-DAY TRIAL FOR ${info.name.uppercase()}"
            } else {
                "UPGRADE TO ${info.name.uppercase()} (${info.priceText}/MO)"
            }

            val isVip = tier == "vip"
            val accentColor = if (isVip) Color.parseColor("#0D9488") else Color.parseColor("#D97706")
            val subColor = if (isVip) Color.parseColor("#0F766E") else Color.parseColor("#92400E")
            val cardBg = if (isVip) R.drawable.bg_paywall_vip_card else R.drawable.bg_paywall_merchant_card

            cardPaywallMerchant?.setBackgroundResource(cardBg)
            cardPaywallCustomer?.setBackgroundResource(cardBg)
            tvMerchantSectionHeader?.setTextColor(accentColor)
            tvCustomerSectionHeader?.setTextColor(accentColor)
            checkViews.forEach { it.setTextColor(accentColor) }
            titleViews.forEach { it.setTextColor(accentColor) }
            subViews.forEach { it.setTextColor(subColor) }

            if (tier == "pro") {
                btnTabPro.setBackgroundResource(R.drawable.bg_btn_orange_outline)
                btnTabPro.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnTabVip.setBackgroundResource(R.drawable.bg_rounded_grey)
                btnTabVip.setTextColor(Color.parseColor("#475569"))
            } else {
                btnTabVip.setBackgroundResource(R.drawable.bg_button_round_teal_pill)
                btnTabVip.setTextColor(Color.WHITE)

                btnTabPro.setBackgroundResource(R.drawable.bg_rounded_grey)
                btnTabPro.setTextColor(Color.parseColor("#475569"))
            }
        }

        updateUiForTier(selectedTier)

        CoroutineScope(Dispatchers.Main).launch {
            SubscriptionManager.fetchLiveTierConfigs()
            updateUiForTier(selectedTier)
        }

        val cachedTier = SubscriptionManager.getCachedTier(this)
        tvCurrentPlanDropdown.text = "Current plan: ${cachedTier.name} ∨"

        btnTabPro.setOnClickListener {
            updateUiForTier("pro")
        }

        btnTabVip.setOnClickListener {
            updateUiForTier("vip")
        }

        btnViewFullBenefits.text = "View Full Benefits"
        btnViewFullBenefits.setOnClickListener {
            PlanBenefitsDialog.show(this, selectedTier)
        }

        btnClose.setOnClickListener {
            finish()
        }

        btnStartTrial.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val success = SubscriptionManager.updateUserSubscription(this@SubscriptionPaywallActivity, selectedTier)
                if (success) {
                    val tierInfo = SubscriptionManager.getTierInfo(selectedTier)
                    Toast.makeText(this@SubscriptionPaywallActivity, "🎉 Welcome to Presyohan ${tierInfo.name}!", Toast.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK, Intent().putExtra("SUBSCRIBED_TIER", selectedTier))
                    finish()
                } else {
                    Toast.makeText(this@SubscriptionPaywallActivity, "Subscription request failed.", Toast.LENGTH_SHORT).show()
                }
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
