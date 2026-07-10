package com.presyohan.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest

class SignupActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var loadingOverlay: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

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

        val nameEditText = findViewById<EditText>(R.id.inputName)
        val emailEditText = findViewById<EditText>(R.id.inputEmail)
        val passwordEditText = findViewById<EditText>(R.id.inputPassword)
        val confirmPasswordEditText = findViewById<EditText>(R.id.inputConfirmPassword)
        val signupBtn = findViewById<Button>(R.id.buttonSignUp)
        val linkLogin = findViewById<TextView>(R.id.linkLogin)

        val layoutName = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutName)
        val layoutEmail = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutEmail)
        val layoutPassword = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutPassword)
        val layoutConfirmPassword = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutConfirmPassword)

        // Setup custom field states
        FieldStateHelper.setupFieldState(layoutName, nameEditText, android.graphics.Color.parseColor("#FB8500"))
        FieldStateHelper.setupFieldState(layoutEmail, emailEditText, android.graphics.Color.parseColor("#FB8500"))
        FieldStateHelper.setupFieldState(layoutPassword, passwordEditText, android.graphics.Color.parseColor("#219EBC"))
        FieldStateHelper.setupFieldState(layoutConfirmPassword, confirmPasswordEditText, android.graphics.Color.parseColor("#219EBC"))

        signupBtn.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            if (name.isEmpty()) {
                FieldStateHelper.setErrorState(layoutName, nameEditText, android.graphics.Color.parseColor("#FB8500"))
                Toast.makeText(this, "Please enter your name.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (email.isEmpty()) {
                FieldStateHelper.setErrorState(layoutEmail, emailEditText, android.graphics.Color.parseColor("#FB8500"))
                Toast.makeText(this, "Please enter your email.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                FieldStateHelper.setErrorState(layoutEmail, emailEditText, android.graphics.Color.parseColor("#FB8500"))
                Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                FieldStateHelper.setErrorState(layoutPassword, passwordEditText, android.graphics.Color.parseColor("#219EBC"))
                Toast.makeText(this, "Please enter a password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (confirmPassword.isEmpty()) {
                FieldStateHelper.setErrorState(layoutConfirmPassword, confirmPasswordEditText, android.graphics.Color.parseColor("#219EBC"))
                Toast.makeText(this, "Please confirm your password.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password != confirmPassword) {
                FieldStateHelper.setErrorState(layoutConfirmPassword, confirmPasswordEditText, android.graphics.Color.parseColor("#219EBC"))
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    // Check if email already exists in the database
                    if (SupabaseAuthService.emailExists(email)) {
                        FieldStateHelper.setErrorState(layoutEmail, emailEditText, android.graphics.Color.parseColor("#FB8500"))
                        Toast.makeText(this@SignupActivity, "Unable to sign up. Email may already be in use.", Toast.LENGTH_LONG).show()
                        LoadingOverlayHelper.hide(loadingOverlay)
                        return@launch
                    }

                    SupabaseAuthService.signUpEmail(name, email, password)

                    // Explicitly sign in user to establish active session
                    SupabaseAuthService.signInEmail(email, password)

                    // Manual profile upsert to ensure database has the user details immediately
                    try {
                        val user = SupabaseProvider.client.auth.currentUserOrNull()
                        val uid = user?.id
                        if (uid != null) {
                            var upserted = false
                            try {
                                SupabaseProvider.client.postgrest["app_users"].upsert(
                                    mapOf(
                                        "id" to uid,
                                        "name" to name,
                                        "email" to email
                                    )
                                )
                                upserted = true
                            } catch (_: Exception) {}
                            if (!upserted) {
                                SupabaseProvider.client.postgrest["app_users"].upsert(
                                    mapOf(
                                        "id" to uid,
                                        "auth_uid" to uid,
                                        "name" to name,
                                        "email" to email
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SignupActivity", "Manual profile upsert failed: ${e.message}", e)
                    }

                    ReusableDialogHelper.showSuccessDialog(
                        context = this@SignupActivity,
                        isPasswordReset = false,
                        buttonText = "Continue",
                        action = {
                            val intent = Intent(this@SignupActivity, OnboardingActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            overridePendingTransition(0, 0)
                            finish()
                        }
                    )
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: "Unknown error"
                    FieldStateHelper.setErrorState(layoutEmail, emailEditText, android.graphics.Color.parseColor("#FB8500"))
                    if (msg.contains("already registered", ignoreCase = true) || msg.contains("already exists", ignoreCase = true)) {
                        Toast.makeText(this@SignupActivity, "Unable to sign up. Email may already be in use.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@SignupActivity, "Sign-up failed: $msg", Toast.LENGTH_LONG).show()
                    }
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        linkLogin.setOnClickListener { finish() }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    // No Google Sign-In handling in SignupActivity; centralized in LoginActivity.

    // Removed Firebase Authentication usage. Google Sign-In happens only in LoginActivity,
    // which exchanges the Google ID Token with Supabase (auth.users) for centralized auth.
}
