package com.presyohan.app

import android.content.Context
import io.github.jan.supabase.auth.auth

object SessionManager {
    private const val PREFS_NAME = "presyo_prefs"
    
    const val KEY_LAST_SCREEN = "last_screen"
    const val KEY_STORE_ID = "last_store_id"
    const val KEY_STORE_NAME = "last_store_name"

    const val SCREEN_HOME = "home"
    const val SCREEN_STORE = "store"
    const val SCREEN_CUSTOMER_HOME = "customer_home"

    fun getScopedKey(context: Context, key: String): String {
        val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id
        return if (!uid.isNullOrBlank()) {
            "${uid}_${key}"
        } else {
            key
        }
    }

    fun markStoreList(context: Context) {
        val key = getScopedKey(context, KEY_LAST_SCREEN)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, SCREEN_STORE)
            .putString(KEY_LAST_SCREEN, SCREEN_STORE)
            .apply()
    }

    fun markCustomerHome(context: Context) {
        val key = getScopedKey(context, KEY_LAST_SCREEN)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, SCREEN_CUSTOMER_HOME)
            .putString(KEY_LAST_SCREEN, SCREEN_CUSTOMER_HOME)
            .apply()
    }

    fun markStoreHome(context: Context, storeId: String?, storeName: String?) {
        val keyLastScreen = getScopedKey(context, KEY_LAST_SCREEN)
        val keyStoreId = getScopedKey(context, KEY_STORE_ID)
        val keyStoreName = getScopedKey(context, KEY_STORE_NAME)

        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(keyLastScreen, SCREEN_HOME)
            .putString(KEY_LAST_SCREEN, SCREEN_HOME)
        if (!storeId.isNullOrBlank() && !storeName.isNullOrBlank()) {
            editor.putString(keyStoreId, storeId)
            editor.putString(keyStoreName, storeName)
            editor.putString(KEY_STORE_ID, storeId)
            editor.putString(KEY_STORE_NAME, storeName)
        }
        editor.apply()
    }
}
