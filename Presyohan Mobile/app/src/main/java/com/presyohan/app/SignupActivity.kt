package com.presyohan.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SignupActivity : androidx.appcompat.app.AppCompatActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9002 // Different from LoginActivity
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val nameEditText = findViewById<EditText>(R.id.inputName)
        val emailEditText = findViewById<EditText>(R.id.inputEmail)
        val passwordEditText = findViewById<EditText>(R.id.inputPassword)
        val confirmPasswordEditText = findViewById<EditText>(R.id.inputConfirmPassword)
        val signupBtn = findViewById<Button>(R.id.buttonSignUp)
        val backBtn = findViewById<Button>(R.id.buttonBackToLogin)

        // Google Sign-In setup (temporarily kept)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        signupBtn.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            when {
                name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() -> {
                    Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show()
                }
                password != confirmPassword -> {
                    Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    lifecycleScope.launch {
                        try {
                            SupabaseAuthService.signUpEmail(name, email, password)
                            Toast.makeText(this@SignupActivity, "Signup successful. Check your email for verification.", Toast.LENGTH_LONG).show()
                            // Supabase can require email confirmation; direct to VerifyEmail screen
                            val intent = Intent(this@SignupActivity, VerifyEmailActivity::class.java)
                            intent.putExtra("email", email)
                            startActivity(intent)
                            finish()
                        } catch (e: Exception) {
                            Toast.makeText(this@SignupActivity, e.localizedMessage ?: "Signup failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        backBtn.setOnClickListener {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val db = FirebaseFirestore.getInstance()
                    val userDocRef = db.collection("users").document(user!!.uid)
                    userDocRef.get().addOnSuccessListener { doc ->
                        if (!doc.exists()) {
                            val userData = hashMapOf(
                                "name" to (user.displayName ?: ""),
                                "email" to (user.email ?: ""),
                                "createdAt" to com.google.firebase.Timestamp.now(),
                                "stores" to listOf<String>()
                            )
                            userDocRef.set(userData, com.google.firebase.firestore.SetOptions.merge())
                        }
                        // Go to StoreActivity after sign-in
                        val intent = Intent(this, StoreActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    Toast.makeText(this, "Google sign in failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
