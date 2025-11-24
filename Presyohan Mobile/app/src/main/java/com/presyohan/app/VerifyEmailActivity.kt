package com.presyohan.app

import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope // Requires 'androidx.lifecycle:lifecycle-runtime-ktx'
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.put
import io.github.jan.supabase.auth.OtpType

// IMPORTANT: Replace this with your actual method to get the Supabase Client
// For production, the SupabaseClient should be initialized once in your Application class
// and provided as a dependency.
fun Context.getSupabaseClient(): SupabaseClient {
    // *** Placeholder Implementation ***
    // Replace YOUR_SUPABASE_URL and YOUR_SUPABASE_KEY with your actual values
    // and ensure the correct Supabase initialization is here or accessed from here.
    return (applicationContext as MainApplication).supabase
}

// Assuming you have an Application class to hold the Supabase instance
// Replace MainApplication with your actual Application class name
class MainApplication : android.app.Application() {
    lateinit var supabase: SupabaseClient
    override fun onCreate() {
        super.onCreate()
        // Initialize Supabase here
        // supabase = createSupabaseClient(...)
    }
}


class VerifyEmailActivity : AppCompatActivity() {
    private lateinit var supabaseClient: SupabaseClient
    private var userEmail: String? = null
    private val codeInputs = mutableListOf<EditText>()
    private val code = Array(6) { "" }
    private var isVerifying = false
    private var countDownTimer: CountDownTimer? = null
    
