package com.presyohan.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
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
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google

class LoginActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private var hasRetriedInteractiveSignIn = false
    private var attemptedRevoke = false
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
                lifecycleScope.launch {
                    try {
                        SupabaseAuthService.signInWithGoogleIdToken(idToken)
                        startActivity(Intent(this@LoginActivity, StoreActivity::class.java))
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "Unable to sign in with Google.", Toast.LENGTH_SHORT).show()
                    }
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
                lifecycleScope.launch {
                    try {
                        SupabaseAuthService.signInWithGoogleIdToken(idToken)
                        startActivity(Intent(this@LoginActivity, StoreActivity::class.java))
                        finish()
                    } catch (e: Exception) {
                        Toast.makeText(this@LoginActivity, "Unable to sign in with Google.", Toast.LENGTH_SHORT).show()
                    }
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
        val signUpBtn = findViewById<Button>(R.id.buttonSignUp)

        loginBtn.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter your email and password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    SupabaseAuthService.signInEmail(email, password)
                    startActivity(Intent(this@LoginActivity, StoreActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "Unable to sign in.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        signUpBtn.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        val googleSignInText = findViewById<TextView>(R.id.googleSignInText)
        googleSignInText.setOnClickListener {
            hasRetriedInteractiveSignIn = false
            if (!ensurePlayServices()) return@setOnClickListener
            // Force showing the Google account picker by clearing any cached account
            googleSignInClient.signOut().addOnCompleteListener {
                val intent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(intent)
            }
        }

        // Handle a possible OAuth deep link when activity is launched via browser callback
        intent?.let { incoming ->
            SupabaseProvider.client.handleDeeplinks(incoming)
            val session = SupabaseProvider.client.auth.currentSessionOrNull()
            if (session != null) {
                startActivity(Intent(this, StoreActivity::class.java))
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        SupabaseProvider.client.handleDeeplinks(intent)
        val session = SupabaseProvider.client.auth.currentSessionOrNull()
        if (session != null) {
            startActivity(Intent(this, StoreActivity::class.java))
            finish()
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
}
