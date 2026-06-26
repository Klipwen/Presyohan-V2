package com.presyohan.app

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object JoinStoreDialogHelper {

    @Serializable
    data class StoreByInviteCodeRow(
        val store_id: String,
        val name: String,
        val branch: String? = null,
        val type: String? = null,
        val invite_code_created_at: String? = null
    )

    @Serializable
    data class UserStoreSummaryRow(
        val store_id: String,
        val name: String,
        val role: String
    )

    @Serializable
    data class NotificationIdRow(
        val id: String
    )

    fun showJoinStoreDialog(
        activity: AppCompatActivity,
        onComplete: (() -> Unit)? = null
    ) {
        val dialog = Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_join_store, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set Dialog Width to 90% of Screen
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Bind Views
        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        val hiddenCodeInput = view.findViewById<EditText>(R.id.hiddenCodeInput)
        val layoutNoStore = view.findViewById<View>(R.id.layoutNoStore)
        val tvNoStoreText = view.findViewById<TextView>(R.id.tvNoStoreText)
        val layoutStoreDetails = view.findViewById<View>(R.id.layoutStoreDetails)
        val tvStorePreviewName = view.findViewById<TextView>(R.id.tvStorePreviewName)
        val tvStorePreviewType = view.findViewById<TextView>(R.id.tvStorePreviewType)
        val tvStorePreviewLocation = view.findViewById<TextView>(R.id.tvStorePreviewLocation)
        val layoutCodeBoxes = view.findViewById<LinearLayout>(R.id.layoutCodeBoxes)
        val buttonRequestJoin = view.findViewById<AppCompatButton>(R.id.buttonRequestJoin)
        val tvInlineWarning = view.findViewById<TextView>(R.id.tvInlineWarning)

        val boxes = listOf(
            view.findViewById<TextView>(R.id.box1),
            view.findViewById<TextView>(R.id.box2),
            view.findViewById<TextView>(R.id.box3),
            view.findViewById<TextView>(R.id.box4),
            view.findViewById<TextView>(R.id.box5),
            view.findViewById<TextView>(R.id.box6)
        )

        // Helper to focus input
        fun focusInput() {
            hiddenCodeInput.requestFocus()
            try {
                hiddenCodeInput.setSelection(hiddenCodeInput.text.length)
            } catch (_: Exception) {}
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(hiddenCodeInput, InputMethodManager.SHOW_IMPLICIT)
        }

        // Focus keyboard when clicking box container or individual box
        layoutCodeBoxes.setOnClickListener { focusInput() }
        boxes.forEach { box -> box.setOnClickListener { focusInput() } }

        var validationJob: Job? = null
        var foundStore: StoreByInviteCodeRow? = null

        // Function to update boxes visual state
        fun updateBoxesUI(code: String) {
            val len = code.length
            for (i in 0 until 6) {
                val box = boxes[i]
                if (i < len) {
                    box.text = code[i].toString()
                    box.setTextColor(ContextCompat.getColor(activity, R.color.presyo_darkblue))
                    box.setBackgroundResource(R.drawable.bg_code_box_done)
                } else if (i == len) {
                    box.text = ""
                    box.setBackgroundResource(R.drawable.bg_code_box_active)
                } else {
                    box.text = ""
                    box.setBackgroundResource(R.drawable.bg_code_box_empty)
                }
            }
        }

        // Initialize UI state
        updateBoxesUI("")

        hiddenCodeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val code = s.toString().trim().uppercase()
                updateBoxesUI(code)

                // Cancel any pending validation job
                validationJob?.cancel()
                foundStore = null

                // Reset preview states and button state
                layoutStoreDetails.visibility = View.GONE
                layoutNoStore.visibility = View.VISIBLE
                tvNoStoreText.text = "no store found"
                tvNoStoreText.setTextColor(ContextCompat.getColor(activity, R.color.presyo_orange))
                tvNoStoreText.visibility = View.INVISIBLE
                tvInlineWarning.visibility = View.INVISIBLE
                buttonRequestJoin.isEnabled = false
                buttonRequestJoin.alpha = 0.5f

                if (code.length == 6) {
                    validationJob = activity.lifecycleScope.launch {
                        val supaUserId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return@launch
                        try {
                            // Query invite code via Supabase RPC
                            val storeResponse = SupabaseProvider.client.postgrest.rpc(
                                "get_store_by_invite_code",
                                buildJsonObject { put("p_invite_code", code) }
                            ).decodeList<StoreByInviteCodeRow>()

                            if (storeResponse.isEmpty()) {
                                // Incorrect code state
                                runOnUiThread {
                                    boxes.forEach { box ->
                                        box.setBackgroundResource(R.drawable.bg_code_box_invalid)
                                        box.setTextColor(ColorHelper.parseColor("#757575"))
                                    }
                                    tvNoStoreText.setTextColor(ColorHelper.parseColor("#757575"))
                                    tvNoStoreText.visibility = View.VISIBLE
                                    tvInlineWarning.text = "Invalid code. Contact the store owner to verify."
                                    tvInlineWarning.setTextColor(ColorHelper.parseColor("#757575"))
                                    tvInlineWarning.visibility = View.VISIBLE
                                    buttonRequestJoin.isEnabled = false
                                    buttonRequestJoin.alpha = 0.5f
                                }
                                return@launch
                            }

                            val store = storeResponse[0]
                            foundStore = store

                            // Check if already a member
                            val userStores = SupabaseProvider.client.postgrest.rpc("get_user_stores")
                                .decodeList<UserStoreSummaryRow>()
                            val isAlreadyMember = userStores.any { it.store_id == store.store_id }

                            if (isAlreadyMember) {
                                runOnUiThread {
                                    // Update store details card
                                    layoutNoStore.visibility = View.GONE
                                    layoutStoreDetails.visibility = View.VISIBLE
                                    tvStorePreviewName.text = store.name
                                    tvStorePreviewType.text = store.type ?: "General Store"
                                    tvStorePreviewLocation.text = store.branch ?: "Presyohan App"

                                    // Boxes orange highlight
                                    boxes.forEach { box ->
                                        box.setBackgroundResource(R.drawable.bg_code_box_glow)
                                        box.setTextColor(ContextCompat.getColor(activity, R.color.presyo_orange))
                                    }

                                    tvInlineWarning.text = "You are already a store member."
                                    tvInlineWarning.setTextColor(ColorHelper.parseColor("#757575"))
                                    tvInlineWarning.visibility = View.VISIBLE
                                    buttonRequestJoin.isEnabled = false
                                    buttonRequestJoin.alpha = 0.5f
                                }
                                return@launch
                            }

                            // Check if pending join request already exists
                            val existingNotifications = SupabaseProvider.client.postgrest["notifications"]
                                .select(Columns.list("id")) {
                                    filter {
                                        eq("sender_user_id", supaUserId)
                                        eq("store_id", store.store_id)
                                        eq("type", "join_request")
                                        eq("read", false)
                                    }
                                }.decodeList<NotificationIdRow>()

                            if (existingNotifications.isNotEmpty()) {
                                runOnUiThread {
                                    layoutNoStore.visibility = View.GONE
                                    layoutStoreDetails.visibility = View.VISIBLE
                                    tvStorePreviewName.text = store.name
                                    tvStorePreviewType.text = store.type ?: "General Store"
                                    tvStorePreviewLocation.text = store.branch ?: "Presyohan App"

                                    boxes.forEach { box ->
                                        box.setBackgroundResource(R.drawable.bg_code_box_glow)
                                        box.setTextColor(ContextCompat.getColor(activity, R.color.presyo_orange))
                                    }

                                    tvInlineWarning.text = "You already have a pending join request."
                                    tvInlineWarning.setTextColor(ColorHelper.parseColor("#757575"))
                                    tvInlineWarning.visibility = View.VISIBLE
                                    buttonRequestJoin.isEnabled = false
                                    buttonRequestJoin.alpha = 0.5f
                                }
                                return@launch
                            }

                            // Correct code & eligible to join
                            runOnUiThread {
                                layoutNoStore.visibility = View.GONE
                                layoutStoreDetails.visibility = View.VISIBLE
                                tvStorePreviewName.text = store.name
                                tvStorePreviewType.text = store.type ?: "General Store"
                                tvStorePreviewLocation.text = store.branch ?: "Presyohan App"

                                // Glowing orange stroke and orange digits
                                boxes.forEach { box ->
                                    box.setBackgroundResource(R.drawable.bg_code_box_glow)
                                    box.setTextColor(ContextCompat.getColor(activity, R.color.presyo_orange))
                                }

                                tvInlineWarning.visibility = View.INVISIBLE
                                buttonRequestJoin.isEnabled = true
                                buttonRequestJoin.alpha = 1.0f
                            }

                        } catch (e: Exception) {
                            android.util.Log.e("JoinStoreDialog", "Validation error", e)
                            runOnUiThread {
                                Toast.makeText(activity, "Error connecting to service.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        })

        // Request To Join Action
        buttonRequestJoin.setOnClickListener {
            val store = foundStore ?: return@setOnClickListener
            val supaUserId = SupabaseProvider.client.auth.currentUserOrNull()?.id ?: return@setOnClickListener

            val overlay = LoadingOverlayHelper.attach(activity)
            LoadingOverlayHelper.show(overlay)

            activity.lifecycleScope.launch {
                try {
                    // Send join request using RPC
                    SupabaseProvider.client.postgrest.rpc(
                        "send_join_request",
                        buildJsonObject {
                            put("p_store_id", store.store_id)
                        }
                    )

                    // Create pending notification locally
                    try {
                        SupabaseProvider.client.postgrest["notifications"].insert(
                            buildJsonObject {
                                put("receiver_user_id", supaUserId)
                                put("sender_user_id", supaUserId)
                                put("store_id", store.store_id)
                                put("type", "join_pending")
                                put("title", "Join Request")
                                put("message", "You requested to join ${store.name}")
                                put("read", false)
                            }
                        )
                    } catch (_: Exception) {}

                    dialog.dismiss()
                    Toast.makeText(activity, "Join request sent successfully!", Toast.LENGTH_SHORT).show()
                    onComplete?.invoke()

                } catch (e: Exception) {
                    android.util.Log.e("JoinStoreDialog", "Send request failed", e)
                    Toast.makeText(activity, "Unable to send request. Please try again.", Toast.LENGTH_SHORT).show()
                } finally {
                    LoadingOverlayHelper.hide(overlay)
                }
            }
        }

        btnBack.setOnClickListener { dialog.dismiss() }

        dialog.show()
        // Auto-show keyboard
        focusInput()
    }

    private fun runOnUiThread(action: Runnable) {
        android.os.Handler(android.os.Looper.getMainLooper()).post(action)
    }

    private object ColorHelper {
        fun parseColor(colorString: String): Int {
            return android.graphics.Color.parseColor(colorString)
        }
    }
}
