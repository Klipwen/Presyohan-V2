package com.presyohan.app

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.*

class AiParsingDialogHelper(
    private val activity: Activity,
    private val coroutineScope: CoroutineScope,
    private val rawText: String,
    private val categoryIdByName: Map<String, String>,
    private val existingProductNames: Set<String>,
    private val onSuccess: (ParseResult) -> Unit,
    private val onCancel: () -> Unit = {}
) {
    private var dialog: Dialog? = null
    private var currentJob: Job? = null

    // UI elements
    private lateinit var btnClose: View
    private lateinit var tvTitle: TextView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var imgMascot: ImageView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnCancel: MaterialButton
    private lateinit var layoutErrorButtons: View
    private lateinit var btnRetry: MaterialButton
    private lateinit var btnUseBuiltIn: MaterialButton

    private enum class DialogState {
        LOADING,
        ERROR
    }

    fun show() {
        dialog = Dialog(activity).apply {
            val view = LayoutInflater.from(activity).inflate(R.layout.dialog_ai_parser, null)
            setContentView(view)
            setCancelable(false) // User must click close X or Cancel button to exit
            window?.setBackgroundDrawableResource(android.R.color.transparent)

            // Bind views
            btnClose = view.findViewById(R.id.btnClose)
            tvTitle = view.findViewById(R.id.dialogTitle)
            progressIndicator = view.findViewById(R.id.progressIndicator)
            imgMascot = view.findViewById(R.id.imgMascot)
            tvSubtitle = view.findViewById(R.id.dialogSubtitle)
            btnCancel = view.findViewById(R.id.btnCancel)
            layoutErrorButtons = view.findViewById(R.id.layoutErrorButtons)
            btnRetry = view.findViewById(R.id.btnRetry)
            btnUseBuiltIn = view.findViewById(R.id.btnUseBuiltIn)

            // Set up click listeners
            btnCancel.setOnClickListener {
                cancelParsing()
                dismiss()
                onCancel()
            }

            btnClose.setOnClickListener {
                cancelParsing()
                dismiss()
                onCancel()
            }

            btnRetry.setOnClickListener {
                startAiParsing()
            }

            btnUseBuiltIn.setOnClickListener {
                startBuiltInParsing()
            }

            // Adjust width programmatically to 95% of screen width to match other dialog designs
            val width = (activity.resources.displayMetrics.widthPixels * 0.95).toInt()
            window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            
            show()
        }

        // Start parsing initially
        startAiParsing()
    }

    private fun updateState(state: DialogState) {
        activity.runOnUiThread {
            when (state) {
                DialogState.LOADING -> {
                    btnClose.visibility = View.GONE
                    tvTitle.text = "I'm parsing your list, please wait..."
                    progressIndicator.visibility = View.VISIBLE
                    imgMascot.setImageResource(R.drawable.icon_happy_robot)
                    tvSubtitle.text = "Reading your list... this may take a moment."
                    btnCancel.visibility = View.VISIBLE
                    layoutErrorButtons.visibility = View.GONE
                }
                DialogState.ERROR -> {
                    btnClose.visibility = View.VISIBLE
                    tvTitle.text = "I've lost connection"
                    progressIndicator.visibility = View.GONE
                    imgMascot.setImageResource(R.drawable.icon_sad_robot)
                    tvSubtitle.text = "Sorry, I've lost connection with my server."
                    btnCancel.visibility = View.GONE
                    layoutErrorButtons.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun startAiParsing() {
        updateState(DialogState.LOADING)
        cancelParsing()

        currentJob = coroutineScope.launch {
            try {
                // Perform Gemini Parsing
                val result = withContext(Dispatchers.IO) {
                    GeminiParser.parseText(rawText, categoryIdByName, existingProductNames)
                }
                
                withContext(Dispatchers.Main) {
                    dialog?.dismiss()
                    onSuccess(result)
                }
            } catch (e: Exception) {
                android.util.Log.e("AiParsingDialogHelper", "Gemini parsing failed", e)
                if (isActive) {
                    updateState(DialogState.ERROR)
                }
            }
        }
    }

    private fun startBuiltInParsing() {
        updateState(DialogState.LOADING)
        // Update subtitle to let the user know we're using built-in parsing now
        tvSubtitle.text = "Parsing using standard rules..."
        cancelParsing()

        currentJob = coroutineScope.launch {
            try {
                // Simulate a brief delay (e.g. 800ms) for the loading state to be visible as requested
                delay(800)

                val result = withContext(Dispatchers.IO) {
                    AddMultipleItemsParser.parseTextToResult(rawText, existingProductNames)
                }

                withContext(Dispatchers.Main) {
                    dialog?.dismiss()
                    onSuccess(result)
                }
            } catch (e: Exception) {
                android.util.Log.e("AiParsingDialogHelper", "Built-in parsing failed", e)
                withContext(Dispatchers.Main) {
                    dialog?.dismiss()
                }
            }
        }
    }

    private fun cancelParsing() {
        currentJob?.cancel()
        currentJob = null
    }

    private fun dismiss() {
        dialog?.dismiss()
    }
}
