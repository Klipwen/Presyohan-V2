package com.presyohan.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import coil.ImageLoader
import coil.load
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.transform.CircleCropTransformation
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class EditProfileActivity : AppCompatActivity() {

    private lateinit var layoutEditProfileState: ConstraintLayout
    private lateinit var layoutPreviewState: ConstraintLayout
    
    // Header
    private lateinit var headerTitle: TextView
    private lateinit var btnBack: ImageView

    // Edit State Views
    private lateinit var imgAvatar: ImageView
    private lateinit var btnUploadAvatar: ImageView
    private lateinit var txtUserId: TextView
    private lateinit var txtUserEmail: TextView
    private lateinit var etUsername: EditText
    private lateinit var btnUsernamePen: ImageView
    private lateinit var btnUsernameDone: AppCompatButton

    // Preview State Views
    private lateinit var cropView: AvatarCropView
    private lateinit var btnSelectAnother: AppCompatButton
    private lateinit var btnConfirm: AppCompatButton

    // Loading overlay
    private lateinit var loadingOverlay: View

    private var currentImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            currentImageUri = uri
            loadUriForCropping(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        // Initialize Views
        layoutEditProfileState = findViewById(R.id.layoutEditProfileState)
        layoutPreviewState = findViewById(R.id.layoutPreviewState)
        
        headerTitle = findViewById(R.id.headerTitle)
        btnBack = findViewById(R.id.btnBack)

        imgAvatar = findViewById(R.id.imgAvatar)
        btnUploadAvatar = findViewById(R.id.btnUploadAvatar)
        txtUserId = findViewById(R.id.txtUserId)
        txtUserEmail = findViewById(R.id.txtUserEmail)
        etUsername = findViewById(R.id.etUsername)
        btnUsernamePen = findViewById(R.id.btnUsernamePen)
        btnUsernameDone = findViewById(R.id.btnUsernameDone)

        cropView = findViewById(R.id.cropView)
        btnSelectAnother = findViewById(R.id.btnSelectAnother)
        btnConfirm = findViewById(R.id.btnConfirm)

        // Initial setup
        switchToEditState()
        loadUserProfile()

        // Listeners
        btnBack.setOnClickListener {
            handleBackAction()
        }

        btnUploadAvatar.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnSelectAnother.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnUsernamePen.setOnClickListener {
            enableUsernameEdit(true)
        }

        btnUsernameDone.setOnClickListener {
            saveUsername()
        }

        btnConfirm.setOnClickListener {
            confirmCropAndUpload()
        }

        // Custom back button handler for Android 13+
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackAction()
            }
        })
    }

    private fun handleBackAction() {
        if (layoutPreviewState.visibility == View.VISIBLE) {
            switchToEditState()
        } else {
            finish()
        }
    }

    private fun switchToEditState() {
        headerTitle.text = "Edit Profile"
        layoutEditProfileState.visibility = View.VISIBLE
        layoutPreviewState.visibility = View.GONE
    }

    private fun switchToPreviewState() {
        headerTitle.text = "Preview"
        layoutEditProfileState.visibility = View.GONE
        layoutPreviewState.visibility = View.VISIBLE
    }

    private fun loadUserProfile() {
        // Fallback display from Supabase auth cache
        val currentUser = SupabaseProvider.client.auth.currentUserOrNull()
        txtUserEmail.text = currentUser?.email ?: ""
        etUsername.setText(SupabaseAuthService.getDisplayNameImmediate())
        txtUserId.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val profile = SupabaseAuthService.getUserProfile()
                if (profile != null) {
                    if (!profile.name.isNullOrBlank()) {
                        etUsername.setText(profile.name)
                    }
                    if (!profile.user_code.isNullOrBlank()) {
                        txtUserId.text = "ID: ${profile.user_code.uppercase()}"
                        txtUserId.visibility = View.VISIBLE
                    }
                    if (!profile.avatar_url.isNullOrBlank()) {
                        imgAvatar.clearColorFilter()
                        imgAvatar.load(profile.avatar_url) {
                            crossfade(true)
                            transformations(CircleCropTransformation())
                            error(R.drawable.avatar_default)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun enableUsernameEdit(enable: Boolean) {
        etUsername.isEnabled = enable
        if (enable) {
            etUsername.requestFocus()
            etUsername.setSelection(etUsername.text.length)
            
            // Show soft keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etUsername, InputMethodManager.SHOW_IMPLICIT)
            
            btnUsernamePen.visibility = View.GONE
            btnUsernameDone.visibility = View.VISIBLE
        } else {
            // Hide soft keyboard
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etUsername.windowToken, 0)
            
            btnUsernamePen.visibility = View.VISIBLE
            btnUsernameDone.visibility = View.GONE
        }
    }

    private fun saveUsername() {
        val newName = etUsername.text.toString().trim()
        if (newName.isBlank()) {
            Toast.makeText(this, "Username cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        enableUsernameEdit(false)
        LoadingOverlayHelper.show(loadingOverlay)

        lifecycleScope.launch {
            try {
                val success = SupabaseAuthService.updateProfile(name = newName, avatarUrl = null)
                if (success) {
                    Toast.makeText(this@EditProfileActivity, "Username updated", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@EditProfileActivity, "Failed to update username", Toast.LENGTH_SHORT).show()
                    loadUserProfile() // Reload previous
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@EditProfileActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                loadUserProfile()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun loadUriForCropping(uri: Uri) {
        LoadingOverlayHelper.show(loadingOverlay)
        
        val loader = ImageLoader(this)
        val req = ImageRequest.Builder(this)
            .data(uri)
            .allowHardware(false) // Software bitmap required to perform Canvas operations
            .build()
            
        lifecycleScope.launch {
            try {
                val result = loader.execute(req)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as BitmapDrawable).bitmap
                    cropView.setBitmap(bitmap)
                    switchToPreviewState()
                } else {
                    Toast.makeText(this@EditProfileActivity, "Failed to load image for cropping", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@EditProfileActivity, "Error loading image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun confirmCropAndUpload() {
        val croppedBitmap = cropView.getCroppedBitmap()
        if (croppedBitmap == null) {
            Toast.makeText(this, "Failed to crop image", Toast.LENGTH_SHORT).show()
            return
        }

        LoadingOverlayHelper.show(loadingOverlay)

        lifecycleScope.launch {
            try {
                // Compress bitmap to bytes
                val stream = ByteArrayOutputStream()
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val byteArray = stream.toByteArray()

                // Upload to Supabase Storage
                val publicUrl = SupabaseAuthService.uploadAvatar(byteArray, "jpg")
                if (publicUrl != null) {
                    // Update user profile record and metadata
                    val success = SupabaseAuthService.updateProfile(name = null, avatarUrl = publicUrl)
                    if (success) {
                        Toast.makeText(this@EditProfileActivity, "Profile picture updated", Toast.LENGTH_SHORT).show()
                        switchToEditState()
                        // Load image with coil
                        imgAvatar.clearColorFilter()
                        imgAvatar.load(publicUrl) {
                            crossfade(true)
                            transformations(CircleCropTransformation())
                            error(R.drawable.avatar_default)
                        }
                    } else {
                        Toast.makeText(this@EditProfileActivity, "Failed to update profile record", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@EditProfileActivity, "Failed to upload avatar to storage", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@EditProfileActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }
}
