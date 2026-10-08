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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

import androidx.core.widget.NestedScrollView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.shimmer.ShimmerFrameLayout

class SubscriptionStatusActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var tvActiveTierName: TextView
    private lateinit var tvBillingEmail: TextView
    private lateinit var tvActiveTierBadge: TextView
    private lateinit var imgActiveTierIcon: ImageView

    // Status Pill & Expired Alert
    private lateinit var layoutActiveStatusPill: LinearLayout
    private lateinit var imgActiveStatusIcon: ImageView
    private lateinit var tvActiveSubscriptionStatus: TextView
    private lateinit var layoutExpiredNoticeBox: LinearLayout
    private lateinit var tvExpiredNoticeMessage: TextView

    // Active Card Action Buttons
    private lateinit var layoutActiveCardActions: LinearLayout
    private lateinit var btnActiveUpgradeAction: AppCompatButton
    private lateinit var btnActiveCancelAction: AppCompatButton

    // Capacity Stats
    private lateinit var tvStatStoresVal: TextView
    private lateinit var progressStores: ProgressBar
    private lateinit var tvStatItemsVal: TextView
    private lateinit var progressItems: ProgressBar
    private lateinit var tvStatMembersVal: TextView
    private lateinit var progressMembers: ProgressBar
    private lateinit var tvStatAiVal: TextView
    private lateinit var progressAi: ProgressBar
    private lateinit var tvStatPhotoVal: TextView
    private lateinit var progressPhoto: ProgressBar
    private lateinit var tvStatSearchVal: TextView
    private lateinit var progressSearch: ProgressBar

    // Plan Selection Buttons
    private lateinit var btnSelectFree: AppCompatButton
    private lateinit var btnSelectPro: AppCompatButton
    private lateinit var btnSelectVip: AppCompatButton
    private lateinit var loadingOverlay: View

    private lateinit var shimmerContainer: ShimmerFrameLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var scrollViewContent: NestedScrollView

    private var currentDetails: UserSubscriptionDetails = SubscriptionManager.calculateSubscriptionDetails("free", null)

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

        layoutActiveStatusPill = findViewById(R.id.layoutActiveStatusPill)
        imgActiveStatusIcon = findViewById(R.id.imgActiveStatusIcon)
        tvActiveSubscriptionStatus = findViewById(R.id.tvActiveSubscriptionStatus)
        layoutExpiredNoticeBox = findViewById(R.id.layoutExpiredNoticeBox)
        tvExpiredNoticeMessage = findViewById(R.id.tvExpiredNoticeMessage)

        layoutActiveCardActions = findViewById(R.id.layoutActiveCardActions)
        btnActiveUpgradeAction = findViewById(R.id.btnActiveUpgradeAction)
        btnActiveCancelAction = findViewById(R.id.btnActiveCancelAction)

        tvStatStoresVal = findViewById(R.id.tvStatStoresVal)
        progressStores = findViewById(R.id.progressStores)
        tvStatItemsVal = findViewById(R.id.tvStatItemsVal)
        progressItems = findViewById(R.id.progressItems)
        tvStatMembersVal = findViewById(R.id.tvStatMembersVal)
        progressMembers = findViewById(R.id.progressMembers)
        tvStatAiVal = findViewById(R.id.tvStatAiVal)
        progressAi = findViewById(R.id.progressAi)
        tvStatPhotoVal = findViewById(R.id.tvStatPhotoVal)
        progressPhoto = findViewById(R.id.progressPhoto)
        tvStatSearchVal = findViewById(R.id.tvStatSearchVal)
        progressSearch = findViewById(R.id.progressSearch)

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
            if (currentDetails.tierId == "free") {
                Toast.makeText(this, "You are currently on the Free Plan.", Toast.LENGTH_SHORT).show()
            } else {
                showCancelSubscriptionDialog(currentDetails)
            }
        }

        btnSelectPro.setOnClickListener {
            if (currentDetails.tierId == "pro") {
                Toast.makeText(this, "You are currently on the PRO Plan.", Toast.LENGTH_SHORT).show()
            } else {
                SubscriptionPaywallDialog.show(this, "pro") {
                    loadSubscriptionData(showShimmer = false)
                }
            }
        }

        btnSelectVip.setOnClickListener {
            if (currentDetails.tierId == "vip") {
                Toast.makeText(this, "You are currently on the VIP Plan.", Toast.LENGTH_SHORT).show()
            } else {
                SubscriptionPaywallDialog.show(this, "vip") {
                    loadSubscriptionData(showShimmer = false)
                }
            }
        }

        loadSubscriptionData(showShimmer = true)
    }

    override fun onResume() {
        super.onResume()
        loadSubscriptionData(showShimmer = false)
    }

    private fun loadSubscriptionData(showShimmer: Boolean = true) {
        val user = SupabaseProvider.client.auth.currentUserOrNull()
        tvBillingEmail.text = "Account: ${user?.email ?: "Guest"}"

        if (showShimmer) {
            shimmerContainer.visibility = View.VISIBLE
            shimmerContainer.startShimmer()
            scrollViewContent.visibility = View.GONE
        }

        lifecycleScope.launch {
            try {
                SubscriptionManager.fetchLiveTierConfigs()
                currentDetails = SubscriptionManager.fetchUserSubscriptionDetails(this@SubscriptionStatusActivity)
                applyDetailsToUi(currentDetails)
                fetchLiveCapacityUsage(currentDetails.tierInfo)
            } catch (e: Exception) {
                e.printStackTrace()
                currentDetails = SubscriptionManager.getCachedSubscriptionDetails(this@SubscriptionStatusActivity)
                applyDetailsToUi(currentDetails)
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

    private fun applyDetailsToUi(details: UserSubscriptionDetails) {
        val tier = details.tierInfo
        tvActiveTierName.text = tier.name

        val layoutActiveHeader = findViewById<View>(R.id.layoutActiveHeader)
        val params = layoutActiveHeader.layoutParams as? android.widget.LinearLayout.LayoutParams

        val freeInfo = SubscriptionManager.getTierInfo("free")
        val proInfo = SubscriptionManager.getTierInfo("pro")
        val vipInfo = SubscriptionManager.getTierInfo("vip")

        // 1. Dynamic Plan Cards Config (Name, Price, Period, Badges, Promo text)
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

        // 2. Set Active Subscription Header & Status Pill
        tvActiveSubscriptionStatus.text = details.statusText

        when (details.statusType) {
            SubscriptionStatusType.TIME_BOUND_TRIAL -> {
                layoutActiveStatusPill.setBackgroundResource(R.drawable.bg_subscription_status_pill_orange)
                imgActiveStatusIcon.visibility = View.VISIBLE
                imgActiveStatusIcon.setImageResource(R.drawable.ic_calendar_clock)
                imgActiveStatusIcon.setColorFilter(ContextCompat.getColor(this, R.color.presyo_orange))
                tvActiveSubscriptionStatus.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))
                layoutExpiredNoticeBox.visibility = View.GONE
            }
            SubscriptionStatusType.AUTO_RENEW -> {
                layoutActiveStatusPill.setBackgroundResource(R.drawable.bg_subscription_status_pill_teal)
                imgActiveStatusIcon.visibility = View.GONE
                tvActiveSubscriptionStatus.setTextColor(Color.parseColor("#0E7490"))
                layoutExpiredNoticeBox.visibility = View.GONE
            }
            SubscriptionStatusType.LIFETIME -> {
                layoutActiveStatusPill.setBackgroundResource(R.drawable.bg_subscription_status_pill_orange)
                imgActiveStatusIcon.visibility = View.GONE
                tvActiveSubscriptionStatus.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))
                layoutExpiredNoticeBox.visibility = View.GONE
            }
            SubscriptionStatusType.EXPIRED -> {
                layoutActiveStatusPill.setBackgroundResource(R.drawable.bg_subscription_status_pill_grey)
                imgActiveStatusIcon.visibility = View.GONE
                tvActiveSubscriptionStatus.setTextColor(Color.parseColor("#475569"))

                val expiredName = details.expiredTierName ?: "PRO"
                tvExpiredNoticeMessage.text = "Your $expiredName trial has expired. Renew to continue with higher limits."
                layoutExpiredNoticeBox.visibility = View.VISIBLE
            }
            SubscriptionStatusType.FREE_TIER -> {
                layoutActiveStatusPill.setBackgroundResource(R.drawable.bg_subscription_status_pill_grey)
                imgActiveStatusIcon.visibility = View.GONE
                tvActiveSubscriptionStatus.setTextColor(Color.parseColor("#475569"))
                layoutExpiredNoticeBox.visibility = View.GONE
            }
        }

        // VIP Card Visibility Control
        findViewById<LinearLayout>(R.id.cardTierVip)?.visibility = if (SubscriptionConfig.IS_VIP_VISIBLE) View.VISIBLE else View.GONE

        // 3. Quick Action Buttons on Active Card
        when (details.tierId) {
            "pro" -> {
                imgActiveTierIcon.visibility = View.VISIBLE
                imgActiveTierIcon.setImageResource(R.drawable.icon_pro)
                imgActiveTierIcon.clearColorFilter()
                params?.marginStart = (12 * resources.displayMetrics.density).toInt()
                tvActiveTierName.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnActiveUpgradeAction.visibility = if (SubscriptionConfig.IS_VIP_VISIBLE) View.VISIBLE else View.GONE
                btnActiveUpgradeAction.text = "UPGRADE TO VIP"
                btnActiveUpgradeAction.setBackgroundResource(R.drawable.bg_button_rect_teal)
                btnActiveUpgradeAction.setTextColor(Color.WHITE)
                btnActiveUpgradeAction.setOnClickListener {
                    SubscriptionPaywallDialog.show(this, "vip") {
                        loadSubscriptionData(showShimmer = false)
                    }
                }

                btnActiveCancelAction.visibility = View.VISIBLE
                btnActiveCancelAction.text = "CANCEL SUBSCRIPTION"
                btnActiveCancelAction.setBackgroundResource(R.drawable.bg_button_rect_outline_orange)
                btnActiveCancelAction.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))
                btnActiveCancelAction.setOnClickListener {
                    showCancelSubscriptionDialog(details)
                }

                btnSelectFree.isEnabled = true
                btnSelectFree.text = "SWITCH TO FREE"
                btnSelectFree.setBackgroundResource(R.drawable.bg_button_rect_outline_orange)
                btnSelectFree.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnSelectPro.isEnabled = false
                btnSelectPro.text = "CURRENT PLAN"
                btnSelectPro.setBackgroundResource(R.drawable.bg_button_rect_outline_orange)
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

                btnActiveUpgradeAction.visibility = View.GONE
                btnActiveCancelAction.visibility = View.VISIBLE
                btnActiveCancelAction.text = "CANCEL SUBSCRIPTION"
                btnActiveCancelAction.setBackgroundResource(R.drawable.bg_button_rect_outline_orange)
                btnActiveCancelAction.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))
                btnActiveCancelAction.setOnClickListener {
                    showCancelSubscriptionDialog(details)
                }

                btnSelectFree.isEnabled = true
                btnSelectFree.text = "SWITCH TO FREE"
                btnSelectFree.setBackgroundResource(R.drawable.bg_button_rect_outline_orange)
                btnSelectFree.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnSelectPro.isEnabled = true
                btnSelectPro.text = proInfo.ctaButtonText ?: "SWITCH TO PRO"
                btnSelectPro.setBackgroundResource(R.drawable.bg_button_rect_outline_orange)
                btnSelectPro.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnSelectVip.isEnabled = false
                btnSelectVip.text = "CURRENT PLAN"
                btnSelectVip.setBackgroundResource(R.drawable.bg_button_rect_outline_teal)
                btnSelectVip.setTextColor(ContextCompat.getColor(this, R.color.presyo_teal))
            }
            else -> { // Free
                imgActiveTierIcon.visibility = View.GONE
                params?.marginStart = 0
                tvActiveTierName.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))

                btnActiveUpgradeAction.visibility = View.VISIBLE
                btnActiveUpgradeAction.text = if (details.isExpired) "RENEW PLAN" else "UPGRADE PLAN"
                btnActiveUpgradeAction.setBackgroundResource(R.drawable.bg_solid_button_orange)
                btnActiveUpgradeAction.setTextColor(Color.WHITE)
                btnActiveUpgradeAction.setOnClickListener {
                    SubscriptionPaywallDialog.show(this, "pro") {
                        loadSubscriptionData(showShimmer = false)
                    }
                }
                btnActiveCancelAction.visibility = View.GONE

                btnSelectFree.isEnabled = false
                btnSelectFree.text = freeInfo.ctaButtonText ?: "CURRENT PLAN"
                btnSelectFree.setBackgroundResource(R.drawable.bg_button_rect_outline_orange)
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

        renderTierFeatures(R.id.featuresPro, "pro", R.drawable.ic_check_circle_orange, Color.parseColor("#374151"))
        if (SubscriptionConfig.IS_VIP_VISIBLE) {
            renderTierFeatures(R.id.featuresVip, "vip", R.drawable.ic_check_circle_teal, Color.parseColor("#1E293B"))
        }
        renderTierFeatures(R.id.featuresFree, "free", R.drawable.ic_check_circle_grey, Color.parseColor("#4B5563"))
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
            var ownedStoresCount = 0
            var maxItemsInAStore = 0
            var maxStaffInAStore = 1

            try {
                val userStores = try {
                    SupabaseProvider.client.postgrest.rpc("get_user_stores").decodeList<JsonObject>()
                } catch (_: Exception) {
                    emptyList()
                }

                val ownedUserStores = userStores.filter { 
                    it["role"]?.jsonPrimitive?.contentOrNull?.equals("owner", ignoreCase = true) == true 
                }

                val storeIds = if (ownedUserStores.isNotEmpty()) {
                    ownedStoresCount = ownedUserStores.size
                    ownedUserStores.mapNotNull { it["store_id"]?.jsonPrimitive?.contentOrNull }
                } else {
                    val stores = SupabaseProvider.client.postgrest["stores"].select {
                        filter { eq("owner_id", uid) }
                    }.decodeList<JsonObject>()
                    ownedStoresCount = stores.size
                    stores.mapNotNull { it["id"]?.jsonPrimitive?.contentOrNull }
                }

                for (sId in storeIds) {
                    try {
                        val products = SupabaseProvider.client.postgrest["products"].select {
                            filter { eq("store_id", sId) }
                        }.decodeList<JsonObject>()
                        if (products.size > maxItemsInAStore) {
                            maxItemsInAStore = products.size
                        }
                    } catch (_: Exception) {}

                    try {
                        val members = SupabaseProvider.client.postgrest["store_members"].select {
                            filter { eq("store_id", sId) }
                        }.decodeList<JsonObject>()
                        val count = members.size.coerceAtLeast(1)
                        if (count > maxStaffInAStore) {
                            maxStaffInAStore = count
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                ownedStoresCount = 0
            }

            // 1. Owned Stores
            val isStoreUnlimited = tier.storeLimit >= 999999 || tier.id.equals("vip", ignoreCase = true)
            if (isStoreUnlimited) {
                tvStatStoresVal.text = "$ownedStoresCount / Unlimited"
                progressStores.progress = 100
            } else {
                tvStatStoresVal.text = "$ownedStoresCount / ${tier.storeLimit}"
                val remainingStores = (tier.storeLimit - ownedStoresCount).coerceAtLeast(0)
                val pct = if (tier.storeLimit > 0) ((remainingStores.toFloat() / tier.storeLimit) * 100).toInt().coerceIn(0, 100) else 0
                progressStores.progress = pct
            }

            // 2. Items per Store
            val isItemsUnlimited = tier.itemsPerStoreLimit >= 999999 || tier.id.equals("vip", ignoreCase = true)
            if (isItemsUnlimited) {
                tvStatItemsVal.text = "$maxItemsInAStore / Unlimited"
                progressItems.progress = 100
            } else {
                tvStatItemsVal.text = "$maxItemsInAStore / ${tier.itemsPerStoreLimit}"
                val remainingItems = (tier.itemsPerStoreLimit - maxItemsInAStore).coerceAtLeast(0)
                val pct = if (tier.itemsPerStoreLimit > 0) ((remainingItems.toFloat() / tier.itemsPerStoreLimit) * 100).toInt().coerceIn(0, 100) else 0
                progressItems.progress = pct
            }

            // 3. Staff Members per Store
            val isMembersUnlimited = tier.membersPerStoreLimit >= 999999 || tier.id.equals("vip", ignoreCase = true)
            if (isMembersUnlimited) {
                tvStatMembersVal.text = "$maxStaffInAStore / Unlimited"
                progressMembers.progress = 100
            } else {
                tvStatMembersVal.text = "$maxStaffInAStore / ${tier.membersPerStoreLimit}"
                val remainingMembers = (tier.membersPerStoreLimit - maxStaffInAStore).coerceAtLeast(0)
                val pct = if (tier.membersPerStoreLimit > 0) ((remainingMembers.toFloat() / tier.membersPerStoreLimit) * 100).toInt().coerceIn(0, 100) else 0
                progressMembers.progress = pct
            }

            // 4. Daily AI Parser Quota
            val isAiUnlimited = tier.aiQuotaDaily >= 999999 || tier.id.equals("vip", ignoreCase = true)
            if (isAiUnlimited) {
                val usedAi = SubscriptionManager.getDailyUsage(this, uid, SubscriptionManager.DailyQuotaType.AI_PARSE)
                tvStatAiVal.text = "$usedAi / Unlimited"
                progressAi.progress = 100
            } else {
                val remainingAi = SubscriptionManager.getRemainingBaseQuota(this, uid, SubscriptionManager.DailyQuotaType.AI_PARSE, tier.aiQuotaDaily)
                tvStatAiVal.text = "$remainingAi / ${tier.aiQuotaDaily} remaining"
                val pct = if (tier.aiQuotaDaily > 0) ((remainingAi.toFloat() / tier.aiQuotaDaily) * 100).toInt().coerceIn(0, 100) else 0
                progressAi.progress = pct
            }

            // 5. Photo Scans Quota
            val isPhotoUnlimited = tier.photoScansQuotaDaily >= 999999 || tier.id.equals("vip", ignoreCase = true)
            if (isPhotoUnlimited) {
                val usedPhoto = SubscriptionManager.getDailyUsage(this, uid, SubscriptionManager.DailyQuotaType.PHOTO_SCAN)
                tvStatPhotoVal.text = "$usedPhoto / Unlimited"
                progressPhoto.progress = 100
            } else {
                val remainingPhoto = SubscriptionManager.getRemainingBaseQuota(this, uid, SubscriptionManager.DailyQuotaType.PHOTO_SCAN, tier.photoScansQuotaDaily)
                tvStatPhotoVal.text = "$remainingPhoto / ${tier.photoScansQuotaDaily} remaining"
                val pct = if (tier.photoScansQuotaDaily > 0) ((remainingPhoto.toFloat() / tier.photoScansQuotaDaily) * 100).toInt().coerceIn(0, 100) else 0
                progressPhoto.progress = pct
            }

            // 6. Daily Internet Search Quota
            val isSearchUnlimited = tier.internetSearchQuota >= 999999 || tier.id.equals("vip", ignoreCase = true)
            if (isSearchUnlimited) {
                val usedSearch = SubscriptionManager.getDailyUsage(this, uid, SubscriptionManager.DailyQuotaType.INTERNET_SEARCH)
                tvStatSearchVal.text = "$usedSearch / Unlimited"
                progressSearch.progress = 100
            } else {
                val remainingSearch = SubscriptionManager.getRemainingBaseQuota(this, uid, SubscriptionManager.DailyQuotaType.INTERNET_SEARCH, tier.internetSearchQuota)
                tvStatSearchVal.text = "$remainingSearch / ${tier.internetSearchQuota} remaining"
                val pct = if (tier.internetSearchQuota > 0) ((remainingSearch.toFloat() / tier.internetSearchQuota) * 100).toInt().coerceIn(0, 100) else 0
                progressSearch.progress = pct
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showCancelSubscriptionDialog(details: UserSubscriptionDetails) {
        val tierName = details.tierInfo.name
        val message = if (details.isAutoRenew) {
            "Cancel auto-renewal for $tierName?\n\nYour account will switch to Free tier limits. Your stores and catalog items will remain safe."
        } else {
            "Cancel your $tierName plan?\n\nYour account will switch to Free tier limits. Your stores and catalog items will remain safe."
        }

        ReusableDialogHelper.showCustomDialog(
            context = this,
            title = "Cancel Subscription",
            message = message,
            positiveButtonText = "Confirm",
            positiveAction = {
                processSubscriptionCancel()
            },
            negativeButtonText = "Keep Plan"
        )
    }

    private fun processSubscriptionCancel() {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val success = SubscriptionManager.cancelSubscription(this@SubscriptionStatusActivity)
                if (success) {
                    currentDetails = SubscriptionManager.calculateSubscriptionDetails("free", null)
                    applyDetailsToUi(currentDetails)
                    fetchLiveCapacityUsage(currentDetails.tierInfo)
                    Toast.makeText(
                        this@SubscriptionStatusActivity,
                        "Subscription cancelled. Switched to Free tier.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this@SubscriptionStatusActivity, "Unable to cancel subscription. Please try again later.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                Toast.makeText(this@SubscriptionStatusActivity, "Unable to cancel subscription. Please try again later.", Toast.LENGTH_SHORT).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }
}
