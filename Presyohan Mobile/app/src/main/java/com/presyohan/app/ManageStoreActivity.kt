package com.presyohan.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Build
import android.net.Uri
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.DownloadManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import androidx.appcompat.app.AlertDialog
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import io.github.jan.supabase.auth.auth
import androidx.appcompat.widget.AppCompatButton
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.os.Handler
import android.os.Looper
import coil.load
import coil.transform.CircleCropTransformation

class ManageStoreActivity : AppCompatActivity() {
    private lateinit var loadingOverlay: android.view.View
    private lateinit var btnBack: ImageView
    private lateinit var btnStoreQrCode: ImageView
    private lateinit var ivStoreIcon: ImageView
    private lateinit var tvStoreName: TextView
    private lateinit var tvStoreType: TextView
    private lateinit var tvStoreBranch: TextView
    private lateinit var cbMakeStorePublic: CheckBox
    
    private lateinit var layoutStoreInfoToggle: LinearLayout
    private lateinit var ivStoreInfoArrow: ImageView
    private lateinit var layoutCollapsibleStats: LinearLayout
    
    private lateinit var tvStatStoreId: TextView
    private lateinit var tvStatCreatedAt: TextView
    private lateinit var tvStatStatus: TextView
    private lateinit var tvStatJoinCode: TextView
    private lateinit var tvStatPasteCode: TextView
    private lateinit var tvStatCategories: TextView
    private lateinit var tvStatItems: TextView
    private lateinit var tvStatMembers: TextView
    private lateinit var tvStatOwners: TextView
    private lateinit var tvStatManagers: TextView
    private lateinit var tvStatSalesStaff: TextView
    private lateinit var tvStatSuki: TextView
    
    private lateinit var toolImport: LinearLayout
    private lateinit var toolConvert: LinearLayout
    private lateinit var toolCopy: LinearLayout
    private lateinit var toolAddStaff: LinearLayout
    private lateinit var toolEditStore: LinearLayout
    private lateinit var toolDeleteStore: LinearLayout
    private lateinit var ivDeleteStoreIcon: ImageView
    
    private lateinit var storeCodeTextView: TextView
    private lateinit var storeCodeExpiryView: TextView
    private lateinit var btnCopyPasteCode: ImageView
    private lateinit var btnGenerateCode: Button
    private lateinit var btnRevokeCode: Button
    
    private lateinit var btnManageMembers: Button
    private lateinit var btnManageCategory: Button
    private lateinit var btnManageItems: Button

    private lateinit var shimmerStoreSettings: com.facebook.shimmer.ShimmerFrameLayout
    private lateinit var layoutStoreCardInner: LinearLayout

    private var storeId: String? = null
    private var storeName: String? = null
    private var branchName: String? = null
    private var storeType: String? = null
    private var displayId: String? = null
    private var currentUserRole: String = "employee"
    private var isStatsExpanded = false

