package com.presyohan.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

@Serializable
data class SukiDbRow(
    val user_id: String? = null,
    val store_id: String? = null,
    val status: String? = "active",
    val created_at: String? = null
)

@Serializable
data class SukiNotifRow(
    val id: String,
    val sender_user_id: String? = null,
    val receiver_user_id: String? = null,
    val store_id: String? = null,
    val type: String? = null,
    val created_at: String? = null
)

@Serializable
data class SukiUserRow(
    val id: String,
    val name: String? = null,
    val username: String? = null,
    val user_code: String? = null,
    val avatar_url: String? = null
)

@Serializable
data class SukiRpcRow(
    val user_id: String? = null,
    val id: String? = null,
    val sender_user_id: String? = null,
    val store_id: String? = null,
    val status: String? = "active",
    val created_at: String? = null,
    val name: String? = null,
    val username: String? = null,
    val user_code: String? = null,
    val avatar_url: String? = null
)

data class SukiItem(
    val notificationId: String? = null,
    val userId: String,
    val name: String,
    val username: String?,
    val userCode: String?,
    val avatarUrl: String?,
    val status: String, // "active" or "pending"
    val createdAt: String?
)

class ManageSukiActivity : AppCompatActivity() {

    private var storeId: String? = null
    private var storeName: String? = null
    private var isStorePublic: Boolean = true

    private lateinit var textTotalSuki: TextView
    private lateinit var tvVisibilityBadge: TextView
    private lateinit var tvVisibilityDesc: TextView
    private lateinit var etSearchSuki: EditText
    private lateinit var btnSearchClear: ImageView

    private lateinit var chipAll: TextView
    private lateinit var chipActive: TextView
    private lateinit var chipPending: TextView

    private lateinit var recyclerView: RecyclerView
    private lateinit var shimmerLayout: View
    private lateinit var layoutEmptyState: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyDesc: TextView

