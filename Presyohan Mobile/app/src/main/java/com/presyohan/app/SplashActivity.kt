package com.presyohan.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@kotlinx.serialization.Serializable
private data class AppReleaseRow(
    val version_code: Int,
    val version_name: String,
    val download_url: String,
    val whats_new: String,
    val is_forced: Boolean
)

class SplashActivity : Activity() {
    private var isForcedUpdateActive = false
    private var isCheckingUpdates = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_splash)

        val getStartedButton = findViewById<Button>(R.id.buttonGetStarted)
        
        // Defer click actions until update check completes
        getStartedButton.isEnabled = false
        getStartedButton.text = "Checking updates..."

        // Run update check on launch
        CoroutineScope(Dispatchers.Main).launch {
            checkForUpdates(getStartedButton)
        }
    }

    private suspend fun checkForUpdates(getStartedButton: Button) {
        val latestRelease = withContext(Dispatchers.IO) {
            try {
                SupabaseProvider.client.postgrest["app_releases"]
                    .select {
                        order("version_code", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<AppReleaseRow>()
                    .firstOrNull()
            } catch (e: Exception) {
                null
            }
        }

        isCheckingUpdates = false
        getStartedButton.isEnabled = true
        getStartedButton.text = "Get Started"

        if (latestRelease != null) {
            val currentVersionCode = try {
                val pInfo = packageManager.getPackageInfo(packageName, 0)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    pInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode
                }
            } catch (e: Exception) {
                1
            }

            if (latestRelease.version_code > currentVersionCode) {
                if (latestRelease.is_forced) {
                    // Show Full-Screen Forced Update UI blocker
                    showForcedUpdateOverlay(latestRelease)
                    return
                } else {
                    // Show Optional Update Dialog
                    showOptionalUpdateDialog(latestRelease, getStartedButton)
                    return
                }
            }
        }

        // Configure default button listener if no updates block proceeding
        setupGetStartedNavigation(getStartedButton)
    }

    private fun showForcedUpdateOverlay(release: AppReleaseRow) {
        isForcedUpdateActive = true

        val rootLayout = findViewById<ViewGroup>(android.R.id.content)
        
        // Dynamic Premium Dark Layout Container
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0f172a")) // Dark slate
            setPadding(80, 80, 80, 80)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Title
        val titleView = TextView(this).apply {
            text = "Update Required"
            setTextColor(Color.WHITE)
            textSize = 24f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 40)
            layoutParams = params
        }

        // Description
        val descView = TextView(this).apply {
            text = "A new version of Presyohan is available (v${release.version_name}). Please update to continue using the application."
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 15f
            gravity = Gravity.CENTER
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 48)
            layoutParams = params
        }

        // Release notes box
        val releaseNotesLabel = TextView(this).apply {
            text = "What's New in v${release.version_name}:"
            setTextColor(Color.parseColor("#ff8c00"))
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(20, 0, 0, 12)
            layoutParams = params
        }

        val releaseNotesBody = TextView(this).apply {
            text = release.whats_new
            setTextColor(Color.parseColor("#cbd5e1"))
            textSize = 13f
            setBackgroundColor(Color.parseColor("#1e293b"))
            setPadding(32, 24, 32, 24)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(0, 0, 0, 60)
            layoutParams = params
        }

        // Action Button
        val updateButton = Button(this).apply {
            text = "UPDATE NOW"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#ff8c00")) // Orange
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.download_url))
                startActivity(intent)
            }
        }

        container.addView(titleView)
        container.addView(descView)
        container.addView(releaseNotesLabel)
        container.addView(releaseNotesBody)
        container.addView(updateButton)

        rootLayout.addView(container)
    }

    private fun showOptionalUpdateDialog(release: AppReleaseRow, getStartedButton: Button) {
        AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("A new version of Presyohan is available (v${release.version_name}). Would you like to update now?\n\nWhat's New:\n${release.whats_new}")
            .setCancelable(false)
            .setPositiveButton("Update Now") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.download_url))
                startActivity(intent)
                setupGetStartedNavigation(getStartedButton)
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
                setupGetStartedNavigation(getStartedButton)
            }
            .show()
    }

    private fun setupGetStartedNavigation(getStartedButton: Button) {
        getStartedButton.setOnClickListener {
            // Trigger background heartbeat if logged in
            if (SupabaseAuthService.isLoggedIn()) {
                CoroutineScope(Dispatchers.IO).launch {
                    SupabaseAuthService.updateUserHeartbeat()
                }

                val prefs = getSharedPreferences("presyo_prefs", MODE_PRIVATE)
                val lastScreenKey = SessionManager.getScopedKey(this, SessionManager.KEY_LAST_SCREEN)
                val lastScreen = prefs.getString(lastScreenKey, SessionManager.SCREEN_STORE)
                if (lastScreen == SessionManager.SCREEN_CUSTOMER_HOME) {
                    startActivity(Intent(this, CustomerHomeActivity::class.java))
                } else if (lastScreen == SessionManager.SCREEN_STORE) {
                    startActivity(Intent(this, StoreActivity::class.java))
                } else {
                    val storeIdKey = SessionManager.getScopedKey(this, SessionManager.KEY_STORE_ID)
                    val storeNameKey = SessionManager.getScopedKey(this, SessionManager.KEY_STORE_NAME)
                    val storeId = prefs.getString(storeIdKey, null)
                    val storeName = prefs.getString(storeNameKey, null)
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
    }

    override fun onBackPressed() {
        if (isForcedUpdateActive) {
            // In a forced update state, back exits the application completely
            finishAffinity()
        } else if (!isCheckingUpdates) {
            super.onBackPressed()
        }
    }
}
