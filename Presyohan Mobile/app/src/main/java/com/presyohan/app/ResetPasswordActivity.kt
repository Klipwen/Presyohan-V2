package com.presyohan.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {
    private lateinit var loadingOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

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

        val passwordEditText = findViewById<EditText>(R.id.inputPassword)
        val confirmPasswordEditText = findViewById<EditText>(R.id.inputConfirmPassword)
        val buttonReset = findViewById<Button>(R.id.buttonReset)
        val buttonBack = findViewById<View>(R.id.buttonBack)

        val layoutPassword = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutPassword)
        val layoutConfirmPassword = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutConfirmPassword)

        // Setup custom field states
        FieldStateHelper.setupFieldState(layoutPassword, passwordEditText, android.graphics.Color.parseColor("#FB8500"))
        FieldStateHelper.setupFieldState(layoutConfirmPassword, confirmPasswordEditText, android.graphics.Color.parseColor("#219EBC"))

        buttonBack.setOnClickListener {
            showCancelConfirmation()
        }

        buttonReset.setOnClickListener {
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            if (password.isEmpty()) {
                FieldStateHelper.setErrorState(layoutPassword, passwordEditText, android.graphics.Color.parseColor("#FB8500"))
                Toast.makeText(this, "Please enter a new password.", Toast.LENGTH_SHORT).show()
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
                    // Update user password in Supabase
                    SupabaseProvider.client.auth.updateUser {
                        this.password = password
                    }
                    
                    // Show success dialog
                    ReusableDialogHelper.showSuccessDialog(
                        context = this@ResetPasswordActivity,
                        isPasswordReset = true,
                        buttonText = "Proceed to Login",
                        action = {
                            navigateToLogin()
                        }
                    )
                } catch (e: Exception) {
                    FieldStateHelper.setErrorState(layoutPassword, passwordEditText, android.graphics.Color.parseColor("#FB8500"))
                    Toast.makeText(this@ResetPasswordActivity, "Unable to update password. Please try again.", Toast.LENGTH_SHORT).show()
                } finally {
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            }
        }
    }

    override fun onBackPressed() {
        showCancelConfirmation()
    }

    private fun showCancelConfirmation() {
        ReusableDialogHelper.showCustomDialog(
            context = this,
            title = "Cancel Password Reset?",
            message = "Are you sure you want to cancel?\n\nYour new password has not been saved, and your account credentials will remain unchanged.",
            positiveButtonText = "Keep Editing",
            positiveAction = {
                // Keep editing is positive (orange), just dismisses
            },
            negativeButtonText = "Abort Change",
            negativeAction = {
                abortAndNavigateToLogin()
            }
        )
    }

    private fun abortAndNavigateToLogin() {
        lifecycleScope.launch {
            try {
                SupabaseAuthService.signOut()
            } catch (_: Exception) {}
            navigateToLogin()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