    // UI Elements
    private lateinit var resendTextView: TextView
    private lateinit var verifyButton: Button
    private lateinit var feedbackMessage: TextView
    private lateinit var loadingOverlay: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify_email)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        // Initialize Supabase Client
        supabaseClient = SupabaseProvider.client
        userEmail = intent.getStringExtra("email")

        // 1. Get views
        resendTextView = findViewById(R.id.textResendCode)
        verifyButton = findViewById(R.id.buttonVerifyCode)
        val backBtn = findViewById<Button>(R.id.buttonBackToLogin)
        val otpInputLayout = findViewById<LinearLayout>(R.id.otpInputLayout)
        val verifyMessage = findViewById<TextView>(R.id.verifyMessage)
        feedbackMessage = findViewById(R.id.feedbackMessage)

        // 2. Setup Message
        verifyMessage.text = "Enter the 6-digit code sent to ${userEmail ?: "your email address"}."

        // 3. Setup OTP inputs
        setupOtpInputs(otpInputLayout)
        
        // 4. Set Listeners
        verifyButton.setOnClickListener { verifyCode() }
        resendTextView.setOnClickListener { if (!isVerifying) resendCode() }

        // 5. Back to Login navigates to the start (ensure session cleared)
        backBtn.setOnClickListener {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try { SupabaseAuthService.signOut() } catch (_: Exception) {}
            val intent = Intent(this@VerifyEmailActivity, LoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
            LoadingOverlayHelper.hide(loadingOverlay)
        }
        }
        
        // 6. No initial cooldown: allow immediate resend for better UX
    }

    private fun setupOtpInputs(layout: LinearLayout) {
        // Use resources for sizing/margins to match design tokens
        val pxSize = resources.getDimensionPixelSize(R.dimen.otp_box_size)
        val pxMargin = resources.getDimensionPixelSize(R.dimen.otp_box_margin)
        
        for (i in 0 until 6) {
            val editText = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(pxSize, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(pxMargin, 0, pxMargin, 0)
                }
                // NOTE: otp_input_background must be created in your drawable folder
                setBackgroundResource(R.drawable.bg_code)
                gravity = Gravity.CENTER
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(1))
                id = 100 + i // Assign unique ID
            }
            codeInputs.add(editText)
            layout.addView(editText)
        }

        // Add TextWatchers for auto-focus
        for (i in 0 until 6) {
            codeInputs[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val value = s.toString()
                    code[i] = value
                    if (value.isNotEmpty() && i < 5) {
                        codeInputs[i + 1].requestFocus()
                    } else if (value.isEmpty() && i > 0 && before == 1) {
                        // Handles backspace if the box is empty
                        codeInputs[i - 1].requestFocus()
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        codeInputs.firstOrNull()?.requestFocus()
    }

    private fun startCooldown(seconds: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val remainingSeconds = millisUntilFinished / 1000
                resendTextView.text = "Resend Code in ${remainingSeconds}s"
                // Disable clicking and change color
                resendTextView.isEnabled = false
                resendTextView.alpha = 0.5f 
            }
            override fun onFinish() {
                resendTextView.text = "Resend Code"
                resendTextView.isEnabled = true
                resendTextView.alpha = 1.0f 
            }
        }.start()
    }

    private fun setFeedback(message: String?, isError: Boolean = false) {
        if (message.isNullOrEmpty()) {
            feedbackMessage.visibility = TextView.GONE
        } else {
            feedbackMessage.text = message
            feedbackMessage.setTextColor(ContextCompat.getColor(this, if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark))
            feedbackMessage.visibility = TextView.VISIBLE
        }
    }
    
    // --- SUPABASE LOGIC ---

    private fun verifyCode() {
        if (isVerifying) return
        
        val joinedCode = code.joinToString("")
        if (joinedCode.length != 6) {
            setFeedback("Please enter the 6-digit code.", true)
            return
        }
        if (userEmail.isNullOrEmpty()) {
            setFeedback("Missing email. Please restart the app.", true)
            return
        }

        setFeedback("Verifying...", false)
        isVerifying = true
        verifyButton.isEnabled = false

        // Use Coroutines for network operations in Android
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                // Verify the OTP code and establish session via Auth plugin
                supabaseClient.auth.verifyEmailOtp(
                    type = OtpType.Email.SIGNUP,
                    email = userEmail!!,
                    token = joinedCode
                )

                // Update user metadata with name from signup if available
                try {
                    val nameExtra = intent.getStringExtra("name")
                    if (!nameExtra.isNullOrBlank()) {
                        supabaseClient.auth.updateUser {
                            data = kotlinx.serialization.json.buildJsonObject {
                                put("name", nameExtra)
                            }
                        }
                    }
                } catch (_: Exception) { /* ignore */ }

                // Ensure app_users row exists/updated, mirroring web upsert
                try {
                    val user = supabaseClient.auth.currentUserOrNull()
                    val uid = user?.id
                    val email = user?.email ?: userEmail!!
                    val displayName = SupabaseAuthService.getDisplayNameImmediate()
                    if (uid != null) {
                        try {
                            supabaseClient.postgrest["app_users"].insert(
                                mapOf(
                                    "id" to uid,
                                    "name" to displayName,
                                    "email" to email
                                )
                            )
                        } catch (_: Exception) { /* ignore upsert fallback for now */ }
                    }
                } catch (_: Exception) { /* ignore */ }

                // Success: Navigate to Store screen (consistent with login)
                setFeedback("Verification successful!", false)
                val intent = Intent(this@VerifyEmailActivity, StoreActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                // Friendly messages + auto-resend on expiration
                val raw = e.message ?: ""
                if (raw.contains("otp_expired", ignoreCase = true) || raw.contains("expired", ignoreCase = true)) {
                    // Auto-resend a fresh code
                    val email = userEmail
                    if (!email.isNullOrBlank()) {
                        try {
                            val ok = SupabaseAuthService.resendSignupEmail(email)
                            if (ok) {
                                setFeedback("Code expired. Sent a new code to $email.", false)
                                startCooldown(60)
                            } else {
                                setFeedback("Code expired. Unable to resend, try again later.", true)
                            }
                        } catch (_: Exception) {
                            setFeedback("Code expired. Unable to resend, try again later.", true)
                        }
                    } else {
                        setFeedback("Code expired or invalid. Tap Resend Code.", true)
                    }
                } else if (raw.contains("invalid", ignoreCase = true)) {
                    setFeedback("Invalid code. Please check and try again.", true)
                } else {
                    setFeedback("Verification failed. Please check the code or resend.", true)
                }
                isVerifying = false
                verifyButton.isEnabled = true
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }

    private fun resendCode() {
        if (resendTextView.isEnabled == false) return // Respect the cooldown
        val email = userEmail
        if (email.isNullOrBlank()) {
            setFeedback("Missing email. Cannot resend.", true)
            return
        }

        setFeedback("Sending new code...", false)

        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val ok = SupabaseAuthService.resendSignupEmail(email)
                if (ok) {
                    setFeedback("A new verification code was sent to $email.", false)
                } else {
                    setFeedback("Unable to resend code. Please try again later.", true)
                }
                startCooldown(60)
            } catch (_: Exception) {
                setFeedback("Unable to resend code. Please try again later.", true)
                startCooldown(60)
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}