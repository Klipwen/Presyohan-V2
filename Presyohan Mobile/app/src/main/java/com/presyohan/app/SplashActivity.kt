package com.presyohan.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_splash)

        val getStartedButton = findViewById<Button>(R.id.buttonGetStarted)
        getStartedButton.setOnClickListener {
            if (SupabaseAuthService.isLoggedIn()) {
                val prefs = getSharedPreferences("presyo_prefs", MODE_PRIVATE)
                val lastScreen = prefs.getString("last_screen", SessionManager.SCREEN_STORE)
                if (lastScreen == SessionManager.SCREEN_STORE) {
                    startActivity(Intent(this, StoreActivity::class.java))
                } else {
                    val storeId = prefs.getString("last_store_id", null)
                    val storeName = prefs.getString("last_store_name", null)
                    if (storeId != null && storeName != null) {
                        val intent = Intent(this, HomeActivity::class.java)
                        intent.putExtra("storeId", storeId)
                        intent.putExtra("storeName", storeName)
                        startActivity(intent)
                    } else {
                        startActivity(Intent(this, StoreActivity::class.java))
                    }
                }
            } else {
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
            }
            finish()
        }
        // No auto-routing; let user decide next action
    }
}
