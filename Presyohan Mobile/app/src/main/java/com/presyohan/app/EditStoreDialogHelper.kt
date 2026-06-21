package com.presyohan.app

import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object EditStoreDialogHelper {

    fun showEditStoreDialog(
        activity: AppCompatActivity,
        storeId: String,
        currentName: String,
        currentBranch: String,
        currentType: String,
        onComplete: ((newName: String, newBranch: String, newType: String) -> Unit)? = null
    ) {
        val dialog = Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_edit_store, null)
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
        val layoutSpecificStoreType = view.findViewById<TextInputLayout>(R.id.layoutSpecificStoreType)
        val inputSpecificStoreType = view.findViewById<TextInputEditText>(R.id.inputSpecificStoreType)
        val buttonDone = view.findViewById<Button>(R.id.buttonDone)

        // Prefill values
        inputStoreName.setText(currentName)
        inputStoreBranch.setText(currentBranch)

        // Setup Dropdown Option Adapter
        val storeTypes = listOf("Grocery", "Pharmacy", "Laundry", "Other")
        val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, storeTypes)
        spinnerStoreType.setAdapter(adapter)

        // Determine selection and set state
        val typeNormalized = currentType.trim()
        if (storeTypes.any { it.equals(typeNormalized, ignoreCase = true) && it != "Other" }) {
            val matchingType = storeTypes.first { it.equals(typeNormalized, ignoreCase = true) }
            spinnerStoreType.setText(matchingType, false)
            layoutSpecificStoreType.visibility = View.GONE
        } else {
            spinnerStoreType.setText("Other", false)
            layoutSpecificStoreType.visibility = View.VISIBLE
            inputSpecificStoreType.setText(currentType)
        }

        // Handle dropdown selection change
        spinnerStoreType.setOnItemClickListener { _, _, position, _ ->
            val selected = storeTypes[position]
            if (selected == "Other") {
                layoutSpecificStoreType.visibility = View.VISIBLE
            } else {
                layoutSpecificStoreType.visibility = View.GONE
            }
        }

        btnBack.setOnClickListener {
            dialog.dismiss()
        }

        buttonDone.setOnClickListener {
            val nameVal = inputStoreName.text.toString().trim()
            val branchVal = inputStoreBranch.text.toString().trim()
            val selectedType = spinnerStoreType.text.toString().trim()
            val finalType = if (selectedType == "Other") {
                inputSpecificStoreType.text.toString().trim()
            } else {
                selectedType
            }

            if (nameVal.isEmpty()) {
                Toast.makeText(activity, "Store name cannot be empty.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedType == "Other" && finalType.isEmpty()) {
                Toast.makeText(activity, "Please specify the store type.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            activity.lifecycleScope.launch {
                try {
                    val updatePayload = buildJsonObject {
                        put("name", nameVal)
                        put("branch", branchVal)
                        put("type", finalType)
                    }
                    SupabaseProvider.client.postgrest["stores"].update(updatePayload) {
                        filter { eq("id", storeId) }
                    }
                    Toast.makeText(activity, "Store updated successfully.", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onComplete?.invoke(nameVal, branchVal, finalType)
                } catch (e: Exception) {
                    Toast.makeText(activity, "Failed to update store: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }
}
