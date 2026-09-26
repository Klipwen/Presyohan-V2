package com.presyohan.app

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class SubscriptionStatusActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var tvActiveTierName: TextView
    private lateinit var tvBillingEmail: TextView
    private lateinit var tvActiveTierBadge: TextView
    private lateinit var imgActiveTierIcon: ImageView

    private lateinit var tvStatStoresVal: TextView
    private lateinit var progressStores: ProgressBar
    private lateinit var tvStatItemsVal: TextView
    private lateinit var progressItems: ProgressBar
    private lateinit var tvStatMembersVal: TextView
    private lateinit var progressMembers: ProgressBar
    private lateinit var tvStatAiVal: TextView
    private lateinit var progressAi: ProgressBar

    private lateinit var btnSelectFree: AppCompatButton
    private lateinit var btnSelectPro: AppCompatButton
    private lateinit var btnSelectVip: AppCompatButton
    private lateinit var loadingOverlay: View

    private var currentTierInfo: SubscriptionTierInfo = SubscriptionManager.TIER_FREE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription_status)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        // Initialize Views
        btnBack = findViewById(R.id.btnBack)
        tvActiveTierName = findViewById(R.id.tvActiveTierName)
        tvBillingEmail = findViewById(R.id.tvBillingEmail)
        tvActiveTierBadge = findViewById(R.id.tvActiveTierBadge)
        imgActiveTierIcon = findViewById(R.id.imgActiveTierIcon)

        tvStatStoresVal = findViewById(R.id.tvStatStoresVal)
        progressStores = findViewById(R.id.progressStores)
        tvStatItemsVal = findViewById(R.id.tvStatItemsVal)
        progressItems = findViewById(R.id.progressItems)
        tvStatMembersVal = findViewById(R.id.tvStatMembersVal)
        progressMembers = findViewById(R.id.progressMembers)
        tvStatAiVal = findViewById(R.id.tvStatAiVal)
        progressAi = findViewById(R.id.progressAi)

        btnSelectFree = findViewById(R.id.btnSelectFree)
        btnSelectPro = findViewById(R.id.btnSelectPro)
        btnSelectVip = findViewById(R.id.btnSelectVip)

        btnBack.setOnClickListener {
            finish()
        }

        btnSelectFree.setOnClickListener {
            if (currentTierInfo.id == "free") {
                Toast.makeText(this, "You are currently on the Free Plan.", Toast.LENGTH_SHORT).show()
            } else {
                showDowngradeDialog("free")
            }
        }

        btnSelectPro.setOnClickListener {
            if (currentTierInfo.id == "pro") {
                Toast.makeText(this, "You are currently on the PRO Plan.", Toast.LENGTH_SHORT).show()
            } else {
                SubscriptionPaywallDialog.show(this, "pro") { newTier ->
                    loadSubscriptionData()
                }
            }
        }

        btnSelectVip.setOnClickListener {
            if (currentTierInfo.id == "vip") {
                Toast.makeText(this, "You are currently on the VIP Plan.", Toast.LENGTH_SHORT).show()
            } else {
                SubscriptionPaywallDialog.show(this, "vip") { newTier ->
                    loadSubscriptionData()
                }
            }
        }

        loadSubscriptionData()
    }

    private fun loadSubscriptionData() {
        val user = SupabaseProvider.client.auth.currentUserOrNull()
        tvBillingEmail.text = "Primary Billing Account: ${user?.email ?: "Guest"}"

        // Set fast cached tier first
        currentTierInfo = SubscriptionManager.getCachedTier(this)
        applyTierToUi(currentTierInfo)

        lifecycleScope.launch {
            LoadingOverlayHelper.show(loadingOverlay)
            try {
                SubscriptionManager.fetchLiveTierConfigs()
                currentTierInfo = SubscriptionManager.fetchUserTier(this@SubscriptionStatusActivity)
                applyTierToUi(currentTierInfo)
                fetchLiveCapacityUsage(currentTierInfo)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun applyTierToUi(tier: SubscriptionTierInfo) {
        tvActiveTierName.text = tier.name

        val layoutActiveHeader = findViewById<View>(R.id.layoutActiveHeader)
        val params = layoutActiveHeader.layoutParams as? android.widget.LinearLayout.LayoutParams

        val freeInfo = SubscriptionManager.getTierInfo("free")
        val proInfo = SubscriptionManager.getTierInfo("pro")
        val vipInfo = SubscriptionManager.getTierInfo("vip")

        findViewById<TextView>(R.id.tvCardPriceFree)?.text = freeInfo.priceText
        findViewById<TextView>(R.id.tvCardPricePro)?.text = proInfo.priceText
        findViewById<TextView>(R.id.tvCardPriceVip)?.text = vipInfo.priceText

        when (tier.id) {
            "pro" -> {
                imgActiveTierIcon.visibility = View.VISIBLE
                imgActiveTierIcon.setImageResource(R.drawable.icon_pro)
                imgActiveTierIcon.clearColorFilter()
                params?.marginStart = (12 * resources.displayMetrics.density).toInt()
                tvActiveTierName.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnSelectFree.isEnabled = true
                btnSelectFree.text = "DOWNGRADE TO FREE"
                btnSelectFree.setBackgroundResource(R.drawable.bg_btn_done_outline)
                btnSelectFree.setTextColor(Color.parseColor("#6B7280"))

                btnSelectPro.isEnabled = false
                btnSelectPro.text = "CURRENT ACTIVE PLAN"
                btnSelectPro.setBackgroundResource(R.drawable.bg_btn_orange_outline)
                btnSelectPro.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnSelectVip.isEnabled = true
                btnSelectVip.text = "UPGRADE TO VIP (${vipInfo.priceText}/MO)"
                btnSelectVip.setBackgroundResource(R.drawable.bg_button_rect_teal)
                btnSelectVip.setTextColor(Color.WHITE)
            }
            "vip" -> {
                imgActiveTierIcon.visibility = View.VISIBLE
                imgActiveTierIcon.setImageResource(R.drawable.icon_vip)
                imgActiveTierIcon.clearColorFilter()
                params?.marginStart = (12 * resources.displayMetrics.density).toInt()
                tvActiveTierName.setTextColor(ContextCompat.getColor(this, R.color.presyo_teal))

                btnSelectFree.isEnabled = true
                btnSelectFree.text = "SWITCH TO FREE"
                btnSelectFree.setBackgroundResource(R.drawable.bg_btn_done_outline)
                btnSelectFree.setTextColor(Color.parseColor("#6B7280"))

                btnSelectPro.isEnabled = true
                btnSelectPro.text = "SWITCH TO PRO"
                btnSelectPro.setBackgroundResource(R.drawable.bg_btn_orange_outline)
                btnSelectPro.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnSelectVip.isEnabled = false
                btnSelectVip.text = "CURRENT ACTIVE PLAN"
                btnSelectVip.setBackgroundResource(R.drawable.bg_button_rect_outline_teal)
                btnSelectVip.setTextColor(ContextCompat.getColor(this, R.color.presyo_teal))
            }
            else -> { // Free
                imgActiveTierIcon.visibility = View.GONE
                params?.marginStart = 0
                tvActiveTierName.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnSelectFree.isEnabled = false
                btnSelectFree.text = "CURRENT ACTIVE PLAN"
                btnSelectFree.setBackgroundResource(R.drawable.bg_btn_orange_outline)
                btnSelectFree.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnSelectPro.isEnabled = true
                btnSelectPro.text = "UPGRADE TO PRO (${proInfo.priceText}/MO)"
                btnSelectPro.setBackgroundResource(R.drawable.bg_solid_button_orange)
                btnSelectPro.setTextColor(Color.WHITE)

                btnSelectVip.isEnabled = true
                btnSelectVip.text = "UPGRADE TO VIP (${vipInfo.priceText}/MO)"
                btnSelectVip.setBackgroundResource(R.drawable.bg_button_rect_teal)
                btnSelectVip.setTextColor(Color.WHITE)
            }
        }
        layoutActiveHeader.layoutParams = params

        renderTierFeatures(R.id.featuresFree, "free", "•", Color.parseColor("#374151"))
        renderTierFeatures(R.id.featuresPro, "pro", "✓", Color.parseColor("#1F2937"))
        renderTierFeatures(R.id.featuresVip, "vip", "★", Color.parseColor("#064E3B"))
    }

    private fun renderTierFeatures(containerId: Int, tierId: String, symbol: String, textColor: Int) {
        val container = findViewById<LinearLayout>(containerId) ?: return
        container.removeAllViews()

        val info = SubscriptionManager.getTierInfo(tierId)
        val benefitsList = mutableListOf<String>()

        if (info.merchantBenefits.isNotEmpty()) {
            benefitsList.addAll(info.merchantBenefits)
        } else {
            benefitsList.add(if (info.storeLimit > 900) "Unlimited Stores" else "Up to ${info.storeLimit} Stores")
            benefitsList.add(if (info.membersPerStoreLimit > 900) "Unlimited staff / store" else "${info.membersPerStoreLimit} staff / store")
            benefitsList.add(if (info.itemsPerStoreLimit > 9000) "Unlimited items / store" else "${info.itemsPerStoreLimit} items / store")
            benefitsList.add("${info.aiQuotaDaily} AI parses / day")
            if (info.allowPriceCloning) benefitsList.add("Store Price Cloning")
            if (info.allowExcelExport) benefitsList.add("Excel & PDF Export")
            if (info.sukiLimit > 0) benefitsList.add(if (info.sukiLimit > 900) "Unlimited Suki Partners" else "${info.sukiLimit} Suki Partners")
        }

        val density = resources.displayMetrics.density
        benefitsList.forEachIndexed { idx, featureText ->
            val tv = TextView(this)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (idx > 0) params.topMargin = (6 * density).toInt()
            tv.layoutParams = params
            tv.text = "$symbol $featureText"
            tv.setTextColor(textColor)
            tv.textSize = 13f
            if (symbol == "★" || featureText.contains("Unlimited", ignoreCase = true)) {
                tv.typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            container.addView(tv)
        }
    }

    private suspend fun fetchLiveCapacityUsage(tier: SubscriptionTierInfo) {
        val uid = SupabaseAuthService.getCurrentUserId() ?: return
        try {
            // Count owned stores
            val ownedStoresCount = try {
                val stores = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("owner_id", uid) }
                }.decodeList<kotlinx.serialization.json.JsonObject>()
                stores.size
            } catch (e: Exception) {
                1
            }

            val storeLimitStr = SubscriptionManager.formatLimitText(tier.storeLimit)
            tvStatStoresVal.text = "$ownedStoresCount / $storeLimitStr"
            if (tier.storeLimit != Int.MAX_VALUE) {
                val pct = ((ownedStoresCount.toFloat() / tier.storeLimit) * 100).toInt().coerceIn(0, 100)
                progressStores.progress = pct
            } else {
                progressStores.progress = 10
            }

            val itemsLimitStr = SubscriptionManager.formatLimitText(tier.itemsPerStoreLimit)
            tvStatItemsVal.text = "0 / $itemsLimitStr"
            progressItems.progress = 0

            val membersLimitStr = SubscriptionManager.formatLimitText(tier.membersPerStoreLimit)
            tvStatMembersVal.text = "1 / $membersLimitStr"
            if (tier.membersPerStoreLimit != Int.MAX_VALUE) {
                val pct = ((1f / tier.membersPerStoreLimit) * 100).toInt().coerceIn(0, 100)
                progressMembers.progress = pct
            } else {
                progressMembers.progress = 10
            }

            tvStatAiVal.text = "${tier.aiQuotaDaily} / ${tier.aiQuotaDaily} remaining"
            progressAi.progress = 100
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showUpgradeDialog(targetTier: SubscriptionTierInfo) {
        val message = "Upgrade your Presyohan account to ${targetTier.name} for ${targetTier.priceText}${targetTier.periodText}!\n\n" +
                "Features Unlocked:\n" +
                "• ${SubscriptionManager.formatLimitText(targetTier.storeLimit)} Owned Stores & Branches\n" +
                "• ${SubscriptionManager.formatLimitText(targetTier.membersPerStoreLimit)} Staff Members per Store\n" +
                "• ${SubscriptionManager.formatLimitText(targetTier.itemsPerStoreLimit)} Items per Store\n" +
                "• ${targetTier.aiQuotaDaily} Daily AI Scans & Invoice Imports\n" +
                (if (targetTier.allowPriceCloning) "• Store Price Cloning & Catalog Imports\n" else "") +
                (if (targetTier.allowExcelExport) "• Convert Pricelists to Excel & PDF\n" else "") +
                "\nWould you like to activate this subscription plan now?"

        ReusableDialogHelper.showCustomDialog(
            context = this,
            title = "Confirm Subscription Upgrade",
            message = message,
            positiveButtonText = "Subscribe Now (${targetTier.priceText})",
            positiveAction = {
                processSubscriptionChange(targetTier.id)
            },
            negativeButtonText = "Cancel"
        )
    }

    private fun showDowngradeDialog(targetTierId: String) {
        ReusableDialogHelper.showCustomDialog(
            context = this,
            title = "Downgrade Subscription",
            message = "Are you sure you want to switch to the Free Tier? Note: Your data will never be deleted, but adding new stores or items beyond Free limits will be paused.",
            positiveButtonText = "Confirm Switch",
            positiveAction = {
                processSubscriptionChange(targetTierId)
            },
            negativeButtonText = "Cancel"
        )
    }

    private fun processSubscriptionChange(newTierId: String) {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val success = SubscriptionManager.updateUserSubscription(this@SubscriptionStatusActivity, newTierId)
                if (success) {
                    val updatedTier = SubscriptionManager.getTierInfo(newTierId)
                    currentTierInfo = updatedTier
                    applyTierToUi(updatedTier)
                    fetchLiveCapacityUsage(updatedTier)
                    Toast.makeText(
                        this@SubscriptionStatusActivity,
                        "🎉 Presyohan plan updated to ${updatedTier.name}!",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this@SubscriptionStatusActivity, "Failed to update subscription.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SubscriptionStatusActivity, "Error updating subscription: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }
}
