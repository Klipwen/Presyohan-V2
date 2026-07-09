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
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
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
    private lateinit var hiddenCodeInput: EditText
    private lateinit var boxes: List<TextView>
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

        // Initialize Supabase Client
        supabaseClient = SupabaseProvider.client
        userEmail = intent.getStringExtra("email")

        // 1. Get views
        resendTextView = findViewById(R.id.textResendCode)
        verifyButton = findViewById(R.id.buttonVerifyCode)
        val backBtn = findViewById<android.view.View>(R.id.buttonBack)
        val verifyMessage = findViewById<TextView>(R.id.verifyMessage)
        val verifyEmailDisplay = findViewById<TextView>(R.id.verifyEmailDisplay)
        feedbackMessage = findViewById(R.id.feedbackMessage)

        // 2. Setup Message
        verifyMessage.text = "We've sent you 6 digit Code to your email"
        verifyEmailDisplay.text = userEmail ?: ""

        // 3. Setup OTP inputs
        setupOtpInputs()
        
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
                overridePendingTransition(0, 0)
                finish()
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
        
        // 6. No initial cooldown: allow immediate resend for better UX
    }

    private fun setupOtpInputs() {
        hiddenCodeInput = findViewById(R.id.hiddenCodeInput)
        boxes = listOf(
            findViewById(R.id.box1),
            findViewById(R.id.box2),
            findViewById(R.id.box3),
            findViewById(R.id.box4),
            findViewById(R.id.box5),
            findViewById(R.id.box6)
        )

        fun focusInput() {
            hiddenCodeInput.requestFocus()
            try {
                hiddenCodeInput.setSelection(hiddenCodeInput.text.length)
            } catch (_: Exception) {}
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenCodeInput, InputMethodManager.SHOW_IMPLICIT)
        }

        findViewById<View>(R.id.layoutCodeBoxesFrame).setOnClickListener { focusInput() }
        findViewById<View>(R.id.layoutCodeBoxes).setOnClickListener { focusInput() }
        
        boxes.forEachIndexed { index, box ->
            box.setOnClickListener {
                val code = hiddenCodeInput.text.toString()
                if (index <= code.length) {
                    hiddenCodeInput.setSelection(index)
                } else {
                    hiddenCodeInput.setSelection(code.length)
                }
                updateBoxesUI()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(hiddenCodeInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        updateBoxesUI()

        hiddenCodeInput.addTextChangedListener(object : TextWatcher {
            private var previousText = ""
            private var previousSelection = 0
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (isUpdating) return
                previousText = s?.toString() ?: ""
                previousSelection = hiddenCodeInput.selectionStart
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                val currentText = s?.toString() ?: ""
                
                // If a character was inserted/overwritten
                if (currentText.length > previousText.length && previousSelection < previousText.length) {
                    val typedChar = currentText[previousSelection].toString()
                    val newText = previousText.substring(0, previousSelection) + 
                                  typedChar + 
                                  previousText.substring(previousSelection + 1)
                    
                    isUpdating = true
                    hiddenCodeInput.setText(newText)
                    val nextSel = (previousSelection + 1).coerceAtMost(6)
                    hiddenCodeInput.setSelection(nextSel)
                    isUpdating = false
                }
                
                // Ensure length does not exceed 6
                if (hiddenCodeInput.text.length > 6) {
                    isUpdating = true
                    hiddenCodeInput.setText(hiddenCodeInput.text.substring(0, 6))
                    hiddenCodeInput.setSelection(6)
                    isUpdating = false
                }

                updateBoxesUI()
                setFeedback(null, false)
            }
        })
        
        focusInput()
    }

    private fun updateBoxesUI() {
        val code = hiddenCodeInput.text.toString()
        val sel = hiddenCodeInput.selectionStart
        val len = code.length
        for (i in 0 until 6) {
            val box = boxes[i]
            
            if (i < len) {
                box.text = code[i].toString()
                box.setTextColor(ContextCompat.getColor(this, R.color.presyo_darkblue))
            } else {
                box.text = ""
            }

            if (i == sel) {
                box.setBackgroundResource(R.drawable.bg_code_box_active) // Orange bold
            } else if (i < len) {
                box.setBackgroundResource(R.drawable.bg_code_box_done) // Orange not bold
            } else {
                box.setBackgroundResource(R.drawable.bg_code_box_empty) // Grey outline
            }
        }
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
        
        val joinedCode = hiddenCodeInput.text.toString().trim()
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
                    val nameExtra = intent.getStringExtra("name")
                    val displayName = if (!nameExtra.isNullOrBlank()) nameExtra else SupabaseAuthService.getDisplayNameImmediate()
                    if (uid != null) {
                        try {
                            var upserted = false
                            try {
                                supabaseClient.postgrest["app_users"].upsert(
                                    mapOf(
                                        "id" to uid,
                                        "name" to displayName,
                                        "email" to email
                                    )
                                )
                                upserted = true
                            } catch (_: Exception) {}
                            if (!upserted) {
                                supabaseClient.postgrest["app_users"].upsert(
                                    mapOf(
                                        "id" to uid,
                                        "auth_uid" to uid,
                                        "name" to displayName,
                                        "email" to email
                                    )
                                )
                            }
                        } catch (_: Exception) { /* ignore */ }
                    }
                } catch (_: Exception) { /* ignore */ }

                // Success: Show Success Dialog first
                setFeedback("Verification successful!", false)
                boxes.forEach { box ->
                    box.setBackgroundResource(R.drawable.bg_code_box_active) // orange bold
                    box.setTextColor(ContextCompat.getColor(this@VerifyEmailActivity, R.color.presyo_orange))
                }
                ReusableDialogHelper.showSuccessDialog(
                    context = this@VerifyEmailActivity,
                    isPasswordReset = false,
                    buttonText = "Continue",
                    action = {
                        // A new account always needs to complete onboarding first
                        val intent = Intent(this@VerifyEmailActivity, OnboardingActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                        overridePendingTransition(0, 0)
                        finish()
                    }
                )

            } catch (e: Exception) {
                boxes.forEach { box ->
                    box.setBackgroundResource(R.drawable.bg_code_box_invalid) // grey state
                    box.setTextColor(android.graphics.Color.parseColor("#757575"))
                }
                Toast.makeText(this@VerifyEmailActivity, "Invalid or expired code", Toast.LENGTH_SHORT).show()
                setFeedback(null)
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

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}