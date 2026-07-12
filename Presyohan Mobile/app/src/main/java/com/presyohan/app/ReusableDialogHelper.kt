package com.presyohan.app

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

@Serializable
data class SukiRelationshipRow(val store_id: String, val status: String = "active")

object ReusableDialogHelper {

    fun checkSukiAndInvite(
        context: Context,
        coroutineScope: kotlinx.coroutines.CoroutineScope,
        userId: String,
        userName: String,
        userEmail: String,
        storeId: String,
        selectedRoleValue: String,
        onStartInviting: () -> Unit,
        onInvitationSent: () -> Unit,
        onInvitationFailed: (String) -> Unit
    ) {
        coroutineScope.launch {
            try {
                // Check if user is suki on this store
                val isSuki = try {
                    val sukiList = SupabaseProvider.client.postgrest["suki_relationships"]
                        .select {
                            filter {
                                eq("user_id", userId)
                                eq("store_id", storeId)
                                eq("status", "active")
                            }
                        }
                        .decodeList<SukiRelationshipRow>()
                    sukiList.isNotEmpty()
                } catch (e: Exception) {
                    false
                }

                val proceedInvite = {
                    onStartInviting()
                    coroutineScope.launch {
                        try {
                            val params = buildJsonObject {
                                put("p_store_id", storeId)
                                put("p_email", userEmail)
                                put("p_role", selectedRoleValue)
                            }
                            SupabaseProvider.client.postgrest.rpc("send_store_invitation", params)
                            onInvitationSent()
                        } catch (e: Exception) {
                            onInvitationFailed(e.message ?: "Failed to invite.")
                        }
                    }
                }

                if (isSuki) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        showCustomDialog(
                            context = context,
                            title = "Confirm Invite",
                            message = "$userName is currently a Suki of your store. If they accept this staff invite, they will become a team member and will no longer be a Suki. Do you want to proceed?",
                            positiveButtonText = "Proceed",
                            positiveAction = {
                                proceedInvite()
                            },
                            negativeButtonText = "Cancel",
                            negativeAction = {
                                onInvitationFailed("")
                            }
                        )
                    }
                } else {
                    proceedInvite()
                }
            } catch (e: Exception) {
                onInvitationFailed(e.message ?: "Failed to invite.")
            }
        }
    }

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
            val msg = cause.message?.lowercase() ?: ""
            if (cause is java.io.IOException ||
                cause is java.net.ConnectException ||
                cause is java.net.UnknownHostException ||
                cause is java.net.SocketTimeoutException ||
                cause is io.ktor.client.plugins.HttpRequestTimeoutException ||
                cause is io.ktor.client.network.sockets.ConnectTimeoutException ||
                cause is io.ktor.client.plugins.ResponseException ||
                cause::class.java.simpleName.contains("HttpRequestException") ||
                cause::class.java.simpleName.contains("RestException") ||
                cause::class.java.simpleName.contains("ConnectException") ||
                cause::class.java.simpleName.contains("SocketException") ||
                cause::class.java.simpleName.contains("UnknownHostException") ||
                cause::class.java.simpleName.contains("TimeoutException") ||
                cause::class.java.name.contains("io.ktor") ||
                msg.contains("supabase.co") ||
                msg.contains("network") ||
                msg.contains("timeout") ||
                msg.contains("connect") ||
                msg.contains("unresolved address") ||
                msg.contains("http request") ||
                msg.contains("restexception") ||
                msg.contains("401") ||
                msg.contains("unauthorized")
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private var reloadCount = 0

    fun resetReloadCount() {
        reloadCount = 0
    }

    fun isSessionExpiredError(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            val msg = cause.message?.lowercase() ?: ""
            if (msg.contains("jwt") ||
                msg.contains("token") ||
                msg.contains("session") ||
                msg.contains("401") ||
                msg.contains("unauthorized") ||
                cause::class.java.simpleName.contains("AuthException") ||
                (cause::class.java.name.contains("Supabase") && (msg.contains("session") || msg.contains("auth")))
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    fun showRestartDialog(
        context: Context,
        title: String,
        body: String
    ): Dialog {
        return showBroadcastDialog(
            context = context,
            title = title,
            body = body,
            buttonText = "Restart"
        ) {
            val intent = Intent(context, SplashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            context.startActivity(intent)
            (context as? android.app.Activity)?.finish()
        }
    }

    fun handleNetworkError(
        activity: android.app.Activity,
        e: Throwable,
        reloadAction: () -> Unit
    ): Boolean {
        if (isSessionExpiredError(e)) {
            showRestartDialog(
                context = activity,
                title = "Session Expired",
                body = "Your session has expired or the server disconnected. Please restart the app (close the app and open again) to refresh your session."
            )
            return true
        }

        if (isNetworkError(e)) {
            reloadCount++
            if (reloadCount >= 5) {
                showRestartDialog(
                    context = activity,
                    title = "Failed to Load Data",
                    body = "We failed to load the data after multiple attempts. Please restart the app and try again."
                )
            } else {
                showConnectionLostDialog(activity, reloadAction)
            }
            return true
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
            message = "You have been disconnected from the server. Please check your internet connection or reload to reconnect.",
            positiveButtonText = "Reload",
            positiveAction = {
                reloadAction()
            },
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
        val simpleAnnouncement = AnnouncementRow(
            id = "simple_temp_id",
            title = title,
            content = body,
            is_active = true,
            button_label = buttonText,
            created_at = "",
            template_type = "simple"
        )
        val dummyScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
        return showBroadcastDialog(context, simpleAnnouncement, dummyScope, onClose)
    }

    fun showBroadcastDialog(
        context: Context,
        announcement: AnnouncementRow,
        scope: kotlinx.coroutines.CoroutineScope,
        onClose: () -> Unit
    ): Dialog {
        val dialog = Dialog(context)
        dialog.setContentView(R.layout.dialog_broadcast)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val preventDismiss = announcement.template_data?.get("prevent_dismiss")?.jsonPrimitive?.booleanOrNull == true
        val isForcedVersion = announcement.template_type == "version_check" &&
                announcement.template_data?.get("is_forced")?.jsonPrimitive?.booleanOrNull == true
        val isMaintenance = announcement.template_type == "maintenance"
        
        dialog.setCancelable(!isForcedVersion && !isMaintenance && !preventDismiss)

        val tvTitle = dialog.findViewById<TextView>(R.id.tvBroadcastTitle)
        val tvBody = dialog.findViewById<TextView>(R.id.tvBroadcastBody)
        val btnAction = dialog.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnBroadcastAction)
        val dynamicContent = dialog.findViewById<LinearLayout>(R.id.layoutDynamicContent)
        val actionButtons = dialog.findViewById<LinearLayout>(R.id.layoutActionButtons)

        tvTitle.text = announcement.title
        tvBody.text = announcement.content
        btnAction.text = announcement.button_label

        val buttonColor = announcement.template_data?.get("button_color")?.jsonPrimitive?.content ?: "teal"
        val resolvedColor = if (buttonColor.equals("orange", ignoreCase = true)) "#FB8500" else "#219EBC"
        btnAction.supportBackgroundTintList = ColorStateList.valueOf(Color.parseColor(resolvedColor))

        val dp = context.resources.displayMetrics.density

        fun makeBtn(label: String, colorHex: String, endMarginDp: Int = 0): androidx.appcompat.widget.AppCompatButton {
            return androidx.appcompat.widget.AppCompatButton(context).apply {
                text = label
                setTextColor(Color.WHITE)
                textSize = 15f
                isAllCaps = false
                setBackgroundResource(R.drawable.button_round)
                supportBackgroundTintList = ColorStateList.valueOf(Color.parseColor(colorHex))
                layoutParams = LinearLayout.LayoutParams(0, (48 * dp).toInt(), 1f).apply {
                    if (endMarginDp > 0) marginEnd = (endMarginDp * dp).toInt()
                }
            }
        }

        fun setActionRow(vararg buttons: android.view.View) {
            actionButtons.removeAllViews()
            buttons.forEach { actionButtons.addView(it) }
        }

        fun saveResponse(response: String) {
            val uid = SupabaseProvider.client.auth.currentSessionOrNull()?.user?.id ?: ""
            if (uid.isNotEmpty()) {
                scope.launch {
                    try {
                        val arr = kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive(response)))
                        SupabaseProvider.client.postgrest.from("poll_responses").insert(buildJsonObject {
                            put("announcement_id", announcement.id)
                            put("user_id", uid)
                            put("selected_options", arr)
                        })
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        when (announcement.template_type) {
            "two_buttons" -> {
                val btnNegativeLabel = announcement.template_data?.get("negative_label")?.jsonPrimitive?.content ?: "Cancel"
                val btnPositiveLabel = announcement.template_data?.get("positive_label")?.jsonPrimitive?.content ?: "OK"

                val btnNegative = makeBtn(btnNegativeLabel, "#219EBC", endMarginDp = 8).apply {
                    setOnClickListener {
                        saveResponse(btnNegativeLabel)
                        val action = announcement.template_data?.get("negative_action")?.jsonPrimitive?.content ?: ""
                        if (action.startsWith("http")) try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action))) } catch (e: Exception) { e.printStackTrace() }
                        dialog.dismiss(); onClose()
                    }
                }
                
                val btnPositive = makeBtn(btnPositiveLabel, "#FB8500").apply {
                    setOnClickListener {
                        saveResponse(btnPositiveLabel)
                        val action = announcement.template_data?.get("positive_action")?.jsonPrimitive?.content ?: ""
                        if (action.startsWith("http")) try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action))) } catch (e: Exception) { e.printStackTrace() }
                        dialog.dismiss(); onClose()
                    }
                }
                setActionRow(btnNegative, btnPositive)
            }
            "feedback" -> {
                val btnNo = makeBtn("No, I'm fine", "#219EBC", endMarginDp = 8).apply {
                    setOnClickListener {
                        saveResponse("No, I'm fine")
                        dialog.dismiss(); onClose()
                    }
                }
                val btnYes = makeBtn("Yes, I Have", "#FB8500").apply {
                    setOnClickListener {
                        saveResponse("Yes, I Have")
                        try { context.startActivity(Intent(context, ContactUsActivity::class.java)) } catch (e: Exception) { e.printStackTrace() }
                        dialog.dismiss(); onClose()
                    }
                }
                setActionRow(btnNo, btnYes)
            }
            "star_ratings" -> {
                var selectedRating = 0
                val starsRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (12 * dp).toInt() }
                }
                val starViews = List(5) { i ->
                    ImageView(context).apply {
                        setImageResource(R.drawable.ic_star_empty)
                        layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                            if (i < 4) marginEnd = (6 * dp).toInt()
                        }
                    }
                }
                fun refreshStars(r: Int) {
                    selectedRating = r
                    starViews.forEachIndexed { i, v ->
                        v.setImageResource(if (i < r) R.drawable.ic_star_filled else R.drawable.ic_star_empty)
                    }
                }
                starViews.forEachIndexed { idx, v -> v.setOnClickListener { refreshStars(idx + 1) }; starsRow.addView(v) }
                dynamicContent.addView(starsRow)

                val edtComment = EditText(context).apply {
                    hint = "Write an optional comment..."
                    setHintTextColor(Color.parseColor("#94A3B8"))
                    setTextColor(Color.parseColor("#1E293B"))
                    setBackgroundResource(R.drawable.bg_edittext)
                    setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (44 * dp).toInt()
                    ).apply { topMargin = (12 * dp).toInt() }
                }
                dynamicContent.addView(edtComment)

                val btnLater = makeBtn("Rate Later", "#219EBC", endMarginDp = 8).apply {
                    setOnClickListener { dialog.dismiss(); onClose() }
                }
                 val btnSubmit = makeBtn("Submit Rate", "#FB8500").apply {
                    setOnClickListener {
                        if (selectedRating == 0) {
                            Toast.makeText(context, "Please select at least 1 star.", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val commentText = edtComment.text.toString().trim().ifBlank { null }
                        scope.launch {
                            try {
                                val success = SupabaseAuthService.upsertUserRating(selectedRating, commentText)
                                if (success && context is android.app.Activity) {
                                    val userId = SupabaseProvider.client.auth.currentSessionOrNull()?.user?.id ?: ""
                                    context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
                                        .edit().putBoolean("has_rated_app_$userId", true).apply()
                                    context.runOnUiThread {
                                        ClonePricesDialogHelper.showCloneCompleteDialog(context, "Feedback Saved",
                                            "Your rating has been submitted successfully. Thank you for your support!", "Close") {}
                                    }
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        dialog.dismiss(); onClose()
                    }
                }
                setActionRow(btnLater, btnSubmit)
            }
            "poll" -> {
                val pollOptions = announcement.template_data?.get("poll_options")?.jsonArray
                val allowMultiselect = announcement.template_data?.get("allow_multiselect")?.jsonPrimitive?.booleanOrNull == true

                val pollContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (12 * dp).toInt() }
                }
                val checkBoxes = mutableListOf<CheckBox>()
                var radioGroup: RadioGroup? = null

                if (allowMultiselect) {
                    pollOptions?.forEach { opt ->
                        val cb = CheckBox(context).apply {
                            text = opt.jsonPrimitive.content
                            setTextColor(Color.parseColor("#475569"))
                            textSize = 14f
                            buttonTintList = ColorStateList.valueOf(Color.parseColor("#FB8500"))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = (6 * dp).toInt() }
                        }
                        checkBoxes.add(cb); pollContainer.addView(cb)
                    }
                } else {
                    radioGroup = RadioGroup(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }
                    pollOptions?.forEach { opt ->
                        radioGroup.addView(RadioButton(context).apply {
                            text = opt.jsonPrimitive.content
                            setTextColor(Color.parseColor("#475569"))
                            textSize = 14f
                            buttonTintList = ColorStateList.valueOf(Color.parseColor("#FB8500"))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { bottomMargin = (6 * dp).toInt() }
                        })
                    }
                    pollContainer.addView(radioGroup)
                }
                dynamicContent.addView(pollContainer)

                val btnVote = makeBtn(announcement.button_label.ifBlank { "Submit Vote" }, resolvedColor).apply {
                    setOnClickListener {
                        val selectedList = mutableListOf<String>()
                        if (allowMultiselect) {
                            checkBoxes.filter { it.isChecked }.forEach { selectedList.add(it.text.toString()) }
                        } else {
                            val cId = radioGroup?.checkedRadioButtonId ?: -1
                            if (cId != -1) radioGroup?.findViewById<RadioButton>(cId)?.text?.toString()?.let { selectedList.add(it) }
                        }
                        if (selectedList.isEmpty()) {
                            Toast.makeText(context, "Please select an option before voting.", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        val uid = SupabaseProvider.client.auth.currentSessionOrNull()?.user?.id ?: ""
                        if (uid.isNotEmpty()) {
                            scope.launch {
                                try {
                                    val arr = kotlinx.serialization.json.JsonArray(selectedList.map { JsonPrimitive(it) })
                                    SupabaseProvider.client.postgrest.from("poll_responses").insert(buildJsonObject {
                                        put("announcement_id", announcement.id)
                                        put("user_id", uid)
                                        put("selected_options", arr)
                                    })
                                } catch (e: Exception) { e.printStackTrace() }
                            }
                        }
                        dialog.dismiss(); onClose()
                    }
                }
                setActionRow(btnVote)
            }
            "wizard" -> {
                class WizardStep(
                    val title: String,
                    val content: String,
                    val inputType: String = "none",
                    val inputOptions: List<String> = emptyList(),
                    val placeholder: String = "",
                    val required: Boolean = false
                )

                val steps = announcement.template_data?.get("steps")?.jsonArray
                val stepsList = mutableListOf<WizardStep>()
                steps?.forEach { elem ->
                    val obj = elem.jsonObject
                    stepsList.add(WizardStep(
                        obj["title"]?.jsonPrimitive?.content ?: "",
                        obj["content"]?.jsonPrimitive?.content ?: "",
                        obj["input_type"]?.jsonPrimitive?.content ?: "none",
                        obj["input_options"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
                        obj["input_placeholder"]?.jsonPrimitive?.content ?: "",
                        obj["input_required"]?.jsonPrimitive?.booleanOrNull ?: false
                    ))
                }
                if (stepsList.isEmpty()) stepsList.add(WizardStep(announcement.title, announcement.content))

                var currentStep = 0
                val answers = mutableMapOf<Int, List<String>>()

                val stepperLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = (12 * dp).toInt(); bottomMargin = (4 * dp).toInt() }
                }

                val stepInputsLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                dynamicContent.addView(stepInputsLayout)
                dynamicContent.addView(stepperLayout)

                fun refreshStepper(step: Int) {
                    stepperLayout.removeAllViews()
                    if (stepsList.size <= 1) {
                        stepperLayout.visibility = View.GONE
                        return
                    } else {
                        stepperLayout.visibility = View.VISIBLE
                    }
                    for (i in 0 until stepsList.size) {
                        stepperLayout.addView(View(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, (8 * dp).toInt(), 1f).apply {
                                if (i < stepsList.size - 1) marginEnd = (10 * dp).toInt()
                            }
                            val shape = android.graphics.drawable.GradientDrawable().apply {
                                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                                cornerRadius = 4 * dp
                                val colorHex = when {
                                    i <= step -> "#EAB308"
                                    else -> "#E2E8F0"
                                }
                                setColor(Color.parseColor(colorHex))
                            }
                            background = shape
                        })
                    }
                }

                fun renderStepInput(stepIdx: Int, stepObj: WizardStep, container: LinearLayout) {
                    container.removeAllViews()
                    when (stepObj.inputType) {
                        "text" -> container.addView(android.widget.EditText(context).apply {
                            hint = stepObj.placeholder.ifEmpty { "Enter response..." }
                            setTextColor(Color.parseColor("#1E293B"))
                            setHintTextColor(Color.parseColor("#94A3B8"))
                            setBackgroundResource(R.drawable.bg_edittext)
                            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
                            setText(answers[stepIdx]?.firstOrNull() ?: "")
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        })
                        "choose" -> {
                            val rg = android.widget.RadioGroup(context).apply {
                                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                            }
                            val prev = answers[stepIdx]?.firstOrNull() ?: ""
                            stepObj.inputOptions.forEach { opt ->
                                rg.addView(android.widget.RadioButton(context).apply {
                                    text = opt
                                    setTextColor(Color.parseColor("#475569"))
                                    buttonTintList = ColorStateList.valueOf(Color.parseColor("#219EBC"))
                                    isChecked = (opt == prev)
                                })
                            }
                            container.addView(rg)
                        }
                        "select" -> {
                            val prevList = answers[stepIdx] ?: emptyList()
                            stepObj.inputOptions.forEach { opt ->
                                container.addView(android.widget.CheckBox(context).apply {
                                    text = opt
                                    setTextColor(Color.parseColor("#475569"))
                                    buttonTintList = ColorStateList.valueOf(Color.parseColor("#219EBC"))
                                    isChecked = prevList.contains(opt)
                                })
                            }
                        }
                    }
                }

                fun saveCurrentStepAnswer(): Boolean {
                    val stepObj = stepsList[currentStep]
                    val cur = mutableListOf<String>()
                    when (stepObj.inputType) {
                        "text" -> {
                            val txt = (stepInputsLayout.getChildAt(0) as? android.widget.EditText)?.text?.toString()?.trim() ?: ""
                            if (stepObj.required && txt.isEmpty()) {
                                Toast.makeText(context, "${stepObj.title} response is required", Toast.LENGTH_SHORT).show(); return false
                            }
                            if (txt.isNotEmpty()) cur.add(txt)
                        }
                        "choose" -> {
                            val rg = stepInputsLayout.getChildAt(0) as? android.widget.RadioGroup
                            val cId = rg?.checkedRadioButtonId ?: -1
                            if (cId != -1) rg?.findViewById<android.widget.RadioButton>(cId)?.text?.toString()?.let { cur.add(it) }
                            if (stepObj.required && cur.isEmpty()) {
                                Toast.makeText(context, "Selection is required", Toast.LENGTH_SHORT).show(); return false
                            }
                        }
                        "select" -> {
                            for (i in 0 until stepInputsLayout.childCount) {
                                val cb = stepInputsLayout.getChildAt(i) as? android.widget.CheckBox
                                if (cb?.isChecked == true) cur.add(cb.text.toString())
                            }
                            if (stepObj.required && cur.isEmpty()) {
                                Toast.makeText(context, "Selection is required", Toast.LENGTH_SHORT).show(); return false
                            }
                        }
                    }
                    answers[currentStep] = cur; return true
                }

                val btnBack = makeBtn("Close", "#219EBC", endMarginDp = 8)
                val btnNext = makeBtn(if (stepsList.size > 1) "Next" else announcement.button_label.ifBlank { "Close" }, "#FB8500")

                fun updateWizardUI(step: Int) {
                    currentStep = step
                    val stepObj = stepsList[step]
                    tvTitle.text = stepObj.title.ifEmpty { announcement.title }
                    tvBody.text = stepObj.content.ifEmpty { announcement.content }
                    
                    if (stepsList.size <= 1) {
                        btnBack.visibility = View.GONE
                        btnNext.text = announcement.button_label.ifBlank { "Close" }
                        btnNext.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (48 * dp).toInt())
                    } else {
                        btnNext.layoutParams = LinearLayout.LayoutParams(0, (48 * dp).toInt(), 1f)
                        if (step == 0) {
                            btnBack.text = "Close"
                            btnBack.visibility = if (preventDismiss) View.GONE else View.VISIBLE
                        } else {
                            btnBack.text = "Back"
                            btnBack.visibility = View.VISIBLE
                        }
                        btnNext.text = if (step == stepsList.size - 1) announcement.button_label.ifBlank { "Finish" } else "Next"
                    }
                    refreshStepper(step)
                    renderStepInput(step, stepObj, stepInputsLayout)
                }

                btnBack.setOnClickListener {
                    if (currentStep == 0) { dialog.dismiss(); onClose() }
                    else if (saveCurrentStepAnswer()) updateWizardUI(currentStep - 1)
                }
                btnNext.setOnClickListener {
                    if (saveCurrentStepAnswer()) {
                        if (currentStep < stepsList.size - 1) {
                            updateWizardUI(currentStep + 1)
                        } else {
                            val uid = SupabaseProvider.client.auth.currentSessionOrNull()?.user?.id ?: ""
                            if (uid.isNotEmpty() && answers.values.any { it.isNotEmpty() }) {
                                scope.launch {
                                    try {
                                        val listAnswers = mutableListOf<String>()
                                        for (i in 0 until stepsList.size) {
                                            val ansList = answers[i]
                                            if (!ansList.isNullOrEmpty()) listAnswers.add("${stepsList[i].title}: ${ansList.joinToString(", ")}")
                                        }
                                        val arr = kotlinx.serialization.json.JsonArray(listAnswers.map { JsonPrimitive(it) })
                                        SupabaseProvider.client.postgrest.from("poll_responses").insert(buildJsonObject {
                                            put("announcement_id", announcement.id)
                                            put("user_id", uid)
                                            put("selected_options", arr)
                                        })
                                    } catch (e: Exception) { e.printStackTrace() }
                                }
                            }
                            dialog.dismiss(); onClose()
                        }
                    }
                }
                setActionRow(btnBack, btnNext)
                updateWizardUI(0)
            }
            "version_check" -> {
                // Hide base header title and body to render custom layout inside card
                tvTitle.visibility = View.GONE
                tvBody.visibility = View.GONE

                // Rocket Image
                val ivRocket = ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((80 * dp).toInt(), (80 * dp).toInt()).apply {
                        gravity = android.view.Gravity.CENTER_HORIZONTAL
                        bottomMargin = (8 * dp).toInt()
                    }
                    setImageResource(R.drawable.icon_rocket)
                    imageTintList = ColorStateList.valueOf(Color.parseColor("#FB8500"))
                }
                dynamicContent.addView(ivRocket)

                // Title: New Version
                val tvCustomTitle = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER_HORIZONTAL
                        bottomMargin = (4 * dp).toInt()
                    }
                    text = "New Version"
                    setTextColor(Color.parseColor("#1F2937")) // Gray 800
                    textSize = 18f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                dynamicContent.addView(tvCustomTitle)

                // Version Tag: vX.Y.Z
                val versionName = announcement.template_data?.get("version_name")?.jsonPrimitive?.content ?: "1.0.0"
                val tvCustomVersion = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.CENTER_HORIZONTAL
                        bottomMargin = (12 * dp).toInt()
                    }
                    text = if (versionName.startsWith("v")) versionName else "v$versionName"
                    setTextColor(Color.parseColor("#219EBC")) // Teal
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                dynamicContent.addView(tvCustomVersion)

                // Whats New Header: Whats New?
                val tvWhatsNewTitle = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = (6 * dp).toInt()
                    }
                    text = "Whats New?"
                    setTextColor(Color.parseColor("#219EBC")) // Teal
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                dynamicContent.addView(tvWhatsNewTitle)

                // Whats New bullet lists
                val whatsNewText = announcement.template_data?.get("whats_new")?.jsonPrimitive?.content ?: ""
                val formattedWhatsNew = if (whatsNewText.isNotEmpty()) {
                    if (whatsNewText.contains("\n") || whatsNewText.startsWith("•") || whatsNewText.startsWith("-")) {
                        whatsNewText
                    } else {
                        "• $whatsNewText"
                    }
                } else {
                    "• Bug fixes and performance improvements."
                }

                val tvWhatsNewContent = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = formattedWhatsNew
                    setTextColor(Color.parseColor("#475569")) // Slate 600
                    textSize = 12f
                    setLineSpacing(0f, 1.2f)
                }
                dynamicContent.addView(tvWhatsNewContent)

                val btnUpdate = makeBtn("Update Now", "#FB8500").apply {
                    setOnClickListener {
                        val url = announcement.template_data?.get("download_url")?.jsonPrimitive?.content ?: ""
                        if (url.startsWith("http")) try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { e.printStackTrace() }
                        if (!isForcedVersion) { dialog.dismiss(); onClose() }
                    }
                }
                if (!isForcedVersion) {
                    val btnLater = makeBtn("Later", "#219EBC", endMarginDp = 8).apply {
                        setOnClickListener { dialog.dismiss(); onClose() }
                    }
                    setActionRow(btnLater, btnUpdate)
                } else {
                    setActionRow(btnUpdate)
                }
            }
            "maintenance" -> {
                val isPersistent = announcement.template_data?.get("persist_on_every_open")?.jsonPrimitive?.booleanOrNull == true
                
                val endAtStr = announcement.end_at
                if (!endAtStr.isNullOrEmpty()) {
                    try {
                        val triggerTime = java.time.OffsetDateTime.parse(endAtStr).toInstant().toEpochMilli()
                        val nowTime = System.currentTimeMillis()
                        val diff = triggerTime - nowTime
                        if (diff > 0) {
                            val tvCountdown = TextView(context).apply {
                                textSize = 14f
                                gravity = android.view.Gravity.CENTER
                                setTextColor(Color.parseColor("#EA580C"))
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { topMargin = (16 * dp).toInt() }
                            }
                            dynamicContent.addView(tvCountdown)
                            
                            val timer = object : android.os.CountDownTimer(diff, 1000) {
                                override fun onTick(millisUntilFinished: Long) {
                                    val minutes = (millisUntilFinished / 1000) / 60
                                    val seconds = (millisUntilFinished / 1000) % 60
                                    tvCountdown.text = "System is under maintenance. Please check back in ${minutes}m ${seconds}s"
                                }
                                override fun onFinish() {
                                    tvCountdown.text = "Maintenance finished."
                                    if (!isPersistent) {
                                        val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
                                        val userId = SupabaseProvider.client.auth.currentSessionOrNull()?.user?.id ?: ""
                                        val guestSeen = prefs.getStringSet("seen_announcement_ids_", emptySet())?.toMutableSet() ?: mutableSetOf()
                                        guestSeen.add(announcement.id)
                                        val edit = prefs.edit().putStringSet("seen_announcement_ids_", guestSeen)
                                        if (userId.isNotEmpty()) {
                                            val userSeen = prefs.getStringSet("seen_announcement_ids_$userId", emptySet())?.toMutableSet() ?: mutableSetOf()
                                            userSeen.add(announcement.id)
                                            edit.putStringSet("seen_announcement_ids_$userId", userSeen)
                                        }
                                        edit.apply()
                                    }
                                    dialog.dismiss()
                                    onClose()
                                }
                            }
                            timer.start()
                            dialog.setOnDismissListener { timer.cancel() }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                setActionRow(makeBtn("Close App", "#219EBC").apply {
                    setOnClickListener {
                        if (!isPersistent) {
                            val prefs = context.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
                            val userId = SupabaseProvider.client.auth.currentSessionOrNull()?.user?.id ?: ""
                            val guestSeen = prefs.getStringSet("seen_announcement_ids_", emptySet())?.toMutableSet() ?: mutableSetOf()
                            guestSeen.add(announcement.id)
                            val edit = prefs.edit().putStringSet("seen_announcement_ids_", guestSeen)
                            if (userId.isNotEmpty()) {
                                val userSeen = prefs.getStringSet("seen_announcement_ids_$userId", emptySet())?.toMutableSet() ?: mutableSetOf()
                                userSeen.add(announcement.id)
                                edit.putStringSet("seen_announcement_ids_$userId", userSeen)
                            }
                            edit.commit()
                        }
                        (context as? android.app.Activity)?.finishAffinity()
                        System.exit(0)
                    }
                })
            }
            else -> {
                btnAction.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (48 * dp).toInt())
                btnAction.setOnClickListener { dialog.dismiss(); onClose() }
                setActionRow(btnAction)
            }
        }

        dialog.show()

        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        return dialog
    }

    private var isRealtimeSubscribed = false

    fun checkAndShowBroadcast(activity: android.app.Activity, scope: kotlinx.coroutines.CoroutineScope) {
        val prefs = activity.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        val userId = SupabaseProvider.client.auth.currentSessionOrNull()?.user?.id ?: ""
        
        if (userId.isNotEmpty()) {
            val firstLoginKey = "first_login_at_$userId"
            if (!prefs.contains(firstLoginKey)) {
                prefs.edit().putLong(firstLoginKey, System.currentTimeMillis()).apply()
            }
        }

        val lastShown = prefs.getLong("last_announcement_shown_at", 0L)
        val now = System.currentTimeMillis()
        // TODO: Re-enable 5-min cooldown after testing
        // if (now - lastShown < 300000) {
        //     return
        // }

        if (!isRealtimeSubscribed) {
            setupRealtimeListener(activity, scope)
        }

        scope.launch {
            try {
                val activeAnnouncements = if (userId.isNotEmpty()) {
                    SupabaseProvider.client.postgrest.rpc(
                        "get_active_announcements",
                        buildJsonObject { put("p_user_id", userId) }
                    ).decodeList<AnnouncementRow>()
                } else {
                    SupabaseProvider.client.postgrest.from("announcements")
                        .select {
                            filter {
                                eq("is_active", true)
                                eq("targeting_type", "all")
                            }
                        }.decodeList<AnnouncementRow>()
                }

                if (activeAnnouncements.isNotEmpty()) {
                    val seenIds = prefs.getStringSet("seen_announcement_ids_$userId", emptySet()) ?: emptySet()
                    val guestSeenIds = prefs.getStringSet("seen_announcement_ids_", emptySet()) ?: emptySet()
                    
                    val filtered = activeAnnouncements.filter { announcement ->
                        val endAtStr = announcement.end_at
                        if (announcement.template_type == "maintenance" && !endAtStr.isNullOrEmpty()) {
                            try {
                                val triggerTime = java.time.OffsetDateTime.parse(endAtStr).toInstant().toEpochMilli()
                                if (System.currentTimeMillis() >= triggerTime) {
                                    return@filter false // Expired!
                                }
                            } catch (e: Exception) {}
                        }

                        val ignoreCooldown = announcement.template_data?.get("ignore_new_user_cooldown")?.jsonPrimitive?.booleanOrNull == true
                        if (!ignoreCooldown && announcement.template_type != "maintenance") {
                            if (userId.isNotEmpty()) {
                                val firstLoginAt = prefs.getLong("first_login_at_$userId", 0L)
                                if (firstLoginAt > 0 && (System.currentTimeMillis() - firstLoginAt) < 300000) {
                                    return@filter false // Less than 5 mins cooldown!
                                }
                            }
                        }

                        val isPersistentMaintenance = announcement.template_type == "maintenance" &&
                            announcement.template_data?.get("persist_on_every_open")?.jsonPrimitive?.booleanOrNull == true
                        if (isPersistentMaintenance) {
                            true // Always show persistent maintenance — skip seen check
                        } else if (announcement.template_type == "star_ratings" && prefs.getBoolean("has_rated_app_$userId", false)) {
                            val nextSeen = seenIds.toMutableSet()
                            nextSeen.add(announcement.id)
                            prefs.edit().putStringSet("seen_announcement_ids_$userId", nextSeen).apply()
                            false
                        } else {
                            announcement.id !in seenIds && announcement.id !in guestSeenIds
                        }
                    }

                    if (filtered.isNotEmpty()) {
                        val announcementToShow = filtered.maxByOrNull { it.created_at } ?: filtered.first()
                        
                        activity.runOnUiThread {
                            showBroadcastDialog(activity, announcementToShow, scope) {
                                val isPersistentMaintenance = announcementToShow.template_type == "maintenance" &&
                                    announcementToShow.template_data?.get("persist_on_every_open")?.jsonPrimitive?.booleanOrNull == true
                                if (!isPersistentMaintenance) {
                                    val nextSeen = seenIds.toMutableSet()
                                    nextSeen.add(announcementToShow.id)
                                    val guestSeen = guestSeenIds.toMutableSet()
                                    guestSeen.add(announcementToShow.id)
                                    prefs.edit()
                                        .putStringSet("seen_announcement_ids_$userId", nextSeen)
                                        .putStringSet("seen_announcement_ids_", guestSeen)
                                        .putLong("last_announcement_shown_at", System.currentTimeMillis())
                                        .apply()
                                } else {
                                    prefs.edit()
                                        .putLong("last_announcement_shown_at", System.currentTimeMillis())
                                        .apply()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupRealtimeListener(activity: android.app.Activity, scope: kotlinx.coroutines.CoroutineScope) {
        try {
            isRealtimeSubscribed = true
            val channel = SupabaseProvider.client.realtime.channel("announcements-channel")
            val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "announcements"
            }
            changeFlow.onEach { action ->
                if (action is PostgresAction.Insert || action is PostgresAction.Update) {
                    activity.runOnUiThread {
                        checkAndShowBroadcast(activity, scope)
                    }
                }
            }.launchIn(scope)
            scope.launch {
                channel.subscribe()
            }
        } catch (e: Exception) {
            isRealtimeSubscribed = false
            e.printStackTrace()
        }
    }

    /**
     * Pre-auth maintenance broadcast check for Splash & Login screens.
     * Fetches only active maintenance announcements with show_before_auth=true and targeting_type='all'.
     * Does NOT require a logged-in user. Does NOT track seen IDs.
     */
    fun checkAndShowMaintenanceBroadcast(activity: android.app.Activity, scope: kotlinx.coroutines.CoroutineScope) {
        val prefs = activity.getSharedPreferences("presyo_prefs", Context.MODE_PRIVATE)
        val userId = SupabaseProvider.client.auth.currentSessionOrNull()?.user?.id ?: ""
        
        scope.launch {
            try {
                val allActiveAnnouncements = SupabaseProvider.client.postgrest.from("announcements")
                    .select {
                        filter {
                            eq("is_active", true)
                            eq("template_type", "maintenance")
                            eq("targeting_type", "all")
                        }
                    }.decodeList<AnnouncementRow>()

                // Only show announcements that have show_before_auth enabled
                val maintenanceAnnouncement = allActiveAnnouncements.firstOrNull { announcement ->
                    announcement.template_data?.get("show_before_auth")?.jsonPrimitive?.booleanOrNull == true
                } ?: return@launch

                val endAtStr = maintenanceAnnouncement.end_at
                if (!endAtStr.isNullOrEmpty()) {
                    try {
                        val triggerTime = java.time.OffsetDateTime.parse(endAtStr).toInstant().toEpochMilli()
                        if (System.currentTimeMillis() >= triggerTime) {
                            return@launch // Expired!
                        }
                    } catch (e: Exception) {}
                }

                // Check if seen list contains it (either user specific seen list or guest seen list)
                val seenIds = prefs.getStringSet("seen_announcement_ids_$userId", emptySet()) ?: emptySet()
                val guestSeenIds = prefs.getStringSet("seen_announcement_ids_", emptySet()) ?: emptySet()
                
                val isPersistent = maintenanceAnnouncement.template_data?.get("persist_on_every_open")?.jsonPrimitive?.booleanOrNull == true
                if (!isPersistent && (maintenanceAnnouncement.id in seenIds || maintenanceAnnouncement.id in guestSeenIds)) {
                    return@launch
                }

                activity.runOnUiThread {
                    showBroadcastDialog(activity, maintenanceAnnouncement, scope) {
                        // Mark as seen if not persistent
                        if (!isPersistent) {
                            val guestSeen = prefs.getStringSet("seen_announcement_ids_", emptySet())?.toMutableSet() ?: mutableSetOf()
                            guestSeen.add(maintenanceAnnouncement.id)
                            val edit = prefs.edit().putStringSet("seen_announcement_ids_", guestSeen)
                            if (userId.isNotEmpty()) {
                                val userSeen = prefs.getStringSet("seen_announcement_ids_$userId", emptySet())?.toMutableSet() ?: mutableSetOf()
                                userSeen.add(maintenanceAnnouncement.id)
                                edit.putStringSet("seen_announcement_ids_$userId", userSeen)
                            }
                            edit.apply()
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
    val created_at: String,
    val template_type: String = "simple",
    val template_data: kotlinx.serialization.json.JsonObject? = null,
    val targeting_type: String = "all",
    val target_roles: List<String>? = null,
    val target_store_id: String? = null,
    val target_user_id: String? = null,
    val start_at: String? = null,
    val end_at: String? = null,
    val recurrence_pattern: String? = null
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
