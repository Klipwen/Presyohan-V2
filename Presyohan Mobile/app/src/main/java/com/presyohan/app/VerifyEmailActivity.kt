package com.presyohan.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth

class VerifyEmailActivity : Activity() {
    private lateinit var auth: FirebaseAuth
    private var userEmail: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)

        auth = FirebaseAuth.getInstance()
        userEmail = intent.getStringExtra("email")

        val verifyMessage = findViewById<TextView>(R.id.verifyMessage)
        val resendBtn = findViewById<Button>(R.id.buttonResendEmail)
        val backBtn = findViewById<Button>(R.id.buttonBackToLogin)

        verifyMessage.text = "A verification email has been sent to ${userEmail ?: "your email address"}. Please check your inbox and click the link to verify your account."

        resendBtn.setOnClickListener {
            val user = auth.currentUser
            user?.sendEmailVerification()?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Verification email resent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to resend verification email.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        backBtn.setOnClickListener {
            auth.signOut()
            val intent = Intent(this, LoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }
}