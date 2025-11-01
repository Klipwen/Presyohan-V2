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

class SignupActivity : androidx.appcompat.app.AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val nameEditText = findViewById<EditText>(R.id.inputName)
        val emailEditText = findViewById<EditText>(R.id.inputEmail)
        val passwordEditText = findViewById<EditText>(R.id.inputPassword)
        val confirmPasswordEditText = findViewById<EditText>(R.id.inputConfirmPassword)
        val signupBtn = findViewById<Button>(R.id.buttonSignUp)
        val backBtn = findViewById<Button>(R.id.buttonBackToLogin)

        // Note: Signup flow uses Supabase Email provider only.
        // Google Sign-In for account creation is centralized via LoginActivity using ID token → Supabase.

        signupBtn.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            when {
                name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() -> {
                    Toast.makeText(this, "Complete all fields.", Toast.LENGTH_SHORT).show()
                }
                password != confirmPassword -> {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    lifecycleScope.launch {
                        try {
                            SupabaseAuthService.signUpEmail(name, email, password)
                            Toast.makeText(this@SignupActivity, "Sign-up successful. Check your email to verify.", Toast.LENGTH_LONG).show()
                            // Supabase can require email confirmation; direct to VerifyEmail screen
                            val intent = Intent(this@SignupActivity, VerifyEmailActivity::class.java)
                            intent.putExtra("email", email)
                            startActivity(intent)
                            finish()
                        } catch (e: Exception) {
                            Toast.makeText(this@SignupActivity, "Unable to sign up.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        backBtn.setOnClickListener {
            finish()
        }
    }

    // No Google Sign-In handling in SignupActivity; centralized in LoginActivity.

    // Removed Firebase Authentication usage. Google Sign-In happens only in LoginActivity,
    // which exchanges the Google ID Token with Supabase (auth.users) for centralized auth.
}
