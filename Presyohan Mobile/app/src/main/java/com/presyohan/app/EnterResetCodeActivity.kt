package com.presyohan.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import kotlinx.coroutines.launch

class EnterResetCodeActivity : AppCompatActivity() {
    private var userEmail: String? = null
    private var isVerifying = false
    private var countDownTimer: CountDownTimer? = null

    private lateinit var resendTextView: TextView
    private lateinit var verifyButton: Button
    private lateinit var feedbackMessage: TextView
    private lateinit var loadingOverlay: View
    private lateinit var hiddenCodeInput: EditText
    private lateinit var boxes: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enter_reset_code)

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

        userEmail = intent.getStringExtra("email")

        resendTextView = findViewById(R.id.textResendCode)
        verifyButton = findViewById(R.id.buttonVerifyCode)
        feedbackMessage = findViewById(R.id.feedbackMessage)
        val buttonBack = findViewById<View>(R.id.buttonBack)
        hiddenCodeInput = findViewById(R.id.hiddenCodeInput)

        val verifyMessage = findViewById<TextView>(R.id.verifyMessage)
        val verifyEmailDisplay = findViewById<TextView>(R.id.verifyEmailDisplay)
        verifyMessage.text = "We've sent you 6 digit Code to your email"
        verifyEmailDisplay.text = userEmail ?: ""

        boxes = listOf(
            findViewById(R.id.box1),
            findViewById(R.id.box2),
            findViewById(R.id.box3),
            findViewById(R.id.box4),
            findViewById(R.id.box5),
            findViewById(R.id.box6)
        )

        // Focus keyboard
        fun focusInput() {
            hiddenCodeInput.requestFocus()
            try {
                hiddenCodeInput.setSelection(hiddenCodeInput.text.length)
            } catch (_: Exception) {}
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenCodeInput, InputMethodManager.SHOW_IMPLICIT)
        }

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
                setFeedback(null) // clear error on change
            }
        })

        buttonBack.setOnClickListener {
            finish()
        }

        verifyButton.setOnClickListener { verifyCode() }
        resendTextView.setOnClickListener { if (!isVerifying) resendCode() }

        startCooldown(60)
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
            feedbackMessage.visibility = View.GONE
        } else {
            feedbackMessage.text = message
            feedbackMessage.setTextColor(ContextCompat.getColor(this, if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark))
            feedbackMessage.visibility = View.VISIBLE
        }
    }

    private fun verifyCode() {
        if (isVerifying) return

        val joinedCode = hiddenCodeInput.text.toString().trim()
        if (joinedCode.length != 6) {
            setFeedback("Please enter the 6-digit code.", true)
            return
        }
        if (userEmail.isNullOrEmpty()) {
            setFeedback("Missing email. Please restart the flow.", true)
            return
        }

        setFeedback("Verifying...", false)
        isVerifying = true
        verifyButton.isEnabled = false

        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                // Verify the Recovery OTP code to establish temporary session
                SupabaseProvider.client.auth.verifyEmailOtp(
                    type = OtpType.Email.RECOVERY,
                    email = userEmail!!,
                    token = joinedCode
                )

                setFeedback("Verification successful!", false)
                boxes.forEach { box ->
                    box.setBackgroundResource(R.drawable.bg_code_box_active) // orange bold
                    box.setTextColor(ContextCompat.getColor(this@EnterResetCodeActivity, R.color.presyo_orange))
                }
                val intent = Intent(this@EnterResetCodeActivity, ResetPasswordActivity::class.java)
                intent.putExtra("email", userEmail)
                startActivity(intent)
                overridePendingTransition(0, 0)
                finish()
            } catch (e: Exception) {
                // Set boxes and text to grey incorrect state
                boxes.forEach { box ->
                    box.setBackgroundResource(R.drawable.bg_code_box_invalid)
                    box.setTextColor(Color.parseColor("#757575"))
                }
                Toast.makeText(this@EnterResetCodeActivity, "Invalid or expired code", Toast.LENGTH_SHORT).show()
                setFeedback(null)
                isVerifying = false
                verifyButton.isEnabled = true
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun resendCode() {
        if (userEmail.isNullOrBlank()) return

        setFeedback("Sending new code...", false)
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.auth.resetPasswordForEmail(userEmail!!)
                setFeedback("A new verification code was sent to $userEmail.", false)
                startCooldown(60)
            } catch (e: Exception) {
                setFeedback("Unable to resend code: ${e.localizedMessage}", true)
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
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
