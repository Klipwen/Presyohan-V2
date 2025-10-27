package com.project.presyohan

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import android.widget.TextView
import androidx.lifecycle.lifecycleScope

class LoginActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

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

        // Google OAuth via Supabase (custom tabs / deeplink)
        val googleSignInText = findViewById<TextView>(R.id.googleSignInText)
        googleSignInText.setOnClickListener {
            lifecycleScope.launch {
                val ok = SupabaseAuthService.signInWithGoogle(this@LoginActivity)
                if (ok) {
                    Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, StoreActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this@LoginActivity, "Google sign in failed.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        // Temporarily keep Firebase Google sign-in during migration
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Google sign in successful!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, StoreActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Google sign in failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
