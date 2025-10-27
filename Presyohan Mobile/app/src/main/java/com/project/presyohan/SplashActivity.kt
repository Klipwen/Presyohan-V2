package com.project.presyohan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)

        if (SupabaseAuthService.isLoggedIn()) {
            val prefs = getSharedPreferences("presyo_prefs", MODE_PRIVATE)
            val lastScreen = prefs.getString("last_screen", "home")
            if (lastScreen == "store") {
                startActivity(Intent(this, StoreActivity::class.java))
            } else if (lastScreen == "home") {
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
            finish()
            return
        }
        setContentView(R.layout.activity_splash)

        val getStartedButton = findViewById<Button>(R.id.buttonGetStarted)
        getStartedButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
