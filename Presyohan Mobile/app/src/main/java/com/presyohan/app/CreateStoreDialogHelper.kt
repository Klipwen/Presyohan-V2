package com.presyohan.app

import android.app.Dialog
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CreateStoreDialogHelper {

    @Serializable
    data class UserStoreRow(
        val store_id: String,
        val name: String,
        val branch: String? = null,
        val type: String? = null,
        val role: String,
        val member_count: Int = 0
    )

    @Serializable
    data class StoreRow(val id: String, val name: String, val branch: String? = null)

    fun showCreateStoreDialog(
        activity: AppCompatActivity,
        onComplete: (() -> Unit)? = null
    ) {
        val dialog = Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_create_store, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set Dialog Width to 90% of Screen
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Bind Views
        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        val inputStoreName = view.findViewById<TextInputEditText>(R.id.inputStoreName)
        val inputStoreBranch = view.findViewById<TextInputEditText>(R.id.inputStoreBranch)
        val spinnerStoreType = view.findViewById<AutoCompleteTextView>(R.id.spinnerStoreType)
        val layoutOtherStoreType = view.findViewById<TextInputLayout>(R.id.layoutOtherStoreType)
        val inputOtherStoreType = view.findViewById<TextInputEditText>(R.id.inputOtherStoreType)
        val buttonDone = view.findViewById<android.widget.Button>(R.id.buttonDone)

        // Setup Store Types Dropdown
        val storeTypes = listOf(
            "Grocery",
            "Pharmacy",
            "Laundry",
            "Other (specify)"
        )
        val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, storeTypes)
        spinnerStoreType.setAdapter(adapter)

        // Handle dropdown selection change
        spinnerStoreType.setOnItemClickListener { _, _, position, _ ->
            val selectedType = storeTypes[position]
            if (selectedType == "Other (specify)") {
                layoutOtherStoreType.visibility = View.VISIBLE
            } else {
                layoutOtherStoreType.visibility = View.GONE
            }
        }

        btnBack.setOnClickListener { dialog.dismiss() }

        buttonDone.setOnClickListener {
            val name = inputStoreName.text.toString().trim()
            val branch = inputStoreBranch.text.toString().trim()
            val selectedType = spinnerStoreType.text.toString().trim()

            val type = if (selectedType == "Other (specify)") {
                inputOtherStoreType.text.toString().trim()
            } else {
                selectedType
            }

            if (name.isEmpty() || branch.isEmpty() || type.isEmpty() || selectedType.isEmpty()) {
                Toast.makeText(activity, "Please complete all fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val client = SupabaseProvider.client
            val uid = client.auth.currentUserOrNull()?.id
            if (uid == null) {
                Toast.makeText(activity, "Not signed in. Please log in and try again.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val overlay = LoadingOverlayHelper.attach(activity)
            LoadingOverlayHelper.show(overlay)

            activity.lifecycleScope.launch {
                try {
                    val payload = buildJsonObject {
                        put("p_name", name)
                        put("p_branch", branch)
                        put("p_type", type)
                    }

                    client.postgrest.rpc("create_store", payload)

                    var newStoreId: String? = null
                    try {
                        val rows = client.postgrest.rpc("get_user_stores").decodeList<UserStoreRow>()
                        newStoreId = rows.firstOrNull {
                            it.role == "owner" &&
                            it.name.equals(name, ignoreCase = true) &&
                            ((it.branch ?: "").equals(branch, ignoreCase = true))
                        }?.store_id
                    } catch (_: Exception) {}

                    if (newStoreId.isNullOrBlank()) {
                        try {
                            val stores = client.postgrest["stores"].select {
                                filter {
                                    eq("name", name)
                                    if (branch.isNotEmpty()) eq("branch", branch)
                                }
                                order("created_at", order = Order.DESCENDING)
                                limit(1)
                            }.decodeList<StoreRow>()
                            newStoreId = stores.firstOrNull()?.id
                        } catch (_: Exception) {}
                    }

                    dialog.dismiss()

                    if (!newStoreId.isNullOrBlank()) {
                        Toast.makeText(activity, "Store created successfully.", Toast.LENGTH_SHORT).show()
                        val goHome = Intent(activity, HomeActivity::class.java).apply {
                            putExtra("storeId", newStoreId)
                            putExtra("storeName", name)
                            // Clear back stack so user lands in new store cleanly
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        activity.startActivity(goHome)
                        activity.finish()
                    } else {
                        Toast.makeText(activity, "Store created. Refreshing list...", Toast.LENGTH_SHORT).show()
                        onComplete?.invoke()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CreateStoreDialog", "Store creation failed", e)
                    Toast.makeText(activity, "Couldn’t create the store. Please try again.", Toast.LENGTH_LONG).show()
                } finally {
                    LoadingOverlayHelper.hide(overlay)
                }
            }
        }

        dialog.show()
    }
}
