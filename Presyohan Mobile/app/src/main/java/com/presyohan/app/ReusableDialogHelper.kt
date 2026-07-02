package com.presyohan.app

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatButton
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.auth

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

    fun showSuccessDialog(
        context: Context,
        isPasswordReset: Boolean,
        buttonText: String,
        action: () -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_success_template, null)
        dialog.setContentView(view)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ivIcon = view.findViewById<ImageView>(R.id.successIcon)
        val tvTitle = view.findViewById<TextView>(R.id.successTitle)
        val btnAction = view.findViewById<AppCompatButton>(R.id.btnAction)

        if (isPasswordReset) {
            ivIcon.setImageResource(R.drawable.icon_checkmark)
            tvTitle.text = "Password Successfully\nUpdated"
        } else {
            ivIcon.setImageResource(R.drawable.icon_account_created)
            tvTitle.text = "Account Created\nSuccessfully"
        }

        btnAction.text = buttonText
        btnAction.setOnClickListener {
            action.invoke()
            dialog.dismiss()
        }

        dialog.show()

        // Set width programmatically to 90% of screen width to prevent shrinking
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return dialog
    }

    fun isNetworkError(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is java.io.IOException ||
                cause is java.net.ConnectException ||
                cause is java.net.UnknownHostException ||
                cause is java.net.SocketTimeoutException ||
                cause is io.ktor.client.plugins.HttpRequestTimeoutException ||
                cause is io.ktor.client.network.sockets.ConnectTimeoutException
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    fun showConnectionLostDialog(
        context: Context,
        reloadAction: () -> Unit
    ): Dialog {
        return showCustomDialog(
            context = context,
            title = "Connection Lost",
            message = "Please check your internet connection and try again.",
            positiveButtonText = "Reload",
            positiveAction = reloadAction,
            negativeButtonText = "Close App",
            negativeAction = {
                (context as? android.app.Activity)?.finishAffinity()
            },
            isCancelable = false
        )
    }

    fun showBroadcastDialog(
        context: Context,
        title: String,
        body: String,
        buttonText: String,
        onClose: () -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_broadcast)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvBroadcastTitle)
        val tvBody = dialog.findViewById<TextView>(R.id.tvBroadcastBody)
        val btnAction = dialog.findViewById<TextView>(R.id.btnBroadcastAction)

        tvTitle.text = title
        tvBody.text = body
        btnAction.text = buttonText

        btnAction.setOnClickListener {
            dialog.dismiss()
            onClose()
        }

        dialog.show()

        // Set width programmatically to 90% of screen width to prevent shrinking
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return dialog
    }

    fun checkAndShowBroadcast(activity: android.app.Activity, scope: kotlinx.coroutines.CoroutineScope) {
        val prefs = activity.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        scope.launch {
            try {
                val activeAnnouncements = SupabaseProvider.client.postgrest.from("announcements")
                    .select {
                        filter {
                            eq("is_active", true)
                        }
                    }.decodeList<AnnouncementRow>()

                if (activeAnnouncements.isNotEmpty()) {
                    val latest = activeAnnouncements.maxByOrNull { it.created_at } ?: activeAnnouncements.first()
                    
                    val userId = SupabaseProvider.client.auth.currentSessionOrNull()?.user?.id ?: ""
                    val key = if (userId.isNotEmpty()) "last_seen_announcement_id_$userId" else "last_seen_announcement_id"
                    
                    val lastSeenId = prefs.getString(key, "")
                    if (latest.id != lastSeenId) {
                        showBroadcastDialog(activity, latest.title, latest.content, latest.button_label) {
                            prefs.edit().putString(key, latest.id).apply()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

@Serializable
data class AnnouncementRow(
    val id: String,
    val title: String,
    val content: String,
    val is_active: Boolean,
    val button_label: String = "Close",
    val created_at: String
)

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

fun Context.showSuccessDialog(
    isPasswordReset: Boolean,
    buttonText: String,
    action: () -> Unit
): Dialog {
    return ReusableDialogHelper.showSuccessDialog(
        context = this,
        isPasswordReset = isPasswordReset,
        buttonText = buttonText,
        action = action
    )
}

fun Context.showConnectionLostDialog(reloadAction: () -> Unit): Dialog {
    return ReusableDialogHelper.showConnectionLostDialog(this, reloadAction)
}
