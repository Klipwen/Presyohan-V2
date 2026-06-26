package com.presyohan.app

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import coil.load
import coil.transform.CircleCropTransformation
import com.google.android.material.bottomsheet.BottomSheetBehavior
import java.util.Locale

class ManageMembersActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MembersAdapter
    private var storeId: String? = null
    private var storeName: String? = null
    private var inviteCode: String? = null
    private var inviteCodeCreatedAt: String? = null
    private lateinit var loadingOverlay: android.view.View
    private var inviteCodeCountdownJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_members)
        loadingOverlay = LoadingOverlayHelper.attach(this)

        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName")
        if (storeId.isNullOrBlank()) {
            Toast.makeText(this, "No store ID provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val currentUserId = SupabaseProvider.client.auth.currentUserOrNull()?.id
        recyclerView = findViewById(R.id.recyclerViewMembers)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MembersAdapter(
            currentUserId = currentUserId,
            onEditClick = { member ->
                if (member.role == "manager" || member.role == "sales staff") {
                    showRoleChangeDialog(member)
                }
            },
            onRemoveClick = { member ->
                if (member.role == "manager" || member.role == "sales staff") {
                    showRemoveStaffDialog(member)
                }
            }
        )
        recyclerView.adapter = adapter

        fetchMembers()

        // Fetch store details to get name, branch and invite code info
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                @Serializable
                data class StoreRow(
                    val id: String,
                    val name: String,
                    val branch: String? = null,
                    val invite_code: String? = null,
                    val invite_code_created_at: String? = null
                )
                val rows = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("id", storeId!!) }
                    limit(1)
                }.decodeList<StoreRow>()
                val s = rows.firstOrNull()
                if (s != null) {
                    storeName = s.name
                    inviteCode = s.invite_code
                    inviteCodeCreatedAt = s.invite_code_created_at
                    SessionManager.markStoreHome(this@ManageMembersActivity, storeId, storeName)
                }
            } catch (_: Exception) {
                // fallback labels
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // Setup Invite Staff Button
        findViewById<View>(R.id.btnInviteStaff).setOnClickListener {
            val expiryMillis = parseInviteCreatedMillis(inviteCodeCreatedAt)
            val expiry = expiryMillis?.plus(86400000L) // Invite codes expire in 24 hours
            showInviteStaffDialog(inviteCode, expiry)
        }

        // Setup Search Bottom Sheet
        val bottomSheet = findViewById<View>(R.id.bottomSheet)
        val behavior = BottomSheetBehavior.from(bottomSheet)

        fun updateRecyclerPadding(bottomHeight: Int) {
            val safetyPadding = (16 * resources.displayMetrics.density).toInt()
            val targetPadding = bottomHeight + safetyPadding
            if (recyclerView.paddingBottom != targetPadding) {
                recyclerView.setPadding(
                    recyclerView.paddingLeft,
                    recyclerView.paddingTop,
                    recyclerView.paddingRight,
                    targetPadding
                )
            }
        }
        updateRecyclerPadding(0)
        recyclerView.isNestedScrollingEnabled = false

        behavior.isHideable = true
        behavior.state = BottomSheetBehavior.STATE_HIDDEN
        val fabSearch = findViewById<View>(R.id.fabSearch)
        val bottomSearchEditText = findViewById<EditText>(R.id.bottomSearchEditText)
        val btnSearchClear = findViewById<ImageView>(R.id.btnSearchClear)

        fabSearch.setOnClickListener {
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        fabSearch?.visibility = View.GONE
                        updateRecyclerPadding(bottomSheet.height)
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        fabSearch?.visibility = View.GONE
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.hideSoftInputFromWindow(bottomSearchEditText.windowToken, 0)
                        updateRecyclerPadding(behavior.peekHeight)
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        fabSearch?.visibility = View.VISIBLE
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.hideSoftInputFromWindow(bottomSearchEditText.windowToken, 0)
                        updateRecyclerPadding(0)
                    }
                    else -> {}
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Empty to prevent recursive layout requests during drag/slide gestures
            }
        })

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 10 && recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    if (behavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                        behavior.state = BottomSheetBehavior.STATE_HIDDEN
                    }
                }
            }
        })

        bottomSearchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                btnSearchClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(180)
                    adapter.filter(query, this)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnSearchClear.setOnClickListener {
            bottomSearchEditText.setText("")
            searchJob?.cancel()
            adapter.filter("", lifecycleScope)
        }
    }

    private fun fetchMembers() {
        val sId = storeId ?: return
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                @Serializable
                data class StoreMemberUser(val user_id: String, val name: String, val role: String)
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "get_store_members",
                    buildJsonObject { put("p_store_id", sId) }
                ).decodeList<StoreMemberUser>()

                // Query app_users to get the user_code (ID) and avatar_url
                val userIds = rows.map { it.user_id }
                @Serializable
                data class UserProfile(val id: String, val user_code: String? = null, val avatar_url: String? = null)
                val profiles = try {
                    SupabaseProvider.client.postgrest["app_users"].select {
                        filter { isIn("id", userIds) }
                    }.decodeList<UserProfile>().associateBy { it.id }
                } catch (e: Exception) {
                    emptyMap()
                }

                val members = rows.map { r ->
                    val prof = profiles[r.user_id]
                    Member(
                        id = r.user_id,
                        name = r.name,
                        role = mapRoleToUi(r.role),
                        userCode = prof?.user_code,
                        avatarUrl = prof?.avatar_url
                    )
                }

                adapter.setMembers(members)

                // Update UI Summary Card Counts
                val ownersCount = members.count { it.role == "owner" }
                val managersCount = members.count { it.role == "manager" }
                val salesStaffCount = members.count { it.role == "sales staff" }
                val totalMembers = members.size

                findViewById<TextView>(R.id.textTotalMembers).text = "$totalMembers ${if (totalMembers == 1) "member" else "members"}"
                findViewById<TextView>(R.id.textOwnersCount).text = ownersCount.toString()
                findViewById<TextView>(R.id.textManagersCount).text = managersCount.toString()
                findViewById<TextView>(R.id.textSalesStaffCount).text = salesStaffCount.toString()

            } catch (e: Exception) {
                Toast.makeText(this@ManageMembersActivity, "Unable to load members.", Toast.LENGTH_LONG).show()
                adapter.setMembers(emptyList())
            }
            LoadingOverlayHelper.hide(loadingOverlay)
        }
    }

    private fun showRoleChangeDialog(member: Member) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_role_change, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Bind Staff Details
        view.findViewById<TextView>(R.id.staffName).text = member.name
        val formattedRole = member.role.replaceFirstChar { it.uppercase() }
        view.findViewById<TextView>(R.id.staffDetails).text = "${member.userCode ?: "NO-ID"} • $formattedRole"

        val avatarImage = view.findViewById<ImageView>(R.id.staffAvatar)
        if (!member.avatarUrl.isNullOrBlank()) {
            avatarImage.load(member.avatarUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                error(R.drawable.avatar_default)
                fallback(R.drawable.avatar_default)
            }
        } else {
            avatarImage.setImageResource(R.drawable.avatar_default)
        }

        // Selection variables
        var selectedRole = member.role

        val layoutOwner = view.findViewById<View>(R.id.layoutOptionOwner)
        val layoutManager = view.findViewById<View>(R.id.layoutOptionManager)
        val layoutSalesStaff = view.findViewById<View>(R.id.layoutOptionSalesStaff)

        val imgOwner = view.findViewById<ImageView>(R.id.imgOptionOwner)
        val imgManager = view.findViewById<ImageView>(R.id.imgOptionManager)
        val imgSalesStaff = view.findViewById<ImageView>(R.id.imgOptionSalesStaff)

        fun updateSelectionUi() {
            // Owner
            if (selectedRole == "owner") {
                layoutOwner.setBackgroundResource(R.drawable.bg_card_selected_orange)
                imgOwner.setImageResource(R.drawable.ic_radio_checked_orange)
            } else {
                layoutOwner.setBackgroundResource(R.drawable.bg_card_unselected)
                imgOwner.setImageResource(R.drawable.ic_radio_unchecked)
            }

            // Manager
            if (selectedRole == "manager") {
                layoutManager.setBackgroundResource(R.drawable.bg_card_selected_orange)
                imgManager.setImageResource(R.drawable.ic_radio_checked_orange)
            } else {
                layoutManager.setBackgroundResource(R.drawable.bg_card_unselected)
                imgManager.setImageResource(R.drawable.ic_radio_unchecked)
            }

            // Sales Staff
            if (selectedRole == "sales staff") {
                layoutSalesStaff.setBackgroundResource(R.drawable.bg_card_selected_orange)
                imgSalesStaff.setImageResource(R.drawable.ic_radio_checked_orange)
            } else {
                layoutSalesStaff.setBackgroundResource(R.drawable.bg_card_unselected)
                imgSalesStaff.setImageResource(R.drawable.ic_radio_unchecked)
            }
        }

        updateSelectionUi()

        layoutOwner.setOnClickListener {
            selectedRole = "owner"
            updateSelectionUi()
        }
        layoutManager.setOnClickListener {
            selectedRole = "manager"
            updateSelectionUi()
        }
        layoutSalesStaff.setOnClickListener {
            selectedRole = "sales staff"
            updateSelectionUi()
        }

        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnChange).setOnClickListener {
            val sId = storeId ?: return@setOnClickListener
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    val newRole = mapRoleToSupabase(selectedRole)
                    SupabaseProvider.client.postgrest.rpc(
                        "update_store_member_role",
                        buildJsonObject {
                            put("p_store_id", sId)
                            put("p_member_id", member.id)
                            put("p_new_role", newRole)
                        }
                    )
                    Toast.makeText(this@ManageMembersActivity, "Role updated.", Toast.LENGTH_SHORT).show()
                    fetchMembers()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(this@ManageMembersActivity, "Unable to update role.", Toast.LENGTH_LONG).show()
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
        dialog.show()
    }

    private fun showRemoveStaffDialog(member: Member) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_remove_staff, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Bind Staff Details
        view.findViewById<TextView>(R.id.staffName).text = member.name
        val formattedRole = member.role.replaceFirstChar { it.uppercase() }
        view.findViewById<TextView>(R.id.staffDetails).text = "${member.userCode ?: "NO-ID"} • $formattedRole"

        val avatarImage = view.findViewById<ImageView>(R.id.staffAvatar)
        if (!member.avatarUrl.isNullOrBlank()) {
            avatarImage.load(member.avatarUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                error(R.drawable.avatar_default)
                fallback(R.drawable.avatar_default)
            }
        } else {
            avatarImage.setImageResource(R.drawable.avatar_default)
        }

        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnRemove).setOnClickListener {
            val sId = storeId ?: return@setOnClickListener
            LoadingOverlayHelper.show(loadingOverlay)
            lifecycleScope.launch {
                try {
                    SupabaseProvider.client.postgrest.rpc(
                        "remove_store_member",
                        buildJsonObject {
                            put("p_store_id", sId)
                            put("p_member_id", member.id)
                        }
                    )
                    Toast.makeText(this@ManageMembersActivity, "Staff removed.", Toast.LENGTH_SHORT).show()
                    fetchMembers()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(this@ManageMembersActivity, "Unable to remove staff.", Toast.LENGTH_LONG).show()
                }
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
        dialog.show()
    }

    private fun showInviteStaffDialog(initialInviteCode: String?, expiryMillis: Long?) {
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
                startInviteCountdown(expiryText, expiresAt)
            } else {
                layoutCodeInactive.visibility = View.VISIBLE
                layoutCodeActive.visibility = View.GONE
                expiryText.text = ""
            }
        }
        updateCodeUI(initialInviteCode, expiryMillis)

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
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
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
                    @Serializable
                    data class InviteCodeReturn(
                        val invite_code: String? = null,
                        val invite_code_created_at: String? = null
                    )
                    val rows = SupabaseProvider.client.postgrest.rpc(
                        "regenerate_invite_code",
                        buildJsonObject { put("p_store_id", sId) }
                    ).decodeList<InviteCodeReturn>()
                    val newCode = rows.firstOrNull()?.invite_code
                    val created = rows.firstOrNull()?.invite_code_created_at

                    val newCreatedMillis = parseInviteCreatedMillis(created)
                    val newExpiry = newCreatedMillis?.plus(86400000L)

                    inviteCode = newCode
                    inviteCodeCreatedAt = created

                    runOnUiThread {
                        updateCodeUI(newCode, newExpiry)
                        Toast.makeText(this@ManageMembersActivity, "Invite code updated.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ManageMembersActivity, "Unable to update invite code.", Toast.LENGTH_SHORT).show()
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
                    SupabaseProvider.client.postgrest["stores"].update(
                        mapOf(
                            "invite_code" to null,
                            "invite_code_created_at" to null
                        )
                    ) {
                        filter { eq("id", sId) }
                    }

                    inviteCode = null
                    inviteCodeCreatedAt = null

                    runOnUiThread {
                        updateCodeUI(null, null)
                        Toast.makeText(this@ManageMembersActivity, "Invite code revoked.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ManageMembersActivity, "Failed to revoke code.", Toast.LENGTH_SHORT).show()
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
                                error(R.drawable.avatar_default)
                                fallback(R.drawable.avatar_default)
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
            val selectedRoleValue = rolesValue.getOrElse(roleIdx) { "employee" }

            btnInvite.text = "Inviting..."
            btnInvite.isEnabled = false

            lifecycleScope.launch {
                try {
                    val params = buildJsonObject {
                        put("p_store_id", sId)
                        put("p_email", user.email)
                        put("p_role", selectedRoleValue)
                    }
                    SupabaseProvider.client.postgrest.rpc("send_store_invitation", params)
                    Toast.makeText(this@ManageMembersActivity, "Invitation sent to ${user.name}", Toast.LENGTH_SHORT).show()
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

        dialog.show()
    }

    private fun startInviteCountdown(expiryText: TextView, expiryMillis: Long) {
        inviteCodeCountdownJob = lifecycleScope.launch {
            while (true) {
                val rem = expiryMillis - System.currentTimeMillis()
                if (rem <= 0) {
                    expiryText.text = "Expired"
                    break
                }
                val hrs = rem / 3600000
                val mins = (rem % 3600000) / 60000
                val secs = (rem % 60000) / 1000
                expiryText.text = String.format("Expires in %02d:%02d:%02d", hrs, mins, secs)
                kotlinx.coroutines.delay(1000)
            }
        }
    }

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

    private fun mapRoleToUi(role: String): String {
        return when (role.lowercase()) {
            "employee" -> "sales staff"
            else -> role.lowercase()
        }
    }

    private fun mapRoleToSupabase(roleUi: String): String {
        return when (roleUi.lowercase()) {
            "sales staff" -> "employee"
            else -> roleUi.lowercase()
        }
    }

    override fun onDestroy() {
        inviteCodeCountdownJob?.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreHome(this, storeId, storeName)
    }
}

data class Member(
    val id: String,
    val name: String,
    val role: String,
    val userCode: String? = null,
    val avatarUrl: String? = null
)

class MembersAdapter(
    private val currentUserId: String?,
    private val onEditClick: (Member) -> Unit,
    private val onRemoveClick: (Member) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var allMembers: List<Member> = emptyList()
    private var groupedMembers: Map<String, List<Member>> = emptyMap()
    private var sectionOrder: List<String> = listOf("owner", "manager", "sales staff")

    fun setMembers(members: List<Member>) {
        this.allMembers = members
        this.groupedMembers = members.groupBy { it.role }
        notifyDataSetChanged()
    }

    fun filter(query: String, scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            val filtered = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                if (query.isBlank()) {
                    allMembers
                } else {
                    val tokens = query.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    
                    val matchedMembers = allMembers.filter { member ->
                        var isMatch = true
                        for (token in tokens) {
                            val matchesName = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, member.name)
                            val matchesCode = member.userCode?.let { com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, it) } ?: false
                            val matchesRole = com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, member.role)
                            
                            if (!matchesName && !matchesCode && !matchesRole) {
                                isMatch = false
                                break
                            }
                        }
                        isMatch
                    }

                    matchedMembers.map { member ->
                        var score = 0.0
                        val cleanName = member.name.lowercase(Locale.getDefault())
                        val cleanQuery = query.lowercase(Locale.getDefault())
                        
                        if (cleanName == cleanQuery) score += 1000.0
                        if (cleanName.startsWith(cleanQuery)) score += 500.0
                        if (cleanName.contains(cleanQuery)) score += 200.0
                        
                        for (token in tokens) {
                            if (com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, member.name)) score += 100.0
                            if (member.userCode != null && com.presyohan.app.helper.SearchHelper.isFuzzyMatch(token, member.userCode)) score += 50.0
                        }
                        Pair(member, score)
                    }
                    .sortedByDescending { it.second }
                    .map { it.first }
                }
            }
            groupedMembers = filtered.groupBy { it.role }
            notifyDataSetChanged()
        }
    }

    override fun getItemCount(): Int {
        return sectionOrder.sumOf { groupedMembers[it]?.size ?: 0 } + sectionOrder.count { (groupedMembers[it]?.isNotEmpty() == true) }
    }

    override fun getItemViewType(position: Int): Int {
        var count = 0
        for (role in sectionOrder) {
            val members = groupedMembers[role] ?: emptyList()
            if (members.isNotEmpty()) {
                if (position == count) return 0 // header
                count++
                if (position < count + members.size) return 1 // member
                count += members.size
            }
        }
        return 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member_section_header, parent, false)
            SectionHeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member, parent, false)
            MemberViewHolder(view, currentUserId, onEditClick, onRemoveClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        var count = 0
        for (role in sectionOrder) {
            val members = groupedMembers[role] ?: emptyList()
            if (members.isNotEmpty()) {
                if (position == count) {
                    (holder as SectionHeaderViewHolder).bind(role)
                    return
                }
                count++
                if (position < count + members.size) {
                    val indexInGroup = position - count
                    (holder as MemberViewHolder).bind(members[indexInGroup], indexInGroup + 1)
                    return
                }
                count += members.size
            }
        }
    }

    class SectionHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(role: String) {
            val displayRole = when (role.lowercase()) {
                "owner" -> "Owners"
                "manager" -> "Managers"
                "sales staff" -> "Sales-staff"
                else -> role
            }
            itemView.findViewById<TextView>(R.id.sectionHeader).text = displayRole
        }
    }

    class MemberViewHolder(
        itemView: View,
        private val currentUserId: String?,
        private val onEditClick: (Member) -> Unit,
        private val onRemoveClick: (Member) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        fun bind(member: Member, displayIndex: Int) {
            itemView.findViewById<TextView>(R.id.textMemberNumber).text = displayIndex.toString()
            itemView.findViewById<TextView>(R.id.memberName).text = member.name
            itemView.findViewById<TextView>(R.id.textMemberId).text = member.userCode ?: "NO-ID"

            val badgeYou = itemView.findViewById<TextView>(R.id.textYouBadge)
            if (member.id == currentUserId) {
                badgeYou.visibility = View.VISIBLE
            } else {
                badgeYou.visibility = View.GONE
            }

            val avatarImage = itemView.findViewById<ImageView>(R.id.imgMemberAvatar)
            if (!member.avatarUrl.isNullOrBlank()) {
                avatarImage.load(member.avatarUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    error(R.drawable.avatar_default)
                    fallback(R.drawable.avatar_default)
                }
            } else {
                avatarImage.setImageResource(R.drawable.avatar_default)
            }

            val btnEdit = itemView.findViewById<View>(R.id.btnEditMember)
            val btnRemove = itemView.findViewById<View>(R.id.btnRemoveMember)

            if (member.role.lowercase() == "owner") {
                btnEdit.visibility = View.GONE
                btnRemove.visibility = View.GONE
            } else {
                btnEdit.visibility = View.VISIBLE
                btnRemove.visibility = View.VISIBLE

                btnEdit.setOnClickListener { onEditClick(member) }
                btnRemove.setOnClickListener { onRemoveClick(member) }
            }
        }
    }
}