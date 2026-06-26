package com.presyohan.app

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Centralized helper for the "Copy Prices" dialog flow.
 *
 * Usage:
 *   CopyPricesDialogHelper.show(
 *       activity      = this,
 *       storeId       = storeId,
 *       storeName     = storeName,
 *       selectedIds   = listOf("id1", "id2"),   // pass null to copy ALL items in the store
 *       preselectedCategory = null               // or a category name to pre-filter
 *   )
 */
object CopyPricesDialogHelper {

    @Serializable
    data class ValidateCodeResult(
        val store_id: String,
        val store_name: String,
        val store_branch: String? = null,
        val store_type: String? = null
    )

    @Serializable
    data class PreviewRow(
        val product_id: String,
        val name: String? = null,
        val source_price: Double? = null,
        val dest_price: Double? = null,
        val action: String? = null
    )

    @Serializable
    data class SourceProduct(
        val product_id: String,
        val name: String? = null,
        val category: String? = null,
        val price: Double? = null,
        val units: String? = null,
        val unit: String? = null,
        val description: String? = null,
        val is_public: Boolean = false
    )

    fun show(
        activity: AppCompatActivity,
        storeId: String,
        storeName: String,
        selectedIds: List<String>? = null,
        preselectedCategory: String? = null,
        descriptionText: String? = null   // null = keep the default XML text
    ) {
        val dialog = Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_copy_prices_code, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Views
        val inputCode = view.findViewById<TextInputEditText>(R.id.inputPasteCode)
        val btnEnter = view.findViewById<AppCompatButton>(R.id.btnEnterCode)
        val btnBack = view.findViewById<AppCompatButton>(R.id.btnBack)
        val btnNext = view.findViewById<AppCompatButton>(R.id.btnNext)
        val tvCodeError = view.findViewById<TextView>(R.id.textCodeError)

        // Store card views
        val layoutPlaceholder = view.findViewById<View>(R.id.layoutStorePlaceholder)
        val layoutVerified = view.findViewById<View>(R.id.layoutStoreVerified)
        val tvDestName = view.findViewById<TextView>(R.id.textDestStoreName)
        val tvDestType = view.findViewById<TextView>(R.id.textDestStoreType)
        val tvDestBranch = view.findViewById<TextView>(R.id.textDestStoreBranch)

        // Set custom description if provided
        if (!descriptionText.isNullOrBlank()) {
            view.findViewById<TextView>(R.id.textCopyDescription).text = descriptionText
        }

        var validDestination: ValidateCodeResult? = null

        // Enable Enter button only when 6 digits are typed
        inputCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val code = s?.toString()?.replace(Regex("[^0-9]"), "")?.take(6) ?: ""
                if (code != s?.toString()) {
                    inputCode.setText(code)
                    inputCode.setSelection(code.length)
                }
                btnEnter.isEnabled = code.length == 6
                if (code.length < 6) {
                    // Reset validation state
                    validDestination = null
                    btnNext.visibility = View.GONE
                    tvCodeError.visibility = View.GONE
                    layoutPlaceholder.visibility = View.VISIBLE
                    layoutVerified.visibility = View.GONE
                }
            }
        })

        // Enter button triggers validation
        btnEnter.setOnClickListener {
            val code = inputCode.text?.toString()?.trim() ?: ""
            if (code.length != 6) return@setOnClickListener
            validateCode(
                activity, code, storeId, tvCodeError, btnNext,
                layoutPlaceholder, layoutVerified, tvDestName, tvDestType, tvDestBranch
            ) { result ->
                validDestination = result
            }
        }

        // BACK = dismiss dialog
        btnBack.setOnClickListener { dialog.dismiss() }

        // NEXT = launch preview/review flow
        btnNext.setOnClickListener {
            val dest = validDestination ?: return@setOnClickListener
            val code = inputCode.text?.toString()?.trim() ?: ""
            launchCopyPreview(activity, storeId, storeName, dest, code, selectedIds, preselectedCategory, btnNext, dialog)
        }

        dialog.show()
    }

    @Serializable
    private data class StoreDetailsRow(
        val branch: String? = null,
        val type: String? = null
    )

    private fun validateCode(
        activity: AppCompatActivity,
        code: String,
        sourceStoreId: String,       // used to block same-store copy
        tvCodeError: TextView,
        btnNext: AppCompatButton,
        layoutPlaceholder: View,
        layoutVerified: View,
        tvDestName: TextView,
        tvDestType: TextView,
        tvDestBranch: TextView,
        onResult: (ValidateCodeResult?) -> Unit
    ) {
        activity.lifecycleScope.launch {
            try {
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "validate_paste_code",
                    buildJsonObject { put("p_code", code) }
                ).decodeList<ValidateCodeResult>()

                if (rows.isNotEmpty()) {
                    var dest = rows[0]

                    // Block same-store copy
                    if (dest.store_id == sourceStoreId) {
                        onResult(null)
                        layoutPlaceholder.visibility = View.VISIBLE
                        layoutVerified.visibility = View.GONE
                        tvCodeError.text = "Cannot copy prices to your own store"
                        tvCodeError.visibility = View.VISIBLE
                        btnNext.visibility = View.GONE
                        return@launch
                    }

                    // Follow-up query to get branch and type (not returned by original SQL function)
                    if (dest.store_branch == null || dest.store_type == null) {
                        try {
                            val storeDetails = SupabaseProvider.client.postgrest["stores"]
                                .select(Columns.list("branch", "type")) {
                                    filter { eq("id", dest.store_id) }
                                }.decodeList<StoreDetailsRow>()
                            if (storeDetails.isNotEmpty()) {
                                dest = dest.copy(
                                    store_branch = storeDetails[0].branch,
                                    store_type = storeDetails[0].type
                                )
                            }
                        } catch (_: Exception) { /* non-critical, proceed without branch/type */ }
                    }

                    onResult(dest)

                    // Show verified store card
                    layoutPlaceholder.visibility = View.GONE
                    tvCodeError.visibility = View.GONE
                    layoutVerified.visibility = View.VISIBLE
                    tvDestName.text = dest.store_name
                    tvDestType.text = dest.store_type?.replaceFirstChar { it.titlecase() } ?: ""
                    val branch = dest.store_branch?.trim()
                    tvDestBranch.text = if (!branch.isNullOrBlank()) branch else ""

                    // NEXT becomes visible only on success
                    btnNext.visibility = View.VISIBLE
                } else {
                    onResult(null)
                    layoutPlaceholder.visibility = View.VISIBLE
                    layoutVerified.visibility = View.GONE
                    tvCodeError.text = "Code does not exist"
                    tvCodeError.visibility = View.VISIBLE
                    btnNext.visibility = View.GONE
                }
            } catch (e: Exception) {
                onResult(null)
                layoutPlaceholder.visibility = View.VISIBLE
                layoutVerified.visibility = View.GONE
                tvCodeError.text = "Failed to validate code"
                tvCodeError.visibility = View.VISIBLE
                btnNext.visibility = View.GONE
            }

        }
    }

    private fun launchCopyPreview(
        activity: AppCompatActivity,
        storeId: String,
        storeName: String,
        dest: ValidateCodeResult,
        code: String,
        selectedIds: List<String>?,
        preselectedCategory: String?,
        btnNext: AppCompatButton,
        dialog: Dialog
    ) {
        btnNext.isEnabled = false
        btnNext.text = "Loading…"

        activity.lifecycleScope.launch {
            try {
                // If no selectedIds provided, load all products from store (and optionally filter by category)
                val finalIds: List<String> = if (selectedIds != null) {
                    selectedIds
                } else {
                    val allProducts = SupabaseProvider.client.postgrest.rpc(
                        "get_store_products",
                        buildJsonObject { put("p_store_id", storeId) }
                    ).decodeList<SourceProduct>()

                    val filtered = if (!preselectedCategory.isNullOrBlank()) {
                        allProducts.filter { it.category?.trim().equals(preselectedCategory.trim(), ignoreCase = true) }
                    } else {
                        allProducts
                    }
                    filtered.map { it.product_id }
                }

                if (finalIds.isEmpty()) {
                    Toast.makeText(activity, "No items selected to copy.", Toast.LENGTH_SHORT).show()
                    btnNext.isEnabled = true
                    btnNext.text = "NEXT"
                    return@launch
                }

                // Dry-run to get preview rows
                val previewRows = SupabaseProvider.client.postgrest.rpc(
                    "copy_prices",
                    buildJsonObject {
                        put("p_source_store_id", storeId)
                        put("p_dest_paste_code", code)
                        put("p_items", buildJsonArray {
                            finalIds.forEach { add(JsonPrimitive(it) as kotlinx.serialization.json.JsonElement) }
                        })
                        put("p_dry_run", true)
                    }
                ).decodeList<PreviewRow>()

                // Load source products for building the draft session
                val sourceProducts = SupabaseProvider.client.postgrest.rpc(
                    "get_store_products",
                    buildJsonObject { put("p_store_id", storeId) }
                ).decodeList<SourceProduct>()

                val selectedProducts = sourceProducts.filter { finalIds.contains(it.product_id) }
                val groupedByCat = selectedProducts.groupBy { it.category?.trim() ?: "General" }

                val draftCategories = mutableListOf<DraftCategory>()
                for ((catName, prods) in groupedByCat) {
                    val draftItems = prods.map { prod ->
                        val preview = previewRows.firstOrNull { it.product_id == prod.product_id }
                        val isUpdate = preview?.action == "update"
                        DraftItem(
                            draftItemId = java.util.UUID.randomUUID().toString(),
                            categoryName = catName,
                            productName = prod.name ?: "",
                            description = prod.description ?: "",
                            unit = prod.units ?: prod.unit ?: "",
                            priceText = prod.price?.toString() ?: "0",
                            price = prod.price ?: 0.0,
                            source = ImportSource.SIMPLE_MANUAL,
                            validationStatus = if (isUpdate) ValidationStatus.UPDATE else ValidationStatus.NEW
                        )
                    }.toMutableList()

                    draftCategories.add(
                        DraftCategory(
                            draftCategoryId = java.util.UUID.randomUUID().toString(),
                            name = if (catName.isBlank()) "General" else catName,
                            items = draftItems
                        )
                    )
                }

                val sessionStore = ImportDraftStore(activity.applicationContext)
                val session = sessionStore.createSession(
                    storeId = dest.store_id,
                    storeName = dest.store_name,
                    source = ImportSource.SIMPLE_MANUAL,
                    categories = draftCategories
                )

                val intent = Intent(activity, ReviewImportActivity::class.java).apply {
                    putExtra("storeId", dest.store_id)
                    putExtra("storeName", dest.store_name)
                    putExtra("draftSessionId", session.sessionId)
                    putExtra("isCopyPrices", true)
                    putExtra("destPasteCode", code)
                    putExtra("sourceStoreId", storeId)
                    putStringArrayListExtra("selectedProductIds", ArrayList(finalIds))
                }
                dialog.dismiss()
                activity.startActivity(intent)

            } catch (e: Exception) {
                Toast.makeText(activity, "Failed to prepare copy preview: ${e.message}", Toast.LENGTH_LONG).show()
                btnNext.isEnabled = true
                btnNext.text = "NEXT"
            }
        }
    }

    /**
     * Shows the "Copy Complete" dialog after the copy operation succeeds.
     * Wired up to the confirm action in ReviewImportActivity via isCopyPrices flag.
     */
    fun showCopyCompleteDialog(context: Context, onDone: () -> Unit) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_export_complete, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.85).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.findViewById<AppCompatButton>(R.id.btnDone).setOnClickListener {
            dialog.dismiss()
            onDone()
        }

        dialog.show()
    }
}
