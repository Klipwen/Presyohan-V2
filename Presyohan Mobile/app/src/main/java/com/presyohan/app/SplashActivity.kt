package com.presyohan.app

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SplashActivity : Activity() {
    private var isNavigating = false
    private var loadingAnimators = mutableListOf<android.animation.Animator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
        setContentView(R.layout.activity_splash)

        // Make activity full screen (hide status bar)
        try {
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
            } else {
                window.setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val getStartedButton = findViewById<Button>(R.id.buttonGetStarted)
        setupGetStartedNavigation(getStartedButton)

        // Check for pre-auth maintenance broadcasts (if any active announcement)
        val uiScope = CoroutineScope(Dispatchers.Main)
        ReusableDialogHelper.checkAndShowMaintenanceBroadcast(this@SplashActivity, uiScope)
    }

    private fun startDotsAnimation() {
        loadingAnimators.forEach { it.cancel() }
        loadingAnimators.clear()

        val dot1 = findViewById<View>(R.id.dot1) ?: return
        val dot2 = findViewById<View>(R.id.dot2) ?: return
        val dot3 = findViewById<View>(R.id.dot3) ?: return

        val dots = listOf(dot1, dot2, dot3)
        dots.forEachIndexed { index, dot ->
            dot.translationY = 0f
            val animator = android.animation.ObjectAnimator.ofFloat(dot, "translationY", 0f, -20f, 0f)
            animator.duration = 600
            animator.repeatMode = android.animation.ValueAnimator.REVERSE
            animator.repeatCount = android.animation.ValueAnimator.INFINITE
            animator.startDelay = index * 150L
            animator.start()
            loadingAnimators.add(animator)
        }
    }

    private fun stopDotsAnimation() {
        loadingAnimators.forEach { it.cancel() }
        loadingAnimators.clear()
        val dot1 = findViewById<View>(R.id.dot1)
        val dot2 = findViewById<View>(R.id.dot2)
        val dot3 = findViewById<View>(R.id.dot3)
        listOfNotNull(dot1, dot2, dot3).forEach { it.translationY = 0f }
    }

    private fun setupGetStartedNavigation(getStartedButton: Button) {
        getStartedButton.setOnClickListener {
            if (isNavigating) return@setOnClickListener
            isNavigating = true

            // Disable button to prevent spamming
            getStartedButton.isEnabled = false
            getStartedButton.isClickable = false
            getStartedButton.text = ""

            // Show dancing dots
            val loadingDotsContainer = findViewById<View>(R.id.loadingDotsContainer)
            loadingDotsContainer?.visibility = View.VISIBLE
            startDotsAnimation()

            runSplashNavigation()
        }
    }

    private fun runSplashNavigation() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                SupabaseProvider.client.auth.awaitInitialization()
            } catch (e: Exception) {
                android.util.Log.e("SplashActivity", "Error awaiting auth initialization: ${e.message}", e)
            }

            if (SupabaseAuthService.isLoggedIn()) {
                val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    try {
                        SupabaseAuthService.updateUserHeartbeat()
                        val onboardingCompleted = SupabaseAuthService.isOnboardingCompleted()

                        if (!onboardingCompleted) {
                            startActivity(Intent(this@SplashActivity, OnboardingActivity::class.java))
                        } else {
                            val prefs = getSharedPreferences("presyo_prefs", MODE_PRIVATE)
                            routeToLastScreen(prefs)
                        }
                    } catch (e: Exception) {
                        val prefs = getSharedPreferences("presyo_prefs", MODE_PRIVATE)
                        routeToLastScreen(prefs)
                    } finally {
                        finish()
                    }
                } else {
                    val prefs = getSharedPreferences("presyo_prefs", MODE_PRIVATE)
                    routeToLastScreen(prefs)
                    finish()
                }
            } else {
                playTransitionAndNavigate()
            }
        }
    }

    private fun playTransitionAndNavigate() {
        val getStartedButton = findViewById<Button>(R.id.buttonGetStarted)
        val txtWala = findViewById<TextView>(R.id.txtWalaKahibawSaPresyo)
        val topCurve = findViewById<View>(R.id.topCurve)
        val logo = findViewById<ImageView>(R.id.logo_presyohan)
        val rootLayout = topCurve.parent as View
        val loadingDotsContainer = findViewById<View>(R.id.loadingDotsContainer)

        loadingDotsContainer?.visibility = View.GONE
        stopDotsAnimation()
        getStartedButton.isEnabled = false

        // 1. Exit button and subtext cleanly
        getStartedButton.animate()
            .alpha(0f)
            .translationY(250f)
            .setDuration(400)
            .start()

        txtWala.animate()
            .alpha(0f)
            .translationY(250f)
            .setDuration(400)
            .start()

        // Prepare constraints for Logo scaling and Y translation
        val lpLogo = logo.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        val startWidth = logo.width
        val endWidth = (280 * resources.displayMetrics.density).toInt()
        val startHeight = logo.height
        val endHeight = (220 * resources.displayMetrics.density).toInt()
        val startTop = logo.top
        val endTop = (60 * resources.displayMetrics.density).toInt()

        lpLogo.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
        lpLogo.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        lpLogo.topMargin = startTop
        logo.layoutParams = lpLogo

        // 2. Synchronized animation for topCurve expansion and logo shrinking/placement
        val startHeightCurve = topCurve.height
        val endHeightCurve = rootLayout.height

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.addUpdateListener { anim ->
            val fraction = anim.animatedFraction

            // Expand topCurve
            val lpCurve = topCurve.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            lpCurve.height = (startHeightCurve + (endHeightCurve - startHeightCurve) * fraction).toInt()
            lpCurve.topMargin = 0
            lpCurve.bottomMargin = 0
            topCurve.layoutParams = lpCurve

            // Shrink and move logo
            val lpL = logo.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            lpL.width = (startWidth + (endWidth - startWidth) * fraction).toInt()
            lpL.height = (startHeight + (endHeight - startHeight) * fraction).toInt()
            lpL.topMargin = (startTop + (endTop - startTop) * fraction).toInt()
            logo.layoutParams = lpL
        }

        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                val intent = Intent(this@SplashActivity, LoginActivity::class.java)
                startActivity(intent)
                overridePendingTransition(0, 0)
                finish()
            }
        })

        animator.duration = 450
        animator.start()
    }

    private fun routeToLastScreen(prefs: android.content.SharedPreferences) {
        val unscopedLastScreen = prefs.getString(SessionManager.KEY_LAST_SCREEN, null)
        if (unscopedLastScreen == SessionManager.SCREEN_HOME) {
            val storeId = prefs.getString(SessionManager.KEY_STORE_ID, null)
            val storeName = prefs.getString(SessionManager.KEY_STORE_NAME, null)
            if (storeId != null && storeName != null) {
                val intent = Intent(this, HomeActivity::class.java)
                intent.putExtra("storeId", storeId)
                intent.putExtra("storeName", storeName)
                startActivity(intent)
                return
            }
        }

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
    }

    override fun onBackPressed() {
        if (!isNavigating) {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        stopDotsAnimation()
        super.onDestroy()
    }
}
