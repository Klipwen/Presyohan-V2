package com.presyohan.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.widget.ImageView
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.postgrest.postgrest

class LoginActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private var hasRetriedInteractiveSignIn = false
    private var attemptedRevoke = false
    private lateinit var loadingOverlay: android.view.View
    private val googleSignInLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)!!
                val idToken = account.idToken
                if (idToken.isNullOrBlank()) {
                    Toast.makeText(this, "Unable to retrieve ID token.", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                LoadingOverlayHelper.show(loadingOverlay)
                lifecycleScope.launch {
                    try {
                        SupabaseAuthService.signInWithGoogleIdToken(idToken)
                        handleSuccessfulLogin()
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "Unable to sign in with Google.", Toast.LENGTH_SHORT).show()
                    }
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            } catch (e: com.google.android.gms.common.api.ApiException) {
                Toast.makeText(this, "Unable to sign in with Google.", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Try to extract error details even on canceled result
            try {
                GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
                // If we reach here unexpectedly, proceed with last signed-in account
            } catch (e: ApiException) {
                when (e.statusCode) {
                    7 /* NETWORK_ERROR */ -> {
                        Toast.makeText(this, "Unable to connect. Please check your internet.", Toast.LENGTH_SHORT).show()
                        return@registerForActivityResult
                    }
                    10 /* DEVELOPER_ERROR */ -> {
                        Toast.makeText(this, "Sign-in configuration issue. Please try again later.", Toast.LENGTH_SHORT).show()
                        android.util.Log.e("GoogleSignIn", "DEVELOPER_ERROR: ${e.statusCode} - ${e.message}")
                        return@registerForActivityResult
                    }
                    12500 /* SIGN_IN_FAILED */ -> {
                        // Clear cached consent and retry once via revoke
                        if (!attemptedRevoke) {
                            attemptedRevoke = true
                            googleSignInClient.revokeAccess().addOnCompleteListener {
                                val intent = googleSignInClient.signInIntent
                                googleSignInLauncher.launch(intent)
                            }
                            return@registerForActivityResult
                        }
                    }
                }
            }

            val last = GoogleSignIn.getLastSignedInAccount(this)
            val idToken = last?.idToken
            if (!idToken.isNullOrBlank()) {
                LoadingOverlayHelper.show(loadingOverlay)
                lifecycleScope.launch {
                    try {
                        SupabaseAuthService.signInWithGoogleIdToken(idToken)
                        handleSuccessfulLogin()
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "Unable to sign in with Google.", Toast.LENGTH_SHORT).show()
                    }
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            } else if (!hasRetriedInteractiveSignIn) {
                hasRetriedInteractiveSignIn = true
                val intent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(intent)
            } else {
                Toast.makeText(this, "Sign-in canceled.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
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

        loadingOverlay = LoadingOverlayHelper.attach(this)
        // Configure native Google Sign-In (opens Google account picker)
        val webClientId = getString(R.string.default_web_client_id)
        android.util.Log.d("GoogleSignIn", "Using web client ID: $webClientId")
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val emailEditText = findViewById<EditText>(R.id.inputEmail)
        val passwordEditText = findViewById<EditText>(R.id.inputPassword)
        val loginBtn = findViewById<Button>(R.id.buttonLogin)
        val linkSignUp = findViewById<TextView>(R.id.linkSignUp)

        val layoutEmail = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutEmail)
        val layoutPassword = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutPassword)

        // Setup custom field states
        FieldStateHelper.setupFieldState(layoutEmail, emailEditText, android.graphics.Color.parseColor("#FB8500"))
        FieldStateHelper.setupFieldState(layoutPassword, passwordEditText, android.graphics.Color.parseColor("#219EBC"))

        loginBtn.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty()) {
                FieldStateHelper.setErrorState(layoutEmail, emailEditText, android.graphics.Color.parseColor("#FB8500"))
                Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                FieldStateHelper.setErrorState(layoutPassword, passwordEditText, android.graphics.Color.parseColor("#219EBC"))
                Toast.makeText(this, "Please enter your password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    SupabaseAuthService.signInEmail(email, password)
                    handleSuccessfulLogin()
                } catch (e: Exception) {
                    FieldStateHelper.setErrorState(layoutEmail, emailEditText, android.graphics.Color.parseColor("#FB8500"))
                    FieldStateHelper.setErrorState(layoutPassword, passwordEditText, android.graphics.Color.parseColor("#219EBC"))
                    Toast.makeText(this@LoginActivity, "Incorrect email or password.", Toast.LENGTH_SHORT).show()
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        linkSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
            overridePendingTransition(0, 0)
        }

        val googleSignInButton = findViewById<android.view.View>(R.id.googleSignInButton)
        googleSignInButton.setOnClickListener {
            hasRetriedInteractiveSignIn = false
            if (!ensurePlayServices()) return@setOnClickListener
            // Force showing the Google account picker by clearing any cached account
            googleSignInClient.signOut().addOnCompleteListener {
                val intent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(intent)
            }
        }

        val linkForgotPassword = findViewById<TextView>(R.id.linkForgotPassword)
        linkForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
            overridePendingTransition(0, 0)
        }

        // Handle a possible OAuth deep link when activity is launched via browser callback
        intent?.let { incoming ->
            SupabaseProvider.client.handleDeeplinks(incoming)
            val session = SupabaseProvider.client.auth.currentSessionOrNull()
            if (session != null) {
                handleSuccessfulLogin()
            }
        }

        // Smoothly animate in the input fields and buttons
        val contentContainer = findViewById<android.view.View>(R.id.loginContentContainer)
        contentContainer.alpha = 0f
        contentContainer.translationY = 80f
        contentContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        SupabaseProvider.client.handleDeeplinks(intent)
        val session = SupabaseProvider.client.auth.currentSessionOrNull()
        if (session != null) {
            handleSuccessfulLogin()
        }
    }

    // Removed browser-based OAuth flow to prevent double account selection
    private fun ensurePlayServices(): Boolean {
        val api = GoogleApiAvailability.getInstance()
        val status = api.isGooglePlayServicesAvailable(this)
        return if (status == ConnectionResult.SUCCESS) {
            true
        } else {
            api.getErrorDialog(this, status, 9000)?.show()
            false
        }
    }

    private fun handleSuccessfulLogin() {
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        if (userId != null) {
            val onboardingCompleted = SupabaseAuthService.isOnboardingCompleted()
            if (!onboardingCompleted) {
                val intent = Intent(this@LoginActivity, OnboardingActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()
            } else {
                navigateToLastActivity()
            }
        } else {
            navigateToLastActivity()
        }
    }

    private fun navigateToLastActivity() {
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
        finish()
    }
}
