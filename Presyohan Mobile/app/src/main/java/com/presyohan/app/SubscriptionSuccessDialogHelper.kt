package com.presyohan.app

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat

object SubscriptionSuccessDialogHelper {

    /**
     * Displays the custom Presyohan Subscription Success Dialog.
     * Uses the authentic Presyohan dialog aesthetic (matching dialog_export_complete.xml).
     * The dynamic message constructs text using live capacity limits from the Admin configuration.
     */
    fun showProClaimSuccessDialog(
        context: Context,
        tierInfo: SubscriptionTierInfo,
        onDone: () -> Unit
    ) {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_export_complete, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTitle = view.findViewById<TextView>(R.id.textTitle)
        val tvMessage = view.findViewById<TextView>(R.id.textMessage)
        val btnDone = view.findViewById<AppCompatButton>(R.id.btnDone)

        // 1. Title
        tvTitle.text = "${tierInfo.name} Activated!"
        tvTitle.setTextColor(ContextCompat.getColor(context, R.color.presyo_orange))

        // 2. Dynamic Message (100% Data-Driven from Supabase subscription_tiers)
        val storesText = if (tierInfo.storeLimit >= 999999) "Unlimited stores" else "${tierInfo.storeLimit} stores"
        val itemsText = if (tierInfo.itemsPerStoreLimit >= 999999) "unlimited items" else "${tierInfo.itemsPerStoreLimit} items per store"
        val aiText = if (tierInfo.aiQuotaDaily >= 999999) "unlimited daily AI parses" else "${tierInfo.aiQuotaDaily} daily AI parses"

        val dynamicMessage = "You have successfully claimed the promotional ${tierInfo.name}. " +
                "Enjoy $storesText, $itemsText, and $aiText."

        tvMessage.text = dynamicMessage
        tvMessage.visibility = View.VISIBLE
        val params = tvTitle.layoutParams as LinearLayout.LayoutParams
        params.bottomMargin = (8 * context.resources.displayMetrics.density).toInt()
        tvTitle.layoutParams = params

        // 3. Pill Action Button
        btnDone.text = "Done"
        btnDone.backgroundTintList = ContextCompat.getColorStateList(context, R.color.presyo_teal)
        btnDone.setOnClickListener {
            dialog.dismiss()
            onDone()
        }

        dialog.show()
    }
}
