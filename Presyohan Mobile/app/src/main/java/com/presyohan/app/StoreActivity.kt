package com.presyohan.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.presyohan.app.adapter.Store
import com.presyohan.app.adapter.StoreAdapter
import androidx.core.content.ContextCompat

import io.github.jan.supabase.auth.auth
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
// Imports for Export
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.DownloadManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import java.io.File
import java.io.FileOutputStream
import androidx.appcompat.app.AlertDialog
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import coil.load
import coil.transform.CircleCropTransformation

class StoreActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private val db = FirebaseFirestore.getInstance()
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StoreAdapter
    private val stores = mutableListOf<Store>()
    private var storeRolesMap: Map<String, String> = emptyMap()
    private var lastBackPress: Long = 0
    private lateinit var loadingOverlay: android.view.View

    // To track the countdown job inside the dialog
    private var inviteCodeCountdownJob: kotlinx.coroutines.Job? = null

    // Export permission handler
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

    private val createStoreLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // After returning from CreateStoreActivity, refresh list
        fetchStores()
    }

    @Serializable
    data class StoreMemberRow(val store_id: String, val user_id: String, val role: String)

    @Serializable
    data class StoreRow(val id: String, val name: String, val branch: String? = null, val type: String? = null)

    // Minimal shape to avoid user_id/member_user_id mismatch; RLS filters rows for current user
    @Serializable
    data class StoreMemberLite(val store_id: String, val role: String)

    // Result shape for get_user_stores RPC
    @Serializable
    data class UserStoreRow(
        val store_id: String,
        val name: String,
        val branch: String? = null,
        val type: String? = null,
        val role: String
    )

    @Serializable
    data class InviteCodeReturn(
        val invite_code: String,
        val invite_code_created_at: String
    )

    @Serializable
    data class StoreInviteFields(
        val invite_code: String? = null,
        val invite_code_created_at: String? = null
    )

    // For Export
    @Serializable
    data class StoreProductExportRow(
        val category: String? = null,
        val name: String? = null,
        val description: String? = null,
        val units: String? = null,
        val price: Double? = null
    )

    private enum class ExportType {
        EXCEL,
        NOTES
    }

    // Data class for RPC response to ensure we get all members correctly (Owner check)
    @Serializable
    data class StoreMemberUser(val user_id: String, val name: String, val role: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_store)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        navigationView.setNavigationItemSelectedListener(this)
        HeaderUtils.updateHeader(this, navigationView)

        // Open drawer when menu icon is clicked
        findViewById<ImageView>(R.id.menuIcon).setOnClickListener {
            drawerLayout.open()
        }

        // Add Store button logic
        findViewById<ImageButton>(R.id.addStoreButton).setOnClickListener {
            showStoreChoiceDialog()
        }

        recyclerView = findViewById(R.id.recyclerViewStores)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = StoreAdapter(stores, emptyMap(), { store, anchor ->
            showStoreMenu(store, anchor)
        }, { store ->
            // On store card tap, open HomeActivity
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra("storeId", store.id)
            intent.putExtra("storeName", store.name)
            startActivity(intent)
            // Don't finish() here if you want back button to return to list
        })
        recyclerView.adapter = adapter


        checkUserStore()
        fetchStores()

        val notifIcon = findViewById<ImageView>(R.id.notifIcon)
        notifIcon.setOnClickListener {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
        }

        loadNotifBadge()
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreList(this)
        fetchStores()
        loadNotifBadge()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.nav_logout) {
            // --- LOGOUT CONFIRMATION DIALOG START ---
            val dialog = Dialog(this)
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
            dialog.setContentView(view)
            dialog.setCancelable(true)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            view.findViewById<TextView>(R.id.dialogTitle).text = "Log Out?"
            view.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to log out of Presyohan?"

            view.findViewById<Button>(R.id.btnCancel).setOnClickListener {
                dialog.dismiss()
            }

            view.findViewById<Button>(R.id.btnDelete).apply {
                text = "Log Out"
                setOnClickListener {
                    lifecycleScope.launch {
                        try {
                            SupabaseAuthService.signOut()
                        } catch (_: Exception) { }
                        val intent = Intent(this@StoreActivity, LoginActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        startActivity(intent)
                        finish()
                    }
                    dialog.dismiss()
                    drawerLayout.closeDrawer(android.view.Gravity.START)
                }
            }
            dialog.show()
            return true
        }
        if (item.itemId == R.id.nav_notifications) {
            val intent = Intent(this, NotificationActivity::class.java)
            startActivity(intent)
            drawerLayout.closeDrawer(android.view.Gravity.START)
            return true
        }
        return false
    }

    override fun onBackPressed() {
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000) {
            finishAffinity()
        } else {
            lastBackPress = now
            android.widget.Toast.makeText(this, "Press again to exit", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkUserStore() {
        val userId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return
        db.collection("stores")
            .whereArrayContains("members", userId)
            .get()
            .addOnSuccessListener { documents ->
                // No dialog here
            }
            .addOnFailureListener {
                // No dialog here
            }
    }

    private fun showStoreChoiceDialog() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_store_choice, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnCreate = view.findViewById<Button>(R.id.btnCreateStore)
        val btnJoin = view.findViewById<Button>(R.id.btnJoinStore)

        btnCreate.setOnClickListener {
            createStoreLauncher.launch(Intent(this, CreateStoreActivity::class.java))
            dialog.dismiss()
        }

        btnJoin.setOnClickListener {
            startActivity(Intent(this, com.presyohan.app.JoinStoreActivity::class.java))
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun fetchStores() {
        val client = SupabaseProvider.client
        val userId = client.auth.currentUserOrNull()?.id ?: return
        val noStoreLabel = findViewById<TextView>(R.id.noStoreLabel)

        LoadingOverlayHelper.show(loadingOverlay)

        lifecycleScope.launch {
            try {
                val rows = client.postgrest.rpc("get_user_stores").decodeList<UserStoreRow>()

                Log.d("StoreActivity", "RPC get_user_stores returned ${rows.size} rows")

                if (rows.isEmpty()) {
                    adapter.updateStores(emptyList(), emptyMap())
                    noStoreLabel.visibility = View.VISIBLE
                    noStoreLabel.text = "You have no stores yet.\nTap the + button to create or join one."
                    return@launch
                }

                val roles = rows.associate { it.store_id to it.role }
                val fetchedStores = rows.map { r ->
                    Store(r.store_id, r.name, r.branch ?: "", r.type ?: "")
                }
                storeRolesMap = roles

                val sortedStores = fetchedStores.sortedWith(compareBy(
                    { val role = roles[it.id]?.lowercase(); when (role) { "owner" -> 0; "manager" -> 1; else -> 2 } },
                    { it.name.lowercase() }
                ))

                adapter.updateStores(sortedStores, roles)
                noStoreLabel.visibility = View.GONE

            } catch (e: Exception) {
                Log.e("StoreActivity", "Error fetching stores via RPC: ${e.message}", e)
                adapter.updateStores(emptyList(), emptyMap())
                noStoreLabel.visibility = View.VISIBLE
                noStoreLabel.text = "Failed to load stores.\nPlease check your connection."
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun showStoreMenu(store: Store, anchor: View) {
        val role = storeRolesMap[store.id]?.lowercase()
        val isOwner = role == "owner"
        if (!isOwner) {
            showStoreMenuEmployee(store)
            return
        }

        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_store_menu, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 1. Layout Fix: Set Dialog Width to 90% of Screen
        val width = (resources.displayMetrics.widthPixels * 0.96).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        // Bind Headers
        view.findViewById<TextView>(R.id.menuStoreName).text = store.name
        view.findViewById<TextView>(R.id.menuBranchName).text = "| ${store.branch}"

        // Bind Grid Buttons
        val btnCopyPrices = view.findViewById<LinearLayout>(R.id.btnCopyPrices)
        val btnInviteStaff = view.findViewById<LinearLayout>(R.id.btnInviteStaff)
        val btnExportPrices = view.findViewById<LinearLayout>(R.id.btnExportPrices)
        val btnImportPrices = view.findViewById<LinearLayout>(R.id.btnImportPrices)

        // Bind Footer Link
        val btnSettings = view.findViewById<TextView>(R.id.btnSettings)

        // Bind Top Right Action Icon (Leave/Delete)
        val btnLeaveDelete = view.findViewById<ImageView>(R.id.btnLeaveDelete)

        // 1. Settings
        btnSettings.setOnClickListener {
            val intent = Intent(this, ManageStoreActivity::class.java)
            intent.putExtra("storeId", store.id)
            intent.putExtra("storeName", store.name)
            startActivity(intent)
            dialog.dismiss()
        }

        // 2. Invite Staff
        btnInviteStaff.setOnClickListener {
            dialog.dismiss()
            showInviteStaffWithCode(store) // Updated to pass 'store' object
        }

        // 3. Copy Prices
        btnCopyPrices.setOnClickListener {
            val intent = Intent(this, CopyPricesActivity::class.java)
            intent.putExtra("storeId", store.id)
            intent.putExtra("storeName", store.name)
            startActivity(intent)
            dialog.dismiss()
        }

        // 4. Import Prices -> Show dialog with options
        btnImportPrices.setOnClickListener {
            dialog.dismiss()
            showImportDialogForStore(store.id, store.name)
        }

        // 5. Export Prices
        btnExportPrices.setOnClickListener {
            dialog.dismiss()
            exportPricelistToExcel(store.id, store.name, store.branch)
        }

        // 6. Leave/Delete Logic
        lifecycleScope.launch {
            try {
                val members = SupabaseProvider.client.postgrest.rpc(
                    "get_store_members",
                    buildJsonObject { put("p_store_id", store.id) }
                ).decodeList<StoreMemberUser>()

                val ownerCount = members.count { it.role.equals("owner", ignoreCase = true) }
                val isSoleOwner = ownerCount <= 1

                if (isSoleOwner) {
                    // Delete Mode
                    btnLeaveDelete.setImageResource(R.drawable.icon_delete)
                    btnLeaveDelete.setColorFilter(android.graphics.Color.parseColor("#D32F2F"))

                    btnLeaveDelete.setOnClickListener {
                        showLeaveDeleteConfirmation(store.id, store.name, isDelete = true, dialog)
                    }
                } else {
                    // Leave Mode
                    btnLeaveDelete.setImageResource(R.drawable.icon_leave_store)
                    btnLeaveDelete.setColorFilter(ContextCompat.getColor(this@StoreActivity, R.color.presyo_teal))

                    btnLeaveDelete.setOnClickListener {
                        showLeaveDeleteConfirmation(store.id, store.name, isDelete = false, dialog)
                    }
                }
            } catch (_: Exception) {
                // Fallback
                btnLeaveDelete.setImageResource(R.drawable.icon_logout)
                btnLeaveDelete.setOnClickListener {
                    showLeaveDeleteConfirmation(store.id, store.name, isDelete = false, dialog)
                }
            }
        }

        dialog.show()
    }

    private fun showLeaveDeleteConfirmation(storeId: String, storeName: String, isDelete: Boolean, menuDialog: Dialog) {
        val confirmDialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
        confirmDialog.setContentView(view)
        confirmDialog.setCancelable(true)
        confirmDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val message = view.findViewById<TextView>(R.id.confirmMessage)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnAction = view.findViewById<Button>(R.id.btnDelete)

        if (isDelete) {
            title.text = "Delete Store"
            message.text = "Are you sure you want to delete this store?.\n\nYour store \"$storeName\" permanently erase all products, members, and data. This cannot be undone."
            btnAction.text = "Delete Store" // Red
        } else {
            title.text = "Leave Store"
            message.text = "Are you sure you want to leave \"$storeName\"?\n\nYou will lose access to the dashboard and products unless invited back."
            btnAction.text = "Leave Store"
        }

        btnCancel.setOnClickListener { confirmDialog.dismiss() }

        btnAction.setOnClickListener {
            lifecycleScope.launch {
                try {
                    if (isDelete) {
                        SupabaseProvider.client.postgrest["stores"].delete { filter { eq("id", storeId) } }
                        Toast.makeText(this@StoreActivity, "Store deleted successfully.", Toast.LENGTH_SHORT).show()
                    } else {
                        SupabaseProvider.client.postgrest.rpc(
                            "leave_store",
                            buildJsonObject { put("p_store_id", storeId) }
                        )
                        Toast.makeText(this@StoreActivity, "You have left the store.", Toast.LENGTH_SHORT).show()
                    }
                    confirmDialog.dismiss()
                    menuDialog.dismiss()
                    fetchStores()
                } catch (e: Exception) {
                    val errorMsg = if (e.message?.contains("sole owner", true) == true)
                        "You are the only owner. Delete the store instead."
                    else "Action failed. Check internet."
                    Toast.makeText(this@StoreActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
        confirmDialog.show()
    }

    private fun showImportDialogForStore(storeId: String, storeName: String) {
        val dlg = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_import_prices, null)
        dlg.setContentView(view)
        dlg.setCancelable(true)
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dlg.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnImportExcel = view.findViewById<LinearLayout>(R.id.btnImportExcel)
        val btnCopyWithCode = view.findViewById<LinearLayout>(R.id.btnCopyWithCode)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)

        btnImportExcel.setOnClickListener {
            Toast.makeText(this, "Excel import is available on the web version.", Toast.LENGTH_LONG).show()
            dlg.dismiss()
        }

        btnCopyWithCode.setOnClickListener {
            val intent = Intent(this, CopyPricesActivity::class.java)
            intent.putExtra("storeId", storeId)
            intent.putExtra("storeName", storeName)
            startActivity(intent)
            dlg.dismiss()
        }

        btnCancel.setOnClickListener { dlg.dismiss() }

        dlg.show()
    }

    private fun showStoreMenuEmployee(store: Store) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_store_menu, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // 1. Layout Fix: Set Dialog Width to 90% of Screen
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        // Headers
        view.findViewById<TextView>(R.id.menuStoreName).text = store.name
        view.findViewById<TextView>(R.id.menuBranchName).text = "| ${store.branch}"

        // Hide buttons restricted for employees
        val btnCopyPrices = view.findViewById<LinearLayout>(R.id.btnCopyPrices)
        val btnInviteStaff = view.findViewById<LinearLayout>(R.id.btnInviteStaff)
        val btnExportPrices = view.findViewById<LinearLayout>(R.id.btnExportPrices)
        val btnImportPrices = view.findViewById<LinearLayout>(R.id.btnImportPrices)
        val btnSettings = view.findViewById<TextView>(R.id.btnSettings)

        // Hide restricted items
        btnInviteStaff.visibility = View.GONE
        btnImportPrices.visibility = View.GONE
        btnSettings.visibility = View.GONE

        // Allow Export/Copy
        btnExportPrices.setOnClickListener {
            dialog.dismiss()
            // Fix: Pass store name and branch to export function
            exportPricelistToExcel(store.id, store.name, store.branch)
        }
        btnCopyPrices.setOnClickListener {
            val intent = Intent(this, CopyPricesActivity::class.java)
            intent.putExtra("storeId", store.id)
            intent.putExtra("storeName", store.name)
            startActivity(intent)
            dialog.dismiss()
        }

        // Leave Logic (Top Right)
        val btnLeaveDelete = view.findViewById<ImageView>(R.id.btnLeaveDelete)
        btnLeaveDelete.setImageResource(R.drawable.icon_logout)
        btnLeaveDelete.setColorFilter(ContextCompat.getColor(this, R.color.presyo_teal))

        btnLeaveDelete.setOnClickListener {
            showLeaveDeleteConfirmation(store.id, store.name, isDelete = false, dialog)
        }

        dialog.show()
    }

    private fun loadNotifBadge() {
        val notifDot = findViewById<View>(R.id.notifDot)
        val userIdNotif = SupabaseProvider.client.auth.currentUserOrNull()?.id
        if (notifDot != null && userIdNotif != null) {
            lifecycleScope.launch {
                try {
                    val rows = SupabaseProvider.client.postgrest["notifications"].select {
                        filter {
                            eq("receiver_user_id", userIdNotif)
                            eq("read", false)
                        }
                        limit(1)
                    }.decodeList<com.presyohan.app.HomeActivity.NotificationRow>()
                    notifDot.visibility = if (rows.isNotEmpty()) View.VISIBLE else View.GONE
                } catch (e: Exception) {
                    notifDot.visibility = View.GONE
                }
            }
        } else if (notifDot != null) {
            notifDot.visibility = View.GONE
        }
    }

    // --- Export Logic ---
    private fun exportPricelistToExcel(storeId: String, storeName: String, branch: String) {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            var overlayVisible = true
            try {
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "get_store_products",
                    buildJsonObject {
                        put("p_store_id", storeId)
                        put("p_category_filter", "PRICELIST")
                        put("p_search_query", null as String?)
                    }
                ).decodeList<StoreProductExportRow>()

                if (rows.isEmpty()) {
                    Toast.makeText(this@StoreActivity, "No products to export.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val sorted = rows.sortedWith(compareBy(
                    { (it.category ?: "").lowercase() },
                    { (it.name ?: "").lowercase() }
                ))

                LoadingOverlayHelper.hide(loadingOverlay)
                overlayVisible = false
                // Fix: Pass storeName and branch to dialog
                showExportConfirmationDialog(sorted, storeName, branch)
            } catch (e: Exception) {
                Toast.makeText(this@StoreActivity, "Failed to export.", Toast.LENGTH_LONG).show()
            } finally {
                if (overlayVisible) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                }
            }
        }
    }

    // Fix: Updated signature to accept storeName and branch
    private fun showExportConfirmationDialog(rows: List<StoreProductExportRow>, storeName: String, branch: String) {
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

        val noteText = buildNoteText(rows, storeName, branch)
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
                        performPricelistExport(rows, storeName, branch)
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

    // Fix: Updated signature to accept storeName and branch
    private fun performPricelistExport(rows: List<StoreProductExportRow>, storeName: String, branch: String) {
        if (rows.isEmpty()) {
            Toast.makeText(this, "No products to export.", Toast.LENGTH_SHORT).show()
            return
        }

        // Fix: Use passed storeName and branch
        val filename = formatExportFilename(storeName, branch)
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

            // Fix: Use passed storeName and branch
            ws.value(1, 0, "Store: $storeName — Branch: $branch")
            val exporter = try { SupabaseAuthService.getDisplayNameImmediate() } catch (_: Exception) { "User" }
            val exportedAt = java.util.Date().toLocaleString()
            ws.value(2, 0, "Exported by: ${exporter}    |    Exported at: ${exportedAt}")

            val headers = listOf("Category", "Name", "Description", "Unit", "Price")
            headers.forEachIndexed { idx, h ->
                ws.value(4, idx, h)
                ws.style(4, idx).bold().set()
            }

            var rowIndex = 5
            rows.forEach { r ->
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

    private fun buildNoteText(rows: List<StoreProductExportRow>, storeName: String?, branchName: String?): String {
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
                .sortedWith(compareBy<StoreProductExportRow> { (it.name ?: "").lowercase(Locale.getDefault()) })
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

    // Fix: Updated signature to accept storeName and branch
    private fun formatExportFilename(storeName: String, branch: String): String {
        val sName = storeName.trim()
        val bName = branch.trim()
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

    // --- Invite Logic & Date Parsing ---

    private fun parseInviteCreatedMillis(createdIso: String?): Long? {
        if (createdIso.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(createdIso).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(createdIso).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    val normalized = if (createdIso.contains("T")) createdIso else createdIso.replace(" ", "T")
                    java.time.LocalDateTime.parse(normalized).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
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

    private fun showInviteStaffWithCode(store: Store) {
        lifecycleScope.launch {
            try {
                val rows = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("id", store.id) }
                    limit(1)
                }.decodeList<StoreInviteFields>()
                val row = rows.firstOrNull()
                val code = row?.invite_code
                val createdIso = row?.invite_code_created_at

                val createdMillis = parseInviteCreatedMillis(createdIso)
                val expiryMillis = createdMillis?.plus(86400000L)

                runOnUiThread {
                    showInviteStaffDialog(store, code, expiryMillis)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@StoreActivity, "Unable to fetch code.", Toast.LENGTH_SHORT).show()
                    showInviteStaffDialog(store, null, null)
                }
            }
        }
    }

    private fun showInviteStaffDialog(store: Store, inviteCode: String?, expiryMillis: Long?) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_invite_staff, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val codeText = view.findViewById<TextView>(R.id.storeCodeText)
        val expiryText = view.findViewById<TextView>(R.id.inviteCodeExpiry)
        val copyBtn = view.findViewById<View>(R.id.btnCopyCode)
        val generateBtn = view.findViewById<TextView>(R.id.btnGenerateCode)

        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val searchLoader = view.findViewById<View>(R.id.searchLoader)
        val searchIcon = view.findViewById<ImageView>(R.id.searchIconStatic)
        val textNotFound = view.findViewById<TextView>(R.id.textNotFound)
        val userResultContainer = view.findViewById<LinearLayout>(R.id.userResultContainer)
        val foundAvatar = view.findViewById<ImageView>(R.id.foundUserAvatar)
        val foundName = view.findViewById<TextView>(R.id.foundUserName)
        val foundDetails = view.findViewById<TextView>(R.id.foundUserDetails)
        val btnInvite = view.findViewById<Button>(R.id.btnInvite)
        val inviteErrorText = view.findViewById<TextView>(R.id.inviteErrorText)
        val roleSpinner = view.findViewById<Spinner>(R.id.roleSpinner)

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
        roleSpinner.adapter = adp

        var selectedUser: SearchedUser? = null
        val searchHandler = Handler(Looper.getMainLooper())
        var searchRunnable: Runnable? = null

        fun updateCodeUI(code: String?, expiresAt: Long?) {
            codeText.text = code ?: "No Code"
            inviteCodeCountdownJob?.cancel()
            val now = System.currentTimeMillis()
            if (code != null && expiresAt != null) {
                if (expiresAt > now) {
                    startInviteCountdown(expiryText, codeText, copyBtn, expiresAt)
                } else {
                    expiryText.text = "Code expired"
                }
            } else {
                expiryText.text = if (code == null) "" else "Code expired"
            }
        }
        updateCodeUI(inviteCode, expiryMillis)

        copyBtn.setOnClickListener {
            val code = codeText.text.toString()
            if (code.isNotEmpty() && code != "No Code") {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Store Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Code copied.", Toast.LENGTH_SHORT).show()
            }
        }

        generateBtn.setOnClickListener {
            val role = storeRolesMap[store.id]?.lowercase()
            val isOwner = role == "owner"
            if (!isOwner) {
                Toast.makeText(this, "Only owner can generate codes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            generateBtn.isEnabled = false
            generateBtn.text = "Regenerating..."
            lifecycleScope.launch {
                try {
                    val params = buildJsonObject { put("p_store_id", store.id) }
                    val rows = SupabaseProvider.client.postgrest
                        .rpc("regenerate_invite_code", params)
                        .decodeList<InviteCodeReturn>()
                    val row = rows.firstOrNull()
                    val newCode = row?.invite_code
                    val newCreated = row?.invite_code_created_at
                    val newCreatedMillis = parseInviteCreatedMillis(newCreated)
                    val newExpiry = newCreatedMillis?.plus(86400000L)
                    runOnUiThread {
                        updateCodeUI(newCode, newExpiry)
                        Toast.makeText(applicationContext, "Code updated.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Failed to generate code.", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    generateBtn.text = "Generate"
                    generateBtn.isEnabled = true
                }
            }
        }

        fun toggleInviteButton() {
            btnInvite.isEnabled = selectedUser != null
            btnInvite.alpha = if (selectedUser != null) 1.0f else 0.5f
        }

        val performSearch = { query: String ->
            searchLoader.visibility = View.VISIBLE
            searchIcon.visibility = View.GONE
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
                    searchIcon.visibility = View.VISIBLE
                    if (results.isNotEmpty()) {
                        val user = results[0]
                        selectedUser = user
                        userResultContainer.visibility = View.VISIBLE
                        foundName.text = user.name ?: "Unnamed User"
                        val code = user.user_code ?: "NO-ID"
                        val email = user.email ?: ""
                        foundDetails.text = "$code • $email"
                        if (!user.avatar_url.isNullOrBlank()) {
                            foundAvatar.load(user.avatar_url) {
                                crossfade(true)
                                transformations(CircleCropTransformation())
                            }
                        } else {
                            foundAvatar.setImageResource(R.drawable.icon_profile)
                        }
                    } else {
                        textNotFound.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    searchLoader.visibility = View.GONE
                    searchIcon.visibility = View.VISIBLE
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
                    searchIcon.visibility = View.VISIBLE
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
            val sId = store.id
            val roleIdx = roleSpinner.selectedItemPosition
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
                    Toast.makeText(this@StoreActivity, "Invitation sent to ${user.name}", Toast.LENGTH_SHORT).show()
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

        dialog.setOnDismissListener { inviteCodeCountdownJob?.cancel() }
        dialog.show()
    }
}
