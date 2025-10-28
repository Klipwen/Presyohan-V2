package com.presyohan.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
// Using Supabase OAuth for Google sign-in
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LoginActivity : androidx.appcompat.app.AppCompatActivity() {
    // OAuth flow handled by Supabase via Custom Tab + deep link

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailEditText = findViewById<EditText>(R.id.inputEmail)
        val passwordEditText = findViewById<EditText>(R.id.inputPassword)
        val loginBtn = findViewById<Button>(R.id.buttonLogin)
        val signUpBtn = findViewById<Button>(R.id.buttonSignUp)

        loginBtn.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    SupabaseAuthService.signInEmail(email, password)
                    Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, StoreActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, e.localizedMessage ?: "Login failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        signUpBtn.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        val googleSignInText = findViewById<TextView>(R.id.googleSignInText)
        googleSignInText.setOnClickListener {
            lifecycleScope.launch {
                val ok = SupabaseAuthService.signInWithGoogle(this@LoginActivity)
                if (!ok) {
                    Toast.makeText(this@LoginActivity, "Unable to start Google sign-in", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
}