    private lateinit var adapter: SukiAdapter
    private var fullSukiList: List<SukiItem> = emptyList()
    private var currentFilterStatus: String = "ALL" // "ALL", "active", "pending"
    private var searchJob: Job? = null
    private lateinit var loadingOverlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_suki)

        loadingOverlay = LoadingOverlayHelper.attach(this)

        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName")
        isStorePublic = intent.getBooleanExtra("isPublic", true)

        if (storeId.isNullOrBlank()) {
            Toast.makeText(this, "No store ID provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initViews()
        setupListeners()
        setupRecyclerView()
        fetchStoreVisibilityAndSuki()
    }

    private fun initViews() {
        textTotalSuki = findViewById(R.id.textTotalSuki)
        tvVisibilityBadge = findViewById(R.id.tvVisibilityBadge)
        tvVisibilityDesc = findViewById(R.id.tvVisibilityDesc)
        etSearchSuki = findViewById(R.id.etSearchSuki)
        btnSearchClear = findViewById(R.id.btnSearchClear)

        chipAll = findViewById(R.id.chipAll)
        chipActive = findViewById(R.id.chipActive)
        chipPending = findViewById(R.id.chipPending)

        recyclerView = findViewById(R.id.recyclerViewSuki)
        shimmerLayout = findViewById(R.id.shimmerLayout)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvEmptyDesc = findViewById(R.id.tvEmptyDesc)

        updateVisibilityUi(isStorePublic)
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        btnSearchClear.setOnClickListener {
            etSearchSuki.setText("")
            btnSearchClear.visibility = View.GONE
            applyFilterAndSearch()
        }

        etSearchSuki.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnSearchClear.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(150)
                    applyFilterAndSearch()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        chipAll.setOnClickListener { selectFilterChip("ALL") }
        chipActive.setOnClickListener { selectFilterChip("active") }
        chipPending.setOnClickListener { selectFilterChip("pending") }
    }

    private fun setupRecyclerView() {
        adapter = SukiAdapter(
            onAcceptClick = { suki ->
                showAcceptSukiConfirmation(suki)
            },
            onRemoveClick = { suki ->
                showRemoveSukiConfirmation(suki)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun updateVisibilityUi(isPublic: Boolean) {
        if (isPublic) {
            tvVisibilityBadge.text = "Store is Public"
            tvVisibilityBadge.setBackgroundResource(R.drawable.bg_badge_teal)
            tvVisibilityDesc.text = "Store is Public — Suki can view your pricelist"
        } else {
            tvVisibilityBadge.text = "Store is Private"
            tvVisibilityBadge.setBackgroundResource(R.drawable.bg_badge_grey)
            tvVisibilityDesc.text = "Store is Private — Suki cannot view your pricelist"
        }
    }

    private fun selectFilterChip(status: String) {
        currentFilterStatus = status

        chipAll.setBackgroundResource(if (status == "ALL") R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        chipAll.setTextColor(if (status == "ALL") getColor(R.color.white) else getColor(R.color.presyo_darkblue))

        chipActive.setBackgroundResource(if (status == "active") R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        chipActive.setTextColor(if (status == "active") getColor(R.color.white) else getColor(R.color.presyo_darkblue))

        chipPending.setBackgroundResource(if (status == "pending") R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected)
        chipPending.setTextColor(if (status == "pending") getColor(R.color.white) else getColor(R.color.presyo_darkblue))

        applyFilterAndSearch()
    }

    private fun parseStatus(rawStatus: String?): String {
        val clean = (rawStatus ?: "active").lowercase(Locale.getDefault())
        return if (clean.contains("pending") || clean.contains("request") || clean.contains("sent") || clean.contains("wait")) {
            "pending"
        } else {
            "active"
        }
    }

    private fun fetchStoreVisibilityAndSuki() {
        val sId = storeId ?: return
        shimmerLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        layoutEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // 1. Fetch store visibility status
                @Serializable
                data class StoreRow(val id: String, val is_public: Boolean? = true)
                try {
                    val storeRes = SupabaseProvider.client.postgrest["stores"].select {
                        filter { eq("id", sId) }
                        limit(1)
                    }.decodeList<StoreRow>()
                    val sRow = storeRes.firstOrNull()
                    if (sRow != null) {
                        isStorePublic = sRow.is_public ?: true
                        updateVisibilityUi(isStorePublic)
                    }
                } catch (e: Exception) {
                    Log.e("ManageSukiActivity", "Failed to fetch store public state", e)
                }

                // 2. Try RPC calls first (SECURITY DEFINER functions bypass RLS)
                val rpcNames = listOf(
                    "get_store_sukis",
                    "get_store_suki",
                    "get_store_suki_list",
                    "get_store_suki_members",
                    "get_store_suki_partners",
                    "get_suki_list",
                    "get_store_suki_relationships"
                )

                var fetchedRpcRows = listOf<SukiRpcRow>()

                for (rpcName in rpcNames) {
                    // Try with p_store_id
                    try {
                        val res = SupabaseProvider.client.postgrest.rpc(
                            rpcName,
                            buildJsonObject { put("p_store_id", sId) }
                        ).decodeList<SukiRpcRow>()
                        if (res.isNotEmpty()) {
                            fetchedRpcRows = res
                            Log.d("ManageSukiActivity", "RPC $rpcName with p_store_id returned ${res.size} items")
                            break
                        }
                    } catch (_: Exception) {}

                    // Try with store_id
                    try {
                        val res = SupabaseProvider.client.postgrest.rpc(
                            rpcName,
                            buildJsonObject { put("store_id", sId) }
                        ).decodeList<SukiRpcRow>()
                        if (res.isNotEmpty()) {
                            fetchedRpcRows = res
                            Log.d("ManageSukiActivity", "RPC $rpcName with store_id returned ${res.size} items")
                            break
                        }
                    } catch (_: Exception) {}
                }

                // 3. Query suki_relationships table safely
                val sukiRelRows = try {
                    SupabaseProvider.client.postgrest["suki_relationships"].select(
                        Columns.list("user_id", "store_id", "status", "created_at")
                    ) {
                        filter { eq("store_id", sId) }
                    }.decodeList<SukiDbRow>()
                } catch (e1: Exception) {
                    try {
                        SupabaseProvider.client.postgrest["suki_relationships"].select {
                            filter { eq("store_id", sId) }
                        }.decodeList<SukiDbRow>()
                    } catch (e2: Exception) {
                        Log.e("ManageSukiActivity", "Failed select on suki_relationships", e2)
                        emptyList()
                    }
                }

                // 4. Query notifications table for pending suki requests
                val pendingNotifRows = try {
                    SupabaseProvider.client.postgrest["notifications"].select(
                        Columns.list("id", "sender_user_id", "receiver_user_id", "store_id", "type", "created_at")
                    ) {
                        filter {
                            eq("store_id", sId)
                        }
                    }.decodeList<SukiNotifRow>().filter {
                        val t = (it.type ?: "").lowercase()
                        (t.contains("suki_request") || t.contains("suki_pending") || t == "suki") &&
                                !t.contains("accepted") && !t.contains("rejected") && !t.contains("removed") && !t.contains("declined") && !t.contains("cancel")
                    }
                } catch (e: Exception) {
                    Log.w("ManageSukiActivity", "Failed to query notifications", e)
                    emptyList()
                }

                // Collect all user IDs
                val rpcUserIds = fetchedRpcRows.mapNotNull { it.user_id ?: it.sender_user_id ?: it.id }
                val relUserIds = sukiRelRows.mapNotNull { it.user_id }
                val notifUserIds = pendingNotifRows.mapNotNull { it.sender_user_id }
                val allUserIds = (rpcUserIds + relUserIds + notifUserIds).distinct()

                if (allUserIds.isEmpty() && fetchedRpcRows.isEmpty()) {
                    fullSukiList = emptyList()
                    updateTotalCountUi(0)
                    applyFilterAndSearch()
                    return@launch
                }

                // 5. Fetch profiles from app_users table
                val profilesMap = if (allUserIds.isNotEmpty()) {
                    try {
                        SupabaseProvider.client.postgrest["app_users"].select(
                            Columns.list("id", "name", "username", "user_code", "avatar_url")
                        ) {
                            filter { isIn("id", allUserIds) }
                        }.decodeList<SukiUserRow>().associateBy { it.id }
                    } catch (e1: Exception) {
                        try {
                            SupabaseProvider.client.postgrest["app_users"].select {
                                filter { isIn("id", allUserIds) }
                            }.decodeList<SukiUserRow>().associateBy { it.id }
                        } catch (e2: Exception) {
                            Log.e("ManageSukiActivity", "Failed app_users query", e2)
                            emptyMap()
                        }
                    }
                } else {
                    emptyMap()
                }

                val itemsList = mutableListOf<SukiItem>()
                val processedUserIds = mutableSetOf<String>()

                // Process RPC rows first if available
                for (r in fetchedRpcRows) {
                    val uid = r.user_id ?: r.sender_user_id ?: r.id ?: continue
                    processedUserIds.add(uid)
                    val user = profilesMap[uid]

                    itemsList.add(
                        SukiItem(
                            notificationId = null,
                            userId = uid,
                            name = r.name?.ifBlank { null } ?: user?.name?.ifBlank { null } ?: "Suki Customer",
                            username = r.username ?: user?.username,
                            userCode = r.user_code ?: user?.user_code,
                            avatarUrl = r.avatar_url ?: user?.avatar_url,
                            status = parseStatus(r.status),
                            createdAt = r.created_at
                        )
                    )
                }

                // Process suki_relationships rows (Take precedence over old notifications)
                for (rel in sukiRelRows) {
                    val uid = rel.user_id ?: continue
                    if (!processedUserIds.contains(uid)) {
                        processedUserIds.add(uid)
                        val user = profilesMap[uid]
                        val matchedNotif = pendingNotifRows.firstOrNull { it.sender_user_id == uid }

                        itemsList.add(
                            SukiItem(
                                notificationId = matchedNotif?.id,
                                userId = uid,
                                name = user?.name?.ifBlank { null } ?: "Suki Customer",
                                username = user?.username,
                                userCode = user?.user_code,
                                avatarUrl = user?.avatar_url,
                                status = parseStatus(rel.status),
                                createdAt = rel.created_at ?: matchedNotif?.created_at
                            )
                        )
                    }
                }

                // Process pending notifications for any user not yet in suki_relationships
                for (notif in pendingNotifRows) {
                    val senderId = notif.sender_user_id ?: continue
                    if (!processedUserIds.contains(senderId)) {
                        processedUserIds.add(senderId)
                        val user = profilesMap[senderId]

                        itemsList.add(
                            SukiItem(
                                notificationId = notif.id,
                                userId = senderId,
                                name = user?.name?.ifBlank { null } ?: "Suki Customer",
                                username = user?.username,
                                userCode = user?.user_code,
                                avatarUrl = user?.avatar_url,
                                status = "pending",
                                createdAt = notif.created_at
                            )
                        )
                    }
                }

                fullSukiList = itemsList
                updateTotalCountUi(fullSukiList.size)
                applyFilterAndSearch()

            } catch (e: Exception) {
                Log.e("ManageSukiActivity", "General error loading suki", e)
                Toast.makeText(this@ManageSukiActivity, "Unable to load Suki list.", Toast.LENGTH_SHORT).show()
                fullSukiList = emptyList()
                updateTotalCountUi(0)
                applyFilterAndSearch()
            } finally {
                shimmerLayout.visibility = View.GONE
            }
        }
    }

    private fun updateTotalCountUi(count: Int) {
        textTotalSuki.text = "$count Connected Suki"
    }

    private fun applyFilterAndSearch() {
        val query = etSearchSuki.text.toString().trim().lowercase(Locale.getDefault())

        val filtered = fullSukiList.filter { item ->
            val matchesStatus = when (currentFilterStatus) {
                "ALL" -> true
                else -> item.status == currentFilterStatus
            }
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                item.name.lowercase(Locale.getDefault()).contains(query) ||
                        (item.username?.lowercase(Locale.getDefault())?.contains(query) == true) ||
                        (item.userCode?.lowercase(Locale.getDefault())?.contains(query) == true)
            }
            matchesStatus && matchesQuery
        }

        adapter.setItems(filtered)

        if (filtered.isEmpty()) {
            recyclerView.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
            if (fullSukiList.isEmpty()) {
                tvEmptyTitle.text = "No Suki Linked"
                tvEmptyDesc.text = "No Suki linked to your store yet. Share your Store QR Code to start connecting with customers!"
            } else {
                tvEmptyTitle.text = "No Matching Suki"
                tvEmptyDesc.text = "No Suki match your current search query or filter selection."
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
    }

    private fun acceptSukiRequest(suki: SukiItem) {
        val sId = storeId ?: return
        LoadingOverlayHelper.show(loadingOverlay)

        lifecycleScope.launch {
            try {
                if (!suki.notificationId.isNullOrBlank()) {
                    SupabaseProvider.client.postgrest.rpc(
                        "handle_suki_decision",
                        buildJsonObject {
                            put("p_notification_id", suki.notificationId)
                            put("p_action", "accept")
                        }
                    )
                } else {
                    // Update suki_relationships directly
                    SupabaseProvider.client.postgrest["suki_relationships"].update(
                        mapOf("status" to "active")
                    ) {
                        filter {
                            eq("store_id", sId)
                            eq("user_id", suki.userId)
                        }
                    }
                }
                Toast.makeText(this@ManageSukiActivity, "Suki request accepted for ${suki.name}", Toast.LENGTH_SHORT).show()
                fetchStoreVisibilityAndSuki()
            } catch (e: Exception) {
                Toast.makeText(this@ManageSukiActivity, "Failed to accept suki request.", Toast.LENGTH_LONG).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    private fun showAcceptSukiConfirmation(suki: SukiItem) {
        ReusableDialogHelper.showCustomDialog(
            context = this,
            title = "Accept Request",
            message = "Do you want to accept ${suki.name} as a Suki partner for your store?",
            positiveButtonText = "Accept",
            positiveAction = {
                acceptSukiRequest(suki)
            },
            negativeButtonText = "Cancel",
            negativeAction = null
        )
    }

    private fun showRemoveSukiConfirmation(suki: SukiItem) {
        val isPending = suki.status == "pending"
        val titleText = if (isPending) "Decline Request" else "Remove Suki"
        val messageText = if (isPending)
            "Are you sure you want to decline ${suki.name}'s suki request?"
        else
            "Are you sure you want to remove ${suki.name} as your suki?"
        val actionText = if (isPending) "Decline" else "Remove"

        ReusableDialogHelper.showCustomDialog(
            context = this,
            title = titleText,
            message = messageText,
            positiveButtonText = actionText,
            positiveAction = {
                removeSukiRelationship(suki)
            },
            negativeButtonText = "Cancel",
            negativeAction = null
        )
    }

    private fun removeSukiRelationship(suki: SukiItem) {
        val sId = storeId ?: return
        LoadingOverlayHelper.show(loadingOverlay)

        lifecycleScope.launch {
            try {
                // 1. Try calling remove_suki_relationship SECURITY DEFINER RPC
                var rpcSuccess = false
                try {
                    SupabaseProvider.client.postgrest.rpc(
                        "remove_suki_relationship",
                        buildJsonObject {
                            put("p_store_id", sId)
                            put("p_user_id", suki.userId)
                        }
                    )
                    rpcSuccess = true
                } catch (_: Exception) {}

                // 2. Fallback if RPC is not deployed yet
                if (!rpcSuccess) {
                    if (suki.status == "pending" && !suki.notificationId.isNullOrBlank()) {
                        try {
                            SupabaseProvider.client.postgrest.rpc(
                                "handle_suki_decision",
                                buildJsonObject {
                                    put("p_notification_id", suki.notificationId)
                                    put("p_action", "reject")
                                }
                            )
                        } catch (_: Exception) {}
                    }

                    try {
                        SupabaseProvider.client.postgrest["suki_relationships"].delete {
                            filter {
                                eq("store_id", sId)
                                eq("user_id", suki.userId)
                            }
                        }
                    } catch (_: Exception) {}

                    try {
                        SupabaseProvider.client.postgrest["notifications"].update(
                            mapOf("type" to "suki_removed")
                        ) {
                            filter {
                                eq("store_id", sId)
                                eq("sender_user_id", suki.userId)
                            }
                        }
                    } catch (_: Exception) {}
                }
                Toast.makeText(this@ManageSukiActivity, "${suki.name} removed.", Toast.LENGTH_SHORT).show()
                fetchStoreVisibilityAndSuki()
            } catch (e: Exception) {
                Log.e("ManageSukiActivity", "Failed to remove suki", e)
                Toast.makeText(this@ManageSukiActivity, "Failed to remove suki relationship.", Toast.LENGTH_LONG).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        SessionManager.markStoreHome(this, storeId, storeName)
    }
}

class SukiAdapter(
    private val onAcceptClick: (SukiItem) -> Unit,
    private val onRemoveClick: (SukiItem) -> Unit
) : RecyclerView.Adapter<SukiAdapter.SukiViewHolder>() {

    private var items: List<SukiItem> = emptyList()

    fun setItems(newItems: List<SukiItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SukiViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_suki_card, parent, false)
        return SukiViewHolder(view)
    }

    override fun onBindViewHolder(holder: SukiViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class SukiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgAvatar: ImageView = itemView.findViewById(R.id.imgSukiAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvSukiName)
        private val tvSubDetails: TextView = itemView.findViewById(R.id.tvSukiSubDetails)
        private val tvSince: TextView = itemView.findViewById(R.id.tvSukiSince)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvSukiStatus)
        private val btnAccept: View = itemView.findViewById(R.id.btnAcceptSuki)
        private val btnRemove: View = itemView.findViewById(R.id.btnRemoveSuki)

        fun bind(item: SukiItem) {
            tvName.text = item.name

            val codeOrHandle = item.username?.let { "@$it" } ?: item.userCode?.let { "ID: $it" } ?: "Customer"
            tvSubDetails.text = codeOrHandle

            val dateFormatted = formatDate(item.createdAt)
            tvSince.text = if (item.status == "pending") "Requested $dateFormatted" else "Suki since $dateFormatted"

            if (item.status == "active") {
                tvStatus.text = "Active"
                tvStatus.setBackgroundResource(R.drawable.bg_badge_teal)
                btnAccept.visibility = View.GONE
            } else {
                tvStatus.text = "Pending"
                tvStatus.setBackgroundResource(R.drawable.bg_badge_orange)
                btnAccept.visibility = View.VISIBLE
            }

            if (!item.avatarUrl.isNullOrBlank()) {
                imgAvatar.load(item.avatarUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                    error(R.drawable.avatar_default)
                    fallback(R.drawable.avatar_default)
                }
            } else {
                imgAvatar.setImageResource(R.drawable.avatar_default)
            }

            btnAccept.setOnClickListener {
                onAcceptClick(item)
            }

            btnRemove.setOnClickListener {
                onRemoveClick(item)
            }
        }

        private fun formatDate(isoString: String?): String {
            if (isoString.isNullOrBlank()) return "--/--/--"
            return try {
                val clean = isoString.trim().replace(" ", "T")
                val datePart = if (clean.contains("T")) clean.split("T")[0] else clean
                val parts = datePart.split("-")
                if (parts.size >= 3) {
                    val year = parts[0].takeLast(2)
                    val month = parts[1]
                    val day = parts[2].take(2)
                    "$month/$day/$year"
                } else {
                    datePart
                }
            } catch (_: Exception) {
                "--/--/--"
            }
        }
    }
}
