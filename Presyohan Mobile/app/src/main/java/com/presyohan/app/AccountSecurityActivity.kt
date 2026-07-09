package com.presyohan.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AccountSecurityActivity : AppCompatActivity() {

    private lateinit var layoutUpdatePassword: View
    private lateinit var layoutCreatePassword: View
    private lateinit var btnUpdate: AppCompatButton
    private lateinit var lblForgotPassword: TextView

    // Update state fields
    private lateinit var layoutCurrentPassword: TextInputLayout
    private lateinit var etCurrentPassword: TextInputEditText
    private lateinit var layoutNewPassword: TextInputLayout
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var layoutConfirmPassword: TextInputLayout
    private lateinit var etConfirmPassword: TextInputEditText

    // Create state fields
    private lateinit var layoutCreateNewPassword: TextInputLayout
    private lateinit var etCreateNewPassword: TextInputEditText
    private lateinit var layoutCreateConfirmPassword: TextInputLayout
    private lateinit var etCreateConfirmPassword: TextInputEditText

    private lateinit var loadingOverlay: View

    private var hasPassword = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_security)

        loadingOverlay = LoadingOverlayHelper.attach(this)

        // Initialize Views
        layoutUpdatePassword = findViewById(R.id.layoutUpdatePassword)
        layoutCreatePassword = findViewById(R.id.layoutCreatePassword)
        btnUpdate = findViewById(R.id.btnUpdate)
        lblForgotPassword = findViewById(R.id.lblForgotPassword)

        layoutCurrentPassword = findViewById(R.id.layoutCurrentPassword)
        etCurrentPassword = findViewById(R.id.etCurrentPassword)
        layoutNewPassword = findViewById(R.id.layoutNewPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        layoutConfirmPassword = findViewById(R.id.layoutConfirmPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)

        layoutCreateNewPassword = findViewById(R.id.layoutCreateNewPassword)
        etCreateNewPassword = findViewById(R.id.etCreateNewPassword)
        layoutCreateConfirmPassword = findViewById(R.id.layoutCreateConfirmPassword)
        etCreateConfirmPassword = findViewById(R.id.etCreateConfirmPassword)

        val btnBack = findViewById<View>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }

        // Apply visual outline styling
        setupFieldStyling()

        // Load auth method state
        checkUserAuthType()

        // Set Click Listeners
        lblForgotPassword.setOnClickListener {
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }

        btnUpdate.setOnClickListener {
            handleUpdateAction()
        }
    }

    private fun setupFieldStyling() {
        val orangeColor = Color.parseColor("#FB8500")
        val tealColor = Color.parseColor("#219EBC")

        // Update profile password fields
        FieldStateHelper.setupFieldState(layoutCurrentPassword, etCurrentPassword, orangeColor)
        FieldStateHelper.setupFieldState(layoutNewPassword, etNewPassword, tealColor)
        FieldStateHelper.setupFieldState(layoutConfirmPassword, etConfirmPassword, tealColor)

        // Create profile password fields
        FieldStateHelper.setupFieldState(layoutCreateNewPassword, etCreateNewPassword, orangeColor)
        FieldStateHelper.setupFieldState(layoutCreateConfirmPassword, etCreateConfirmPassword, tealColor)
    }

    private fun checkUserAuthType() {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                // Retrieve user freshly from GoTrue API to populate the identities array
                val currentUser = try {
                    SupabaseProvider.client.auth.retrieveUserForCurrentSession()
                } catch (e: Exception) {
                    e.printStackTrace()
                    SupabaseProvider.client.auth.currentUserOrNull()
                }
                
                // Get provider info from appMetadata (more reliable for cached session user)
                val appMetadata = currentUser?.appMetadata ?: buildJsonObject {}
                val appProvider = appMetadata["provider"]?.jsonPrimitive?.contentOrNull
                val appProviders = try {
                    appMetadata["providers"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
                
                val identityProviders = currentUser?.identities?.map { it.provider } ?: emptyList()
                val isEmailUser = appProvider == "email" || "email" in appProviders || "email" in identityProviders

                // Check has_password from userMetadata to support Google accounts that set a password
                val metaAny: Any? = currentUser?.userMetadata
                val hasMetadataPassword = when (metaAny) {
                    is Map<*, *> -> metaAny["has_password"] as? Boolean == true
                    is JsonObject -> {
                        metaAny["has_password"]?.jsonPrimitive?.booleanOrNull == true
                    }
                    else -> false
                }

                // Query database directly to see if user has a password set (covers both email and linked google accounts)
                var hasPasswordRpc: Boolean? = null
                try {
                    val rpcResult = SupabaseProvider.client.postgrest.rpc("has_password")
                    hasPasswordRpc = rpcResult.decodeAs<Boolean>()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                hasPassword = if (hasPasswordRpc != null) {
                    hasPasswordRpc
                } else {
                    isEmailUser || hasMetadataPassword
                }

                android.util.Log.d("AccountSecurity", "checkUserAuthType: hasPassword=$hasPassword, rpc=$hasPasswordRpc, isEmail=$isEmailUser, meta=$hasMetadataPassword")

                if (hasPassword) {
                    layoutUpdatePassword.visibility = View.VISIBLE
                    layoutCreatePassword.visibility = View.GONE
                    lblForgotPassword.visibility = View.VISIBLE
                    btnUpdate.text = "Update"
                } else {
                    layoutUpdatePassword.visibility = View.GONE
                    layoutCreatePassword.visibility = View.VISIBLE
                    lblForgotPassword.visibility = View.GONE
                    btnUpdate.text = "Confirm"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun handleUpdateAction() {
        var currentPass = ""
        val newPass: String
        val confirmPass: String

        if (hasPassword) {
            currentPass = etCurrentPassword.text.toString().trim()
            newPass = etNewPassword.text.toString().trim()
            confirmPass = etConfirmPassword.text.toString().trim()

            if (currentPass.isEmpty()) {
                Toast.makeText(this, "Please enter your current password", Toast.LENGTH_SHORT).show()
                return
            }
        } else {
            newPass = etCreateNewPassword.text.toString().trim()
            confirmPass = etCreateConfirmPassword.text.toString().trim()
        }

        if (newPass.isEmpty()) {
            Toast.makeText(this, "Please enter a new password", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPass.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPass != confirmPass) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        // Show double check reusable dialog asking user if they wish to proceed
        showReusableDialog(
            title = "Update Password?",
            message = "Are you sure you want to proceed with this update?",
            positiveButtonText = "Update",
            positiveAction = {
                executePasswordUpdate(currentPass, newPass)
            },
            negativeButtonText = "Cancel"
        )
    }

    private fun executePasswordUpdate(currentPass: String, newPass: String) {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val email = SupabaseProvider.client.auth.currentUserOrNull()?.email ?: ""
                
                // If user already had a password, verify their current password first
                if (hasPassword) {
                    var currentPassValid = false
                    try {
                        SupabaseProvider.client.auth.signInWith(Email) {
                            this.email = email
                            this.password = currentPass
                        }
                        currentPassValid = true
                    } catch (_: Exception) {}

                    if (!currentPassValid) {
                        LoadingOverlayHelper.hide(loadingOverlay)
                        Toast.makeText(this@AccountSecurityActivity, "Incorrect current password.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                }

                // Update/Create the password in Supabase and mark has_password in user metadata
                val currentMeta = SupabaseProvider.client.auth.currentUserOrNull()?.userMetadata ?: buildJsonObject {}
                val mergedMeta = buildJsonObject {
                    currentMeta.forEach { (key, value) ->
                        put(key, value)
                    }
                    put("has_password", true)
                }

                SupabaseProvider.client.auth.updateUser {
                    password = newPass
                    data = mergedMeta
                }

                LoadingOverlayHelper.hide(loadingOverlay)
                
                // Show Success Dialog
                showSuccessDialog(
                    isPasswordReset = true,
                    buttonText = "Proceed to Login",
                    action = {
                        performLogoutAndRedirect()
                    }
                )

            } catch (e: Exception) {
                LoadingOverlayHelper.hide(loadingOverlay)
                Toast.makeText(this@AccountSecurityActivity, "Failed to update password: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performLogoutAndRedirect() {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                SupabaseAuthService.signOut()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
                val intent = Intent(this@AccountSecurityActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
        }
    }
}
