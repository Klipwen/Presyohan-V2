package com.presyohan.app

import android.app.Dialog
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
// Removed Apache POI imports; using FastExcel for writing XLSX
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.auth.auth

class ManageStoreActivity : AppCompatActivity() {
    private lateinit var inputStoreName: EditText
    private lateinit var inputBranchName: EditText
    private lateinit var spinnerStoreType: Spinner
    private lateinit var btnDone: MaterialButton
    private lateinit var btnBack: ImageView
    private lateinit var headerLabel: TextView
    private lateinit var inputCustomType: EditText
    private lateinit var storeCodeTextView: TextView
    private lateinit var storeCodeExpiryView: TextView
    private lateinit var btnGenerateCode: Button
    private lateinit var btnRevokeCode: Button

    private val storeTypes = arrayOf("Laundry Shop", "Car Wash", "Water Refilling Station", "Other")
    private var storeId: String? = null
    private var storeName: String? = null
    private var branchName: String? = null
    private var storeType: String? = null

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
        val paste_code: String? = null,
        val paste_code_expires_at: String? = null
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_store)

        storeId = intent.getStringExtra("storeId")
        if (storeId.isNullOrBlank()) {
            Toast.makeText(this, "No store ID provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        inputStoreName = findViewById(R.id.inputStoreName)
        inputBranchName = findViewById(R.id.inputBranchName)
        spinnerStoreType = findViewById(R.id.spinnerStoreType)
        btnDone = findViewById(R.id.btnDone)
        btnBack = findViewById(R.id.btnBack)
        headerLabel = findViewById(R.id.headerLabel)
        storeCodeTextView = findViewById(R.id.storeCodeText)
        storeCodeExpiryView = findViewById(R.id.storeCodeExpiry)
        btnGenerateCode = findViewById(R.id.btnGenerateCode)
        btnRevokeCode = findViewById(R.id.btnRevokeCode)

        // Set up spinner before fetching store data
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, storeTypes)
        spinnerStoreType.adapter = adapter

        inputCustomType = findViewById(R.id.inputCustomType)
        if (inputCustomType == null) {
            Toast.makeText(this, "Layout error: inputCustomType not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Fetch current store info from Supabase
        lifecycleScope.launch {
            try {
                val rows = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("id", storeId!!) }
                    limit(1)
                }.decodeList<StoreRow>()
                val store = rows.firstOrNull()
                storeName = store?.name ?: ""
                branchName = store?.branch ?: ""
                storeType = store?.type ?: storeTypes[0]

                inputStoreName.setText(storeName)
                inputBranchName.setText(branchName)

                val typeIndex = storeTypes.indexOfFirst { it.equals(storeType, ignoreCase = true) }
                if (typeIndex >= 0) {
                    spinnerStoreType.setSelection(typeIndex)
                    inputCustomType.visibility = View.GONE
                } else {
                    spinnerStoreType.setSelection(storeTypes.size - 1) // 'Other'
                    inputCustomType.visibility = View.VISIBLE
                    inputCustomType.setText(storeType)
                }
                // Store name and branch are shown in input fields within this screen
                // No header storeText/storeBranchText views exist in this layout

                // Initialize paste-code UI
                if (!store?.paste_code.isNullOrBlank() && !store?.paste_code_expires_at.isNullOrBlank()) {
                    applyPasteCode(store!!.paste_code!!, store.paste_code_expires_at!!)
                } else {
                    clearPasteCodeUi()
                }
            } catch (e: Exception) {
                Toast.makeText(this@ManageStoreActivity, "Unable to load store.", Toast.LENGTH_LONG).show()
                finish()
            }
        }

        spinnerStoreType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                if (storeTypes[position] == "Other") {
                    inputCustomType.visibility = View.VISIBLE
                } else {
                    inputCustomType.visibility = View.GONE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        btnBack.setOnClickListener { finish() }

        btnDone.setOnClickListener {
            val newName = inputStoreName.text.toString().trim()
            val newBranch = inputBranchName.text.toString().trim()
            val selectedType = spinnerStoreType.selectedItem?.toString() ?: storeTypes[0]
            val newType = if (selectedType == "Other") inputCustomType.text.toString().trim() else selectedType
            if (newName.isEmpty() || newBranch.isEmpty() || (selectedType == "Other" && newType.isEmpty())) {
                Toast.makeText(this, "Complete all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Show confirmation dialog
            val dialog = Dialog(this)
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_delete, null)
            dialog.setContentView(view)
            dialog.setCancelable(true)
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            view.findViewById<TextView>(R.id.dialogTitle).text = "Confirm Changes?"
            view.findViewById<TextView>(R.id.confirmMessage).text = "Are you sure you want to update the store details?"
            view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
            view.findViewById<Button>(R.id.btnDelete).apply {
                text = "Update"
                setOnClickListener {
                    // Update Supabase
                    lifecycleScope.launch {
                        try {
                            SupabaseProvider.client.postgrest["stores"].update(
                                mapOf(
                                    "name" to newName,
                                    "branch" to newBranch,
                                    "type" to newType
                                )
                            ) {
                                filter { eq("id", storeId!!) }
                            }
                            Toast.makeText(this@ManageStoreActivity, "Store updated.", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            finish()
                        } catch (e: Exception) {
                            Toast.makeText(this@ManageStoreActivity, "Unable to update store.", Toast.LENGTH_LONG).show()
                            dialog.dismiss()
                        }
                    }
                }
            }
            dialog.show()
        }

        val btnAddItem = findViewById<Button>(R.id.btnAddItem)
        btnAddItem.setOnClickListener {
            val intent = android.content.Intent(this, AddItemActivity::class.java)
            intent.putExtra("storeId", storeId)
            intent.putExtra("storeName", storeName)
            startActivity(intent)
        }

        val btnManageMembers = findViewById<View>(R.id.btnManageMembers)
        btnManageMembers.setOnClickListener {
            val intent = android.content.Intent(this, ManageMembersActivity::class.java)
            intent.putExtra("storeId", storeId)
            startActivity(intent)
        }

        val btnManageCategory = findViewById<View>(R.id.btnManageCategory)
        btnManageCategory.setOnClickListener {
            val intent = android.content.Intent(this, ManageCategoryActivity::class.java)
            intent.putExtra("storeId", storeId)
            startActivity(intent)
        }

        // Invite Staff action
        findViewById<View>(R.id.btnInviteStaff).setOnClickListener {
            showInviteStaffWithCode()
        }

        // Convert (Export Excel) action
        findViewById<View>(R.id.btnConvert).setOnClickListener {
            exportPricelistToExcel()
        }

        // Paste-code actions
        btnGenerateCode.setOnClickListener {
            if (storeId.isNullOrBlank()) return@setOnClickListener
            lifecycleScope.launch {
                try {
                    val result = SupabaseProvider.client.postgrest.rpc(
                        "generate_paste_code",
                        buildJsonObject { put("p_store_id", storeId!!) }
                    ).decodeList<PasteCodeResult>().firstOrNull()
                    if (result != null) {
                        applyPasteCode(result.code, result.expires_at)
                        Toast.makeText(this@ManageStoreActivity, "Paste-Code generated.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ManageStoreActivity, "Failed to generate code.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ManageStoreActivity, "You must be the owner to generate.", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnRevokeCode.setOnClickListener {
            if (storeId.isNullOrBlank()) return@setOnClickListener
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest.rpc(
                        "revoke_paste_code",
                        buildJsonObject { put("p_store_id", storeId!!) }
                    )
                    clearPasteCodeUi()
                    Toast.makeText(this@ManageStoreActivity, "Paste-Code revoked.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@ManageStoreActivity, "You must be the owner to revoke.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Extension function for dp to px
    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

    private var countdownJob: kotlinx.coroutines.Job? = null

    private fun clearPasteCodeUi() {
        storeCodeTextView.text = "------"
        storeCodeExpiryView.text = "No active code"
        countdownJob?.cancel()
    }

    private fun applyPasteCode(code: String, expiresAtIso: String) {
        storeCodeTextView.text = code
        countdownJob?.cancel()
        countdownJob = lifecycleScope.launch {
            try {
                val expiry = java.time.OffsetDateTime.parse(expiresAtIso).toInstant()
                while (true) {
                    val now = java.time.Instant.now()
                    val remaining = java.time.Duration.between(now, expiry).seconds
                    if (remaining <= 0) {
                        storeCodeExpiryView.text = "Expired"
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
        lifecycleScope.launch {
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
 
                val filename = formatExportFilename()
                val mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

                // Prepare output stream for the workbook
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
                    Toast.makeText(this@ManageStoreActivity, "Failed to create file.", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Write Excel using FastExcel (no XMLBeans/POI)
                try {
                    val wb = org.dhatim.fastexcel.Workbook(output, "Presyohan", "1.0")
                    val ws = wb.newWorksheet("Pricelist")

                    // Title
                    ws.value(0, 0, "Presyohan")
                    ws.style(0, 0).bold().set()

                    // Store info and export meta
                    ws.value(1, 0, "Store: ${storeName ?: ""} — Branch: ${branchName ?: ""}")
                    val exporter = try { SupabaseAuthService.getDisplayNameImmediate() } catch (_: Exception) { "User" }
                    val exportedAt = java.util.Date().toLocaleString()
                    ws.value(2, 0, "Exported by: ${exporter}    |    Exported at: ${exportedAt}")

                    // Blank line
                    // Headers
                    val headers = listOf("Category", "Name", "Description", "Unit", "Price")
                    headers.forEachIndexed { idx, h ->
                        ws.value(4, idx, h)
                        ws.style(4, idx).bold().set()
                    }

                    // Data rows
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

                    // Column widths (best-effort)
                    try {
                        ws.width(0, 20.0)
                        ws.width(1, 28.0)
                        ws.width(2, 40.0)
                        ws.width(3, 12.0)
                        ws.width(4, 14.0)
                    } catch (_: Throwable) { /* optional */ }

                    wb.finish()
                    output.flush()
                    Toast.makeText(this@ManageStoreActivity, "Excel exported to Downloads.", Toast.LENGTH_SHORT).show()
                    notifyExportSuccessOrRequest(filename)
                } catch (e: Exception) {
                    Toast.makeText(this@ManageStoreActivity, "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    try { output.close() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Toast.makeText(this@ManageStoreActivity, "Failed to export.", Toast.LENGTH_LONG).show()
            }
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

    private fun showInviteStaffDialog(inviteCode: String?) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_invite_staff, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val codeText = view.findViewById<TextView>(R.id.storeCodeText)
        val copyBtn = view.findViewById<ImageView>(R.id.btnCopyCode)
        val generateBtn = view.findViewById<Button>(R.id.btnGenerateCode)

        fun updateCodeUI(code: String?) {
            codeText.visibility = View.VISIBLE
            copyBtn.visibility = View.VISIBLE
            codeText.text = code ?: ""
        }
        updateCodeUI(inviteCode)

        copyBtn.setOnClickListener {
            val code = codeText.text.toString()
            if (code.isNotEmpty()) {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Store Code", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Invite code copied.", Toast.LENGTH_SHORT).show()
            }
        }

        generateBtn.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val uid = SupabaseProvider.client.auth.currentUserOrNull()?.id.orEmpty()
                    val roleRows = SupabaseProvider.client.postgrest["store_members"].select {
                        filter { eq("store_id", storeId!!); eq("user_id", uid) }
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
                        buildJsonObject { put("p_store_id", storeId!!) }
                    ).decodeList<com.presyohan.app.StoreActivity.InviteCodeReturn>()
                    val newCode = rows.firstOrNull()?.invite_code
                    runOnUiThread { updateCodeUI(newCode) }
                    Toast.makeText(this@ManageStoreActivity, "Invite code updated.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@ManageStoreActivity, "Unable to update invite code.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        view.findViewById<Button>(R.id.btnBack).setOnClickListener { dialog.dismiss() }

        view.findViewById<Button>(R.id.btnInvite).setOnClickListener {
            val email = view.findViewById<EditText>(R.id.usernameInput).text.toString().trim()
            val roleSpinner = view.findViewById<android.widget.Spinner>(R.id.roleSpinner)
            val selectedPermission = roleSpinner.selectedItem.toString().trim().lowercase()
            val role = when (selectedPermission) {
                "manage prices" -> "manager"
                "view only prices" -> "sales staff"
                else -> "sales staff"
            }
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter an email address.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val db = FirebaseFirestore.getInstance()
            db.collection("users").whereEqualTo("email", email).get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        val userDoc = querySnapshot.documents[0]
                        val staffUserId = userDoc.id
                        db.collection("stores").document(storeId!!)
                            .collection("members")
                            .document(staffUserId)
                            .get()
                            .addOnSuccessListener { memberDoc ->
                                if (memberDoc.exists()) {
                                    Toast.makeText(this, "${userDoc.getString("name") ?: email} is already your store staff", Toast.LENGTH_SHORT).show()
                                } else {
                                    db.collection("users").document(staffUserId)
                                        .collection("notifications")
                                        .whereEqualTo("type", "Store Invitation")
                                        .whereEqualTo("storeName", storeName ?: "")
                                        .get()
                                        .addOnSuccessListener { notifSnapshot ->
                                            val hasPending = notifSnapshot.any { it.getString("status") == "Pending" }
                                            val hasAccepted = notifSnapshot.any { it.getString("status") == "Accepted" }
                                            if (hasAccepted) {
                                                Toast.makeText(this, "Already a staff member.", Toast.LENGTH_SHORT).show()
                                            } else if (hasPending) {
                                                Toast.makeText(this, "Invitation already sent.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val senderName = SupabaseAuthService.getDisplayNameImmediate()
                                                val senderId = SupabaseProvider.client.auth.currentUserOrNull()?.id.orEmpty()
                                                val notification = hashMapOf(
                                                    "type" to "Store Invitation",
                                                    "status" to "Pending",
                                                    "sender" to senderName,
                                                    "senderId" to senderId,
                                                    "storeName" to (storeName ?: ""),
                                                    "role" to role,
                                                    "timestamp" to System.currentTimeMillis(),
                                                    "message" to "$senderName invited you to join their store, ${storeName ?: ""} as $role."
                                                )
                                                db.collection("users").document(staffUserId)
                                                    .collection("notifications")
                                                    .add(notification)
                                                Toast.makeText(this, "Invitation sent.", Toast.LENGTH_SHORT).show()
                                                dialog.dismiss()
                                            }
                                        }
                                }
                            }
                    } else {
                        Toast.makeText(this, "User not found.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Unable to send invitation.", Toast.LENGTH_SHORT).show()
                }
        }

        // Permissions list in dialog (View only prices / Manage prices)
        val roleSpinner = view.findViewById<android.widget.Spinner>(R.id.roleSpinner)
        val permissions = listOf("View only prices", "Manage prices")
        val permissionAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, permissions)
        roleSpinner.adapter = permissionAdapter

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
                showInviteStaffDialog(code)
            } catch (_: Exception) {
                Toast.makeText(this@ManageStoreActivity, "Unable to fetch invite code. Generate a new one.", Toast.LENGTH_SHORT).show()
                showInviteStaffDialog(null)
            }
        }
    }
}