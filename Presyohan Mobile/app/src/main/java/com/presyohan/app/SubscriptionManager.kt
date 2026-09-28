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
            return if (fp % 1.0 == 0.0) "₱${fp.toInt()}" else "₱%.2f".format(fp)
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
}

object SubscriptionManager {

    var liveTierConfigs: Map<String, SubscriptionTierDbRow> = emptyMap()

    fun parseIsoToEpochMs(isoString: String?): Long? {
        if (isoString.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            try {
                val cleanDate = isoString.substringBefore("T").substringBefore(" ")
                val date = java.time.LocalDate.parse(cleanDate)
                date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                null
            }
        }
    }

    fun formatIsoToShortDate(isoString: String?): String? {
        if (isoString.isNullOrBlank()) return null
        return try {
            val instant = java.time.Instant.parse(isoString)
            val zdt = instant.atZone(java.time.ZoneId.systemDefault())
            java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy").format(zdt)
        } catch (e: Exception) {
            try {
                val cleanDate = isoString.substringBefore("T").substringBefore(" ")
                val date = java.time.LocalDate.parse(cleanDate)
                java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy").format(date)
            } catch (e2: Exception) {
                null
            }
        }
    }

    suspend fun fetchLiveTierConfigs(): Map<String, SubscriptionTierDbRow> = withContext(Dispatchers.IO) {
        try {
            val list = SupabaseProvider.client.postgrest["subscription_tiers"]
                .select()
                .decodeList<JsonObject>()

            Log.d("SubscriptionManager", "Raw JSON fetched from Supabase: ${list.size} rows")

            val resultMap = mutableMapOf<String, SubscriptionTierDbRow>()
            for (json in list) {
                val tid = json["tier_id"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: continue
                val priceVal = json["price"]?.jsonPrimitive?.doubleOrNull
                    ?: json["price"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                    ?: 0.0
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
                val nameVal = json["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val trialDaysVal = json["trial_days"]?.jsonPrimitive?.intOrNull ?: 0
                val maxStoresVal = json["max_stores"]?.jsonPrimitive?.intOrNull ?: 1
                val maxItemsVal = json["max_items_per_store"]?.jsonPrimitive?.intOrNull ?: 100
                val maxStaffVal = json["max_staff_per_store"]?.jsonPrimitive?.intOrNull ?: 3
                val maxCatVal = json["max_categories_per_store"]?.jsonPrimitive?.intOrNull ?: 10
                val maxAiVal = json["max_ai_parses_per_day"]?.jsonPrimitive?.intOrNull ?: 3
                val maxPhotoVal = json["max_photo_scans_per_day"]?.jsonPrimitive?.intOrNull ?: 3
                val maxSukiVal = json["max_suki_partners"]?.jsonPrimitive?.intOrNull ?: 5
                val maxPresyohanVal = json["max_presyohan_stores"]?.jsonPrimitive?.intOrNull ?: 5
                val maxSearchVal = json["max_internet_searches_per_day"]?.jsonPrimitive?.intOrNull ?: 3

                val hasExcelVal = json["has_excel_export"]?.jsonPrimitive?.booleanOrNull ?: false
                val hasPdfVal = json["has_pdf_export"]?.jsonPrimitive?.booleanOrNull ?: false
                val hasNotesVal = json["has_notes_export"]?.jsonPrimitive?.booleanOrNull ?: true
                val hasPriceCloneVal = json["has_price_cloning"]?.jsonPrimitive?.booleanOrNull ?: false
                val hasPairingVal = json["has_customer_pairing"]?.jsonPrimitive?.booleanOrNull ?: false
                val hasPriorityVal = json["has_priority_support"]?.jsonPrimitive?.booleanOrNull ?: false

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
            resultMap
        } catch (e: Exception) {
            Log.e("SubscriptionManager", "Error fetching subscription_tiers from Supabase: ${e.message}", e)
            emptyMap()
        }
    }

    val TIER_FREE: SubscriptionTierInfo
        get() {
            val db = liveTierConfigs["free"]
            return SubscriptionTierInfo(
                id = "free",
                name = db?.name ?: "Free Tier",
                priceText = "₱${db?.price?.toInt() ?: 0}",
                periodText = "forever",
                storeLimit = db?.max_stores ?: 1,
                membersPerStoreLimit = db?.max_staff_per_store ?: 3,
                categoriesPerStoreLimit = db?.max_categories_per_store ?: 10,
                itemsPerStoreLimit = db?.max_items_per_store ?: 100,
                aiQuotaDaily = db?.max_ai_parses_per_day ?: 3,
                sukiLimit = db?.max_suki_partners ?: 5,
                presyohanStoresLimit = db?.max_presyohan_stores ?: 5,
                publicItemsLimit = 5,
                internetSearchQuota = db?.max_internet_searches_per_day ?: 3,
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
                customerBenefits = db?.customer_benefits ?: listOf("5 Suking Tindahan", "5 Presyohan Stores", "3 Internet Searches / day"),
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
                storeLimit = db?.max_stores ?: 10,
                membersPerStoreLimit = db?.max_staff_per_store ?: 10,
                categoriesPerStoreLimit = db?.max_categories_per_store ?: 25,
                itemsPerStoreLimit = db?.max_items_per_store ?: 500,
                aiQuotaDaily = db?.max_ai_parses_per_day ?: 10,
                sukiLimit = db?.max_suki_partners ?: 15,
                presyohanStoresLimit = db?.max_presyohan_stores ?: 15,
                publicItemsLimit = 15,
                internetSearchQuota = db?.max_internet_searches_per_day ?: 15,
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
                merchantBenefits = db?.merchant_benefits ?: listOf("Up to 10 Stores", "10 Staffs / Store", "25 Categories / Store", "500 Items / Store", "10 AI Parses / day", "Store Items Cloning", "Customer Pairing", "Convert to Excel & PDF"),
                customerBenefits = db?.customer_benefits ?: listOf("15 Suking Tindahan", "15 Presyohan Stores", "15 Internet Searches / day"),
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
                storeLimit = if ((db?.max_stores ?: 999999) > 90000) Int.MAX_VALUE else (db?.max_stores ?: 999999),
                membersPerStoreLimit = if ((db?.max_staff_per_store ?: 999999) > 90000) Int.MAX_VALUE else (db?.max_staff_per_store ?: 999999),
                categoriesPerStoreLimit = if ((db?.max_categories_per_store ?: 999999) > 90000) Int.MAX_VALUE else (db?.max_categories_per_store ?: 999999),
                itemsPerStoreLimit = if ((db?.max_items_per_store ?: 999999) > 90000) Int.MAX_VALUE else (db?.max_items_per_store ?: 999999),
                aiQuotaDaily = db?.max_ai_parses_per_day ?: 50,
                sukiLimit = if ((db?.max_suki_partners ?: 999999) > 90000) Int.MAX_VALUE else (db?.max_suki_partners ?: 999999),
                presyohanStoresLimit = if ((db?.max_presyohan_stores ?: 999999) > 90000) Int.MAX_VALUE else (db?.max_presyohan_stores ?: 999999),
                publicItemsLimit = Int.MAX_VALUE,
                internetSearchQuota = if ((db?.max_internet_searches_per_day ?: 999999) > 90000) Int.MAX_VALUE else (db?.max_internet_searches_per_day ?: 999999),
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
                merchantBenefits = db?.merchant_benefits ?: listOf("Unlimited Stores", "Unlimited Staff / Store", "Unlimited Categories / Store", "Unlimited Items / Store", "50 AI Parses / day", "Unlimited Cloning & Export", "Unlimited Customer Pairing"),
                customerBenefits = db?.customer_benefits ?: listOf("Unlimited Suking Tindahan", "Unlimited Presyohan Stores", "Unlimited Internet Search"),
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

    fun getCachedTier(context: Context): SubscriptionTierInfo {
        val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        val tierId = prefs.getString("user_subscription_tier", "free") ?: "free"
        return getTierInfo(tierId)
    }

    fun saveCachedTier(context: Context, tierId: String) {
        val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("user_subscription_tier", tierId.lowercase()).apply()
    }

    suspend fun fetchUserTier(context: Context): SubscriptionTierInfo = withContext(Dispatchers.IO) {
        val userId = SupabaseAuthService.getCurrentUserId() ?: return@withContext getCachedTier(context)
        try {
            val profile = SupabaseAuthService.getUserProfile()
            val tierId = profile?.subscription_tier ?: "free"
            saveCachedTier(context, tierId)
            return@withContext getTierInfo(tierId)
        } catch (e: Exception) {
            return@withContext getCachedTier(context)
        }
    }

    suspend fun updateUserSubscription(context: Context, newTierId: String): Boolean = withContext(Dispatchers.IO) {
        val userId = SupabaseAuthService.getCurrentUserId() ?: return@withContext false
        val sanitizedTier = newTierId.lowercase()
        try {
            val payload = buildJsonObject {
                put("subscription_tier", sanitizedTier)
            }
            SupabaseProvider.client.postgrest["app_users"].update(payload) {
                filter { eq("id", userId) }
            }
            saveCachedTier(context, sanitizedTier)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            saveCachedTier(context, sanitizedTier)
            true
        }
    }

    fun formatLimitText(limit: Int): String {
        return if (limit == Int.MAX_VALUE) "Unlimited" else limit.toString()
    }
}
