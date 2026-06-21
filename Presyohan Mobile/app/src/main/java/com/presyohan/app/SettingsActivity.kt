package com.presyohan.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var settingsUserName: TextView
    private lateinit var settingsUserEmail: TextView
    private lateinit var settingsUserId: TextView
    private lateinit var settingsUserAvatar: ImageView
    private lateinit var btnEditProfile: ImageView
    private lateinit var btnAccountSecurity: View
    private lateinit var btnMemberships: View
    private lateinit var btnAtongPresyohan: View
    private lateinit var btnSupport: View
    private lateinit var btnContactUs: View
    private lateinit var btnLogout: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Initialize Views
        btnBack = findViewById(R.id.btnBack)
        settingsUserName = findViewById(R.id.settingsUserName)
        settingsUserEmail = findViewById(R.id.settingsUserEmail)
        settingsUserId = findViewById(R.id.settingsUserId)
        settingsUserAvatar = findViewById(R.id.settingsUserAvatar)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        btnAccountSecurity = findViewById(R.id.btnAccountSecurity)
        btnMemberships = findViewById(R.id.btnMemberships)
        btnAtongPresyohan = findViewById(R.id.btnAtongPresyohan)
        btnSupport = findViewById(R.id.btnSupport)
        btnContactUs = findViewById(R.id.btnContactUs)
        btnLogout = findViewById(R.id.btnLogout)

        // Back action
        btnBack.setOnClickListener {
            finish()
        }

        // Load profile data
        loadUserProfile()

        // Edit Profile
        btnEditProfile.setOnClickListener {
            Toast.makeText(this, "Profile edit is coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Account Security
        btnAccountSecurity.setOnClickListener {
            Toast.makeText(this, "Security settings coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Memberships
        btnMemberships.setOnClickListener {
            Toast.makeText(this, "Memberships management coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Navigate back to Atong Presyohan? (Staff StoreActivity)
        btnAtongPresyohan.setOnClickListener {
            val intent = Intent(this, StoreActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // Support
        btnSupport.setOnClickListener {
            Toast.makeText(this, "Support center coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Contact Us
        btnContactUs.setOnClickListener {
            Toast.makeText(this, "Contact details coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Logout action
        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                try {
                    SupabaseAuthService.signOut()
                    val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, "Logout failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadUserProfile() {
        // Fallback display email immediately
        val currentUser = SupabaseProvider.client.auth.currentUserOrNull()
        settingsUserEmail.text = currentUser?.email ?: "No email"
        settingsUserName.text = SupabaseAuthService.getDisplayNameImmediate()
        settingsUserId.visibility = View.GONE
        settingsUserAvatar.setImageResource(R.drawable.avatar_default)
        settingsUserAvatar.setColorFilter(ContextCompat.getColor(this, android.R.color.white))

        lifecycleScope.launch {
            val profile = SupabaseAuthService.getUserProfile()
            if (profile != null) {
                if (!profile.name.isNullOrBlank()) {
                    settingsUserName.text = profile.name
                }
                if (!profile.user_code.isNullOrBlank()) {
                    settingsUserId.text = "ID: ${profile.user_code.uppercase()}"
                    settingsUserId.visibility = View.VISIBLE
                }
                if (!profile.avatar_url.isNullOrBlank()) {
                    settingsUserAvatar.clearColorFilter()
                    settingsUserAvatar.load(profile.avatar_url) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        error(R.drawable.avatar_default)
                    }
                }
            }
        }
    }
}
