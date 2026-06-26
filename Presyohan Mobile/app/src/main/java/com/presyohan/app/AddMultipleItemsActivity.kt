package com.presyohan.app

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.AppCompatButton
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddMultipleItemsActivity : AppCompatActivity() {

    // UI Components
    private lateinit var tvSubHeaderTitle: TextView
    private lateinit var tvSubHeaderSubtitle: TextView
    private lateinit var btnImportPricesSubheader: View
    private lateinit var btnToggleMode: MaterialButton
    private lateinit var btnReviewHeader: MaterialButton
    
    private lateinit var containerSimple: View
    private lateinit var containerFast: View
    private lateinit var simpleRecyclerView: RecyclerView
    private lateinit var btnSelectCategoryBottom: androidx.appcompat.widget.AppCompatButton
    
    private lateinit var inputRawText: EditText
    private lateinit var btnBack: ImageView
    private lateinit var loadingOverlay: View

    // Redesigned Smart Mode UI Components
    private lateinit var btnSmartModeInfo: ImageView
    private lateinit var layoutParserSelector: View
    private lateinit var imgSelectedParserIcon: ImageView
    private lateinit var tvSelectedParserTitle: TextView
    private lateinit var tvSelectedParserBadge: TextView
    private lateinit var tvSelectedParserSubtext: TextView
    private lateinit var viewParserDivider: View
    private lateinit var layoutBodyAi: View
    private lateinit var layoutBodyPresyohan: View
    private lateinit var tvAiGreeting: TextView
    private lateinit var btnScanPhoto: View
    private lateinit var btnViewFormats: View
    private lateinit var tvParserFooterNote: TextView
    private lateinit var layoutDropdownOverlay: View
    private lateinit var btnOptionAi: View
    private lateinit var btnOptionPresyohan: View
    private lateinit var imgCheckAi: ImageView
    private lateinit var imgCheckPresyohan: ImageView

    private enum class ParserType {
        AI,
        PRESYOHAN
    }
    private var selectedParserType = ParserType.AI

    // ViewModel
    private lateinit var viewModel: AddMultipleItemsViewModel

    // Data
    private var storeId: String? = null
    private var storeName: String? = null
    private var isSessionInitialized = false
    private var currentMode = EntryMode.SIMPLE

    // Local manual entry categories copy to preserve focus/cursor state
    private val localCategories = mutableListOf<DraftCategory>()
    private var simpleAdapter: SimpleCategoryAdapter? = null
    private val categoryIdByName = mutableMapOf<String, String>()
    private var existingProductNames = mutableSetOf<String>()
    private var isCategoriesLoaded = false
    private var hasAutoOpenedCategoryMenu = false

    // File Picker and Dialog state
    private var selectedExcelUri: Uri? = null
    private var activeDialog: Dialog? = null

    private enum class ImportMethod {
        EXCEL,
        PASTE
    }

    private val excelFilePickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedExcelUri = uri
            activeDialog?.let { dlg ->
                val btnSelect = dlg.findViewById<MaterialButton>(R.id.btnSelectExcelFile)
                val layoutSelected = dlg.findViewById<View>(R.id.layoutSelectedFile)
                val tvSelected = dlg.findViewById<TextView>(R.id.tvSelectedFile)
                
                btnSelect.text = "Change"
                layoutSelected.visibility = View.VISIBLE
                tvSelected.text = "Selected: ${getFileName(uri)}"
                
                val btnNext = dlg.findViewById<View>(R.id.btnNext)
                if (btnNext != null) {
                    btnNext.isEnabled = true
                    btnNext.alpha = 1.0f
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_multiple_items)

        loadingOverlay = LoadingOverlayHelper.attach(this)
        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName")
        val sessionId = intent.getStringExtra("draftSessionId")
        val showImportDialog = intent.getBooleanExtra("showImportDialog", false)

        viewModel = ViewModelProvider(this)[AddMultipleItemsViewModel::class.java]

        // Observe ViewModel states
        viewModel.draftSession.observe(this) { session ->
            if (session != null) {
                storeId = session.storeId
                storeName = session.storeName

                // Initialize local copy once to prevent typing refresh loss
                if (!isSessionInitialized) {
                    localCategories.clear()
                    localCategories.addAll(session.categories.filterNot { it.isEmptyUncategorizedPlaceholder() })
                    
                    setupSimpleRecyclerView()
                    updateSubHeaderCount()
                    isSessionInitialized = true
                    checkAutoOpenCategoryMenu()
                }
            }
        }
        viewModel.isLoading.observe(this) { loading ->
            if (loading) {
                LoadingOverlayHelper.show(loadingOverlay)
            } else {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
        viewModel.categoryIdByName.observe(this) { cats ->
            if (cats != null) {
                categoryIdByName.clear()
                categoryIdByName.putAll(cats)
                isCategoriesLoaded = true
                checkAutoOpenCategoryMenu()
            }
        }
        viewModel.existingProductNames.observe(this) { prods ->
            if (prods != null) {
                existingProductNames.clear()
                existingProductNames.addAll(prods)
            }
        }
        viewModel.error.observe(this) { errMsg ->
            if (errMsg != null) {
                Toast.makeText(this, errMsg, Toast.LENGTH_LONG).show()
            }
        }

        initViews()
        setupDrawer()
        checkAutoOpenCategoryMenu()

        // Load/create session
        val sId = storeId
        if (sId != null) {
            viewModel.loadOrCreateSession(sId, storeName, sessionId)
        }

        if (showImportDialog) {
            showImportPricesDialog()
        }
    }

    private fun initViews() {
        tvSubHeaderTitle = findViewById(R.id.tvSubHeaderTitle)
        tvSubHeaderSubtitle = findViewById(R.id.tvSubHeaderSubtitle)
        btnImportPricesSubheader = findViewById(R.id.btnImportPricesSubheader)
        btnToggleMode = findViewById(R.id.btnToggleMode)
        btnReviewHeader = findViewById(R.id.btnReviewHeader)
        
        containerSimple = findViewById(R.id.containerSimple)
        containerFast = findViewById(R.id.containerFast)
        simpleRecyclerView = findViewById(R.id.simpleRecyclerView)
        btnSelectCategoryBottom = findViewById(R.id.btnSelectCategoryBottom)
        
        inputRawText = findViewById(R.id.inputRawText)
        btnBack = findViewById(R.id.btnBack)

        // Bind redesigned Smart Mode UI elements
        btnSmartModeInfo = findViewById(R.id.btnSmartModeInfo)
        layoutParserSelector = findViewById(R.id.layoutParserSelector)
        imgSelectedParserIcon = findViewById(R.id.imgSelectedParserIcon)
        tvSelectedParserTitle = findViewById(R.id.tvSelectedParserTitle)
        tvSelectedParserBadge = findViewById(R.id.tvSelectedParserBadge)
        tvSelectedParserSubtext = findViewById(R.id.tvSelectedParserSubtext)
        viewParserDivider = findViewById(R.id.viewParserDivider)
        layoutBodyAi = findViewById(R.id.layoutBodyAi)
        layoutBodyPresyohan = findViewById(R.id.layoutBodyPresyohan)
        tvAiGreeting = findViewById(R.id.tvAiGreeting)
        btnScanPhoto = findViewById(R.id.btnScanPhoto)
        btnViewFormats = findViewById(R.id.btnViewFormats)
        tvParserFooterNote = findViewById(R.id.tvParserFooterNote)
        layoutDropdownOverlay = findViewById(R.id.layoutDropdownOverlay)
        btnOptionAi = findViewById(R.id.btnOptionAi)
        btnOptionPresyohan = findViewById(R.id.btnOptionPresyohan)
        imgCheckAi = findViewById(R.id.imgCheckAi)
        imgCheckPresyohan = findViewById(R.id.imgCheckPresyohan)

        // Load User Name for greeting
        lifecycleScope.launch {
            try {
                val profile = SupabaseAuthService.getUserProfile()
                val firstName = profile?.name?.trim()?.substringBefore(" ") 
                    ?: SupabaseAuthService.getDisplayName()?.trim()?.substringBefore(" ") 
                    ?: "Caliph"
                tvAiGreeting.text = "Hi $firstName!"
            } catch (e: Exception) {
                tvAiGreeting.text = "Hi Caliph!"
            }
        }

        // Setup Dropdown toggle and options
        layoutParserSelector.setOnClickListener {
            layoutDropdownOverlay.visibility = if (layoutDropdownOverlay.visibility == View.VISIBLE) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }

        btnOptionAi.setOnClickListener {
            selectParser(ParserType.AI)
            layoutDropdownOverlay.visibility = View.GONE
        }

        btnOptionPresyohan.setOnClickListener {
            selectParser(ParserType.PRESYOHAN)
            layoutDropdownOverlay.visibility = View.GONE
        }

        btnScanPhoto.setOnClickListener {
            Toast.makeText(this, "Photo scanning coming soon!", Toast.LENGTH_SHORT).show()
        }

        btnViewFormats.setOnClickListener {
            showValidFormatsDialog()
        }

        btnSmartModeInfo.setOnClickListener {
            showSmartModeInfoDialog()
        }

        simpleRecyclerView.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { onBackPressed() }
        btnImportPricesSubheader.setOnClickListener { showImportPricesDialog() }

        // Mode Toggle Button click
        btnToggleMode.setOnClickListener {
            currentFocus?.clearFocus()
            layoutDropdownOverlay.visibility = View.GONE
            if (currentMode == EntryMode.SIMPLE) {
                currentMode = EntryMode.FAST
                btnToggleMode.text = "Simple Mode"
                tvSubHeaderTitle.text = "Smart Mode"
                btnSmartModeInfo.visibility = View.VISIBLE
                tvSubHeaderSubtitle.visibility = View.VISIBLE
                containerSimple.visibility = View.GONE
                containerFast.visibility = View.VISIBLE
            } else {
                currentMode = EntryMode.SIMPLE
                btnToggleMode.text = "Smart Mode"
                btnSmartModeInfo.visibility = View.GONE
                tvSubHeaderSubtitle.visibility = View.GONE
                containerSimple.visibility = View.VISIBLE
                containerFast.visibility = View.GONE
                updateSubHeaderCount()
            }
            updateButtonsState()
        }

        // Review Header Button click
        btnReviewHeader.setOnClickListener {
            currentFocus?.clearFocus()
            layoutDropdownOverlay.visibility = View.GONE
            if (currentMode == EntryMode.SIMPLE) {
                performSaveSimpleMode()
            } else {
                performPreviewSmartMode()
            }
        }

        btnSelectCategoryBottom.setOnClickListener {
            showCategorySelectorMenu()
        }

        // Add TextWatcher to inputRawText to update button states on editing in Fast Mode
        inputRawText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateButtonsState()
            }
        })

        updateButtonsState()
    }

    private fun DraftCategory.isEmptyUncategorizedPlaceholder(): Boolean {
        return name.equals("UNCATEGORIZED", ignoreCase = true) &&
            items.all { item ->
                item.source == ImportSource.SIMPLE_MANUAL &&
                    item.productName.isBlank() &&
                    item.priceText.isBlank() &&
                    item.description.isNullOrBlank()
            }
    }

    private fun normalizeCategoryName(value: String): String {
        return value.trim().uppercase()
    }

    private fun categoryIdFor(categoryName: String): String? {
        return categoryIdByName.entries
            .firstOrNull { it.key.equals(categoryName, ignoreCase = true) }
            ?.value
    }

    private fun displayNameForCategory(categoryName: String): String {
        return categoryIdByName.keys
            .firstOrNull { it.equals(categoryName, ignoreCase = true) }
            ?: normalizeCategoryName(categoryName)
    }

    private fun createBlankItem(categoryName: String, categoryId: String? = null): DraftItem {
        return DraftItem(
            draftItemId = "item-${java.util.UUID.randomUUID()}",
            categoryId = categoryId,
            categoryName = categoryName,
            productName = "",
            description = null,
            unit = "",
            priceText = "",
            price = null,
            source = ImportSource.SIMPLE_MANUAL,
            validationStatus = ValidationStatus.INVALID,
            validationErrors = listOf(ValidationError.EMPTY_PRODUCT_NAME)
        )
    }

    private fun setupSimpleRecyclerView() {
        simpleAdapter = SimpleCategoryAdapter(localCategories) {
            updateSubHeaderCount()
        }
        simpleRecyclerView.adapter = simpleAdapter
    }

    private fun updateSubHeaderCount() {
        var total = 0
        localCategories.forEach { cat ->
            total += cat.items.size
        }
        if (currentMode == EntryMode.SIMPLE) {
            tvSubHeaderTitle.text = "Total: $total Items"
        }
        updateButtonsState()
    }

    private fun hasSimpleModeInput(): Boolean {
        return localCategories.any { cat ->
            cat.items.any { item ->
                item.productName.isNotBlank() || item.priceText.isNotBlank()
            }
        }
    }

    private fun hasFastModeInput(): Boolean {
        return inputRawText.text.isNotBlank()
    }

    private fun updateButtonsState() {
        val hasInput = if (currentMode == EntryMode.SIMPLE) {
            hasSimpleModeInput()
        } else {
            hasFastModeInput()
        }

        // Update Review button state (clickable & opacity)
        if (hasInput) {
            btnReviewHeader.isEnabled = true
            btnReviewHeader.alpha = 1.0f
        } else {
            btnReviewHeader.isEnabled = false
            btnReviewHeader.alpha = 0.5f
        }

        // Update Toggle Mode button state (clickable & opacity)
        if (hasInput) {
            btnToggleMode.isEnabled = false
            btnToggleMode.alpha = 0.5f
        } else {
            btnToggleMode.isEnabled = true
            btnToggleMode.alpha = 1.0f
        }
    }

    private fun performSaveSimpleMode() {
        val session = viewModel.draftSession.value ?: return
        val categoriesForReview = localCategories
            .filter { it.items.isNotEmpty() }
            .map { it.copy(items = it.items.toMutableList()) }
            .toMutableList()

        if (categoriesForReview.sumOf { it.items.size } == 0) {
            Toast.makeText(this, "Choose a category and add an item first.", Toast.LENGTH_SHORT).show()
            return
        }

        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            // Save updated categories to session
            val mappedCategories = categoriesForReview.map { cat ->
                val normName = cat.name.trim().uppercase()
                val catId = categoryIdByName[normName] ?: cat.categoryId
                cat.copy(
                    categoryId = catId,
                    items = cat.items.map { item ->
                        item.copy(categoryId = catId)
                    }.toMutableList()
                )
            }.toMutableList()
            val updatedSession = session.copy(
                categories = mappedCategories,
                isDirty = true
            )
            val dbProds = ImportValidationUseCase().fetchExistingProducts(session.storeId)
            val dbCats = categoryIdByName.map { DbCategory(it.value, it.key) }
            val validatedSession = ImportValidationUseCase().validate(updatedSession, dbProds, dbCats)

            viewModel.updateSession(validatedSession)

            withContext(Dispatchers.Main) {
                LoadingOverlayHelper.hide(loadingOverlay)
                val intent = Intent(this@AddMultipleItemsActivity, ReviewImportActivity::class.java).apply {
                    putExtra("draftSessionId", validatedSession.sessionId)
                    putExtra("storeId", storeId)
                    putExtra("storeName", storeName)
                }
                startActivity(intent)
            }
        }
    }

    private fun onParsingSuccess(parseResult: ParseResult, dbProds: List<DbProduct>) {
        val session = viewModel.draftSession.value ?: return
        lifecycleScope.launch {
            try {
                val mappedCategories = parseResult.categories.map { cat ->
                    val normName = cat.name.trim().uppercase()
                    val catId = categoryIdByName[normName] ?: cat.categoryId
                    cat.copy(
                        categoryId = catId,
                        items = cat.items.map { item ->
                            item.copy(categoryId = catId)
                        }.toMutableList()
                    )
                }.toMutableList()
                val updatedSession = session.copy(
                    categories = mappedCategories,
                    isDirty = true
                )
                val dbCats = categoryIdByName.map { DbCategory(it.value, it.key) }
                val validatedSession = withContext(Dispatchers.IO) {
                    ImportValidationUseCase().validate(updatedSession, dbProds, dbCats)
                }

                viewModel.updateSession(validatedSession)

                withContext(Dispatchers.Main) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                    val intent = Intent(this@AddMultipleItemsActivity, ReviewImportActivity::class.java).apply {
                        putExtra("draftSessionId", validatedSession.sessionId)
                        putExtra("storeId", storeId)
                        putExtra("storeName", storeName)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                    Toast.makeText(this@AddMultipleItemsActivity, "Failed to save smart parse session: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun selectParser(parserType: ParserType) {
        selectedParserType = parserType
        if (parserType == ParserType.AI) {
            imgSelectedParserIcon.setImageResource(R.drawable.icon_happy_robot)
            tvSelectedParserTitle.text = "AI-powered parser"
            tvSelectedParserTitle.setTextColor(ContextCompat.getColor(this, R.color.presyo_orange))
            tvSelectedParserBadge.text = "Smart"
            tvSelectedParserBadge.setBackgroundResource(R.drawable.bg_badge_orange)
            tvSelectedParserSubtext.text = "Reads any custom layout or format."
            viewParserDivider.setBackgroundColor(ContextCompat.getColor(this, R.color.presyo_orange))
            
            layoutBodyAi.visibility = View.VISIBLE
            layoutBodyPresyohan.visibility = View.GONE
            
            inputRawText.hint = "Type your pricelist here or paste the Presyohan-generated pricelist..."
            inputRawText.setBackgroundResource(R.drawable.bg_edittext_orange_border)
            
            tvParserFooterNote.text = "Note: The AI needs a stable connection to work."
            
            imgCheckAi.visibility = View.VISIBLE
            imgCheckPresyohan.visibility = View.INVISIBLE
        } else {
            imgSelectedParserIcon.setImageResource(R.drawable.icon_presyohan_parser)
            tvSelectedParserTitle.text = "Presyohan parser"
            tvSelectedParserTitle.setTextColor(ContextCompat.getColor(this, R.color.presyo_teal))
            tvSelectedParserBadge.text = "Fast"
            tvSelectedParserBadge.setBackgroundResource(R.drawable.bg_badge_teal)
            tvSelectedParserSubtext.text = "Reads standard templates instantly."
            viewParserDivider.setBackgroundColor(ContextCompat.getColor(this, R.color.presyo_teal))
            
            layoutBodyAi.visibility = View.GONE
            layoutBodyPresyohan.visibility = View.VISIBLE
            
            inputRawText.hint = "Paste your exported Presyohan prices or standard formatted text here..."
            inputRawText.setBackgroundResource(R.drawable.bg_edittext_blue_border)
            
            tvParserFooterNote.text = "Note: Descriptions are optional"
            
            imgCheckAi.visibility = View.INVISIBLE
            imgCheckPresyohan.visibility = View.VISIBLE
        }
    }

    private fun showValidFormatsDialog() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_valid_formats, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        
        val btnGotIt = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnGotIt)
        btnGotIt.setOnClickListener { dialog.dismiss() }
        
        dialog.show()
    }

    private fun showSmartModeInfoDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.popup_smart_mode_info, null)
        val popupWindow = android.widget.PopupWindow(
            view,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        
        popupWindow.elevation = 10f
        popupWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        
        popupWindow.showAsDropDown(btnSmartModeInfo, 0, 10)
    }

    private fun performPreviewSmartMode() {
        val raw = inputRawText.text.toString()
        if (raw.isBlank()) {
            Toast.makeText(this, "Please type or paste items first.", Toast.LENGTH_SHORT).show()
            return
        }

        val session = viewModel.draftSession.value ?: return

        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val dbProds = withContext(Dispatchers.IO) {
                    ImportValidationUseCase().fetchExistingProducts(session.storeId)
                }
                val existingProducts = dbProds.map { it.name.lowercase() }.toSet()

                withContext(Dispatchers.Main) {
                    if (selectedParserType == ParserType.AI) {
                        LoadingOverlayHelper.hide(loadingOverlay)
                        AiParsingDialogHelper(
                            activity = this@AddMultipleItemsActivity,
                            coroutineScope = lifecycleScope,
                            rawText = raw,
                            categoryIdByName = categoryIdByName,
                            existingProductNames = existingProducts,
                            onSuccess = { parseResult ->
                                onParsingSuccess(parseResult, dbProds)
                            }
                        ).show()
                    } else {
                        // Presyohan Parser (Offline / Standard)
                        lifecycleScope.launch {
                            try {
                                val parseResult = withContext(Dispatchers.IO) {
                                    AddMultipleItemsParser.parseTextToResult(raw, existingProducts)
                                }
                                onParsingSuccess(parseResult, dbProds)
                            } catch (e: Exception) {
                                LoadingOverlayHelper.hide(loadingOverlay)
                                Toast.makeText(this@AddMultipleItemsActivity, "Failed to parse: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                    Toast.makeText(this@AddMultipleItemsActivity, "Failed to fetch database products: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    // --- IMPORT PRICES DIALOG & PARSING FLOWS ---
    private fun showImportPricesDialog() {
        val dlg = Dialog(this)
        activeDialog = dlg
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_import_prices, null)
        dlg.setContentView(view)
        dlg.setCancelable(true)
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        dlg.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnBackDlg = view.findViewById<ImageView>(R.id.btnBack)
        val cardExcelOption = view.findViewById<LinearLayout>(R.id.cardExcelOption)
        val imgExcelRadio = view.findViewById<ImageView>(R.id.imgExcelRadio)
        val cardPasteOption = view.findViewById<LinearLayout>(R.id.cardPasteOption)
        val imgPasteRadio = view.findViewById<ImageView>(R.id.imgPasteRadio)
        
        val panelExcel = view.findViewById<View>(R.id.panelExcel)
        val btnSelectExcelFile = view.findViewById<MaterialButton>(R.id.btnSelectExcelFile)
        val layoutSelectedFile = view.findViewById<View>(R.id.layoutSelectedFile)
        val tvSelectedFile = view.findViewById<TextView>(R.id.tvSelectedFile)
        val btnClearSelectedFile = view.findViewById<ImageView>(R.id.btnClearSelectedFile)
        
        val panelPaste = view.findViewById<View>(R.id.panelPaste)
        val inputDialogPaste = view.findViewById<EditText>(R.id.inputDialogPaste)
        val tvExcelLabel = view.findViewById<TextView>(R.id.tvExcelLabel)
        val tvPasteLabel = view.findViewById<TextView>(R.id.tvPasteLabel)
        val btnNext = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnNext)

        selectedExcelUri = null
        var selectedMethod = ImportMethod.EXCEL

        // Selection style update helper
        fun updateNextButtonVisibility() {
            val isEnabled = if (selectedMethod == ImportMethod.EXCEL) {
                selectedExcelUri != null
            } else {
                inputDialogPaste.text.toString().trim().isNotEmpty()
            }
            btnNext.isEnabled = isEnabled
            btnNext.alpha = if (isEnabled) 1.0f else 0.35f
        }

        fun updateSelectionUI() {
            if (selectedMethod == ImportMethod.EXCEL) {
                cardExcelOption.setBackgroundResource(R.drawable.bg_card_selected_orange)
                imgExcelRadio.setImageResource(R.drawable.ic_radio_checked_orange)
                tvExcelLabel.setTextColor(ContextCompat.getColor(this@AddMultipleItemsActivity, R.color.presyo_orange))
                
                cardPasteOption.setBackgroundResource(R.drawable.bg_card_unselected_teal)
                imgPasteRadio.setImageResource(R.drawable.ic_radio_unchecked)
                tvPasteLabel.setTextColor(ContextCompat.getColor(this@AddMultipleItemsActivity, R.color.presyo_teal))
                
                panelExcel.visibility = View.VISIBLE
                panelPaste.visibility = View.GONE
            } else {
                cardExcelOption.setBackgroundResource(R.drawable.bg_card_unselected_teal)
                imgExcelRadio.setImageResource(R.drawable.ic_radio_unchecked)
                tvExcelLabel.setTextColor(ContextCompat.getColor(this@AddMultipleItemsActivity, R.color.presyo_teal))
                
                cardPasteOption.setBackgroundResource(R.drawable.bg_card_selected_orange)
                imgPasteRadio.setImageResource(R.drawable.ic_radio_checked_orange)
                tvPasteLabel.setTextColor(ContextCompat.getColor(this@AddMultipleItemsActivity, R.color.presyo_orange))
                
                panelExcel.visibility = View.GONE
                panelPaste.visibility = View.VISIBLE
            }
            updateNextButtonVisibility()
        }

        // Initialize UI State
        updateSelectionUI()

        cardExcelOption.setOnClickListener {
            selectedMethod = ImportMethod.EXCEL
            updateSelectionUI()
        }

        cardPasteOption.setOnClickListener {
            selectedMethod = ImportMethod.PASTE
            updateSelectionUI()
        }

        btnSelectExcelFile.setOnClickListener {
            excelFilePickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        }

        btnClearSelectedFile.setOnClickListener {
            selectedExcelUri = null
            layoutSelectedFile.visibility = View.GONE
            btnSelectExcelFile.text = "Choose xlsx"
            updateNextButtonVisibility()
        }

        inputDialogPaste.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateNextButtonVisibility()
            }
        })

        btnBackDlg.setOnClickListener {
            dlg.dismiss()
        }

        btnNext.setOnClickListener {
            if (selectedMethod == ImportMethod.EXCEL) {
                val uri = selectedExcelUri
                if (uri == null) {
                    Toast.makeText(this, "Please select an Excel file first.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dlg.dismiss()
                performExcelImport(uri)
            } else {
                val text = inputDialogPaste.text.toString()
                if (text.isBlank()) {
                    Toast.makeText(this, "Please paste supplier prices list first.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dlg.dismiss()
                performPasteImport(text)
            }
        }

        dlg.show()
    }

    private fun performExcelImport(uri: Uri) {
        val session = viewModel.draftSession.value ?: return
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        LoadingOverlayHelper.hide(loadingOverlay)
                        Toast.makeText(this@AddMultipleItemsActivity, "Failed to open Excel stream.", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val parsedCategories = withContext(Dispatchers.IO) {
                    ExcelImportParser.parseXlsx(inputStream)
                }

                val updatedSession = session.copy(
                    categories = parsedCategories.toMutableList(),
                    isDirty = true
                )

                val dbProds = withContext(Dispatchers.IO) {
                    ImportValidationUseCase().fetchExistingProducts(session.storeId)
                }
                val dbCats = categoryIdByName.map { DbCategory(it.value, it.key) }
                val validatedSession = withContext(Dispatchers.IO) {
                    ImportValidationUseCase().validate(updatedSession, dbProds, dbCats)
                }
                viewModel.updateSession(validatedSession)

                withContext(Dispatchers.Main) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                    val intent = Intent(this@AddMultipleItemsActivity, ReviewImportActivity::class.java).apply {
                        putExtra("draftSessionId", validatedSession.sessionId)
                        putExtra("storeId", storeId)
                        putExtra("storeName", storeName)
                    }
                    startActivity(intent)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                    Toast.makeText(this@AddMultipleItemsActivity, "Error parsing Excel: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun performPasteImport(text: String) {
        val session = viewModel.draftSession.value ?: return

        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val dbProds = withContext(Dispatchers.IO) {
                    ImportValidationUseCase().fetchExistingProducts(session.storeId)
                }
                val existingProducts = dbProds.map { it.name.lowercase() }.toSet()

                // "Paste raw text" is for Presyohan-generated pricelist text — use the
                // offline Presyohan parser directly (no AI / network required).
                val parseResult = withContext(Dispatchers.IO) {
                    AddMultipleItemsParser.parseTextToResult(text, existingProducts)
                }

                val mappedCategories = parseResult.categories.map { cat ->
                    val normName = cat.name.trim().uppercase()
                    val catId = categoryIdByName[normName] ?: cat.categoryId
                    cat.copy(
                        categoryId = catId,
                        items = cat.items.map { item ->
                            item.copy(categoryId = catId)
                        }.toMutableList()
                    )
                }.toMutableList()

                val updatedSession = session.copy(
                    categories = mappedCategories,
                    isDirty = true
                )
                val dbCats = categoryIdByName.map { DbCategory(it.value, it.key) }
                val validatedSession = withContext(Dispatchers.IO) {
                    ImportValidationUseCase().validate(updatedSession, dbProds, dbCats)
                }
                viewModel.updateSession(validatedSession)

                withContext(Dispatchers.Main) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                    val intent = Intent(this@AddMultipleItemsActivity, ReviewImportActivity::class.java).apply {
                        putExtra("draftSessionId", validatedSession.sessionId)
                        putExtra("storeId", storeId)
                        putExtra("storeName", storeName)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                    Toast.makeText(
                        this@AddMultipleItemsActivity,
                        "Failed to parse pricelist text: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result ?: "selected_file.xlsx"
    }

    private fun setupDrawer() {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        findViewById<ImageView>(R.id.menuIcon)?.setOnClickListener { drawerLayout.open() }
        DrawerHelper.setupDrawer(this, drawerLayout)
    }

    private fun checkAutoOpenCategoryMenu() {
        val isFromReview = intent.getBooleanExtra("isFromReview", false)
        val showImportDialog = intent.getBooleanExtra("showImportDialog", false)
        if (!isFromReview && !showImportDialog && isSessionInitialized && isCategoriesLoaded && localCategories.isEmpty() && currentMode == EntryMode.SIMPLE && !hasAutoOpenedCategoryMenu && ::btnSelectCategoryBottom.isInitialized) {
            hasAutoOpenedCategoryMenu = true
            showCategorySelectorMenu()
        }
    }

    private fun showCategorySelectorMenu() {
        val listPopupWindow = android.widget.ListPopupWindow(this)
        listPopupWindow.anchorView = btnSelectCategoryBottom

        val cats = categoryIdByName.keys.sorted().toMutableList()
        cats.add("Add Category")

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            cats
        )
        listPopupWindow.setAdapter(adapter)

        listPopupWindow.setOnItemClickListener { _, _, position, _ ->
            val selected = cats[position]
            if (selected == "Add Category") {
                showAddCategoryDialog()
            } else {
                selectCategory(selected)
            }
            listPopupWindow.dismiss()
        }
        listPopupWindow.show()
    }

    private fun selectCategory(catName: String) {
        val normalizedCatName = normalizeCategoryName(catName)
        val categoryId = categoryIdFor(catName)

        val existingIndex = localCategories.indexOfFirst { normalizeCategoryName(it.name) == normalizedCatName }
        if (existingIndex != -1) {
            val existingCategory = localCategories[existingIndex]
            if (existingCategory.items.isEmpty()) {
                existingCategory.items.add(createBlankItem(existingCategory.name, existingCategory.categoryId))
                simpleAdapter?.notifyItemChanged(existingIndex)
                updateSubHeaderCount()
            }
            simpleRecyclerView.scrollToPosition(existingIndex)
        } else {
            val newCat = DraftCategory(
                draftCategoryId = "category-${java.util.UUID.randomUUID()}",
                categoryId = categoryId,
                name = catName,
                items = mutableListOf(createBlankItem(catName, categoryId))
            )
            localCategories.add(newCat)
            simpleAdapter?.notifyItemInserted(localCategories.size - 1)
            simpleRecyclerView.scrollToPosition(localCategories.size - 1)
            updateSubHeaderCount()
        }
    }

    private fun showAddCategoryDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_new_category, null)
        val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        dlg.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val input = view.findViewById<EditText>(R.id.inputCategory)
        val btnAdd = view.findViewById<android.widget.Button>(R.id.btnAdd)
        val btnBack = view.findViewById<android.widget.Button>(R.id.btnBack)

        view.findViewById<TextView>(R.id.title)?.let {
            it.text = "Add Category"
        }

        if (btnAdd == null || btnBack == null || input == null) {
            Toast.makeText(this, "Failed to initialize category dialog.", Toast.LENGTH_SHORT).show()
            return
        }

        btnBack.setOnClickListener { dlg.dismiss() }
        btnAdd.setOnClickListener {
            val category = input.text.toString().trim()
            if (category.isEmpty()) {
                input.error = "Enter a category name"
                return@setOnClickListener
            }
            selectCategory(category)
            dlg.dismiss()
        }
        dlg.show()
        dlg.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onBackPressed() {
        val session = viewModel.draftSession.value
        // Check dirty based on local categories edits too
        if (session != null) {
            showDiscardDraftDialog()
        } else {
            super.onBackPressed()
        }
    }

    private fun showDiscardDraftDialog() {
        showReusableDialog(
            title = "Discard Draft Items?",
            message = "The items you have typed out have not been saved yet and will be permanently lost.",
            positiveButtonText = "Cancel",
            positiveAction = {
                // Dialog automatically dismisses
            },
            negativeButtonText = "Discard All",
            negativeAction = {
                val session = viewModel.draftSession.value
                if (session != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        ImportDraftStore(application).deleteSession(session.sessionId)
                    }
                }
                finish()
            },
            isCancelable = true
        )
    }

    // --- CATEGORY GROUPED ADAPTER FOR SIMPLE MODE ---
    inner class SimpleCategoryAdapter(
        private val categories: MutableList<DraftCategory>,
        private val onDataChanged: () -> Unit
    ) : RecyclerView.Adapter<SimpleCategoryAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvCategoryName: TextView = v.findViewById(R.id.tvCategoryName)
            val itemsContainer: LinearLayout = v.findViewById(R.id.itemsContainer)
            val btnAddItemInner: AppCompatButton = v.findViewById(R.id.btnAddItemInner)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_simple_category_card, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val category = categories[position]
            holder.tvCategoryName.text = category.name

            holder.btnAddItemInner.setOnClickListener {
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    val targetCategory = categories[currentPos]
                    targetCategory.items.add(createBlankItem(targetCategory.name, targetCategory.categoryId))
                    notifyItemChanged(currentPos)
                    onDataChanged()
                }
            }

            // Set initial visibility of Add Item button
            val canAddMore = category.items.isEmpty() || category.items.all { it.productName.isNotBlank() && it.priceText.isNotBlank() }
            holder.btnAddItemInner.visibility = if (canAddMore) View.VISIBLE else View.GONE

            // Dynamically inflate or reuse items to prevent unnecessary layout inflation and lag
            val currentChildCount = holder.itemsContainer.childCount
            val targetChildCount = category.items.size
            if (currentChildCount > targetChildCount) {
                holder.itemsContainer.removeViews(targetChildCount, currentChildCount - targetChildCount)
            }
            category.items.forEachIndexed { index, item ->
                val itemView = if (index < currentChildCount) {
                    holder.itemsContainer.getChildAt(index)
                } else {
                    val newView = LayoutInflater.from(holder.itemView.context).inflate(R.layout.item_simple_import_row, holder.itemsContainer, false)
                    holder.itemsContainer.addView(newView)
                    newView
                }
                bindItemView(itemView, category, index, item) {
                    val canAdd = category.items.isEmpty() || category.items.all { it.productName.isNotBlank() && it.priceText.isNotBlank() }
                    holder.btnAddItemInner.visibility = if (canAdd) View.VISIBLE else View.GONE
                }
            }
        }

        override fun getItemCount(): Int = categories.size

        private fun bindItemView(
            v: View,
            category: DraftCategory,
            index: Int,
            item: DraftItem,
            onItemFieldsChanged: () -> Unit
        ) {
            val tvRowIndex = v.findViewById<TextView>(R.id.tvRowIndex)
            val btnDelete = v.findViewById<ImageView>(R.id.btnDelete)
            val inputProductName = v.findViewById<EditText>(R.id.inputProductName)
            val inputPrice = v.findViewById<EditText>(R.id.inputPrice)
            val inputUnit = v.findViewById<EditText>(R.id.inputUnit)
            val inputDescription = v.findViewById<EditText>(R.id.inputDescription)
            val tvErrorText = v.findViewById<TextView>(R.id.tvErrorText)

            // Remove existing TextWatcher from tag to prevent setting text from triggering listeners on wrong/reused rows
            val oldWatcher = v.tag as? TextWatcher
            if (oldWatcher != null) {
                inputProductName.removeTextChangedListener(oldWatcher)
                inputPrice.removeTextChangedListener(oldWatcher)
                inputUnit.removeTextChangedListener(oldWatcher)
                inputDescription.removeTextChangedListener(oldWatcher)
            }

            tvRowIndex.text = "#${index + 1}"
            inputProductName.setText(item.productName)
            inputPrice.setText(item.priceText)
            inputUnit.setText(item.unit)
            inputDescription.setText(item.description ?: "")

            renderValidationErrors(tvErrorText, item.validationErrors)

            btnDelete.setOnClickListener {
                if (index in category.items.indices) {
                    category.items.removeAt(index)
                    val categoryPosition = categories.indexOfFirst { it.draftCategoryId == category.draftCategoryId }
                    if (categoryPosition != -1) {
                        notifyItemChanged(categoryPosition)
                    }
                    onDataChanged()
                }
            }

            val rowWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    syncItemFromInputs(
                        category = category,
                        index = index,
                        inputProductName = inputProductName,
                        inputPrice = inputPrice,
                        inputUnit = inputUnit,
                        inputDescription = inputDescription,
                        tvErrorText = tvErrorText,
                        onItemFieldsChanged = onItemFieldsChanged
                    )
                }
            }

            v.tag = rowWatcher
            inputProductName.addTextChangedListener(rowWatcher)
            inputPrice.addTextChangedListener(rowWatcher)
            inputUnit.addTextChangedListener(rowWatcher)
            inputDescription.addTextChangedListener(rowWatcher)
        }

        private fun syncItemFromInputs(
            category: DraftCategory,
            index: Int,
            inputProductName: EditText,
            inputPrice: EditText,
            inputUnit: EditText,
            inputDescription: EditText,
            tvErrorText: TextView,
            onItemFieldsChanged: () -> Unit
        ) {
            if (index !in category.items.indices) return

            val productName = inputProductName.text.toString().trim()
            val priceText = inputPrice.text.toString().trim()
            val description = inputDescription.text.toString().trim().ifBlank { null }
            val unitVal = inputUnit.text.toString().trim()
            val updated = category.items[index].copy(
                categoryId = category.categoryId,
                categoryName = category.name,
                productName = productName,
                description = description,
                unit = unitVal,
                priceText = priceText,
                price = priceText.toDoubleOrNull(),
                duplicateKey = ImportDraftKeys.productKey(productName, description, unitVal)
            )

            category.items[index] = validateItem(updated)
            renderValidationErrors(tvErrorText, category.items[index].validationErrors)
            onItemFieldsChanged()
            onDataChanged()
        }

        private fun validateItem(item: DraftItem): DraftItem {
            val errors = mutableListOf<ValidationError>()
            var status = ValidationStatus.NEW

            if (item.productName.trim().isEmpty()) {
                errors.add(ValidationError.EMPTY_PRODUCT_NAME)
                status = ValidationStatus.INVALID
            }
            val priceVal = item.price
            if (priceVal == null) {
                errors.add(ValidationError.INVALID_PRICE)
                status = ValidationStatus.INVALID
            } else if (priceVal < 0) {
                errors.add(ValidationError.NEGATIVE_PRICE)
                status = ValidationStatus.INVALID
            }

            return item.copy(
                validationStatus = status,
                validationErrors = errors
            )
        }

        private fun renderValidationErrors(tvErrorText: TextView, errors: List<ValidationError>) {
            tvErrorText.visibility = View.GONE
            tvErrorText.text = ""
        }
    }
}
