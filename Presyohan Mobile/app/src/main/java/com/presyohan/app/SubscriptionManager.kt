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
    val billing_period: String = "month",
    val trial_days: Int = 0,
    val max_stores: Int = 1,
    val max_items_per_store: Int = 50,
    val max_staff_per_store: Int = 1,
    val max_ai_parses_per_day: Int = 2,
    val max_photo_scans_per_day: Int = 2,
    val max_suki_partners: Int = 5,
    val has_excel_export: Boolean = false,
    val has_pdf_export: Boolean = false,
    val has_price_cloning: Boolean = false,
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
    val publicItemsLimit: Int,
    val internetSearchQuota: Int,
    val allowPriceCloning: Boolean,
    val allowExcelExport: Boolean,
    val allowPdfExport: Boolean,
    val allowCustomerPairing: Boolean,
    val hasPrioritySupport: Boolean = false,
    val description: String,
    @param:DrawableRes val iconRes: Int = 0,
    val priceValue: Double = 0.0,
    val trialDays: Int = 0,
    val merchantBenefits: List<String> = emptyList(),
    val customerBenefits: List<String> = emptyList()
)

object SubscriptionManager {

    var liveTierConfigs: Map<String, SubscriptionTierDbRow> = emptyMap()

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
                val nameVal = json["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val trialDaysVal = json["trial_days"]?.jsonPrimitive?.intOrNull ?: 0
                val maxStoresVal = json["max_stores"]?.jsonPrimitive?.intOrNull ?: 1
                val maxItemsVal = json["max_items_per_store"]?.jsonPrimitive?.intOrNull ?: 50
                val maxStaffVal = json["max_staff_per_store"]?.jsonPrimitive?.intOrNull ?: 1
                val maxAiVal = json["max_ai_parses_per_day"]?.jsonPrimitive?.intOrNull ?: 2
                val maxPhotoVal = json["max_photo_scans_per_day"]?.jsonPrimitive?.intOrNull ?: 2
                val maxSukiVal = json["max_suki_partners"]?.jsonPrimitive?.intOrNull ?: 5
                val hasExcelVal = json["has_excel_export"]?.jsonPrimitive?.booleanOrNull ?: false
                val hasPdfVal = json["has_pdf_export"]?.jsonPrimitive?.booleanOrNull ?: false
                val hasPriceCloneVal = json["has_price_cloning"]?.jsonPrimitive?.booleanOrNull ?: false
                val hasPriorityVal = json["has_priority_support"]?.jsonPrimitive?.booleanOrNull ?: false

                val merchantBen = json["merchant_benefits"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                val customerBen = json["customer_benefits"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                resultMap[tid] = SubscriptionTierDbRow(
                    tier_id = tid,
                    name = nameVal,
                    price = priceVal,
                    trial_days = trialDaysVal,
                    max_stores = maxStoresVal,
                    max_items_per_store = maxItemsVal,
                    max_staff_per_store = maxStaffVal,
                    max_ai_parses_per_day = maxAiVal,
                    max_photo_scans_per_day = maxPhotoVal,
                    max_suki_partners = maxSukiVal,
                    has_excel_export = hasExcelVal,
                    has_pdf_export = hasPdfVal,
                    has_price_cloning = hasPriceCloneVal,
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
                membersPerStoreLimit = db?.max_staff_per_store ?: 1,
                categoriesPerStoreLimit = 10,
                itemsPerStoreLimit = db?.max_items_per_store ?: 50,
                aiQuotaDaily = db?.max_ai_parses_per_day ?: 2,
                sukiLimit = db?.max_suki_partners ?: 5,
                publicItemsLimit = 5,
                internetSearchQuota = db?.max_ai_parses_per_day ?: 2,
                allowPriceCloning = db?.has_price_cloning ?: false,
                allowExcelExport = db?.has_excel_export ?: false,
                allowPdfExport = db?.has_pdf_export ?: false,
                allowCustomerPairing = false,
                hasPrioritySupport = db?.has_priority_support ?: false,
                description = "Ideal for micro sari-sari stores & single vendors.",
                iconRes = 0,
                priceValue = db?.price ?: 0.0,
                trialDays = db?.trial_days ?: 0,
                merchantBenefits = db?.merchant_benefits ?: listOf("1 Store Branch", "50 Items / Store", "1 Staff Account", "2 AI Parses / day"),
                customerBenefits = db?.customer_benefits ?: listOf("5 Suking Tindahan Partners", "Basic Price Search")
            )
        }

    val TIER_PRO: SubscriptionTierInfo
        get() {
            val db = liveTierConfigs["pro"]
            return SubscriptionTierInfo(
                id = "pro",
                name = db?.name ?: "PRO Tier",
                priceText = "₱${db?.price?.toInt() ?: 100}",
                periodText = "/ month",
                storeLimit = db?.max_stores ?: 10,
                membersPerStoreLimit = db?.max_staff_per_store ?: 10,
                categoriesPerStoreLimit = 25,
                itemsPerStoreLimit = db?.max_items_per_store ?: 500,
                aiQuotaDaily = db?.max_ai_parses_per_day ?: 10,
                sukiLimit = db?.max_suki_partners ?: 15,
                publicItemsLimit = 15,
                internetSearchQuota = db?.max_ai_parses_per_day ?: 10,
                allowPriceCloning = db?.has_price_cloning ?: true,
                allowExcelExport = db?.has_excel_export ?: true,
                allowPdfExport = db?.has_pdf_export ?: true,
                allowCustomerPairing = true,
                hasPrioritySupport = db?.has_priority_support ?: false,
                description = "Ideal for growing single & multi-branch retail stores.",
                iconRes = R.drawable.icon_pro,
                priceValue = db?.price ?: 100.0,
                trialDays = db?.trial_days ?: 7,
                merchantBenefits = db?.merchant_benefits ?: listOf("Up to 10 Stores", "500 items / store branch", "10 staffs / store branch", "10 AI parses / day", "10 photo scans / day"),
                customerBenefits = db?.customer_benefits ?: listOf("15 partner stores", "15 Presyohan Stores", "AI Online Price Search")
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
                storeLimit = if ((db?.max_stores ?: 999) > 900) Int.MAX_VALUE else (db?.max_stores ?: 999),
                membersPerStoreLimit = if ((db?.max_staff_per_store ?: 999) > 900) Int.MAX_VALUE else (db?.max_staff_per_store ?: 999),
                categoriesPerStoreLimit = Int.MAX_VALUE,
                itemsPerStoreLimit = if ((db?.max_items_per_store ?: 9999) > 9000) Int.MAX_VALUE else (db?.max_items_per_store ?: 9999),
                aiQuotaDaily = db?.max_ai_parses_per_day ?: 50,
                sukiLimit = if ((db?.max_suki_partners ?: 999) > 900) Int.MAX_VALUE else (db?.max_suki_partners ?: 999),
                publicItemsLimit = Int.MAX_VALUE,
                internetSearchQuota = db?.max_ai_parses_per_day ?: 50,
                allowPriceCloning = db?.has_price_cloning ?: true,
                allowExcelExport = db?.has_excel_export ?: true,
                allowPdfExport = db?.has_pdf_export ?: true,
                allowCustomerPairing = true,
                hasPrioritySupport = db?.has_priority_support ?: true,
                description = "Ideal for high-volume businesses & enterprise managers.",
                iconRes = R.drawable.icon_vip,
                priceValue = db?.price ?: 299.0,
                trialDays = db?.trial_days ?: 0,
                merchantBenefits = db?.merchant_benefits ?: listOf("Unlimited Stores", "Unlimited items / store", "Unlimited staff / store", "50 AI parses / day"),
                customerBenefits = db?.customer_benefits ?: listOf("Unlimited partner stores", "Unlimited Presyohan Stores", "AI Online Price Search + Priority")
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


