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

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var loadingOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

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

        val emailEditText = findViewById<EditText>(R.id.inputEmail)
        val buttonSend = findViewById<Button>(R.id.buttonSend)
        val buttonBack = findViewById<View>(R.id.buttonBack)

        val layoutEmail = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutEmail)

        // Setup custom field states
        FieldStateHelper.setupFieldState(layoutEmail, emailEditText, android.graphics.Color.parseColor("#FB8500"))

        buttonBack.setOnClickListener {
            finish()
        }

        buttonSend.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (email.isEmpty()) {
                FieldStateHelper.setErrorState(layoutEmail, emailEditText, android.graphics.Color.parseColor("#FB8500"))
                Toast.makeText(this, "Please enter your email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    // Send password reset / recovery email via Supabase Auth
                    SupabaseProvider.client.auth.resetPasswordForEmail(email)
                    Toast.makeText(this@ForgotPasswordActivity, "Reset code sent to your email.", Toast.LENGTH_SHORT).show()
                    
                    val intent = Intent(this@ForgotPasswordActivity, EnterResetCodeActivity::class.java)
                    intent.putExtra("email", email)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    finish()
                } catch (e: Exception) {
                    FieldStateHelper.setErrorState(layoutEmail, emailEditText, android.graphics.Color.parseColor("#FB8500"))
                    Toast.makeText(this@ForgotPasswordActivity, "Unable to send reset code. Please verify your email address.", Toast.LENGTH_SHORT).show()
                } finally {
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
