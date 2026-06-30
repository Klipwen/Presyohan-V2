package com.presyohan.app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class OnboardingActivity : AppCompatActivity() {

    private enum class UserRole { STORE, CUSTOMER, NONE }
    private enum class StoreAction { CREATE, JOIN, NONE }
    private enum class SukiPreference { YES, NO, NONE }

    private var currentStep = 1
    private var selectedRole = UserRole.NONE
    private var selectedStoreAction = StoreAction.NONE
    private var selectedSukiPref = SukiPreference.NONE

    // Step containers
    private lateinit var step1Container: LinearLayout
    private lateinit var step2StoreContainer: LinearLayout
    private lateinit var step2CustomerContainer: LinearLayout
    private lateinit var step3SuccessContainer: LinearLayout

    // Step 1 Cards
    private lateinit var cardRoleStore: LinearLayout
    private lateinit var cardRoleCustomer: LinearLayout

    // Step 2 Store Cards
    private lateinit var cardStoreCreate: LinearLayout
    private lateinit var cardStoreJoin: LinearLayout

    // Step 2 Customer Cards
    private lateinit var cardSukiYes: LinearLayout
    private lateinit var cardSukiNo: LinearLayout

    // Success elements
    private lateinit var successMessage: TextView
    private lateinit var ceoAvatar: ImageView

    // Bottom progress views
    private lateinit var indicatorStep1: View
    private lateinit var indicatorStep2: View
    private lateinit var indicatorStep3: View

    // Navigation buttons
    private lateinit var btnBack: Button
    private lateinit var btnNext: Button

    // Loading State
    private lateinit var loadingDotsContainer: LinearLayout
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View
    private var loadingAnimators = mutableListOf<android.animation.Animator>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        // Make activity full screen (hide status bar)
        try {
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                window.insetsController?.hide(android.view.WindowInsets.Type.statusBars())
            } else {
                window.setFlags(
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize Views
        step1Container = findViewById(R.id.step1Container)
        step2StoreContainer = findViewById(R.id.step2StoreContainer)
        step2CustomerContainer = findViewById(R.id.step2CustomerContainer)
        step3SuccessContainer = findViewById(R.id.step3SuccessContainer)

        cardRoleStore = findViewById(R.id.cardRoleStore)
        cardRoleCustomer = findViewById(R.id.cardRoleCustomer)

        cardStoreCreate = findViewById(R.id.cardStoreCreate)
        cardStoreJoin = findViewById(R.id.cardStoreJoin)

        cardSukiYes = findViewById(R.id.cardSukiYes)
        cardSukiNo = findViewById(R.id.cardSukiNo)

        successMessage = findViewById(R.id.successMessage)
        ceoAvatar = findViewById(R.id.ceoAvatar)

        indicatorStep1 = findViewById(R.id.indicatorStep1)
        indicatorStep2 = findViewById(R.id.indicatorStep2)
        indicatorStep3 = findViewById(R.id.indicatorStep3)

        loadingDotsContainer = findViewById(R.id.loadingDotsContainer)
        dot1 = findViewById(R.id.dot1)
        dot2 = findViewById(R.id.dot2)
        dot3 = findViewById(R.id.dot3)

        btnBack = findViewById(R.id.btnBack)
        btnNext = findViewById(R.id.btnNext)

        btnBack.visibility = View.GONE
        setNextButtonEnabled(false)

        // Step 1 Card click listeners
        cardRoleStore.setOnClickListener {
            selectedRole = UserRole.STORE
            updateStep1CardStyles()
            animateCardSelection(cardRoleStore)
            animateCardDeselection(cardRoleCustomer)
            setNextButtonEnabled(true)
        }
        cardRoleCustomer.setOnClickListener {
            selectedRole = UserRole.CUSTOMER
            updateStep1CardStyles()
            animateCardSelection(cardRoleCustomer)
            animateCardDeselection(cardRoleStore)
            setNextButtonEnabled(true)
        }

        // Step 2 Store Card click listeners
        cardStoreCreate.setOnClickListener {
            selectedStoreAction = StoreAction.CREATE
            updateStep2StoreCardStyles()
            animateCardSelection(cardStoreCreate)
            animateCardDeselection(cardStoreJoin)
            setNextButtonEnabled(true)
        }
        cardStoreJoin.setOnClickListener {
            selectedStoreAction = StoreAction.JOIN
            updateStep2StoreCardStyles()
            animateCardSelection(cardStoreJoin)
            animateCardDeselection(cardStoreCreate)
            setNextButtonEnabled(true)
        }

        // Step 2 Customer Card click listeners
        cardSukiYes.setOnClickListener {
            selectedSukiPref = SukiPreference.YES
            updateStep2CustomerCardStyles()
            animateCardSelection(cardSukiYes)
            animateCardDeselection(cardSukiNo)
            setNextButtonEnabled(true)
        }
        cardSukiNo.setOnClickListener {
            selectedSukiPref = SukiPreference.NO
            updateStep2CustomerCardStyles()
            animateCardSelection(cardSukiNo)
            animateCardDeselection(cardSukiYes)
            setNextButtonEnabled(true)
        }

        // Button listeners
        btnNext.setOnClickListener {
            navigateNext()
        }

        btnBack.setOnClickListener {
            navigateBack()
        }

        // Modified: Top header back button always shows exit setup dialog directly
        val buttonHeaderBack = findViewById<ImageView>(R.id.buttonBack)
        buttonHeaderBack.setOnClickListener {
            showExitSetupDialog()
        }

        // Trigger initial step 1 entrance animation
        animateEntrance(cardRoleStore, cardRoleCustomer)
    }

    private fun setNextButtonEnabled(enabled: Boolean) {
        btnNext.isEnabled = enabled
        btnNext.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun styleCard(
        container: LinearLayout,
        isHighlighted: Boolean,
        textResIds: List<Int>,
        imageResIds: List<Int>
    ) {
        val context = container.context
        if (isHighlighted) {
            container.setBackgroundResource(R.drawable.bg_card_selected_orange)
            val orangeColor = ContextCompat.getColor(context, R.color.presyo_orange)
            textResIds.forEach { container.findViewById<TextView>(it)?.setTextColor(orangeColor) }
            imageResIds.forEach { container.findViewById<ImageView>(it)?.imageTintList = android.content.res.ColorStateList.valueOf(orangeColor) }
        } else {
            container.setBackgroundResource(R.drawable.bg_card_unselected_teal)
            val tealColor = ContextCompat.getColor(context, R.color.presyo_teal)
            textResIds.forEach { container.findViewById<TextView>(it)?.setTextColor(tealColor) }
            imageResIds.forEach { container.findViewById<ImageView>(it)?.imageTintList = android.content.res.ColorStateList.valueOf(tealColor) }
        }
    }

    private fun animateEntrance(vararg views: View) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 60f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450)
                .setStartDelay(index * 120L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    private fun animateCardSelection(selectedCard: View) {
        selectedCard.animate()
            .scaleX(1.02f)
            .scaleY(1.02f)
            .setDuration(120)
            .withEndAction {
                selectedCard.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun animateCardDeselection(unselectedCard: View) {
        unselectedCard.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(120)
            .start()
    }

    private fun animateClosingEmphasis(onComplete: () -> Unit) {
        val currentCards = when (currentStep) {
            1 -> {
                if (selectedRole == UserRole.STORE) Pair(cardRoleStore, cardRoleCustomer)
                else if (selectedRole == UserRole.CUSTOMER) Pair(cardRoleCustomer, cardRoleStore)
                else null
            }
            2 -> {
                if (selectedRole == UserRole.STORE) {
                    if (selectedStoreAction == StoreAction.CREATE) Pair(cardStoreCreate, cardStoreJoin)
                    else if (selectedStoreAction == StoreAction.JOIN) Pair(cardStoreJoin, cardStoreCreate)
                    else null
                } else {
                    if (selectedSukiPref == SukiPreference.YES) Pair(cardSukiYes, cardSukiNo)
                    else if (selectedSukiPref == SukiPreference.NO) Pair(cardSukiNo, cardSukiYes)
                    else null
                }
            }
            else -> null
        }

        if (currentCards == null) {
            onComplete()
            return
        }

        val chosen = currentCards.first
        val unchosen = currentCards.second

        // 1. Animate unchosen to scale down and fade out
        unchosen.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(250)
            .start()

        // 2. Animate chosen to scale up slightly and then fade out
        chosen.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(150)
            .withEndAction {
                chosen.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction {
                        // Restore scales/alpha for future entries when backing
                        chosen.scaleX = 1f
                        chosen.scaleY = 1f
                        chosen.alpha = 1f
                        unchosen.scaleX = 1f
                        unchosen.scaleY = 1f
                        unchosen.alpha = 1f
                        onComplete()
                    }
                    .start()
            }
            .start()
    }

    private fun startDotsAnimation() {
        loadingAnimators.forEach { it.cancel() }
        loadingAnimators.clear()

        val dots = listOf(dot1, dot2, dot3)
        dots.forEachIndexed { index, dot ->
            dot.translationY = 0f
            val animator = android.animation.ObjectAnimator.ofFloat(dot, "translationY", 0f, -28f, 0f)
            animator.duration = 600
            animator.repeatMode = android.animation.ValueAnimator.REVERSE
            animator.repeatCount = android.animation.ValueAnimator.INFINITE
            animator.startDelay = index * 150L
            animator.start()
            loadingAnimators.add(animator)
        }
    }

    private fun stopDotsAnimation() {
        loadingAnimators.forEach { it.cancel() }
        loadingAnimators.clear()
        val dots = listOf(dot1, dot2, dot3)
        dots.forEach { it.translationY = 0f }
    }

    private fun updateStep1CardStyles() {
        styleCard(
            cardRoleStore,
            selectedRole == UserRole.STORE,
            listOf(R.id.txtRoleStoreDesc),
            listOf(R.id.imgRoleStore)
        )
        styleCard(
            cardRoleCustomer,
            selectedRole == UserRole.CUSTOMER,
            listOf(R.id.txtRoleCustomerDesc),
            listOf(R.id.imgRoleCustomer)
        )
    }

    private fun updateStep2StoreCardStyles() {
        styleCard(
            cardStoreCreate,
            selectedStoreAction == StoreAction.CREATE,
            listOf(R.id.txtTitleStoreCreate, R.id.txtDescStoreCreate),
            listOf(R.id.iconPlusStoreCreate, R.id.imgIconStoreCreate)
        )
        styleCard(
            cardStoreJoin,
            selectedStoreAction == StoreAction.JOIN,
            listOf(R.id.txtTitleStoreJoin, R.id.txtDescStoreJoin),
            listOf(R.id.iconArrowStoreJoin, R.id.imgIconStoreJoin)
        )
    }

    private fun updateStep2CustomerCardStyles() {
        styleCard(
            cardSukiYes,
            selectedSukiPref == SukiPreference.YES,
            listOf(R.id.txtTitleSukiYes, R.id.txtDescSukiYes),
            listOf(R.id.imgSukiYes)
        )
        styleCard(
            cardSukiNo,
            selectedSukiPref == SukiPreference.NO,
            listOf(R.id.txtTitleSukiNo, R.id.txtDescSukiNo),
            listOf(R.id.imgSukiNo)
        )
    }

    private fun updateProgressIndicators() {
        when (currentStep) {
            1 -> {
                indicatorStep1.setBackgroundResource(R.drawable.bg_indicator_active)
                indicatorStep2.setBackgroundResource(R.drawable.bg_indicator_inactive)
                indicatorStep3.setBackgroundResource(R.drawable.bg_indicator_inactive)
            }
            2 -> {
                indicatorStep1.setBackgroundResource(R.drawable.bg_indicator_active)
                indicatorStep2.setBackgroundResource(R.drawable.bg_indicator_active)
                indicatorStep3.setBackgroundResource(R.drawable.bg_indicator_inactive)
            }
            3 -> {
                indicatorStep1.setBackgroundResource(R.drawable.bg_indicator_active)
                indicatorStep2.setBackgroundResource(R.drawable.bg_indicator_active)
                indicatorStep3.setBackgroundResource(R.drawable.bg_indicator_active)
            }
        }
    }

    private fun performDelayedNextTransition(action: () -> Unit) {
        val currentContainer = when (currentStep) {
            1 -> step1Container
            2 -> if (selectedRole == UserRole.STORE) step2StoreContainer else step2CustomerContainer
            3 -> step3SuccessContainer
            else -> null
        }

        // Disable buttons
        btnNext.isEnabled = false
        btnBack.isEnabled = false
        val buttonHeaderBack = findViewById<View>(R.id.buttonBack)
        buttonHeaderBack.isEnabled = false
        buttonHeaderBack.alpha = 0.3f

        animateClosingEmphasis {
            currentContainer?.visibility = View.GONE
            loadingDotsContainer.visibility = View.VISIBLE
            startDotsAnimation()

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                stopDotsAnimation()
                loadingDotsContainer.visibility = View.GONE

                // Enable buttons
                btnNext.isEnabled = true
                btnBack.isEnabled = true
                buttonHeaderBack.isEnabled = true
                buttonHeaderBack.alpha = 1.0f

                // Perform actual step logic
                action()
            }, 1200)
        }
    }

    private fun navigateNext() {
        when (currentStep) {
            1 -> {
                performDelayedNextTransition {
                    currentStep = 2
                    btnBack.visibility = View.VISIBLE
                    if (selectedRole == UserRole.STORE) {
                        step2StoreContainer.visibility = View.VISIBLE
                        updateStep2StoreCardStyles()
                        animateEntrance(cardStoreCreate, cardStoreJoin)
                        setNextButtonEnabled(selectedStoreAction != StoreAction.NONE)
                    } else {
                        step2CustomerContainer.visibility = View.VISIBLE
                        updateStep2CustomerCardStyles()
                        animateEntrance(cardSukiYes, cardSukiNo)
                        setNextButtonEnabled(selectedSukiPref != SukiPreference.NONE)
                    }
                    updateProgressIndicators()
                }
            }
            2 -> {
                performDelayedNextTransition {
                    currentStep = 3
                    btnNext.text = "LAUNCH APP"
                    step3SuccessContainer.visibility = View.VISIBLE
                    setNextButtonEnabled(true) // Success screen has no selection validation required

                    // Inject dynamic success text based on role path
                    if (selectedRole == UserRole.STORE) {
                        successMessage.text = "I hope this app makes tracking and managing prices much easier for you.\n\nEnjoy using the app! :)"
                    } else {
                        successMessage.text = "I hope this app helps you stay updated on daily price changes with ease.\n\nEnjoy using the app! :)"
                    }
                    animateEntrance(ceoAvatar, successMessage)
                    updateProgressIndicators()
                }
            }
            3 -> {
                performDelayedNextTransition {
                    launchMainApp()
                }
            }
        }
    }

    private fun navigateBack() {
        when (currentStep) {
            2 -> {
                currentStep = 1
                selectedRole = UserRole.NONE
                step2StoreContainer.visibility = View.GONE
                step2CustomerContainer.visibility = View.GONE
                step1Container.visibility = View.VISIBLE
                btnBack.visibility = View.GONE
                btnNext.text = "NEXT"

                // Hard reset card transformation properties to verify clean entrance transitions
                cardRoleStore.scaleX = 1f
                cardRoleStore.scaleY = 1f
                cardRoleStore.alpha = 1f
                cardRoleCustomer.scaleX = 1f
                cardRoleCustomer.scaleY = 1f
                cardRoleCustomer.alpha = 1f

                updateStep1CardStyles()
                animateEntrance(cardRoleStore, cardRoleCustomer)
                setNextButtonEnabled(false) // Force user to choose again on step 1
                updateProgressIndicators()
            }
            3 -> {
                currentStep = 2
                step3SuccessContainer.visibility = View.GONE
                btnNext.text = "NEXT"

                // Hard reset Step 2 layouts transformation properties
                cardStoreCreate.scaleX = 1f
                cardStoreCreate.scaleY = 1f
                cardStoreCreate.alpha = 1f
                cardStoreJoin.scaleX = 1f
                cardStoreJoin.scaleY = 1f
                cardStoreJoin.alpha = 1f

                cardSukiYes.scaleX = 1f
                cardSukiYes.scaleY = 1f
                cardSukiYes.alpha = 1f
                cardSukiNo.scaleX = 1f
                cardSukiNo.scaleY = 1f
                cardSukiNo.alpha = 1f

                if (selectedRole == UserRole.STORE) {
                    selectedStoreAction = StoreAction.NONE
                    step2StoreContainer.visibility = View.VISIBLE
                    updateStep2StoreCardStyles()
                    animateEntrance(cardStoreCreate, cardStoreJoin)
                } else {
                    selectedSukiPref = SukiPreference.NONE
                    step2CustomerContainer.visibility = View.VISIBLE
                    updateStep2CustomerCardStyles()
                    animateEntrance(cardSukiYes, cardSukiNo)
                }
                setNextButtonEnabled(false) // Force user to choose again on step 2
                updateProgressIndicators()
            }
        }
    }

    private fun launchMainApp() {
        val prefs = getSharedPreferences("presyo_prefs", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putBoolean("onboarding_completed", true)

        var destinationIntent: Intent? = null
        when (selectedRole) {
            UserRole.STORE -> {
                destinationIntent = Intent(this, StoreActivity::class.java)
                if (selectedStoreAction == StoreAction.CREATE) {
                    editor.putString("onboarding_action_pending", "create_store")
                } else if (selectedStoreAction == StoreAction.JOIN) {
                    editor.putString("onboarding_action_pending", "join_store")
                }
            }
            UserRole.CUSTOMER -> {
                if (selectedSukiPref == SukiPreference.YES) {
                    destinationIntent = Intent(this, CustomerHomeActivity::class.java)
                    editor.putString("onboarding_action_pending", "add_suki")
                } else {
                    // Direct browse
                    destinationIntent = Intent(this, SelectPresyohanActivity::class.java)
                    editor.putString("onboarding_action_pending", "select_presyohan")
                }
            }
            else -> {}
        }
        editor.apply()

        // Sync state to Supabase User Metadata (persistent across devices)
        lifecycleScope.launch {
            try {
                SupabaseAuthService.setOnboardingCompleted()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (destinationIntent != null) {
                startActivity(destinationIntent)
                overridePendingTransition(0, 0)
            }
            finish()
        }
    }

    override fun onBackPressed() {
        showExitSetupDialog()
    }

    private fun performSignOut() {
        lifecycleScope.launch {
            try {
                kotlinx.coroutines.withTimeoutOrNull(2500) {
                    SupabaseAuthService.signOut()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                val intent = Intent(this@OnboardingActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                overridePendingTransition(0, 0)
                finish()
            }
        }
    }

    private fun showExitSetupDialog() {
        ReusableDialogHelper.showCustomDialog(
            context = this,
            title = "Exit Setup?",
            message = "Setup is not complete. Are you sure you want to close Presyohan?\n\nDon't worry, your account is saved and you can finish this later.",
            positiveButtonText = "Continue Setup",
            positiveAction = {
                // Continue Setup is positive (orange), just dismisses
            },
            negativeButtonText = "Sign Out",
            negativeAction = {
                performSignOut()
            }
        )
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}