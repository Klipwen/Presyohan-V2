package com.presyohan.app

import android.content.Context
import android.util.Log
import androidx.annotation.DrawableRes
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Serializable
data class SubscriptionTierDbRow(
    val tier_id: String = "free",
    val name: String = "",
    val price: Double = 0.0,
    val discount_percent: Double = 0.0,
    val promo_price: Double? = null,
    val promo_badge: String? = null,
    val promo_start_at: String? = null,
    val promo_end_at: String? = null,
    val promo_expiry_label: String? = null,
    val cta_button_text: String? = null,
    val billing_period: String = "month",
    val trial_days: Int = 0,
    val max_stores: Int = 1,
    val max_items_per_store: Int = 100,
    val max_staff_per_store: Int = 3,
    val max_categories_per_store: Int = 10,
    val max_ai_parses_per_day: Int = 3,
    val max_photo_scans_per_day: Int = 3,
    val max_suki_partners: Int = 5,
    val max_presyohan_stores: Int = 5,
    val max_internet_searches_per_day: Int = 3,
    val has_excel_export: Boolean = false,
    val has_pdf_export: Boolean = false,
    val has_notes_export: Boolean = true,
    val has_price_cloning: Boolean = false,
    val has_customer_pairing: Boolean = false,
    val has_priority_support: Boolean = false,
    val merchant_benefits: List<String> = emptyList(),
    val customer_benefits: List<String> = emptyList()
)

data class SubscriptionTierInfo(
    val id: String,
    val name: String,
    val priceText: String,
    val periodText: String,
    val storeLimit: Int,
    val membersPerStoreLimit: Int,
    val categoriesPerStoreLimit: Int,
    val itemsPerStoreLimit: Int,
    val aiQuotaDaily: Int,
    val photoScansQuotaDaily: Int = 3,
    val sukiLimit: Int,
    val presyohanStoresLimit: Int,
    val publicItemsLimit: Int,
    val internetSearchQuota: Int,
    val allowPriceCloning: Boolean,
    val allowCustomerPairing: Boolean,
    val allowExcelExport: Boolean,
    val allowPdfExport: Boolean,
    val allowNotesExport: Boolean = true,
    val hasPrioritySupport: Boolean = false,
    val description: String,
    @param:DrawableRes val iconRes: Int = 0,
    val priceValue: Double = 0.0,
    val trialDays: Int = 0,
    val merchantBenefits: List<String> = emptyList(),
    val customerBenefits: List<String> = emptyList(),
    val discountPercent: Double = 0.0,
    val promoPrice: Double? = null,
    val promoBadge: String? = null,
    val promoStartAt: String? = null,
    val promoEndAt: String? = null,
    val promoExpiryLabel: String? = null,
    val ctaButtonText: String? = null
) {
    val photoScansPerDay: Int get() = photoScansQuotaDaily
    val isPromoActive: Boolean
        get() {
            val hasPromoConfig = (discountPercent > 0.0) || (promoPrice != null && promoPrice > 0.0) || (trialDays > 0) || !promoBadge.isNullOrBlank()
            if (!hasPromoConfig) return false

            val now = System.currentTimeMillis()
            val startMs = SubscriptionManager.parseIsoToEpochMs(promoStartAt) ?: 0L
            val endMs = SubscriptionManager.parseIsoToEpochMs(promoEndAt) ?: Long.MAX_VALUE

            return now in startMs..endMs
        }

    val finalPriceValue: Double
        get() {
            if (!isPromoActive) return priceValue
            if (promoPrice != null && promoPrice > 0.0) return promoPrice
            if (discountPercent > 0.0) return priceValue * (1.0 - discountPercent / 100.0)
            return priceValue
        }

    val effectivePriceText: String
        get() {
            val fp = finalPriceValue
            return if (fp % 1.0 == 0.0) "₱${fp.toInt()}" else "₱%.2f".format(Locale.US, fp)
        }

    val originalPriceText: String?
        get() {
            return if (isPromoActive && finalPriceValue < priceValue) "₱${priceValue.toInt()}" else null
        }

    val effectiveBadgeText: String?
        get() {
            if (!isPromoActive) return null
            return when {
                !promoBadge.isNullOrBlank() -> promoBadge
                discountPercent > 0.0 -> "${discountPercent.toInt()}% OFF"
                trialDays > 0 -> "${trialDays} DAYS TRIAL"
                else -> null
            }
        }

    val promoExpirationText: String?
        get() {
            if (!isPromoActive) return null
            if (!promoExpiryLabel.isNullOrBlank()) return promoExpiryLabel
            val formattedEnd = SubscriptionManager.formatIsoToShortDate(promoEndAt)
            return if (!formattedEnd.isNullOrBlank()) "Special offer valid until $formattedEnd" else null
        }

    val canExportExcel: Boolean get() = allowExcelExport
    val canExportPdf: Boolean get() = allowPdfExport
    val canExportNotes: Boolean get() = allowNotesExport
    val canClonePrices: Boolean get() = allowPriceCloning
}

enum class SubscriptionStatusType {
    FREE_TIER,
    TIME_BOUND_TRIAL,
    AUTO_RENEW,
    LIFETIME,
    EXPIRED
}

data class UserSubscriptionDetails(
    val tierId: String,
    val tierInfo: SubscriptionTierInfo,
    val expiresAtIso: String?,
    val isAutoRenew: Boolean,
    val statusText: String,
    val statusType: SubscriptionStatusType,
    val daysRemaining: Long?,
    val isExpiringSoon: Boolean,
    val isExpired: Boolean,
    val formattedExpiryDate: String? = null,
    val expiredTierName: String? = null
)

object SubscriptionManager {

    var liveTierConfigs: Map<String, SubscriptionTierDbRow> = emptyMap()

