package com.presyohan.app

import android.content.Context

object SessionManager {
    private const val PREFS_NAME = "presyo_prefs"
    private const val KEY_LAST_SCREEN = "last_screen"
    private const val KEY_STORE_ID = "last_store_id"
    private const val KEY_STORE_NAME = "last_store_name"

    const val SCREEN_HOME = "home"
    const val SCREEN_STORE = "store"

    fun markStoreList(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SCREEN, SCREEN_STORE)
            .apply()
    }

    fun markStoreHome(context: Context, storeId: String?, storeName: String?) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SCREEN, SCREEN_HOME)
        if (!storeId.isNullOrBlank() && !storeName.isNullOrBlank()) {
            editor.putString(KEY_STORE_ID, storeId)
            editor.putString(KEY_STORE_NAME, storeName)
        }
        editor.apply()
    }
}