    private var lastExportFilenamePending: String? = null
    private val requestNotifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = lastExportFilenamePending
        lastExportFilenamePending = null
        if (granted && pending != null) {
            postExportNotification(pending)
        } else {
            Toast.makeText(this, "Enable notifications to see download alerts.", Toast.LENGTH_LONG).show()
        }
    }

    @Serializable
    data class StoreRow(
        val id: String,
        val name: String,
        val branch: String? = null,
        val type: String? = null,
        val is_public: Boolean = false,
        val created_at: String? = null,
        val paste_code: String? = null,
        val paste_code_expires_at: String? = null,
        val invite_code: String? = null,
        val invite_code_created_at: String? = null,
        val display_id: String? = null
    )

    @Serializable
    data class PasteCodeResult(
        val code: String,
        val expires_at: String
    )

    @Serializable
    data class StoreProductRow(
        val category: String? = null,
        val name: String? = null,
        val description: String? = null,
        val units: String? = null,
        val price: Double? = null
    )

    @Serializable
    data class SukiRelationshipRow(
        val store_id: String
    )

    @Serializable
    data class UserCategoryRow(val category_id: String, val store_id: String, val name: String)

    @Serializable
    data class UserProductRow(
        val product_id: String,
        val store_id: String,
        val name: String,
        val description: String? = null,
        val price: Double = 0.0,
        val units: String? = null,
        val category: String? = null,
        val is_public: Boolean = false
    )

    @Serializable
    data class StoreMemberUser(
        val user_id: String,
        val name: String,
        val role: String
    )

    private enum class ExportType {
        EXCEL,
        NOTES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_store)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        storeId = intent.getStringExtra("storeId")
        if (storeId.isNullOrBlank()) {
            Toast.makeText(this, "No store ID provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Initialize Views
        btnBack = findViewById(R.id.btnBack)
        btnStoreQrCode = findViewById(R.id.btnStoreQrCode)
        ivStoreIcon = findViewById(R.id.ivStoreIcon)
        tvStoreName = findViewById(R.id.tvStoreName)
        tvStoreType = findViewById(R.id.tvStoreType)
        tvStoreBranch = findViewById(R.id.tvStoreBranch)
        cbMakeStorePublic = findViewById(R.id.cbMakeStorePublic)
        
        layoutStoreInfoToggle = findViewById(R.id.layoutStoreInfoToggle)
        ivStoreInfoArrow = findViewById(R.id.ivStoreInfoArrow)
        layoutCollapsibleStats = findViewById(R.id.layoutCollapsibleStats)
        
        tvStatStoreId = findViewById(R.id.tvStatStoreId)
        tvStatCreatedAt = findViewById(R.id.tvStatCreatedAt)
        tvStatStatus = findViewById(R.id.tvStatStatus)
        tvStatJoinCode = findViewById(R.id.tvStatJoinCode)
        tvStatPasteCode = findViewById(R.id.tvStatPasteCode)
        tvStatCategories = findViewById(R.id.tvStatCategories)
        tvStatItems = findViewById(R.id.tvStatItems)
        tvStatMembers = findViewById(R.id.tvStatMembers)
        tvStatOwners = findViewById(R.id.tvStatOwners)
        tvStatManagers = findViewById(R.id.tvStatManagers)
        tvStatSalesStaff = findViewById(R.id.tvStatSalesStaff)
        tvStatSuki = findViewById(R.id.tvStatSuki)
        
        toolImport = findViewById(R.id.toolImport)
        toolConvert = findViewById(R.id.toolConvert)
        toolCopy = findViewById(R.id.toolCopy)
        toolAddStaff = findViewById(R.id.toolAddStaff)
        toolEditStore = findViewById(R.id.toolEditStore)
        toolDeleteStore = findViewById(R.id.toolDeleteStore)
        ivDeleteStoreIcon = findViewById(R.id.ivDeleteStoreIcon)
        
        storeCodeTextView = findViewById(R.id.storeCodeText)
        storeCodeExpiryView = findViewById(R.id.storeCodeExpiry)
        btnCopyPasteCode = findViewById(R.id.btnCopyPasteCode)
        btnGenerateCode = findViewById(R.id.btnGenerateCode)
        btnRevokeCode = findViewById(R.id.btnRevokeCode)
        
        btnManageMembers = findViewById(R.id.btnManageMembers)
        btnManageCategory = findViewById(R.id.btnManageCategory)
        btnManageItems = findViewById(R.id.btnManageItems)

        shimmerStoreSettings = findViewById(R.id.shimmerStoreSettings)
        layoutStoreCardInner = findViewById(R.id.layoutStoreCardInner)

        // Set Initial Visibility of Collapsible Stats
        layoutCollapsibleStats.visibility = View.GONE
        isStatsExpanded = false
        ivStoreInfoArrow.rotation = 0f

        // Fetch details
        loadStoreDetails()

        // Listeners
        btnBack.setOnClickListener { finish() }

        btnStoreQrCode.setOnClickListener {
            if (storeId.isNullOrBlank()) return@setOnClickListener
            val intent = Intent(this, StoreQrActivity::class.java).apply {
                putExtra("storeId", storeId)
                putExtra("storeName", storeName ?: "Store")
                putExtra("displayId", displayId ?: formatStoreId(storeId!!))
                putExtra("storeLocation", branchName ?: "Main Branch")
            }
            val options = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_up,
                R.anim.stay
            )
            startActivity(intent, options.toBundle())
        }

        // Toggle Stats Collapsible
        layoutStoreInfoToggle.setOnClickListener {
            isStatsExpanded = !isStatsExpanded
            layoutCollapsibleStats.visibility = if (isStatsExpanded) View.VISIBLE else View.GONE
            ivStoreInfoArrow.animate()
                .rotation(if (isStatsExpanded) 180f else 0f)
                .setDuration(200)
                .start()
        }

        // Setup the Public Switch Checkbox
        setupPublicCheckbox()

        // Tool Action: Import
        toolImport.setOnClickListener {
            showImportDialog()
        }

        // Tool Action: Convert (Export)
        toolConvert.setOnClickListener {
            exportPricelistToExcel()
        }

        // Tool Action: Copy (Copy Prices)
        toolCopy.setOnClickListener {
            CopyPricesDialogHelper.show(
                activity = this,
                storeId = storeId ?: return@setOnClickListener,
                storeName = storeName ?: "",
                descriptionText = "Enter the 6-digit paste-code generated by the destination store to securely transfer your pricelist."
            )
        }

        // Tool Action: Add Staff
        toolAddStaff.setOnClickListener {
            showInviteStaffWithCode()
        }

        // Tool Action: Edit Store
        toolEditStore.setOnClickListener {
            if (storeId.isNullOrBlank()) return@setOnClickListener
            EditStoreDialogHelper.showEditStoreDialog(
                activity = this@ManageStoreActivity,
                storeId = storeId!!,
                currentName = storeName ?: "",
                currentBranch = branchName ?: "",
                currentType = storeType ?: "",
                onComplete = { newName, newBranch, newType ->
                    storeName = newName
                    branchName = newBranch
                    storeType = newType
                    
                    tvStoreName.text = newName
                    tvStoreBranch.text = newBranch
                    tvStoreType.text = newType
                    setStoreIcon(newType)
                }
            )
        }

        // Tool Action: Delete Store
        toolDeleteStore.setOnClickListener {
            if (storeId.isNullOrBlank()) return@setOnClickListener
            if (currentUserRole.lowercase() != "owner") {
                Toast.makeText(this, "Only the owner can delete this store.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showManageStoreDeleteConfirmation(storeId!!, storeName ?: "Store")
        }

        // Bottom Navigation Buttons
        btnManageMembers.setOnClickListener {
            val intent = Intent(this, ManageMembersActivity::class.java).apply {
                putExtra("storeId", storeId)
                putExtra("storeName", storeName)
            }
            startActivity(intent)
        }

        btnManageCategory.setOnClickListener {
            val intent = Intent(this, ManageCategoryActivity::class.java).apply {
                putExtra("storeId", storeId)
                putExtra("storeName", storeName)
            }
            startActivity(intent)
        }

        btnManageItems.setOnClickListener {
            if (storeId.isNullOrBlank()) return@setOnClickListener
            val intent = Intent(this, ManageItemsActivity::class.java).apply {
                putExtra("storeId", storeId)
                putExtra("storeName", storeName)
            }
            startActivity(intent)
        }

        // Generate Code & Revoke Code for Paste-Code
        btnGenerateCode.setOnClickListener {
            if (storeId.isNullOrBlank()) return@setOnClickListener
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    val result = SupabaseProvider.client.postgrest.rpc(
                        "generate_paste_code",
                        buildJsonObject { put("p_store_id", storeId!!) }
                    ).decodeList<PasteCodeResult>().firstOrNull()
                    if (result != null) {
                        applyPasteCode(result.code, result.expires_at)
                        tvStatPasteCode.text = result.code
                        Toast.makeText(this@ManageStoreActivity, "Paste-Code generated.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ManageStoreActivity, "Failed to generate code.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ManageStoreActivity, "You must be the owner to generate.", Toast.LENGTH_LONG).show()
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        btnRevokeCode.setOnClickListener {
            if (storeId.isNullOrBlank()) return@setOnClickListener
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest.rpc(
                        "revoke_paste_code",
                        buildJsonObject { put("p_store_id", storeId!!) }
                    )
                    clearPasteCodeUi()
                    tvStatPasteCode.text = "None"
                    Toast.makeText(this@ManageStoreActivity, "Paste-Code revoked.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@ManageStoreActivity, "You must be the owner to revoke.", Toast.LENGTH_LONG).show()
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }

        btnCopyPasteCode.setOnClickListener {
            val code = storeCodeTextView.text.toString()
            if (code.isNotEmpty() && code != "------") {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Paste Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadStoreDetails() {
        fetchAndPopulateStoreDetails(showShimmer = true)
    }

    private fun refreshStoreStatsQuietly() {
        fetchAndPopulateStoreDetails(showShimmer = false)
    }

    private fun fetchAndPopulateStoreDetails(showShimmer: Boolean) {
        val sId = storeId ?: return
        if (showShimmer) {
            shimmerStoreSettings.visibility = View.VISIBLE
            shimmerStoreSettings.startShimmer()
            layoutStoreCardInner.visibility = View.GONE
        }
        lifecycleScope.launch {
            try {
                // 1. Fetch store details
                val rows = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("id", sId) }
                    limit(1)
                }.decodeList<StoreRow>()
                val store = rows.firstOrNull()
                if (store != null) {
                    storeName = store.name
                    branchName = store.branch
                    storeType = store.type

                    tvStoreName.text = store.name
                    tvStoreBranch.text = store.branch ?: "Main Branch"
                    tvStoreType.text = store.type ?: "General Store"
                    setStoreIcon(store.type)

                    cbMakeStorePublic.setOnCheckedChangeListener(null)
                    cbMakeStorePublic.isChecked = store.is_public
                    setupPublicCheckbox()

                    displayId = store.display_id ?: formatStoreId(store.id)
                    tvStatStoreId.text = displayId
                    tvStatCreatedAt.text = formatCreatedAt(store.created_at)
                    tvStatStatus.text = if (store.is_public) "Public" else "Private"

                    // Join code and expiry countdown
                    val now = System.currentTimeMillis()
                    val inviteCode = store.invite_code
                    val isInviteExpired = if (store.invite_code_created_at != null) {
                        val expiryMillis = parseInviteCreatedMillis(store.invite_code_created_at) ?: 0L
                        now > (expiryMillis + 86400000L)
                    } else {
                        true
                    }
                    tvStatJoinCode.text = if (inviteCode.isNullOrBlank()) "None" else if (isInviteExpired) "Expired" else inviteCode

                    // Paste Code
                    tvStatPasteCode.text = store.paste_code ?: "None"
                    if (!store.paste_code.isNullOrBlank() && !store.paste_code_expires_at.isNullOrBlank()) {
                        applyPasteCode(store.paste_code, store.paste_code_expires_at)
                    } else {
                        clearPasteCodeUi()
                    }
                }

                // 2. Fetch Category Count
                val categories = SupabaseProvider.client.postgrest.rpc(
                    "get_user_categories",
                    buildJsonObject { put("p_store_id", sId) }
                ).decodeList<UserCategoryRow>()
                tvStatCategories.text = categories.size.toString()

                // 3. Fetch Product Count
                val products = SupabaseProvider.client.postgrest.rpc(
                    "get_store_products",
                    buildJsonObject {
                        put("p_store_id", sId)
                        put("p_category_filter", "PRICELIST")
                        put("p_search_query", null as String?)
                    }
                ).decodeList<UserProductRow>()
                tvStatItems.text = products.size.toString()

                // 4. Fetch Members and calculate counts by role
                val members = SupabaseProvider.client.postgrest.rpc(
                    "get_store_members",
                    buildJsonObject { put("p_store_id", sId) }
                ).decodeList<StoreMemberUser>()

                tvStatMembers.text = members.size.toString()
                val ownersCount = members.count { it.role.lowercase() == "owner" }
                val managersCount = members.count { it.role.lowercase() == "manager" }
                val salesStaffCount = members.count { it.role.lowercase() == "employee" }

                tvStatOwners.text = ownersCount.toString()
                tvStatManagers.text = managersCount.toString()
                tvStatSalesStaff.text = salesStaffCount.toString()

                // Fetch current user's role
                val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id.orEmpty()
                val currentMember = members.firstOrNull { it.user_id == uid }
                currentUserRole = currentMember?.role ?: "employee"

                // 5. Fetch Suki relationship count via RPC to bypass RLS
                val sukiCount = try {
                    SupabaseProvider.client.postgrest.rpc(
                        "get_store_suki_count",
                        buildJsonObject { put("p_store_id", sId) }
                    ).decodeAs<Int>()
                } catch (e: Exception) {
                    0
                }
                tvStatSuki.text = sukiCount.toString()

                // Dynamically update the Delete Store button to Leave Store if there are multiple owners
                val isUserOwner = currentUserRole.lowercase() == "owner"
                if (isUserOwner) {
                    toolDeleteStore.visibility = View.VISIBLE
                    if (ownersCount > 1) {
                        ivDeleteStoreIcon.setImageResource(R.drawable.icon_leave_store)
                        val sizePx = (22 * resources.displayMetrics.density).toInt()
                        ivDeleteStoreIcon.layoutParams.width = sizePx
                        ivDeleteStoreIcon.layoutParams.height = sizePx
                        ivDeleteStoreIcon.requestLayout()
                        toolDeleteStore.contentDescription = "Leave store"
                        toolDeleteStore.setOnClickListener {
                            showManageStoreLeaveConfirmation(sId, storeName ?: "Store")
                        }
                    } else {
                        ivDeleteStoreIcon.setImageResource(R.drawable.icon_delete)
                        val sizePx = (18 * resources.displayMetrics.density).toInt()
                        ivDeleteStoreIcon.layoutParams.width = sizePx
                        ivDeleteStoreIcon.layoutParams.height = sizePx
                        ivDeleteStoreIcon.requestLayout()
                        toolDeleteStore.contentDescription = "Delete store"
                        toolDeleteStore.setOnClickListener {
                            showManageStoreDeleteConfirmation(sId, storeName ?: "Store")
                        }
                    }
                } else {
                    toolDeleteStore.visibility = View.GONE
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ManageStoreActivity, "Error loading statistics: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                if (showShimmer) {
                    shimmerStoreSettings.stopShimmer()
                    shimmerStoreSettings.visibility = View.GONE
                    layoutStoreCardInner.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setupPublicCheckbox() {
        cbMakeStorePublic.setOnCheckedChangeListener { _, isChecked ->
            // Temporarily detach listener to prevent programmatic triggers
            cbMakeStorePublic.setOnCheckedChangeListener(null)
            cbMakeStorePublic.isChecked = !isChecked

            val title = if (isChecked) "Publish Store" else "Unpublish Store"
            val message = if (isChecked) {
                "Are you sure you want to publish this store?\n\nMaking your store public allows sukis to view your products and prices."
            } else {
                "Are you sure you want to unpublish this store?\n\nMaking your store private will hide all products and prices from your suki."
            }
            val actionText = if (isChecked) "Publish" else "Unpublish"

            showReusableDialog(
                title = title,
                message = message,
                positiveButtonText = actionText,
                positiveAction = {
                    cbMakeStorePublic.isChecked = isChecked
                    setupPublicCheckbox()

                    lifecycleScope.launch {
                        try {
                            SupabaseProvider.client.postgrest["stores"].update(
                                mapOf("is_public" to isChecked)
                            ) {
                                filter { eq("id", storeId!!) }
                            }
                            tvStatStatus.text = if (isChecked) "Public" else "Private"
                            Toast.makeText(
                                this@ManageStoreActivity,
                                if (isChecked) "Store is now Public." else "Store is now Private.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (e: Exception) {
                            cbMakeStorePublic.setOnCheckedChangeListener(null)
                            cbMakeStorePublic.isChecked = !isChecked
                            setupPublicCheckbox()
                            Toast.makeText(this@ManageStoreActivity, "Failed to update store visibility.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                negativeButtonText = "Cancel",
                negativeAction = {
                    setupPublicCheckbox()
                }
            )
        }
    }

    private fun showManageStoreDeleteConfirmation(storeId: String, storeName: String) {
        val confirmDialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reusable_template, null)
        confirmDialog.setContentView(view)
        confirmDialog.setCancelable(true)
        confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.dialogMessage)
        val btnCancel = view.findViewById<Button>(R.id.btnNegative)
        val btnAction = view.findViewById<Button>(R.id.btnPositive)

        btnCancel.text = "Cancel"
        title.text = "Delete Store"
        message.text = "Are you sure you want to delete this store?\n\nDeleting your store \"$storeName\" will permanently delete all products, members, and categories.\n\nThis cannot be undone."
        btnAction.text = "Delete"

        btnCancel.setOnClickListener { confirmDialog.dismiss() }

        btnAction.setOnClickListener {
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest["stores"].delete { filter { eq("id", storeId) } }
                    Toast.makeText(this@ManageStoreActivity, "Store deleted successfully.", Toast.LENGTH_SHORT).show()
                    confirmDialog.dismiss()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@ManageStoreActivity, "Action failed. Check internet.", Toast.LENGTH_LONG).show()
                }
            }
        }
        confirmDialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        confirmDialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun showManageStoreLeaveConfirmation(storeId: String, storeName: String) {
        val confirmDialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_reusable_template, null)
        confirmDialog.setContentView(view)
        confirmDialog.setCancelable(true)
        confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.dialogMessage)
        val btnCancel = view.findViewById<Button>(R.id.btnNegative)
        val btnAction = view.findViewById<Button>(R.id.btnPositive)

        btnCancel.text = "Cancel"
        title.text = "Leave Store"
        btnAction.text = "Leave"
        message.text = "Are you sure you want to leave this store?\n\nThe store will remain active, but you will lose all access and ownership rights."

        btnCancel.setOnClickListener { confirmDialog.dismiss() }

        btnAction.setOnClickListener {
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest.rpc(
                        "leave_store",
                        buildJsonObject { put("p_store_id", storeId) }
                    )
                    Toast.makeText(this@ManageStoreActivity, "You have left the store.", Toast.LENGTH_SHORT).show()
                    confirmDialog.dismiss()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@ManageStoreActivity, "Action failed. Check internet.", Toast.LENGTH_LONG).show()
                } finally {
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            }
        }
        confirmDialog.show()
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        confirmDialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun formatStoreId(uuid: String): String {
        return try {
            val parts = uuid.split("-")
            val p1 = parts[0].take(3)
            val p2 = parts[1].take(3)
            "SID$p1-$p2".uppercase()
        } catch (e: Exception) {
            "SID-" + uuid.take(6).uppercase()
        }
    }

    private fun formatCreatedAt(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "--/--/--"
        return try {
            val parser = java.time.format.DateTimeFormatter.ISO_DATE_TIME
            val date = java.time.ZonedDateTime.parse(isoString, parser)
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MM/dd/yy")
            date.format(formatter)
        } catch (e: Exception) {
            try {
                val sdfInput = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val sdfOutput = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
                val parsed = sdfInput.parse(isoString.substring(0, 10))
                if (parsed != null) sdfOutput.format(parsed) else "--/--/--"
            } catch (e2: Exception) {
                "--/--/--"
            }
        }
    }

    private fun setStoreIcon(type: String?) {
        ivStoreIcon.setImageResource(R.drawable.icon_store)
    }

    private fun showImportDialog() {
        val intent = Intent(this, AddMultipleItemsActivity::class.java).apply {
            putExtra("storeId", storeId)
            putExtra("storeName", storeName)
            putExtra("showImportDialog", true)
        }
        startActivity(intent)
    }

    private var countdownJob: kotlinx.coroutines.Job? = null
    private var inviteCodeCountdownJob: kotlinx.coroutines.Job? = null

    private fun parseInviteCreatedMillis(createdIso: String?): Long? {
        if (createdIso.isNullOrBlank()) return null
        val clean = createdIso.trim().replace(" ", "T")
        val hasTimezone = clean.contains("+") || (clean.lastIndexOf("-") > clean.indexOf("T")) || clean.endsWith("Z")
        val parsedStr = if (hasTimezone) clean else clean + "Z"
        return try {
            java.time.Instant.parse(parsedStr).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(parsedStr).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    java.time.LocalDateTime.parse(clean).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                } catch (_: Exception) { null }
            }
        }
    }

    private fun startInviteCountdown(expiryText: TextView, codeText: TextView, copyBtn: View, expiryMillis: Long) {
        inviteCodeCountdownJob?.cancel()
        inviteCodeCountdownJob = lifecycleScope.launch {
            try {
                while (true) {
                    val remaining = expiryMillis - System.currentTimeMillis()
                    if (remaining <= 0) {
                        codeText.visibility = View.VISIBLE
                        copyBtn.visibility = View.VISIBLE
                        expiryText.text = "Code expired"
                        break
                    }
                    val hrs = remaining / 3600000
                    val mins = (remaining % 3600000) / 60000
                    val secs = (remaining % 60000) / 1000
                    expiryText.text = String.format("Expires in %02d:%02d:%02d", hrs, mins, secs)
                    kotlinx.coroutines.delay(1000)
                }
            } catch (_: Exception) {
                expiryText.text = "Expires soon"
            }
        }
    }

    private fun clearPasteCodeUi() {
        storeCodeTextView.text = "------"
        storeCodeExpiryView.text = "No active code"
        btnCopyPasteCode.visibility = View.GONE
        tvStatPasteCode.text = "None"
        countdownJob?.cancel()
    }

    private fun applyPasteCode(code: String, expiresAtIso: String) {
        storeCodeTextView.text = code
        storeCodeTextView.visibility = View.VISIBLE
        btnCopyPasteCode.visibility = View.VISIBLE
        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            try {
                val expiry = java.time.OffsetDateTime.parse(expiresAtIso).toInstant()
                while (true) {
                    val now = java.time.Instant.now()
                    val remaining = java.time.Duration.between(now, expiry).seconds
                    if (remaining <= 0) {
                        storeCodeTextView.visibility = View.GONE
                        btnCopyPasteCode.visibility = View.GONE
                        storeCodeExpiryView.text = "No active code"
                        tvStatPasteCode.text = "None"
                        break
                    }
                    val hrs = remaining / 3600
                    val mins = (remaining % 3600) / 60
                    val secs = remaining % 60
                    storeCodeExpiryView.text = String.format("Expires in %02d:%02d:%02d", hrs, mins, secs)
                    kotlinx.coroutines.delay(1000)
                }
            } catch (e: Exception) {
                storeCodeExpiryView.text = "Expires soon"
            }
        }
    }

    private fun formatExportFilename(): String {
        val sName = (storeName ?: "store").trim()
        val bName = (branchName ?: "main").trim()
        val slug = "${sName}_${bName}".lowercase()
            .replace("\n", " ")
            .replace("\\s+".toRegex(), "-")
            .replace("[^a-z0-9\\-]".toRegex(), "")
        val d = java.util.Calendar.getInstance()
        val y = d.get(java.util.Calendar.YEAR)
        val m = String.format("%02d", d.get(java.util.Calendar.MONTH) + 1)
        val day = String.format("%02d", d.get(java.util.Calendar.DAY_OF_MONTH))
        val hh = String.format("%02d", d.get(java.util.Calendar.HOUR_OF_DAY))
        val mm = String.format("%02d", d.get(java.util.Calendar.MINUTE))
        val ss = String.format("%02d", d.get(java.util.Calendar.SECOND))
        return "presyohan_${slug}_pricelist_${y}${m}${day}_${hh}${mm}${ss}.xlsx"
    }

    private fun exportPricelistToExcel() {
        if (storeId.isNullOrBlank()) return
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            var overlayVisible = true
            try {
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "get_store_products",
                    buildJsonObject {
                        put("p_store_id", storeId!!)
                        put("p_category_filter", "PRICELIST")
                        put("p_search_query", null as String?)
                    }
                ).decodeList<StoreProductRow>()

                if (rows.isEmpty()) {
                    Toast.makeText(this@ManageStoreActivity, "No products to export.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val sorted = rows.sortedWith(compareBy(
                    { (it.category ?: "").lowercase() },
                    { (it.name ?: "").lowercase() }
                ))

                LoadingOverlayHelper.hide(loadingOverlay)
                overlayVisible = false
                showExportConfirmationDialog(sorted)
            } catch (e: Exception) {
                Toast.makeText(this@ManageStoreActivity, "Failed to export.", Toast.LENGTH_LONG).show()
            } finally {
                if (overlayVisible) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            }
        }
    }

    private fun showExportConfirmationDialog(rows: List<StoreProductRow>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_export_confirmation, null)
        val rowsCountView = dialogView.findViewById<TextView>(R.id.rowsCount)
        val rowsLabelView = dialogView.findViewById<TextView>(R.id.rowsLabel)
        val scopeValueView = dialogView.findViewById<TextView>(R.id.scopeValue)
        val subtitleView = dialogView.findViewById<TextView>(R.id.exportSubtitle)
        val exportDetailsCard = dialogView.findViewById<View>(R.id.exportDetailsCard)
        val exportTypeGroup = dialogView.findViewById<RadioGroup>(R.id.exportTypeGroup)
        val notePreviewContainer = dialogView.findViewById<View>(R.id.notePreviewContainer)
        val notePreviewText = dialogView.findViewById<TextView>(R.id.notePreviewText)
        val noteMetaText = dialogView.findViewById<TextView>(R.id.noteMetaText)
        val btnCopyNote = dialogView.findViewById<AppCompatButton>(R.id.btnCopyNote)
        val btnConfirmExport = dialogView.findViewById<AppCompatButton>(R.id.btnConfirmExport)

        rowsCountView.text = rows.size.toString()
        rowsLabelView.text = if (rows.size == 1) "Row to export" else "Rows to export"
        scopeValueView.text = "All products"
        subtitleView.text = "Generate a professionally formatted Excel file or a sharable note."

        val noteText = buildNoteText(rows, storeName, branchName)
        notePreviewText.text = if (noteText.isNotBlank()) noteText else "No products available. Add items to generate a note."
        noteMetaText.text = "${rows.size} items • ${noteText.length} characters"
        btnCopyNote.isEnabled = noteText.isNotBlank()
        btnCopyNote.setOnClickListener {
            if (noteText.isNotBlank()) {
                copyNoteToClipboard(noteText)
            } else {
                Toast.makeText(this, "Nothing to copy.", Toast.LENGTH_SHORT).show()
            }
        }

        var selectedExportType = ExportType.EXCEL
        fun refreshExportTypeUi() {
            val isExcel = selectedExportType == ExportType.EXCEL
            exportDetailsCard.visibility = if (isExcel) View.VISIBLE else View.GONE
            notePreviewContainer.visibility = if (isExcel) View.GONE else View.VISIBLE
            btnConfirmExport.text = if (isExcel) "Export Excel" else "Share Note"
            btnConfirmExport.isEnabled = if (isExcel) true else noteText.isNotBlank()
        }

        exportTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedExportType = if (checkedId == R.id.rbExportNotes) ExportType.NOTES else ExportType.EXCEL
            refreshExportTypeUi()
        }
        exportTypeGroup.check(R.id.rbExportExcel)
        refreshExportTypeUi()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<View>(R.id.btnCancelExport).setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmExport.setOnClickListener {
            if (selectedExportType == ExportType.EXCEL) {
                dialog.dismiss()
                lifecycleScope.launch {
                    LoadingOverlayHelper.show(loadingOverlay)
                    try {
                        performPricelistExport(rows)
                    } finally {
                        LoadingOverlayHelper.hide(loadingOverlay)
                    }
                }
            } else {
                if (noteText.isBlank()) {
                    Toast.makeText(this, "No products available for notes.", Toast.LENGTH_SHORT).show()
                } else {
                    shareNoteText(noteText)
                }
            }
        }

        dialog.show()
    }

    private fun performPricelistExport(rows: List<StoreProductRow>) {
        if (rows.isEmpty()) {
            Toast.makeText(this, "No products to export.", Toast.LENGTH_SHORT).show()
            return
        }

        val sorted = rows.sortedWith(compareBy(
            { (it.category ?: "").lowercase() },
            { (it.name ?: "").lowercase() }
        ))

        val filename = formatExportFilename()
        val mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        val output: java.io.OutputStream? = try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri: Uri? = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) contentResolver.openOutputStream(uri) else null
            } else {
                val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, filename)
                FileOutputStream(file)
            }
        } catch (_: Exception) { null }

        if (output == null) {
            Toast.makeText(this, "Failed to create file.", Toast.LENGTH_LONG).show()
            return
        }

        try {
            val wb = org.dhatim.fastexcel.Workbook(output, "Presyohan", "1.0")
            val ws = wb.newWorksheet("Pricelist")

            ws.value(0, 0, "Presyohan")
            ws.style(0, 0).bold().set()

            ws.value(1, 0, "Store: ${storeName ?: ""} — Branch: ${branchName ?: ""}")
            val exporter = try { SupabaseAuthService.getDisplayNameImmediate() } catch (_: Exception) { "User" }
            val exportedAt = java.util.Date().toLocaleString()
            ws.value(2, 0, "Exported by: ${exporter}    |    Exported at: ${exportedAt}")

            val headers = listOf("Category", "Name", "Description", "Unit", "Price")
            headers.forEachIndexed { idx, h ->
                ws.value(4, idx, h)
                ws.style(4, idx).bold().set()
            }

            var rowIndex = 5
            sorted.forEach { r ->
                ws.value(rowIndex, 0, r.category ?: "")
                ws.value(rowIndex, 1, r.name ?: "")
                ws.value(rowIndex, 2, r.description ?: "")
                ws.value(rowIndex, 3, r.units ?: "")
                val priceNum = r.price ?: 0.0
                ws.value(rowIndex, 4, priceNum)
                ws.style(rowIndex, 4).format("\"₱\"#,##0.00").set()
                rowIndex++
            }

            try {
                ws.width(0, 20.0)
                ws.width(1, 28.0)
                ws.width(2, 40.0)
                ws.width(3, 12.0)
                ws.width(4, 14.0)
            } catch (_: Throwable) { /* optional */ }

            wb.finish()
            output.flush()
            Toast.makeText(this, "Excel exported to Downloads.", Toast.LENGTH_SHORT).show()
            notifyExportSuccessOrRequest(filename)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            try { output.close() } catch (_: Exception) {}
        }
    }

    private fun buildNoteText(rows: List<StoreProductRow>, storeName: String?, branchName: String?): String {
        if (rows.isEmpty()) return ""
        val priceFormat = NumberFormat.getNumberInstance(Locale("en", "PH")).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val grouped = rows.groupBy { row ->
            val cat = row.category?.trim()
            if (cat.isNullOrBlank()) "General" else cat
        }
        val sortedCategories = grouped.keys.sortedWith(compareBy<String> { it.lowercase(Locale.getDefault()) }.thenBy { it })
        val dateFormatter = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        val lines = mutableListOf<String>()
        lines.add("PRICELIST:")
        val cleanStore = storeName?.trim().orEmpty()
        val cleanBranch = branchName?.trim().orEmpty()
        val storeLine = when {
            cleanStore.isNotEmpty() && cleanBranch.isNotEmpty() -> "$cleanStore — $cleanBranch"
            cleanStore.isNotEmpty() -> cleanStore
            cleanBranch.isNotEmpty() -> cleanBranch
            else -> ""
        }
        if (storeLine.isNotBlank()) lines.add(storeLine)
        lines.add(dateFormatter.format(Date()))
        lines.add("")

        sortedCategories.forEachIndexed { index, category ->
            lines.add("[$category]")
            grouped[category].orEmpty()
                .sortedWith(compareBy<StoreProductRow> { (it.name ?: "").lowercase(Locale.getDefault()) })
                .forEach { row ->
                    val name = row.name?.takeIf { it.isNotBlank() } ?: "Unnamed Item"
                    val desc = row.description?.trim().orEmpty()
                    val descPart = if (desc.isNotEmpty()) " ($desc)" else ""
                    val priceValue = row.price ?: 0.0
                    val unit = row.units?.trim().orEmpty()
                    val unitPart = if (unit.isNotEmpty()) " | $unit" else ""
                    lines.add("• $name$descPart — ₱${priceFormat.format(priceValue)}$unitPart")
                }
            if (index != sortedCategories.lastIndex) {
                lines.add("")
            }
        }
        lines.add("")
        lines.add("Shared via Presyohan")
        return lines.joinToString("\n").trim()
    }

    private fun copyNoteToClipboard(noteText: String) {
        try {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Presyohan Note", noteText))
            Toast.makeText(this, "Note copied to clipboard.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to copy note.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareNoteText(noteText: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Presyohan Pricelist")
                putExtra(Intent.EXTRA_TEXT, noteText)
            }
            startActivity(Intent.createChooser(intent, "Share note"))
        } catch (_: Exception) {
            Toast.makeText(this, "Unable to share note.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureExportNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "presyohan.exports"
            val name = "File Exports"
            val descriptionText = "Notifications when files are saved to Downloads"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance)
            channel.description = descriptionText
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun notifyExportSuccessOrRequest(filename: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                postExportNotification(filename)
            } else {
                lastExportFilenamePending = filename
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            postExportNotification(filename)
        }
    }

    private fun postExportNotification(filename: String) {
        try {
            ensureExportNotificationChannel()
            val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "presyohan.exports" else ""
            val intent = android.content.Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE else PendingIntent.FLAG_UPDATE_CURRENT
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)

            val builder = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("File downloaded")
                .setContentText("Saved to Downloads: $filename")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            NotificationManagerCompat.from(this)
                .notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
        } catch (se: SecurityException) {
            Toast.makeText(this, "Enable notifications for Presyohan to see download alerts.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            // ignore
        }
    }

    private fun showInviteStaffDialog(inviteCode: String?, expiryMillis: Long?) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_invite_staff, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val layoutDirectInvitation = view.findViewById<View>(R.id.layoutDirectInvitation)
        val layoutJoinStoreCode = view.findViewById<View>(R.id.layoutJoinStoreCode)
        val btnSwitchToCode = view.findViewById<View>(R.id.btnSwitchToCode)
        val btnSwitchToDirect = view.findViewById<View>(R.id.btnSwitchToDirect)
        val btnDone = view.findViewById<View>(R.id.btnDone)

        val layoutCodeInactive = view.findViewById<View>(R.id.layoutCodeInactive)
        val layoutCodeActive = view.findViewById<View>(R.id.layoutCodeActive)

        val codeText = view.findViewById<TextView>(R.id.storeCodeText)
        val expiryText = view.findViewById<TextView>(R.id.inviteCodeExpiry)
        val copyBtnActive = view.findViewById<View>(R.id.btnCopyCodeActive)

        val btnGenerateCodeInactive = view.findViewById<View>(R.id.btnGenerateCodeInactive)
        val btnGenerateCodeActive = view.findViewById<View>(R.id.btnGenerateCodeActive)
        val btnRevokeCodeActive = view.findViewById<View>(R.id.btnRevokeCodeActive)

        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val searchLoader = view.findViewById<View>(R.id.searchLoader)
        val textNotFound = view.findViewById<TextView>(R.id.textNotFound)
        val userResultContainer = view.findViewById<LinearLayout>(R.id.userResultContainer)
        val foundAvatar = view.findViewById<View>(R.id.foundUserAvatar) as? ImageView
        val foundName = view.findViewById<TextView>(R.id.foundUserName)
        val foundDetails = view.findViewById<TextView>(R.id.foundUserDetails)
        val btnInvite = view.findViewById<Button>(R.id.btnInvite)
        val spinnerRole = view.findViewById<android.widget.AutoCompleteTextView>(R.id.spinnerRole)
        val inviteErrorText = view.findViewById<TextView>(R.id.inviteErrorText)

        @Serializable
        data class SearchedUser(
            val id: String,
            val name: String? = null,
            val email: String? = null,
            val user_code: String? = null,
            val avatar_url: String? = null
        )

        val rolesDisplay = listOf("View only price list", "Manage prices")
        val rolesValue = listOf("employee", "manager")
        val adp = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rolesDisplay)
        spinnerRole.setAdapter(adp)
        spinnerRole.setText(rolesDisplay[0], false)

        var selectedUser: SearchedUser? = null
        val searchHandler = Handler(Looper.getMainLooper())
        var searchRunnable: Runnable? = null

        fun updateCodeUI(code: String?, expiresAt: Long?) {
            inviteCodeCountdownJob?.cancel()
            val now = System.currentTimeMillis()
            val isValid = !code.isNullOrBlank() && expiresAt != null && expiresAt > now

            if (isValid) {
                layoutCodeInactive.visibility = View.GONE
                layoutCodeActive.visibility = View.VISIBLE
                codeText.text = code
                startInviteCountdown(expiryText, codeText, copyBtnActive, expiresAt)
                tvStatJoinCode.text = code
            } else {
                layoutCodeInactive.visibility = View.VISIBLE
                layoutCodeActive.visibility = View.GONE
                expiryText.text = ""
                tvStatJoinCode.text = if (code != null && expiresAt != null && expiresAt <= now) "Expired" else "None"
            }
        }
        updateCodeUI(inviteCode, expiryMillis)

        btnSwitchToCode.setOnClickListener {
            layoutDirectInvitation.visibility = View.GONE
            layoutJoinStoreCode.visibility = View.VISIBLE
        }

        btnSwitchToDirect.setOnClickListener {
            layoutJoinStoreCode.visibility = View.GONE
            layoutDirectInvitation.visibility = View.VISIBLE
        }

        btnDone.setOnClickListener {
            dialog.dismiss()
        }

        copyBtnActive.setOnClickListener {
            val code = codeText.text.toString()
            if (code.isNotEmpty()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Store Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Code copied.", Toast.LENGTH_SHORT).show()
            }
        }

        fun generateCode() {
            val sId = storeId ?: return
            btnGenerateCodeInactive.isEnabled = false
            btnGenerateCodeActive.isEnabled = false

            lifecycleScope.launch {
                try {
                    val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id.orEmpty()
                    val roleRows = SupabaseProvider.client.postgrest["store_members"].select {
                        filter { eq("store_id", sId); eq("user_id", uid) }
                        limit(1)
                    }.decodeList<com.presyohan.app.StoreActivity.StoreMemberRow>()
                    val role = roleRows.firstOrNull()?.role?.lowercase()
                    val isOwner = role == "owner"
                    if (!isOwner) {
                        Toast.makeText(this@ManageStoreActivity, "Only the owner can generate a code.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val rows = SupabaseProvider.client.postgrest.rpc(
                        "regenerate_invite_code",
                        buildJsonObject { put("p_store_id", sId) }
                    ).decodeList<com.presyohan.app.StoreActivity.InviteCodeReturn>()
                    val newCode = rows.firstOrNull()?.invite_code
                    val created = rows.firstOrNull()?.invite_code_created_at

                    val newCreatedMillis = parseInviteCreatedMillis(created)
                    val newExpiry = newCreatedMillis?.plus(86400000L)

                    runOnUiThread {
                        updateCodeUI(newCode, newExpiry)
                        Toast.makeText(this@ManageStoreActivity, "Invite code updated.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ManageStoreActivity, "Unable to update invite code.", Toast.LENGTH_SHORT).show()
                } finally {
                    btnGenerateCodeInactive.isEnabled = true
                    btnGenerateCodeActive.isEnabled = true
                }
            }
        }

        btnGenerateCodeInactive.setOnClickListener { generateCode() }
        btnGenerateCodeActive.setOnClickListener { generateCode() }

        btnRevokeCodeActive.setOnClickListener {
            val sId = storeId ?: return@setOnClickListener
            btnRevokeCodeActive.isEnabled = false
            lifecycleScope.launch {
                try {
                    val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id.orEmpty()
                    val roleRows = SupabaseProvider.client.postgrest["store_members"].select {
                        filter { eq("store_id", sId); eq("user_id", uid) }
                        limit(1)
                    }.decodeList<com.presyohan.app.StoreActivity.StoreMemberRow>()
                    val role = roleRows.firstOrNull()?.role?.lowercase()
                    val isOwner = role == "owner"
                    if (!isOwner) {
                        Toast.makeText(this@ManageStoreActivity, "Only the owner can revoke code.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    SupabaseProvider.client.postgrest["stores"].update(
                        mapOf(
                            "invite_code" to null,
                            "invite_code_created_at" to null
                        )
                    ) {
                        filter { eq("id", sId) }
                    }

                    runOnUiThread {
                        updateCodeUI(null, null)
                        Toast.makeText(this@ManageStoreActivity, "Invite code revoked.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ManageStoreActivity, "Failed to revoke code.", Toast.LENGTH_SHORT).show()
                } finally {
                    btnRevokeCodeActive.isEnabled = true
                }
            }
        }

        fun toggleInviteButton() {
            btnInvite.isEnabled = selectedUser != null
            btnInvite.alpha = if (selectedUser != null) 1.0f else 0.5f
        }

        val performSearch = { query: String ->
            searchLoader.visibility = View.VISIBLE
            textNotFound.visibility = View.GONE
            userResultContainer.visibility = View.GONE
            selectedUser = null
            toggleInviteButton()

            lifecycleScope.launch {
                try {
                    val results = SupabaseProvider.client.postgrest.rpc(
                        "search_app_user",
                        buildJsonObject { put("search_term", query) }
                    ).decodeList<SearchedUser>()

                    searchLoader.visibility = View.GONE

                    if (results.isNotEmpty()) {
                        val user = results[0]
                        selectedUser = user
                        userResultContainer.visibility = View.VISIBLE
                        foundName.text = user.name ?: "Unnamed User"
                        val code = user.user_code ?: "NO-ID"
                        val email = user.email ?: ""
                        foundDetails.text = "$code • $email"

                        if (!user.avatar_url.isNullOrBlank()) {
                            foundAvatar?.load(user.avatar_url) {
                                crossfade(true)
                                transformations(CircleCropTransformation())
                            }
                        } else {
                            foundAvatar?.setImageResource(R.drawable.avatar_default)
                        }
                    } else {
                        textNotFound.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    searchLoader.visibility = View.GONE
                    textNotFound.text = "Error searching user."
                    textNotFound.visibility = View.VISIBLE
                }
                toggleInviteButton()
            }
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                inviteErrorText.visibility = View.GONE
                val query = s.toString().trim()
                if (query.isEmpty()) {
                    searchLoader.visibility = View.GONE
                    textNotFound.visibility = View.GONE
                    userResultContainer.visibility = View.GONE
                    selectedUser = null
                    toggleInviteButton()
                    return
                }
                searchRunnable = Runnable { performSearch(query) }
                searchHandler.postDelayed(searchRunnable!!, 500)
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        view.findViewById<Button>(R.id.btnBack).setOnClickListener { dialog.dismiss() }

        btnInvite.setOnClickListener {
            val user = selectedUser ?: return@setOnClickListener
            val sId = storeId ?: return@setOnClickListener
            val roleText = spinnerRole.text.toString()
            val roleIdx = rolesDisplay.indexOf(roleText).coerceAtLeast(0)
            val selectedRole = rolesValue.getOrElse(roleIdx) { "employee" }

            btnInvite.text = "Inviting..."
            btnInvite.isEnabled = false

            lifecycleScope.launch {
                try {
                    val params = buildJsonObject {
                        put("p_store_id", sId)
                        put("p_email", user.email)
                        put("p_role", selectedRole)
                    }
                    SupabaseProvider.client.postgrest.rpc("send_store_invitation", params)
                    Toast.makeText(this@ManageStoreActivity, "Invitation sent to ${user.name}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    val msg = e.message ?: "Failed to invite."
                    inviteErrorText.text = if (msg.contains("already a member", ignoreCase = true)) "User is already a member." else "Failed to send invitation."
                    inviteErrorText.visibility = View.VISIBLE
                    btnInvite.text = "Invite"
                    btnInvite.isEnabled = true
                }
            }
        }

        dialog.setOnDismissListener {
            inviteCodeCountdownJob?.cancel()
            refreshStoreStatsQuietly()
        }
        dialog.show()
    }

    private fun showInviteStaffWithCode() {
        lifecycleScope.launch {
            try {
                @Serializable
                data class StoreInviteFields(
                    val invite_code: String? = null,
                    val invite_code_created_at: String? = null
                )
                val rows = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("id", storeId!!) }
                    limit(1)
                }.decodeList<StoreInviteFields>()
                val row = rows.firstOrNull()
                val code = row?.invite_code
                val created = row?.invite_code_created_at

                val createdMillis = parseInviteCreatedMillis(created)
                val expiryMillis = createdMillis?.plus(86400000L)
                showInviteStaffDialog(code, expiryMillis)
            } catch (_: Exception) {
                Toast.makeText(this@ManageStoreActivity, "Unable to fetch invite code. Generate a new one.", Toast.LENGTH_SHORT).show()
                showInviteStaffDialog(null, null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreHome(this, storeId, storeName)
        refreshStoreStatsQuietly()
    }
}
