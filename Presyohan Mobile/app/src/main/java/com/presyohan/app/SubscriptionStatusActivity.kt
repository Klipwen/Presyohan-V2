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

import androidx.core.widget.NestedScrollView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.shimmer.ShimmerFrameLayout

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

    private lateinit var shimmerContainer: ShimmerFrameLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var scrollViewContent: NestedScrollView

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

        shimmerContainer = findViewById(R.id.shimmerContainer)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        scrollViewContent = findViewById(R.id.scrollViewContent)

        swipeRefreshLayout.setColorSchemeResources(R.color.presyo_orange)
        swipeRefreshLayout.setOnRefreshListener {
            loadSubscriptionData(showShimmer = false)
        }

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
                    loadSubscriptionData(showShimmer = false)
                }
            }
        }

        btnSelectVip.setOnClickListener {
            if (currentTierInfo.id == "vip") {
                Toast.makeText(this, "You are currently on the VIP Plan.", Toast.LENGTH_SHORT).show()
            } else {
                SubscriptionPaywallDialog.show(this, "vip") { newTier ->
                    loadSubscriptionData(showShimmer = false)
                }
            }
        }

        loadSubscriptionData(showShimmer = true)
    }

    private fun loadSubscriptionData(showShimmer: Boolean = true) {
        val user = SupabaseProvider.client.auth.currentUserOrNull()
        tvBillingEmail.text = "Primary Billing Account: ${user?.email ?: "Guest"}"

        if (showShimmer) {
            shimmerContainer.visibility = View.VISIBLE
            shimmerContainer.startShimmer()
            scrollViewContent.visibility = View.GONE
        }

        lifecycleScope.launch {
            try {
                SubscriptionManager.fetchLiveTierConfigs()
                currentTierInfo = SubscriptionManager.fetchUserTier(this@SubscriptionStatusActivity)
                applyTierToUi(currentTierInfo)
                fetchLiveCapacityUsage(currentTierInfo)
            } catch (e: Exception) {
                e.printStackTrace()
                currentTierInfo = SubscriptionManager.getCachedTier(this@SubscriptionStatusActivity)
                applyTierToUi(currentTierInfo)
            } finally {
                swipeRefreshLayout.isRefreshing = false
                if (showShimmer) {
                    shimmerContainer.stopShimmer()
                    shimmerContainer.visibility = View.GONE
                    scrollViewContent.visibility = View.VISIBLE
                }
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

        // Dynamic Title, Prices, Periods & Descriptions from Admin Config
        findViewById<TextView>(R.id.tvCardTitlePro)?.text = proInfo.name
        findViewById<TextView>(R.id.tvCardPricePro)?.text = proInfo.effectivePriceText
        findViewById<TextView>(R.id.tvCardPeriodPro)?.text = proInfo.periodText
        findViewById<TextView>(R.id.tvCardDescPro)?.text = proInfo.description

        val tvOrigPro = findViewById<TextView>(R.id.tvCardOriginalPricePro)
        if (proInfo.originalPriceText != null) {
            tvOrigPro?.text = proInfo.originalPriceText
            tvOrigPro?.paintFlags = (tvOrigPro?.paintFlags ?: 0) or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            tvOrigPro?.visibility = View.VISIBLE
        } else {
            tvOrigPro?.visibility = View.GONE
        }

        val promoBadgePro = findViewById<TextView>(R.id.tvPromoBadgePro)
        if (!proInfo.effectiveBadgeText.isNullOrBlank()) {
            promoBadgePro?.text = proInfo.effectiveBadgeText
            promoBadgePro?.visibility = View.VISIBLE
        } else {
            promoBadgePro?.visibility = View.GONE
        }

        val tvPromoExpPro = findViewById<TextView>(R.id.tvPromoExpirationPro)
        if (!proInfo.promoExpirationText.isNullOrBlank()) {
            tvPromoExpPro?.text = proInfo.promoExpirationText
            tvPromoExpPro?.visibility = View.VISIBLE
        } else {
            tvPromoExpPro?.visibility = View.GONE
        }

        findViewById<TextView>(R.id.tvCardTitleVip)?.text = vipInfo.name
        findViewById<TextView>(R.id.tvCardPriceVip)?.text = vipInfo.effectivePriceText
        findViewById<TextView>(R.id.tvCardPeriodVip)?.text = vipInfo.periodText
        findViewById<TextView>(R.id.tvCardDescVip)?.text = vipInfo.description

        val tvOrigVip = findViewById<TextView>(R.id.tvCardOriginalPriceVip)
        if (vipInfo.originalPriceText != null) {
            tvOrigVip?.text = vipInfo.originalPriceText
            tvOrigVip?.paintFlags = (tvOrigVip?.paintFlags ?: 0) or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            tvOrigVip?.visibility = View.VISIBLE
        } else {
            tvOrigVip?.visibility = View.GONE
        }

        val promoBadgeVip = findViewById<TextView>(R.id.tvPromoBadgeVip)
        if (!vipInfo.effectiveBadgeText.isNullOrBlank()) {
            promoBadgeVip?.text = vipInfo.effectiveBadgeText
            promoBadgeVip?.visibility = View.VISIBLE
        } else {
            promoBadgeVip?.visibility = View.GONE
        }

        val tvPromoExpVip = findViewById<TextView>(R.id.tvPromoExpirationVip)
        if (!vipInfo.promoExpirationText.isNullOrBlank()) {
            tvPromoExpVip?.text = vipInfo.promoExpirationText
            tvPromoExpVip?.visibility = View.VISIBLE
        } else {
            tvPromoExpVip?.visibility = View.GONE
        }

        findViewById<TextView>(R.id.tvCardTitleFree)?.text = freeInfo.name
        findViewById<TextView>(R.id.tvCardPriceFree)?.text = freeInfo.effectivePriceText
        findViewById<TextView>(R.id.tvCardPeriodFree)?.text = freeInfo.periodText
        findViewById<TextView>(R.id.tvCardDescFree)?.text = freeInfo.description

        val tvOrigFree = findViewById<TextView>(R.id.tvCardOriginalPriceFree)
        if (freeInfo.originalPriceText != null) {
            tvOrigFree?.text = freeInfo.originalPriceText
            tvOrigFree?.paintFlags = (tvOrigFree?.paintFlags ?: 0) or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            tvOrigFree?.visibility = View.VISIBLE
        } else {
            tvOrigFree?.visibility = View.GONE
        }

        val promoBadgeFree = findViewById<TextView>(R.id.tvPromoBadgeFree)
        if (!freeInfo.effectiveBadgeText.isNullOrBlank()) {
            promoBadgeFree?.text = freeInfo.effectiveBadgeText
            promoBadgeFree?.visibility = View.VISIBLE
        } else {
            promoBadgeFree?.visibility = View.GONE
        }

        when (tier.id) {
            "pro" -> {
                imgActiveTierIcon.visibility = View.VISIBLE
                imgActiveTierIcon.setImageResource(R.drawable.icon_pro)
                imgActiveTierIcon.clearColorFilter()
                params?.marginStart = (12 * resources.displayMetrics.density).toInt()
                tvActiveTierName.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnSelectFree.isEnabled = true
                btnSelectFree.text = freeInfo.ctaButtonText ?: "DOWNGRADE TO FREE"
                btnSelectFree.setBackgroundResource(R.drawable.bg_btn_done_outline)
                btnSelectFree.setTextColor(Color.parseColor("#6B7280"))

                btnSelectPro.isEnabled = false
                btnSelectPro.text = "CURRENT ACTIVE PLAN"
                btnSelectPro.setBackgroundResource(R.drawable.bg_btn_orange_outline)
                btnSelectPro.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnSelectVip.isEnabled = true
                btnSelectVip.text = vipInfo.ctaButtonText ?: "UPGRADE TO VIP (${vipInfo.effectivePriceText}/MO)"
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
                btnSelectFree.text = freeInfo.ctaButtonText ?: "SWITCH TO FREE"
                btnSelectFree.setBackgroundResource(R.drawable.bg_btn_done_outline)
                btnSelectFree.setTextColor(Color.parseColor("#6B7280"))

                btnSelectPro.isEnabled = true
                btnSelectPro.text = proInfo.ctaButtonText ?: "SWITCH TO PRO"
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
                btnSelectFree.text = freeInfo.ctaButtonText ?: "CURRENT ACTIVE PLAN"
                btnSelectFree.setBackgroundResource(R.drawable.bg_btn_orange_outline)
                btnSelectFree.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnSelectPro.isEnabled = true
                btnSelectPro.text = proInfo.ctaButtonText ?: "UPGRADE TO PRO (${proInfo.effectivePriceText}/MO)"
                btnSelectPro.setBackgroundResource(R.drawable.bg_solid_button_orange)
                btnSelectPro.setTextColor(Color.WHITE)

                btnSelectVip.isEnabled = true
                btnSelectVip.text = vipInfo.ctaButtonText ?: "UPGRADE TO VIP (${vipInfo.effectivePriceText}/MO)"
                btnSelectVip.setBackgroundResource(R.drawable.bg_button_rect_teal)
                btnSelectVip.setTextColor(Color.WHITE)
            }
        }
        layoutActiveHeader.layoutParams = params

        renderTierFeatures(R.id.featuresPro, "pro", R.drawable.ic_check_circle_orange, Color.parseColor("#1F2937"))
        renderTierFeatures(R.id.featuresVip, "vip", R.drawable.ic_check_circle_teal, Color.parseColor("#064E3B"))
        renderTierFeatures(R.id.featuresFree, "free", R.drawable.ic_check_circle_grey, Color.parseColor("#374151"))
    }

    private fun renderTierFeatures(containerId: Int, tierId: String, checkIconRes: Int, textColor: Int) {
        val container = findViewById<LinearLayout>(containerId) ?: return
        container.removeAllViews()

        val info = SubscriptionManager.getTierInfo(tierId)
        val isVip = tierId == "vip"
        val isPro = tierId == "pro"
        val tierLabelColor = if (isVip) {
            ContextCompat.getColor(this, R.color.presyo_teal)
        } else if (isPro) {
            ContextCompat.getColor(this, R.color.presyo_orange)
        } else {
            Color.parseColor("#4B5563")
        }

        // 1. Merchant Features
        val rawMerchant = mutableListOf<String>()
        if (info.merchantBenefits.isNotEmpty()) {
            rawMerchant.addAll(info.merchantBenefits)
        } else {
            rawMerchant.add(if (info.storeLimit > 900) "Unlimited Stores" else "Up to ${info.storeLimit} Stores")
            rawMerchant.add(if (info.membersPerStoreLimit > 900) "Unlimited staff / store" else "${info.membersPerStoreLimit} staff / store")
            rawMerchant.add(if (info.itemsPerStoreLimit > 9000) "Unlimited items / store" else "${info.itemsPerStoreLimit} items / store")
            rawMerchant.add(if (info.categoriesPerStoreLimit > 900) "Unlimited Categories" else "${info.categoriesPerStoreLimit} categories / store")
            rawMerchant.add("${info.aiQuotaDaily} AI parses / day")
            if (info.allowCustomerPairing) rawMerchant.add("Unlocked Customer Pairing (suki)")
            if (info.allowPdfExport) rawMerchant.add("Convert to PDF")
            if (info.allowExcelExport) rawMerchant.add("Convert to Excel")
            if (info.allowPriceCloning) rawMerchant.add("Price Cloning & Export")
        }

        val merchantList = rawMerchant.mapNotNull { raw ->
            val clean = raw.replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\s•\\-]+"), "").trim()
            if (clean.isNotEmpty()) clean else null
        }

        // 2. Customer & Suki Features
        val rawCustomer = mutableListOf<String>()
        if (info.customerBenefits.isNotEmpty()) {
            rawCustomer.addAll(info.customerBenefits)
        } else {
            rawCustomer.add(if (info.sukiLimit > 900) "Suking Tindahan Partners: Unlimited Partners" else "Suking Tindahan Partners: ${info.sukiLimit} Partners")
            rawCustomer.add(if (info.presyohanStoresLimit > 900) "Presyohan Store Limit: Unlimited Stores" else "Presyohan Store Limit: ${info.presyohanStoresLimit} Stores")
            if (info.internetSearchQuota > 0) {
                rawCustomer.add(if (info.internetSearchQuota > 900) "Internet Search Quota: Unlimited Searches" else "Internet Search Quota: ${info.internetSearchQuota} Searches / day")
            }
        }

        val customerList = rawCustomer.mapNotNull { raw ->
            val clean = raw.replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\s•\\-]+"), "").trim()
            if (clean.isNotEmpty()) clean else null
        }

        val density = resources.displayMetrics.density

        fun addSectionHeader(title: String, isFirst: Boolean) {
            val tvHeader = TextView(this).apply {
                text = title
                setTextColor(tierLabelColor)
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (isFirst) 0 else (12 * density).toInt()
                    bottomMargin = (4 * density).toInt()
                }
                layoutParams = params
            }
            container.addView(tvHeader)
        }

        fun addBenefitRow(featureText: String) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (6 * density).toInt()
                }
                layoutParams = params
            }

            val checkIcon = ImageView(this).apply {
                val iconSize = (17 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                setImageResource(checkIconRes)
            }

            val tv = TextView(this).apply {
                val tvParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (9 * density).toInt()
                }
                layoutParams = tvParams
                text = featureText
                setTextColor(textColor)
                textSize = 13f
                if (featureText.contains("Unlimited", ignoreCase = true) || isVip) {
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
            }

            rowLayout.addView(checkIcon)
            rowLayout.addView(tv)
            container.addView(rowLayout)
        }

        if (merchantList.isNotEmpty()) {
            addSectionHeader("Store & Merchant Features", isFirst = true)
            merchantList.forEach { addBenefitRow(it) }
        }

        if (customerList.isNotEmpty()) {
            addSectionHeader("Customer & Suki Partner Features", isFirst = merchantList.isEmpty())
            customerList.forEach { addBenefitRow(it) }
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
