package com.presyohan.app

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton

object ReusableDialogHelper {

    fun showCustomDialog(
        context: Context,
        title: String,
        message: String,
        positiveButtonText: String? = null,
        positiveAction: (() -> Unit)? = null,
        negativeButtonText: String? = null,
        negativeAction: (() -> Unit)? = null,
        isCancelable: Boolean = true
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_reusable_template, null)
        dialog.setContentView(view)
        dialog.setCancelable(isCancelable)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val tvTitle = view.findViewById<TextView>(R.id.dialogTitle)
        val tvMessage = view.findViewById<TextView>(R.id.dialogMessage)
        val btnPositive = view.findViewById<AppCompatButton>(R.id.btnPositive)
        val btnNegative = view.findViewById<AppCompatButton>(R.id.btnNegative)

        tvTitle.text = title
        tvMessage.text = message

        // Positive Button Setup
        if (!positiveButtonText.isNullOrEmpty()) {
            btnPositive.text = positiveButtonText
            btnPositive.visibility = View.VISIBLE
            btnPositive.setOnClickListener {
                positiveAction?.invoke()
                dialog.dismiss()
            }
        } else {
            btnPositive.visibility = View.GONE
        }

        // Negative Button Setup
        if (!negativeButtonText.isNullOrEmpty()) {
            btnNegative.text = negativeButtonText
            btnNegative.visibility = View.VISIBLE
            btnNegative.setOnClickListener {
                negativeAction?.invoke()
                dialog.dismiss()
            }
        } else {
            btnNegative.visibility = View.GONE
        }

        // Adjust constraints/weights if only one button is displayed
        if (positiveButtonText.isNullOrEmpty() || negativeButtonText.isNullOrEmpty()) {
            val container = btnPositive.parent as? LinearLayout
            if (container != null) {
                container.weightSum = 1f
                val activeBtn = if (btnPositive.visibility == View.VISIBLE) btnPositive else btnNegative
                val lp = activeBtn.layoutParams as? LinearLayout.LayoutParams
                if (lp != null) {
                    lp.width = LinearLayout.LayoutParams.WRAP_CONTENT
                    lp.weight = 0f
                    lp.marginStart = 0
                    lp.marginEnd = 0
                    activeBtn.layoutParams = lp
                }
            }
        }

        dialog.show()

        // Set width programmatically to 90% of screen width to prevent shrinking
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return dialog
    }
}

/**
 * Extension function on Context to easily display the reusable dialog.
 */
fun Context.showReusableDialog(
    title: String,
    message: String,
    positiveButtonText: String? = null,
    positiveAction: (() -> Unit)? = null,
    negativeButtonText: String? = null,
    negativeAction: (() -> Unit)? = null,
    isCancelable: Boolean = true
): Dialog {
    return ReusableDialogHelper.showCustomDialog(
        context = this,
        title = title,
        message = message,
        positiveButtonText = positiveButtonText,
        positiveAction = positiveAction,
        negativeButtonText = negativeButtonText,
        negativeAction = negativeAction,
        isCancelable = isCancelable
    )
}
