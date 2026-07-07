package com.presyohan.app

import android.app.Activity
import android.app.Dialog
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatButton
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
    private lateinit var skeletonLoader: SkeletonLoaderView
    private lateinit var imgMascot: ImageView
    private lateinit var tvSubtitle: TextView
    private lateinit var btnCancel: AppCompatButton
    private lateinit var layoutErrorButtons: View
    private lateinit var btnRetry: AppCompatButton
    private lateinit var btnUseBuiltIn: AppCompatButton
    
    private var mascotAnimator: ValueAnimator? = null
    private var textShimmerAnimator: ValueAnimator? = null
    private var textCycleJob: Job? = null

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
            skeletonLoader = view.findViewById(R.id.skeletonLoader)
            imgMascot = view.findViewById(R.id.imgMascot)
            tvSubtitle = view.findViewById(R.id.dialogSubtitle)
            btnCancel = view.findViewById(R.id.btnCancel)
            layoutErrorButtons = view.findViewById(R.id.layoutErrorButtons)
            btnRetry = view.findViewById(R.id.btnRetry)
            btnUseBuiltIn = view.findViewById(R.id.btnUseBuiltIn)

            setOnDismissListener {
                cancelParsing()
                stopAllAnimations()
            }

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
                    skeletonLoader.visibility = View.VISIBLE
                    skeletonLoader.resumeAnimations()
                    imgMascot.setImageResource(R.drawable.icon_happy_robot)
                    startMascotInfinityAnimation()
                    startTextCycling()
                    startTextShimmer()
                    btnCancel.visibility = View.VISIBLE
                    layoutErrorButtons.visibility = View.GONE
                }
                DialogState.ERROR -> {
                    btnClose.visibility = View.VISIBLE
                    tvTitle.text = "I've lost connection"
                    skeletonLoader.visibility = View.GONE
                    skeletonLoader.stopAnimations()
                    imgMascot.setImageResource(R.drawable.icon_sad_robot)
                    stopMascotInfinityAnimation()
                    stopTextCycling()
                    stopTextShimmer()
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

    private fun stopAllAnimations() {
        stopMascotInfinityAnimation()
        stopTextCycling()
        stopTextShimmer()
        if (::skeletonLoader.isInitialized) {
            skeletonLoader.stopAnimations()
        }
    }

    private fun startMascotInfinityAnimation() {
        mascotAnimator?.cancel()
        val density = activity.resources.displayMetrics.density
        val ampX = 56f * density
        val ampY = 20f * density
        
        mascotAnimator = ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
            duration = 5000 // Slow and smooth loop
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                val tx = ampX * Math.sin(t.toDouble()).toFloat()
                val ty = ampY * Math.sin(2.0 * t.toDouble()).toFloat()
                imgMascot.translationX = tx
                imgMascot.translationY = ty
            }
            start()
        }
    }

    private fun stopMascotInfinityAnimation() {
        mascotAnimator?.cancel()
        mascotAnimator = null
        imgMascot.translationX = 0f
        imgMascot.translationY = 0f
    }

    private fun startTextCycling() {
        textCycleJob?.cancel()
        val phrases = listOf(
            "Reading your list... this may take a moment.",
            "Analyzing details...",
            "Thinking...",
            "Almost there...",
            "Hold on a minute...",
            "Still working on it...",
            "Just a little longer..."
        )
        
        textCycleJob = coroutineScope.launch {
            var index = 0
            while (isActive) {
                val text = phrases[index]
                withContext(Dispatchers.Main) {
                    tvSubtitle.text = text
                }
                delay(4000) // Change message every 4 seconds
                index = (index + 1) % phrases.size
            }
        }
    }

    private fun stopTextCycling() {
        textCycleJob?.cancel()
        textCycleJob = null
    }

    private fun startTextShimmer() {
        textShimmerAnimator?.cancel()
        tvSubtitle.post {
            val width = tvSubtitle.width.toFloat()
            if (width <= 0) return@post

            val baseColor = tvSubtitle.currentTextColor
            // Mix base text color with a light/medium gray for shimmer highlight
            val highlightColor = Color.parseColor("#DDDDDD")
            
            val shader = LinearGradient(
                0f, 0f, width * 0.4f, 0f,
                intArrayOf(baseColor, highlightColor, baseColor),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            tvSubtitle.paint.shader = shader

            textShimmerAnimator = ValueAnimator.ofFloat(0f, width * 1.5f).apply {
                duration = 2500
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    val offset = animator.animatedValue as Float
                    val matrix = Matrix()
                    matrix.setTranslate(offset - width * 0.5f, 0f)
                    shader.setLocalMatrix(matrix)
                    tvSubtitle.invalidate()
                }
                start()
            }
        }
    }

    private fun stopTextShimmer() {
        textShimmerAnimator?.cancel()
        textShimmerAnimator = null
        tvSubtitle.paint.shader = null
        tvSubtitle.invalidate()
    }
}