    fun parseIsoToEpochMs(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            try {
                val cleanDate = isoString.substringBefore("T").substringBefore(" ")
                val date = LocalDate.parse(cleanDate)
                date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun formatIsoToShortDate(isoString: String?): String? {
        if (isoString.isNullOrBlank()) return null
        return try {
            val instant = Instant.parse(isoString)
            val zdt = instant.atZone(ZoneId.systemDefault())
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(zdt)
        } catch (e: Exception) {
            try {
                val cleanDate = isoString.substringBefore("T").substringBefore(" ")
                val date = LocalDate.parse(cleanDate)
                DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US).format(date)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun saveCachedTierConfigs(context: Context, configs: Map<String, SubscriptionTierDbRow>) {
        try {
            val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
            val jsonStr = kotlinx.serialization.json.Json.encodeToString(configs.values.toList())
            prefs.edit().putString("cached_live_tier_configs", jsonStr).apply()
        } catch (_: Exception) {}
    }

    private fun loadCachedTierConfigs(context: Context): Map<String, SubscriptionTierDbRow> {
        try {
            val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("cached_live_tier_configs", null) ?: return emptyMap()
            val list = kotlinx.serialization.json.Json.decodeFromString<List<SubscriptionTierDbRow>>(jsonStr)
            return list.associateBy { it.tier_id.lowercase() }
        } catch (_: Exception) {
            return emptyMap()
        }
    }

    suspend fun fetchLiveTierConfigs(context: Context? = null): Map<String, SubscriptionTierDbRow> = withContext(Dispatchers.IO) {
        try {
            val list = SupabaseProvider.client.postgrest["subscription_tiers"]
                .select()
                .decodeList<JsonObject>()

            Log.d("SubscriptionManager", "Raw JSON fetched from Supabase: ${list.size} rows")

            val resultMap = mutableMapOf<String, SubscriptionTierDbRow>()
            for (json in list) {
                val tid = json["tier_id"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: continue

                val defStores = when (tid) { "vip" -> 999999; "pro" -> 10; else -> 1 }
                val defItems = when (tid) { "vip" -> 999999; "pro" -> 500; else -> 100 }
                val defStaff = when (tid) { "vip" -> 999999; "pro" -> 10; else -> 3 }
                val defCats = when (tid) { "vip" -> 999999; "pro" -> 25; else -> 10 }
                val defAi = when (tid) { "vip" -> 50; "pro" -> 10; else -> 3 }
                val defSuki = when (tid) { "vip" -> 999999; "pro" -> 15; else -> 5 }
                val defPresyohan = when (tid) { "vip" -> 999999; "pro" -> 15; else -> 5 }
                val defSearches = when (tid) { "vip" -> 999999; "pro" -> 15; else -> 3 }

                val priceVal = json["price"]?.jsonPrimitive?.doubleOrNull
                    ?: json["price"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: if (tid == "vip") 299.0 else if (tid == "pro") 99.0 else 0.0
                val discVal = json["discount_percent"]?.jsonPrimitive?.doubleOrNull
                    ?: json["discount_percent"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: 0.0
                val promoPriceVal = json["promo_price"]?.jsonPrimitive?.doubleOrNull
                    ?: json["promo_price"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val promoBadgeVal = json["promo_badge"]?.jsonPrimitive?.contentOrNull
                val promoStartAtVal = json["promo_start_at"]?.jsonPrimitive?.contentOrNull
                val promoEndAtVal = json["promo_end_at"]?.jsonPrimitive?.contentOrNull
                val promoExpiryLabelVal = json["promo_expiry_label"]?.jsonPrimitive?.contentOrNull
                val ctaTextVal = json["cta_button_text"]?.jsonPrimitive?.contentOrNull
                val nameVal = json["name"]?.jsonPrimitive?.contentOrNull ?: when (tid) { "vip" -> "VIP Tier"; "pro" -> "PRO Tier"; else -> "Free Tier" }
                val trialDaysVal = json["trial_days"]?.jsonPrimitive?.intOrNull
                    ?: json["trial_days"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: if (tid == "pro") 7 else 0

                val maxStoresVal = json["max_stores"]?.jsonPrimitive?.intOrNull
                    ?: json["max_stores"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: defStores
                val maxItemsVal = json["max_items_per_store"]?.jsonPrimitive?.intOrNull
                    ?: json["max_items_per_store"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: defItems
                val maxStaffVal = json["max_staff_per_store"]?.jsonPrimitive?.intOrNull
                    ?: json["max_staff_per_store"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: defStaff
                val maxCatVal = json["max_categories_per_store"]?.jsonPrimitive?.intOrNull
                    ?: json["max_categories_per_store"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: defCats
                val maxAiVal = json["max_ai_parses_per_day"]?.jsonPrimitive?.intOrNull
                    ?: json["max_ai_parses_per_day"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: defAi
                val maxPhotoVal = json["max_photo_scans_per_day"]?.jsonPrimitive?.intOrNull
                    ?: json["max_photo_scans_per_day"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: defAi
                val maxSukiVal = json["max_suki_partners"]?.jsonPrimitive?.intOrNull
                    ?: json["max_suki_partners"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: defSuki
                val maxPresyohanVal = json["max_presyohan_stores"]?.jsonPrimitive?.intOrNull
                    ?: json["max_presyohan_stores"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: defPresyohan
                val maxSearchVal = json["max_internet_searches_per_day"]?.jsonPrimitive?.intOrNull
                    ?: json["max_internet_searches_per_day"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: defSearches

                val hasExcelVal = json["has_excel_export"]?.jsonPrimitive?.booleanOrNull ?: (tid != "free")
                val hasPdfVal = json["has_pdf_export"]?.jsonPrimitive?.booleanOrNull ?: (tid != "free")
                val hasNotesVal = json["has_notes_export"]?.jsonPrimitive?.booleanOrNull ?: true
                val hasPriceCloneVal = json["has_price_cloning"]?.jsonPrimitive?.booleanOrNull ?: (tid != "free")
                val hasPairingVal = json["has_customer_pairing"]?.jsonPrimitive?.booleanOrNull ?: (tid != "free")
                val hasPriorityVal = json["has_priority_support"]?.jsonPrimitive?.booleanOrNull ?: (tid == "vip")

                val merchantBen = json["merchant_benefits"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val customerBen = json["customer_benefits"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                resultMap[tid] = SubscriptionTierDbRow(
                    tier_id = tid,
                    name = nameVal,
                    price = priceVal,
                    discount_percent = discVal,
                    promo_price = promoPriceVal,
                    promo_badge = promoBadgeVal,
                    promo_start_at = promoStartAtVal,
                    promo_end_at = promoEndAtVal,
                    promo_expiry_label = promoExpiryLabelVal,
                    cta_button_text = ctaTextVal,
                    trial_days = trialDaysVal,
                    max_stores = maxStoresVal,
                    max_items_per_store = maxItemsVal,
                    max_staff_per_store = maxStaffVal,
                    max_categories_per_store = maxCatVal,
                    max_ai_parses_per_day = maxAiVal,
                    max_photo_scans_per_day = maxPhotoVal,
                    max_suki_partners = maxSukiVal,
                    max_presyohan_stores = maxPresyohanVal,
                    max_internet_searches_per_day = maxSearchVal,
                    has_excel_export = hasExcelVal,
                    has_pdf_export = hasPdfVal,
                    has_notes_export = hasNotesVal,
                    has_price_cloning = hasPriceCloneVal,
                    has_customer_pairing = hasPairingVal,
                    has_priority_support = hasPriorityVal,
                    merchant_benefits = merchantBen,
                    customer_benefits = customerBen
                )
            }
            Log.d("SubscriptionManager", "Successfully loaded ${resultMap.size} subscription_tiers from Supabase live")
            liveTierConfigs = resultMap
            if (context != null && resultMap.isNotEmpty()) {
                saveCachedTierConfigs(context, resultMap)
            }
            resultMap
        } catch (e: Exception) {
            Log.e("SubscriptionManager", "Error fetching subscription_tiers from Supabase: ${e.message}", e)
            if (context != null && liveTierConfigs.isEmpty()) {
                liveTierConfigs = loadCachedTierConfigs(context)
            }
            liveTierConfigs
        }
    }

    fun sanitizeLimit(value: Int?, defaultVal: Int = 1): Int {
        val v = value ?: defaultVal
        return if (v >= 90000) 999999 else v
    }

    val TIER_FREE: SubscriptionTierInfo
        get() {
            val db = liveTierConfigs["free"]
            return SubscriptionTierInfo(
                id = "free",
                name = db?.name ?: "Free Tier",
                priceText = "₱${db?.price?.toInt() ?: 0}",
                periodText = "forever",
                storeLimit = sanitizeLimit(db?.max_stores, 1),
                membersPerStoreLimit = sanitizeLimit(db?.max_staff_per_store, 3),
                categoriesPerStoreLimit = sanitizeLimit(db?.max_categories_per_store, 10),
                itemsPerStoreLimit = sanitizeLimit(db?.max_items_per_store, 100),
                aiQuotaDaily = sanitizeLimit(db?.max_ai_parses_per_day, 3),
                photoScansQuotaDaily = sanitizeLimit(db?.max_photo_scans_per_day, 3),
                sukiLimit = sanitizeLimit(db?.max_suki_partners, 5),
                presyohanStoresLimit = sanitizeLimit(db?.max_presyohan_stores, 5),
                publicItemsLimit = 5,
                internetSearchQuota = sanitizeLimit(db?.max_internet_searches_per_day, 3),
                allowPriceCloning = db?.has_price_cloning ?: false,
                allowCustomerPairing = db?.has_customer_pairing ?: false,
                allowExcelExport = db?.has_excel_export ?: false,
                allowPdfExport = db?.has_pdf_export ?: false,
                allowNotesExport = db?.has_notes_export ?: true,
                hasPrioritySupport = db?.has_priority_support ?: false,
                description = "Ideal for micro sari-sari stores & single vendors.",
                iconRes = 0,
                priceValue = db?.price ?: 0.0,
                trialDays = db?.trial_days ?: 0,
                merchantBenefits = db?.merchant_benefits ?: listOf("1 Store Branch", "3 Staffs / Store", "10 Categories / Store", "100 Items / Store", "3 AI Parses / day", "Convert as Notes"),
                customerBenefits = db?.customer_benefits ?: listOf("Suking Tindahan Partners: 5 Partners", "Presyohan Store Limit: 5 Stores", "Internet Search Quota: 3 Searches / day"),
                discountPercent = db?.discount_percent ?: 0.0,
                promoPrice = db?.promo_price,
                promoBadge = db?.promo_badge ?: "BASIC PLAN",
                promoStartAt = db?.promo_start_at,
                promoEndAt = db?.promo_end_at,
                promoExpiryLabel = db?.promo_expiry_label,
                ctaButtonText = db?.cta_button_text
            )
        }

    val TIER_PRO: SubscriptionTierInfo
        get() {
            val db = liveTierConfigs["pro"]
            return SubscriptionTierInfo(
                id = "pro",
                name = db?.name ?: "PRO Tier",
                priceText = "₱${db?.price?.toInt() ?: 99}",
                periodText = "/ month",
                storeLimit = sanitizeLimit(db?.max_stores, 10),
                membersPerStoreLimit = sanitizeLimit(db?.max_staff_per_store, 10),
                categoriesPerStoreLimit = sanitizeLimit(db?.max_categories_per_store, 25),
                itemsPerStoreLimit = sanitizeLimit(db?.max_items_per_store, 500),
                aiQuotaDaily = sanitizeLimit(db?.max_ai_parses_per_day, 10),
                photoScansQuotaDaily = sanitizeLimit(db?.max_photo_scans_per_day, 10),
                sukiLimit = sanitizeLimit(db?.max_suki_partners, 15),
                presyohanStoresLimit = sanitizeLimit(db?.max_presyohan_stores, 15),
                publicItemsLimit = 15,
                internetSearchQuota = sanitizeLimit(db?.max_internet_searches_per_day, 15),
                allowPriceCloning = db?.has_price_cloning ?: true,
                allowCustomerPairing = db?.has_customer_pairing ?: true,
                allowExcelExport = db?.has_excel_export ?: true,
                allowPdfExport = db?.has_pdf_export ?: true,
                allowNotesExport = db?.has_notes_export ?: true,
                hasPrioritySupport = db?.has_priority_support ?: false,
                description = "Ideal for growing single & multi-branch retail stores.",
                iconRes = R.drawable.icon_pro,
                priceValue = db?.price ?: 99.0,
                trialDays = db?.trial_days ?: 7,
                merchantBenefits = db?.merchant_benefits ?: listOf("Up to 10 Stores", "10 Staffs / Store", "25 Categories / Store", "500 Items / Store", "10 AI Parses / day", "Unlocked Customer Pairing (suki)", "Convert to PDF", "Convert to Excel", "Price Cloning & Export"),
                customerBenefits = db?.customer_benefits ?: listOf("Suking Tindahan Partners: 15 Partners", "Presyohan Store Limit: 15 Stores", "Internet Search Quota: 15 Searches / day"),
                discountPercent = db?.discount_percent ?: 0.0,
                promoPrice = db?.promo_price,
                promoBadge = db?.promo_badge ?: "7 DAYS TRIAL",
                promoStartAt = db?.promo_start_at,
                promoEndAt = db?.promo_end_at,
                promoExpiryLabel = db?.promo_expiry_label,
                ctaButtonText = db?.cta_button_text
            )
        }

    val TIER_VIP: SubscriptionTierInfo
        get() {
            val db = liveTierConfigs["vip"]
            return SubscriptionTierInfo(
                id = "vip",
                name = db?.name ?: "VIP Tier",
                priceText = "₱${db?.price?.toInt() ?: 299}",
                periodText = "/ month",
                storeLimit = sanitizeLimit(db?.max_stores, 999999),
                membersPerStoreLimit = sanitizeLimit(db?.max_staff_per_store, 999999),
                categoriesPerStoreLimit = sanitizeLimit(db?.max_categories_per_store, 999999),
                itemsPerStoreLimit = sanitizeLimit(db?.max_items_per_store, 999999),
                aiQuotaDaily = sanitizeLimit(db?.max_ai_parses_per_day, 50),
                photoScansQuotaDaily = sanitizeLimit(db?.max_photo_scans_per_day, 50),
                sukiLimit = sanitizeLimit(db?.max_suki_partners, 999999),
                presyohanStoresLimit = sanitizeLimit(db?.max_presyohan_stores, 999999),
                publicItemsLimit = Int.MAX_VALUE,
                internetSearchQuota = sanitizeLimit(db?.max_internet_searches_per_day, 999999),
                allowPriceCloning = db?.has_price_cloning ?: true,
                allowCustomerPairing = db?.has_customer_pairing ?: true,
                allowExcelExport = db?.has_excel_export ?: true,
                allowPdfExport = db?.has_pdf_export ?: true,
                allowNotesExport = db?.has_notes_export ?: true,
                hasPrioritySupport = db?.has_priority_support ?: true,
                description = "Ideal for high-volume businesses & enterprise managers.",
                iconRes = R.drawable.icon_vip,
                priceValue = db?.price ?: 299.0,
                trialDays = db?.trial_days ?: 0,
                merchantBenefits = db?.merchant_benefits ?: listOf("Unlimited Stores", "Unlimited Staff / Store", "Unlimited Categories / Store", "Unlimited Items / Store", "50 AI Parses / day", "Unlimited Customer Pairing (suki)", "Unlimited PDF & Excel Exports", "Unlimited Price Cloning", "24/7 VIP Priority Support"),
                customerBenefits = db?.customer_benefits ?: listOf("Unlimited Suking Tindahan Partners", "Unlimited Presyohan Stores", "Unlimited Internet Search Quota"),
                discountPercent = db?.discount_percent ?: 0.0,
                promoPrice = db?.promo_price,
                promoBadge = db?.promo_badge ?: "BEST VALUE",
                promoStartAt = db?.promo_start_at,
                promoEndAt = db?.promo_end_at,
                promoExpiryLabel = db?.promo_expiry_label,
                ctaButtonText = db?.cta_button_text
            )
        }

    fun getTierInfo(tierId: String?): SubscriptionTierInfo {
        return when (tierId?.lowercase()) {
            "pro" -> TIER_PRO
            "vip" -> TIER_VIP
            else -> TIER_FREE
        }
    }

    /**
     * Compute full status text, remaining days, and status type based on tier and expiration metadata.
     */
    fun calculateSubscriptionDetails(
        tierId: String,
        expiresAtIso: String?,
        isAutoRenew: Boolean = false,
        wasRecentlyExpired: Boolean = false,
        expiredTierName: String? = null
    ): UserSubscriptionDetails {
        val sanitizedTier = tierId.lowercase()
        val tierInfo = getTierInfo(sanitizedTier)
        val now = System.currentTimeMillis()
        val expMs = parseIsoToEpochMs(expiresAtIso)
        val formattedDate = formatIsoToShortDate(expiresAtIso)

        // Case 1: Free Tier
        if (sanitizedTier == "free") {
            if (wasRecentlyExpired && !expiredTierName.isNullOrBlank()) {
                return UserSubscriptionDetails(
                    tierId = "free",
                    tierInfo = TIER_FREE,
                    expiresAtIso = null,
                    isAutoRenew = false,
                    statusText = "Standard quota limits (No expiration)",
                    statusType = SubscriptionStatusType.EXPIRED,
                    daysRemaining = null,
                    isExpiringSoon = false,
                    isExpired = true,
                    formattedExpiryDate = null,
                    expiredTierName = expiredTierName
                )
            }
            return UserSubscriptionDetails(
                tierId = "free",
                tierInfo = TIER_FREE,
                expiresAtIso = null,
                isAutoRenew = false,
                statusText = "Standard quota limits (No expiration)",
                statusType = SubscriptionStatusType.FREE_TIER,
                daysRemaining = null,
                isExpiringSoon = false,
                isExpired = false,
                formattedExpiryDate = null
            )
        }

        // Case 2: Permanent / Lifetime Access (PRO or VIP without expiration)
        if (expMs == null || expMs <= 0L) {
            return UserSubscriptionDetails(
                tierId = sanitizedTier,
                tierInfo = tierInfo,
                expiresAtIso = null,
                isAutoRenew = false,
                statusText = "Permanent Access (Never Expires)",
                statusType = SubscriptionStatusType.LIFETIME,
                daysRemaining = null,
                isExpiringSoon = false,
                isExpired = false,
                formattedExpiryDate = null
            )
        }

        // Check if expiration timestamp has passed
        if (now >= expMs) {
            return UserSubscriptionDetails(
                tierId = "free",
                tierInfo = TIER_FREE,
                expiresAtIso = expiresAtIso,
                isAutoRenew = false,
                statusText = "Standard quota limits (No expiration)",
                statusType = SubscriptionStatusType.EXPIRED,
                daysRemaining = 0L,
                isExpiringSoon = false,
                isExpired = true,
                formattedExpiryDate = formattedDate,
                expiredTierName = tierInfo.name
            )
        }

        // Active with expiration
        val diffMs = expMs - now
        val days = Math.ceil(diffMs.toDouble() / (1000.0 * 60 * 60 * 24)).toLong().coerceAtLeast(0)
        val isExpiringSoon = days in 0..3

        // Case 3: Auto-Renewing Subscriptions (e.g. Monthly Google Play / Store Billing)
        if (isAutoRenew) {
            val monthlyPriceFormatted = if (tierInfo.finalPriceValue % 1.0 == 0.0) {
                "₱%.2f/mo".format(Locale.US, tierInfo.finalPriceValue)
            } else {
                "₱%.2f/mo".format(Locale.US, tierInfo.finalPriceValue)
            }
            val statusText = "Renews automatically on $formattedDate ($monthlyPriceFormatted)"

            return UserSubscriptionDetails(
                tierId = sanitizedTier,
                tierInfo = tierInfo,
                expiresAtIso = expiresAtIso,
                isAutoRenew = true,
                statusText = statusText,
                statusType = SubscriptionStatusType.AUTO_RENEW,
                daysRemaining = days,
                isExpiringSoon = false, // Auto-renew handles payment automatically
                isExpired = false,
                formattedExpiryDate = formattedDate
            )
        }

        // Case 4: Free Trial or Time-Bound Access (e.g., 7-Day PRO Trial / Admin Override)
        val remainingPart = when {
            days <= 0 -> "(Expires today)"
            days == 1L -> "(1 day remaining)"
            else -> "($days days remaining)"
        }
        val statusText = "Expires on $formattedDate $remainingPart"

        return UserSubscriptionDetails(
            tierId = sanitizedTier,
            tierInfo = tierInfo,
            expiresAtIso = expiresAtIso,
            isAutoRenew = false,
            statusText = statusText,
            statusType = SubscriptionStatusType.TIME_BOUND_TRIAL,
            daysRemaining = days,
            isExpiringSoon = isExpiringSoon,
            isExpired = false,
            formattedExpiryDate = formattedDate
        )
    }

    fun getCachedTier(context: Context): SubscriptionTierInfo {
        val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        val tierId = prefs.getString("user_subscription_tier", "free") ?: "free"
        return getTierInfo(tierId)
    }

    fun getCachedSubscriptionDetails(context: Context): UserSubscriptionDetails {
        val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        val tierId = prefs.getString("user_subscription_tier", "free") ?: "free"
        val expiresAt = prefs.getString("user_subscription_expires_at", null)
        val isAutoRenew = prefs.getBoolean("user_subscription_auto_renew", false)
        val wasExpired = prefs.getBoolean("user_subscription_was_expired", false)
        val expiredTierName = prefs.getString("user_subscription_expired_tier_name", null)

        return calculateSubscriptionDetails(
            tierId = tierId,
            expiresAtIso = expiresAt,
            isAutoRenew = isAutoRenew,
            wasRecentlyExpired = wasExpired,
            expiredTierName = expiredTierName
        )
    }

    fun saveCachedDetails(
        context: Context,
        tierId: String,
        expiresAt: String?,
        isAutoRenew: Boolean = false,
        wasExpired: Boolean = false,
        expiredTierName: String? = null
    ) {
        val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("user_subscription_tier", tierId.lowercase())
            .putString("user_subscription_expires_at", expiresAt)
            .putBoolean("user_subscription_auto_renew", isAutoRenew)
            .putBoolean("user_subscription_was_expired", wasExpired)
            .putString("user_subscription_expired_tier_name", expiredTierName)
            .apply()
    }

    fun clearExpiredNotice(context: Context) {
        val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("user_subscription_was_expired", false)
            .putString("user_subscription_expired_tier_name", null)
            .apply()
    }

    suspend fun fetchUserTier(context: Context): SubscriptionTierInfo {
        val details = fetchUserSubscriptionDetails(context)
        return details.tierInfo
    }

    /**
     * Fetches user profile, calculates detailed status, and handles graceful expiration fallback if needed.
     */
    suspend fun fetchUserSubscriptionDetails(context: Context): UserSubscriptionDetails = withContext(Dispatchers.IO) {
        try {
            fetchLiveTierConfigs(context)
        } catch (_: Exception) {}

        val userId = SupabaseAuthService.getCurrentUserId() ?: return@withContext getCachedSubscriptionDetails(context)
        try {
            val profile = SupabaseAuthService.getUserProfile()
            val rawTier = profile?.subscription_tier ?: "free"
            val rawExpiresAt = profile?.subscription_expires_at
            val rawAutoRenew = profile?.subscription_auto_renew ?: false

            val details = calculateSubscriptionDetails(
                tierId = rawTier,
                expiresAtIso = rawExpiresAt,
                isAutoRenew = rawAutoRenew
            )

            // Graceful Expiration Fallback: If expired, downgrade user in Supabase to free without deleting stores/products
            if (details.isExpired && rawTier != "free") {
                val expiredTierInfo = getTierInfo(rawTier)
                try {
                    val payload = buildJsonObject {
                        put("subscription_tier", "free")
                        put("subscription_expires_at", kotlinx.serialization.json.JsonNull)
                        put("subscription_auto_renew", false)
                    }
                    SupabaseProvider.client.postgrest["app_users"].update(payload) {
                        filter { eq("id", userId) }
                    }
                    // Sync stores owned by this user to free as well
                    val storePayload = buildJsonObject {
                        put("subscription_tier", "free")
                        put("subscription_expires_at", kotlinx.serialization.json.JsonNull)
                        put("subscription_auto_renew", false)
                    }
                    SupabaseProvider.client.postgrest["stores"].update(storePayload) {
                        filter { eq("owner_id", userId) }
                    }
                } catch (ex: Exception) {
                    Log.w("SubscriptionManager", "Note: Graceful expiration sync failed: ${ex.message}")
                }

                saveCachedDetails(
                    context = context,
                    tierId = "free",
                    expiresAt = null,
                    isAutoRenew = false,
                    wasExpired = true,
                    expiredTierName = expiredTierInfo.name
                )

                return@withContext details.copy(
                    tierId = "free",
                    tierInfo = TIER_FREE,
                    statusType = SubscriptionStatusType.EXPIRED,
                    expiredTierName = expiredTierInfo.name
                )
            }

            saveCachedDetails(
                context = context,
                tierId = details.tierId,
                expiresAt = details.expiresAtIso,
                isAutoRenew = details.isAutoRenew,
                wasExpired = false,
                expiredTierName = null
            )
            return@withContext details
        } catch (e: Exception) {
            Log.e("SubscriptionManager", "Error fetching user subscription: ${e.message}", e)
            return@withContext getCachedSubscriptionDetails(context)
        }
    }

    /**
     * Updates the user's subscription tier.
     * When upgrading to PRO/VIP, sets duration & auto-renew automatically based on tier configs if not explicitly specified.
     */
    suspend fun updateUserSubscription(
        context: Context,
        newTierId: String,
        durationDays: Int? = null,
        isAutoRenew: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val userId = SupabaseAuthService.getCurrentUserId() ?: return@withContext false
        val sanitizedTier = newTierId.lowercase()
        val targetInfo = getTierInfo(sanitizedTier)

        try {
            val expiresAtIso: String? = when {
                sanitizedTier == "free" -> null
                durationDays != null -> {
                    val expMs = System.currentTimeMillis() + (durationDays.toLong() * 24 * 60 * 60 * 1000)
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(expMs))
                }
                targetInfo.trialDays > 0 -> {
                    val expMs = System.currentTimeMillis() + (targetInfo.trialDays.toLong() * 24 * 60 * 60 * 1000)
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(expMs))
                }
                else -> {
                    // Default monthly subscription (30 days) with auto-renew
                    val expMs = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(expMs))
                }
            }

            val autoRenewFlag = if (sanitizedTier == "free") false else (isAutoRenew || (targetInfo.trialDays == 0 && durationDays == null))

            val payload = buildJsonObject {
                put("subscription_tier", sanitizedTier)
                if (expiresAtIso != null) {
                    put("subscription_expires_at", expiresAtIso)
                } else {
                    put("subscription_expires_at", kotlinx.serialization.json.JsonNull)
                }
                put("subscription_auto_renew", autoRenewFlag)
            }

            SupabaseProvider.client.postgrest["app_users"].update(payload) {
                filter { eq("id", userId) }
            }

            // Sync stores owned by this user
            try {
                val storePayload = buildJsonObject {
                    put("subscription_tier", sanitizedTier)
                    if (expiresAtIso != null) {
                        put("subscription_expires_at", expiresAtIso)
                    } else {
                        put("subscription_expires_at", kotlinx.serialization.json.JsonNull)
                    }
                    put("subscription_auto_renew", autoRenewFlag)
                    if (!targetInfo.allowCustomerPairing) {
                        put("is_public", false)
                    }
                }
                SupabaseProvider.client.postgrest["stores"].update(storePayload) {
                    filter { eq("billing_owner_id", userId) }
                }
            } catch (_: Exception) {}

            saveCachedDetails(
                context = context,
                tierId = sanitizedTier,
                expiresAt = expiresAtIso,
                isAutoRenew = autoRenewFlag,
                wasExpired = false,
                expiredTierName = null
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            saveCachedDetails(
                context = context,
                tierId = sanitizedTier,
                expiresAt = null,
                isAutoRenew = false,
                wasExpired = false,
                expiredTierName = null
            )
            true
        }
    }

    /**
     * Cancels an active subscription / auto-renewal and downgrades the user to the Free tier.
     * All stores and items remain completely preserved.
     */
    suspend fun cancelSubscription(context: Context): Boolean = withContext(Dispatchers.IO) {
        updateUserSubscription(context, "free")
    }

    /**
     * Fetches the live active subscription tier for a specific store.
     * Looks up billing owner's active status and handles expiration.
     */
    suspend fun fetchStoreSubscriptionTier(storeId: String): SubscriptionTierInfo = withContext(Dispatchers.IO) {
        try {
            @Serializable
            data class StoreBillingRow(
                val id: String,
                val billing_owner_id: String? = null,
                val subscription_tier: String? = null,
                val subscription_expires_at: String? = null,
                val is_public: Boolean = false,
                val is_public_preference: Boolean = false
            )

            val rows = SupabaseProvider.client.postgrest["stores"].select {
                filter { eq("id", storeId) }
                limit(1)
            }.decodeList<StoreBillingRow>()

            val store = rows.firstOrNull() ?: return@withContext TIER_FREE
            var tierId = "free"
            val expIso = store.subscription_expires_at

            // 1. Resolve store owner ID (check billing_owner_id first, fallback to store_members)
            var ownerId = store.billing_owner_id
            if (ownerId.isNullOrBlank()) {
                try {
                    @Serializable
                    data class MemberRow(val user_id: String, val role: String)
                    val memberRows = SupabaseProvider.client.postgrest["store_members"].select {
                        filter {
                            eq("store_id", storeId)
                            eq("role", "owner")
                        }
                        limit(1)
                    }.decodeList<MemberRow>()
                    ownerId = memberRows.firstOrNull()?.user_id
                } catch (_: Exception) {}
            }

            // 2. Fetch the owner's active tier from app_users
            if (!ownerId.isNullOrBlank()) {
                try {
                    @Serializable
                    data class OwnerUserRow(
                        val id: String,
                        val subscription_tier: String? = null,
                        val subscription_expires_at: String? = null
                    )
                    val ownerRows = SupabaseProvider.client.postgrest["app_users"].select {
                        filter { eq("id", ownerId) }
                        limit(1)
                    }.decodeList<OwnerUserRow>()
                    val owner = ownerRows.firstOrNull()
                    if (owner != null) {
                        val oTier = owner.subscription_tier?.lowercase()?.trim() ?: "free"
                        val ownerExpIso = owner.subscription_expires_at
                        val ownerExpMs = parseIsoToEpochMs(ownerExpIso)
                        if (ownerExpMs != null && System.currentTimeMillis() >= ownerExpMs) {
                            tierId = "free"
                        } else {
                            tierId = oTier
                        }
                    } else {
                        tierId = "free"
                    }
                } catch (_: Exception) {
                    tierId = "free"
                }
            } else {
                // If no owner could be identified, check store record expiration
                val sTier = store.subscription_tier?.lowercase()?.trim() ?: "free"
                val expMs = parseIsoToEpochMs(expIso)
                if (expMs != null && System.currentTimeMillis() >= expMs) {
                    tierId = "free"
                } else {
                    tierId = sTier
                }
            }

            val resolvedTierInfo = getTierInfo(tierId)
            val targetIsPublic = if (resolvedTierInfo.allowCustomerPairing) store.is_public_preference else false

            // 3. Keep stores table in sync asynchronously if stale or if public status does not match tier preference
            if (store.subscription_tier?.lowercase()?.trim() != tierId || 
                (store.billing_owner_id.isNullOrBlank() && !ownerId.isNullOrBlank()) ||
                store.is_public != targetIsPublic) {
                try {
                    val updateObj = buildJsonObject {
                        put("subscription_tier", tierId)
                        if (!ownerId.isNullOrBlank()) {
                            put("billing_owner_id", ownerId)
                        }
                        put("is_public", targetIsPublic)
                    }
                    SupabaseProvider.client.postgrest["stores"].update(updateObj) {
                        filter { eq("id", storeId) }
                    }
                } catch (_: Exception) {}
            }

            resolvedTierInfo
        } catch (e: Exception) {
            Log.e("SubscriptionManager", "Failed to fetch store subscription tier for $storeId: ${e.message}")
            TIER_FREE
        }
    }

    fun formatLimitText(limit: Int): String {
        return if (limit == Int.MAX_VALUE || limit >= 90000) "Unlimited" else limit.toString()
    }

    fun formatCountWithLimit(count: Int, limit: Int, isVip: Boolean = false): CharSequence {
        if (isVip || limit >= 90000 || limit <= 0 || limit == Int.MAX_VALUE) {
            return count.toString()
        }
        val countStr = count.toString()
        val limitStr = " /$limit"
        val fullText = "$countStr$limitStr"
        val spannable = SpannableString(fullText)
        val start = countStr.length
        val end = fullText.length
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor("#757575")),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            RelativeSizeSpan(0.65f),
            start,
            end,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    fun resolveEffectiveTier(userTier: String?, storeTier: String?): String {
        val s = storeTier?.lowercase()?.trim()
        if (!s.isNullOrBlank()) return s
        val u = userTier?.lowercase()?.trim()
        return if (!u.isNullOrBlank()) u else "free"
    }

    /**
     * Shows a consistent, friendly paywall modal when a gated feature is tapped.
     */
    fun showFeatureGatedDialog(
        activity: android.app.Activity,
        featureName: String,
        requiredTier: String = "pro",
        onDismiss: (() -> Unit)? = null
    ) {
        val tierName = if (requiredTier.equals("vip", ignoreCase = true)) "VIP" else "PRO"
        ReusableDialogHelper.showCustomDialog(
            context = activity,
            title = "Upgrade to $tierName",
            message = "$featureName is available on the $tierName plan. Upgrade today to unlock this feature and enjoy more tools to grow your store.",
            positiveButtonText = "UPGRADE",
            positiveAction = {
                onDismiss?.invoke()
                SubscriptionPaywallActivity.launch(activity, requiredTier)
            },
            negativeButtonText = "Cancel",
            negativeAction = {
                onDismiss?.invoke()
            }
        )
    }

    /**
     * Shows a consistent, friendly modal when a resource capacity limit is reached.
     */
    fun showCapacityReachedDialog(
        activity: android.app.Activity,
        resourceName: String,
        limit: Int,
        requiredTier: String = "pro"
    ) {
        val tierName = if (requiredTier.equals("vip", ignoreCase = true)) "VIP" else "PRO"
        val word = if (limit == 1) resourceName else "${resourceName}s"
        ReusableDialogHelper.showCustomDialog(
            context = activity,
            title = "Upgrade to $tierName",
            message = "You have reached your limit of $limit $word on your current plan. Upgrade to $tierName to increase your capacity and add more $word.",
            positiveButtonText = "UPGRADE",
            positiveAction = {
                SubscriptionPaywallActivity.launch(activity, requiredTier)
            },
            negativeButtonText = "Cancel"
        )
    }

    // Daily Quota Tracking (with local midnight reset & bonus ad reward)
    enum class DailyQuotaType {
        AI_PARSE,
        PHOTO_SCAN,
        INTERNET_SEARCH
    }

    fun getTodayDateKey(): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date())
        } catch (e: Exception) {
            "today"
        }
    }

    private fun getUsagePrefKey(uid: String?, type: DailyQuotaType, dateKey: String): String {
        val resolvedUid = uid ?: "guest"
        return "quota_usage_${type.name}_${resolvedUid}_$dateKey"
    }

    private fun getBonusPrefKey(uid: String?, type: DailyQuotaType, dateKey: String): String {
        val resolvedUid = uid ?: "guest"
        return "quota_bonus_${type.name}_${resolvedUid}_$dateKey"
    }

    fun getDailyUsage(context: Context, uid: String?, type: DailyQuotaType): Int {
        val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        val dateKey = getTodayDateKey()
        return prefs.getInt(getUsagePrefKey(uid, type, dateKey), 0)
    }

    fun getRemainingBaseQuota(context: Context, uid: String?, type: DailyQuotaType, tierLimit: Int): Int {
        if (tierLimit >= 999999) return 999999
        val usage = getDailyUsage(context, uid, type)
        return (tierLimit - usage).coerceAtLeast(0)
    }

    fun getBonusQuota(context: Context, uid: String?, type: DailyQuotaType): Int {
        val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        val dateKey = getTodayDateKey()
        return prefs.getInt(getBonusPrefKey(uid, type, dateKey), 0)
    }

    fun hasQuotaAvailable(context: Context, uid: String?, type: DailyQuotaType, tierLimit: Int): Boolean {
        if (tierLimit >= 999999) return true
        val baseRemaining = getRemainingBaseQuota(context, uid, type, tierLimit)
        val adPasses = getBonusQuota(context, uid, type)
        return (baseRemaining > 0 || adPasses > 0)
    }

    fun consumeQuota(context: Context, uid: String?, type: DailyQuotaType, tierLimit: Int = 1) {
        val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        val dateKey = getTodayDateKey()
        val currentUsage = prefs.getInt(getUsagePrefKey(uid, type, dateKey), 0)
        val currentAdPasses = prefs.getInt(getBonusPrefKey(uid, type, dateKey), 0)

        if (currentUsage < tierLimit) {
            // Consume from standard base daily tier limit
            prefs.edit().putInt(getUsagePrefKey(uid, type, dateKey), currentUsage + 1).apply()
        } else if (currentAdPasses > 0) {
            // Consume 1 temporary ad pass
            prefs.edit().putInt(getBonusPrefKey(uid, type, dateKey), currentAdPasses - 1).apply()
        } else {
            prefs.edit().putInt(getUsagePrefKey(uid, type, dateKey), currentUsage + 1).apply()
        }
    }

    fun incrementDailyUsage(context: Context, uid: String?, type: DailyQuotaType, tierLimit: Int = 1) {
        consumeQuota(context, uid, type, tierLimit)
    }

    fun grantBonusAccess(context: Context, uid: String?, type: DailyQuotaType, count: Int = 1) {
        val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        val dateKey = getTodayDateKey()
        val current = prefs.getInt(getBonusPrefKey(uid, type, dateKey), 0)
        prefs.edit().putInt(getBonusPrefKey(uid, type, dateKey), current + count).apply()
    }

    /**
     * Shows a friendly, non-technical soft gate dialog when daily quota is exhausted.
     * Free Tier -> Upgrade to PRO with Watch Ad option
     * PRO Tier -> Upgrade to VIP with Watch Ad option
     */
    fun showQuotaExhaustedDialog(
        activity: android.app.Activity,
        quotaType: DailyQuotaType,
        currentTierId: String,
        onBonusGranted: () -> Unit
    ) {
        val isPro = currentTierId.equals("pro", ignoreCase = true)
        val isVip = currentTierId.equals("vip", ignoreCase = true)
        val uid = SupabaseAuthService.getCurrentUserId()

        val typeName = when (quotaType) {
            DailyQuotaType.AI_PARSE -> "AI parsing"
            DailyQuotaType.PHOTO_SCAN -> "photo scanning"
            DailyQuotaType.INTERNET_SEARCH -> "internet search"
        }

        val bonusTypeName = when (quotaType) {
            DailyQuotaType.AI_PARSE -> "AI parse"
            DailyQuotaType.PHOTO_SCAN -> "photo scan"
            DailyQuotaType.INTERNET_SEARCH -> "internet search"
        }

        val title: String
        val message: String
        val positiveBtnText: String
        val positiveAction: () -> Unit

        if (!isPro && !isVip) {
            // Free Tier user
            title = "Upgrade to PRO"
            message = "You have used all your daily $typeName limit for today. You can watch a short ad to get 1 bonus access for $bonusTypeName right away, or upgrade to PRO for higher daily limits."
            positiveBtnText = "UPGRADE"
            positiveAction = {
                SubscriptionPaywallDialog.show(activity, "pro")
            }
        } else if (isPro && SubscriptionConfig.IS_VIP_VISIBLE) {
            // PRO Tier user (When VIP is enabled)
            title = "Upgrade to VIP"
            message = "You have used all your daily PRO limit for $typeName. You can watch a short ad to get 1 bonus access for $bonusTypeName right away, or upgrade to VIP for unlimited daily limits."
            positiveBtnText = "UPGRADE"
            positiveAction = {
                SubscriptionPaywallDialog.show(activity, "vip")
            }
        } else {
            // VIP Tier or PRO tier when VIP is hidden
            title = "Daily Limit Reached"
            message = "You have reached your daily $typeName limit. You can watch a short ad to get 1 bonus access for $bonusTypeName right away."
            positiveBtnText = "Close"
            positiveAction = {}
        }

        ReusableDialogHelper.showCustomDialog(
            context = activity,
            title = title,
            message = message,
            positiveButtonText = positiveBtnText,
            positiveAction = positiveAction,
            negativeButtonText = "Watch Ad",
            negativeAction = {
                grantBonusAccess(activity, uid, quotaType, 1)
                android.widget.Toast.makeText(
                    activity,
                    "1 bonus access added for today.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                onBonusGranted.invoke()
            }
        )
    }

    /**
     * Claims the promotional PRO Tier for the current user.
     * Synchronizes promotional expiration timestamp with the Admin configuration (e.g. promo_end_at or +30 days).
     */
    suspend fun claimPromotionalProTier(context: Context): Result<UserSubscriptionDetails> = withContext(Dispatchers.IO) {
        try {
            val userId = SupabaseAuthService.getCurrentUserId()
                ?: return@withContext Result.failure(IllegalStateException("User is not authenticated"))

            // 1. Fetch live tier configs from Supabase
            val configs = fetchLiveTierConfigs(context)
            val proConfig = configs["pro"]

            // 2. Resolve dynamic promotional expiration date
            val promoEndAtIso: String = if (!proConfig?.promo_end_at.isNullOrBlank()) {
                proConfig!!.promo_end_at!!
            } else {
                val expMs = System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).format(Date(expMs))
            }

            // 3. Update user subscription record in Supabase
            val updateSuccess = updateUserSubscription(
                context = context,
                newTierId = "pro",
                durationDays = null,
                isAutoRenew = false
            )

            // Override explicit promo expiry date if admin configured one
            if (!proConfig?.promo_end_at.isNullOrBlank()) {
                try {
                    val customExpiryPayload = buildJsonObject {
                        put("subscription_expires_at", promoEndAtIso)
                    }
                    SupabaseProvider.client.postgrest["app_users"].update(customExpiryPayload) {
                        filter { eq("id", userId) }
                    }
                    SupabaseProvider.client.postgrest["stores"].update(customExpiryPayload) {
                        filter { eq("billing_owner_id", userId) }
                    }
                } catch (e: Exception) {
                    Log.w("SubscriptionManager", "Non-critical: Could not set exact promo_end_at timestamp: ${e.message}")
                }
            }

            // 4. Log ₱0 promotional payment record
            try {
                val paymentPayload = buildJsonObject {
                    put("user_id", userId)
                    put("tier_id", "pro")
                    put("amount", 0.0)
                    put("currency", "PHP")
                    put("payment_method", "promotional_claim")
                    put("status", "paid")
                }
                SupabaseProvider.client.postgrest["subscription_payments"].insert(paymentPayload)
            } catch (e: Exception) {
                Log.w("SubscriptionManager", "Non-critical: Could not insert promotional record into subscription_payments: ${e.message}")
            }

            val details = calculateSubscriptionDetails("pro", promoEndAtIso)
            Result.success(details)
        } catch (e: Exception) {
            Log.e("SubscriptionManager", "Error claiming promotional PRO tier", e)
            Result.failure(e)
        }
    }

    /**
     * Launches the bespoke Presyohan Web Checkout page for PayMongo payments.
     */
    fun openWebCheckout(context: Context, tierId: String = "pro") {
        val uid = SupabaseAuthService.getCurrentUserId() ?: ""
        val baseUrl = "https://presyohan.onrender.com/checkout"
        val checkoutUrl = "$baseUrl?tier=${tierId.lowercase()}&uid=$uid"
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(checkoutUrl))
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Could not open checkout browser: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
