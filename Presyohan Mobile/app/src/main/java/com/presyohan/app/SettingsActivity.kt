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
    private lateinit var loadingOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        loadingOverlay = LoadingOverlayHelper.attach(this)

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
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(
                    android.app.Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.slide_in_up,
                    R.anim.stay
                )
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.slide_in_up, R.anim.stay)
            }
            finish()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                overrideActivityTransition(
                    android.app.Activity.OVERRIDE_TRANSITION_CLOSE,
                    R.anim.stay,
                    R.anim.stay
                )
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(R.anim.stay, R.anim.stay)
            }
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
            showReusableDialog(
                title = "Logout",
                message = "Are you sure you want to log out?\n\nYou can sign back in anytime. See you again soon!",
                positiveButtonText = "Logout",
                positiveAction = {
                    performLogout()
                },
                negativeButtonText = "Cancel"
            )
        }
    }

    private fun performLogout() {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                kotlinx.coroutines.withTimeoutOrNull(2500) {
                    SupabaseAuthService.signOut()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
                val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
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
