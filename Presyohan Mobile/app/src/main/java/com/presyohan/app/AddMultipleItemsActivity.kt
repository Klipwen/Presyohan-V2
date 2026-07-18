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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.ByteArrayOutputStream

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
    
    private lateinit var layoutStickyHeader: View
    private lateinit var tvStickyCategoryName: TextView
    private lateinit var btnStickyAddItemInner: androidx.appcompat.widget.AppCompatButton
    
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
    private val expandedCategoryIds = mutableSetOf<String>()
    
    sealed class SimpleModeItem {
        data class Header(val category: DraftCategory) : SimpleModeItem()
        data class Row(val category: DraftCategory, val item: DraftItem, val index: Int) : SimpleModeItem()
    }
    private val simpleModeItems = mutableListOf<SimpleModeItem>()
    
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

    private var tempPhotoUri: Uri? = null
    private var tempPhotoFile: File? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val uri = tempPhotoUri
            if (uri != null) {
                processAndParseImage(uri)
            }
        } else {
            cleanupTempPhotoFile()
        }
    }

    private val galleryImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            processAndParseImage(uri)
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
                    
                    if (localCategories.isNotEmpty()) {
                        expandedCategoryIds.clear()
                        expandedCategoryIds.add(localCategories[0].draftCategoryId)
                    }

                    rebuildSimpleModeItems()
                    simpleAdapter?.notifyDataSetChanged()
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
        setupSimpleRecyclerView()
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
        
        layoutStickyHeader = findViewById(R.id.layoutStickyHeader)
        tvStickyCategoryName = findViewById(R.id.tvStickyCategoryName)
        btnStickyAddItemInner = findViewById(R.id.btnStickyAddItemInner)
        
        simpleRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateStickyHeader()
            }
        })
        
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
            showPhotoChoiceDialog()
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
                layoutStickyHeader.visibility = View.GONE
            } else {
                currentMode = EntryMode.SIMPLE
                btnToggleMode.text = "Smart Mode"
                btnSmartModeInfo.visibility = View.GONE
                tvSubHeaderSubtitle.visibility = View.GONE
                containerSimple.visibility = View.VISIBLE
                containerFast.visibility = View.GONE
                updateStickyHeader()
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

        // Enable scroll inside EditText
        @android.annotation.SuppressLint("ClickableViewAccessibility")
        inputRawText.setOnTouchListener { v, event ->
            if (v.id == R.id.inputRawText) {
                v.parent.requestDisallowInterceptTouchEvent(true)
                if ((event.action and android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_UP) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }

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

    private fun isCategoryFullyCompleted(category: DraftCategory): Boolean {
        return category.items.all { item ->
            item.productName.trim().isNotEmpty() &&
            item.priceText.trim().isNotEmpty() &&
            item.unit.trim().isNotEmpty()
        }
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

    private fun rebuildSimpleModeItems() {
        simpleModeItems.clear()
        localCategories.forEach { category ->
            simpleModeItems.add(SimpleModeItem.Header(category))
            val isExpanded = expandedCategoryIds.contains(category.draftCategoryId)
            if (isExpanded) {
                category.items.forEachIndexed { index, item ->
                    simpleModeItems.add(SimpleModeItem.Row(category, item, index))
                }
            }
        }
    }

    private fun updateStickyHeader() {
        val layoutManager = simpleRecyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePos == RecyclerView.NO_POSITION || simpleModeItems.isEmpty() || currentMode != EntryMode.SIMPLE) {
            layoutStickyHeader.visibility = View.GONE
            return
        }

        val item = simpleModeItems[firstVisiblePos]
        when (item) {
            is SimpleModeItem.Header -> {
                layoutStickyHeader.visibility = View.GONE
            }
            is SimpleModeItem.Row -> {
                val category = item.category
                tvStickyCategoryName.text = "▼  ${category.name}"
                layoutStickyHeader.visibility = View.VISIBLE

                tvStickyCategoryName.setOnClickListener {
                    if (expandedCategoryIds.contains(category.draftCategoryId)) {
                        expandedCategoryIds.remove(category.draftCategoryId)
                    }
                    rebuildSimpleModeItems()
                    simpleAdapter?.notifyDataSetChanged()
                    updateStickyHeader()
                }

                btnStickyAddItemInner.setOnClickListener {
                    if (!isCategoryFullyCompleted(category)) {
                        Toast.makeText(this@AddMultipleItemsActivity, "Please complete the current item fields first.", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    category.items.add(0, createBlankItem(category.name, category.categoryId))
                    expandedCategoryIds.add(category.draftCategoryId)
                    rebuildSimpleModeItems()
                    simpleAdapter?.notifyDataSetChanged()
                    val headerIndex = simpleModeItems.indexOfFirst { it is SimpleModeItem.Header && it.category.draftCategoryId == category.draftCategoryId }
                    if (headerIndex != -1) {
                        (simpleRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(headerIndex, 0)
                    }
                    updateStickyHeader()
                    updateSubHeaderCount()
                }
            }
        }
    }

    private fun setupSimpleRecyclerView() {
        simpleAdapter = SimpleCategoryAdapter {
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
            }
            expandedCategoryIds.clear()
            expandedCategoryIds.add(existingCategory.draftCategoryId)
            rebuildSimpleModeItems()
            simpleAdapter?.notifyDataSetChanged()
            val headerIndex = simpleModeItems.indexOfFirst { it is SimpleModeItem.Header && it.category.draftCategoryId == existingCategory.draftCategoryId }
            if (headerIndex != -1) {
                (simpleRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(headerIndex, 0)
            }
            updateStickyHeader()
            updateSubHeaderCount()
        } else {
            val newCat = DraftCategory(
                draftCategoryId = "category-${java.util.UUID.randomUUID()}",
                categoryId = categoryId,
                name = catName,
                items = mutableListOf(createBlankItem(catName, categoryId))
            )
            localCategories.add(newCat)
            expandedCategoryIds.clear()
            expandedCategoryIds.add(newCat.draftCategoryId)
            rebuildSimpleModeItems()
            simpleAdapter?.notifyDataSetChanged()
            val headerIndex = simpleModeItems.indexOfFirst { it is SimpleModeItem.Header && it.category.draftCategoryId == newCat.draftCategoryId }
            if (headerIndex != -1) {
                (simpleRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(headerIndex, 0)
            }
            updateStickyHeader()
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

    private fun showPhotoChoiceDialog() {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_photo_choice, null)
        dialog.setContentView(view)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val width = (resources.displayMetrics.widthPixels * 0.95).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnTakePhoto = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnTakePhoto)
        val btnUploadPhoto = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btnUploadPhoto)

        btnTakePhoto.setOnClickListener {
            dialog.dismiss()
            checkCameraPermissionAndLaunch()
        }

        btnUploadPhoto.setOnClickListener {
            dialog.dismiss()
            galleryImageLauncher.launch("image/*")
        }

        dialog.show()
    }

    private fun checkCameraPermissionAndLaunch() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            cleanupTempPhotoFile()
            val cameraDir = File(cacheDir, "shared_images").apply { mkdirs() }
            val file = File(cameraDir, "temp_scan_${System.currentTimeMillis()}.jpg")
            tempPhotoFile = file
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            tempPhotoUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Error initializing camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanupTempPhotoFile() {
        try {
            tempPhotoFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AddMultipleItemsActivity", "Error cleaning up temporary file", e)
        } finally {
            tempPhotoFile = null
            tempPhotoUri = null
        }
    }

    private fun processAndParseImage(uri: Uri) {
        val session = viewModel.draftSession.value ?: return
        LoadingOverlayHelper.show(loadingOverlay)
        
        lifecycleScope.launch {
            try {
                val dbProds = withContext(Dispatchers.IO) {
                    ImportValidationUseCase().fetchExistingProducts(session.storeId)
                }
                val existingProducts = dbProds.map { it.name.lowercase() }.toSet()

                val imageBytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        contentResolver.openInputStream(uri)?.use { boundsStream ->
                            BitmapFactory.decodeStream(boundsStream, null, options)
                        }
                        
                        var scale = 1
                        val limit = 1500
                        if (options.outWidth > limit || options.outHeight > limit) {
                            scale = Math.max(options.outWidth / limit, options.outHeight / limit)
                        }
                        
                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = scale
                        }
                        
                        val bitmap = BitmapFactory.decodeStream(inputStream, null, decodeOptions)
                        
                        if (bitmap != null) {
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                            val bytes = outputStream.toByteArray()
                            bitmap.recycle()
                            bytes
                        } else {
                            null
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                    if (imageBytes != null) {
                        AiParsingDialogHelper(
                            activity = this@AddMultipleItemsActivity,
                            coroutineScope = lifecycleScope,
                            rawText = null,
                            categoryIdByName = categoryIdByName,
                            existingProductNames = existingProducts,
                            imageBytes = imageBytes,
                            mimeType = "image/jpeg",
                            onSuccess = { parseResult ->
                                cleanupTempPhotoFile()
                                onParsingSuccess(parseResult, dbProds)
                            },
                            onCancel = {
                                cleanupTempPhotoFile()
                            }
                        ).show()
                    } else {
                        cleanupTempPhotoFile()
                        Toast.makeText(this@AddMultipleItemsActivity, "Failed to load image from photo.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    LoadingOverlayHelper.hide(loadingOverlay)
                    cleanupTempPhotoFile()
                    Toast.makeText(this@AddMultipleItemsActivity, "Error processing image: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- CATEGORY GROUPED ADAPTER FOR SIMPLE MODE ---
    inner class SimpleCategoryAdapter(
        private val onDataChanged: () -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val TYPE_HEADER = 0
        private val TYPE_ROW = 1

        override fun getItemViewType(position: Int): Int {
            return when (simpleModeItems[position]) {
                is SimpleModeItem.Header -> TYPE_HEADER
                is SimpleModeItem.Row -> TYPE_ROW
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                val v = inflater.inflate(R.layout.item_simple_category_header, parent, false)
                HeaderViewHolder(v)
            } else {
                val v = inflater.inflate(R.layout.item_simple_import_row, parent, false)
                RowViewHolder(v)
            }
        }

        inner class HeaderViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvCategoryName: TextView = v.findViewById(R.id.tvCategoryName)
            val btnAddItemInner: AppCompatButton = v.findViewById(R.id.btnAddItemInner)
        }

        inner class RowViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvRowIndex: TextView = v.findViewById(R.id.tvRowIndex)
            val btnDelete: ImageView = v.findViewById(R.id.btnDelete)
            val cbMakePublic: android.widget.CheckBox = v.findViewById(R.id.cbMakePublic)
            val inputProductName: EditText = v.findViewById(R.id.inputProductName)
            val inputPrice: EditText = v.findViewById(R.id.inputPrice)
            val inputUnit: EditText = v.findViewById(R.id.inputUnit)
            val inputDescription: EditText = v.findViewById(R.id.inputDescription)
            val tvErrorText: TextView = v.findViewById(R.id.tvErrorText)
        }

        override fun getItemCount(): Int = simpleModeItems.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = simpleModeItems[position]) {
                is SimpleModeItem.Header -> {
                    val hHolder = holder as HeaderViewHolder
                    val category = item.category
                    val isExpanded = expandedCategoryIds.contains(category.draftCategoryId)
                    
                    val displayName = if (isExpanded) "▼  ${category.name}" else "▶  ${category.name}"
                    hHolder.tvCategoryName.text = displayName
                    
                    hHolder.tvCategoryName.setOnClickListener {
                        val currentPos = holder.adapterPosition
                        if (currentPos != RecyclerView.NO_POSITION) {
                            val targetItem = simpleModeItems[currentPos] as? SimpleModeItem.Header ?: return@setOnClickListener
                            val cat = targetItem.category
                            if (expandedCategoryIds.contains(cat.draftCategoryId)) {
                                expandedCategoryIds.remove(cat.draftCategoryId)
                            } else {
                                expandedCategoryIds.clear() // Accordion style
                                expandedCategoryIds.add(cat.draftCategoryId)
                            }
                            rebuildSimpleModeItems()
                            notifyDataSetChanged()

                            val headerIndex = simpleModeItems.indexOfFirst { it is SimpleModeItem.Header && it.category.draftCategoryId == cat.draftCategoryId }
                            if (headerIndex != -1) {
                                (simpleRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(headerIndex, 0)
                            }
                            updateStickyHeader()
                        }
                    }

                    hHolder.btnAddItemInner.setOnClickListener {
                        val currentPos = holder.adapterPosition
                        if (currentPos != RecyclerView.NO_POSITION) {
                            val targetItem = simpleModeItems[currentPos] as? SimpleModeItem.Header ?: return@setOnClickListener
                            val cat = targetItem.category
                            if (!isCategoryFullyCompleted(cat)) {
                                Toast.makeText(this@AddMultipleItemsActivity, "Please complete the current item fields first.", Toast.LENGTH_SHORT).show()
                                return@setOnClickListener
                            }
                            cat.items.add(0, createBlankItem(cat.name, cat.categoryId))
                            
                            expandedCategoryIds.add(cat.draftCategoryId)
                            rebuildSimpleModeItems()
                            notifyDataSetChanged()
                            onDataChanged()

                            val headerIndex = simpleModeItems.indexOfFirst { it is SimpleModeItem.Header && it.category.draftCategoryId == cat.draftCategoryId }
                            if (headerIndex != -1) {
                                (simpleRecyclerView.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(headerIndex, 0)
                            }
                            updateStickyHeader()
                        }
                    }
                }
                is SimpleModeItem.Row -> {
                    val rHolder = holder as RowViewHolder
                    val category = item.category
                    val index = item.index
                    val draftItem = item.item
                    
                    val oldWatcher = rHolder.itemView.tag as? TextWatcher
                    if (oldWatcher != null) {
                        rHolder.inputProductName.removeTextChangedListener(oldWatcher)
                        rHolder.inputPrice.removeTextChangedListener(oldWatcher)
                        rHolder.inputUnit.removeTextChangedListener(oldWatcher)
                        rHolder.inputDescription.removeTextChangedListener(oldWatcher)
                    }

                    rHolder.tvRowIndex.text = "#${index + 1}"
                    rHolder.inputProductName.setText(draftItem.productName)
                    rHolder.inputPrice.setText(draftItem.priceText)
                    rHolder.inputUnit.setText(draftItem.unit)
                    rHolder.inputDescription.setText(draftItem.description ?: "")

                    rHolder.cbMakePublic.setOnCheckedChangeListener(null)
                    rHolder.cbMakePublic.isChecked = draftItem.isPublic
                    rHolder.cbMakePublic.setOnCheckedChangeListener { _, isChecked ->
                        val curPos = rHolder.adapterPosition
                        if (curPos != RecyclerView.NO_POSITION) {
                            val rowItem = simpleModeItems[curPos] as? SimpleModeItem.Row ?: return@setOnCheckedChangeListener
                            val cat = rowItem.category
                            val idx = rowItem.index
                            if (idx in cat.items.indices) {
                                cat.items[idx] = cat.items[idx].copy(isPublic = isChecked)
                                onDataChanged()
                            }
                        }
                    }

                    renderValidationErrors(rHolder.tvErrorText, draftItem.validationErrors)

                    val isIncomplete = draftItem.productName.trim().isEmpty() ||
                            draftItem.priceText.trim().isEmpty() ||
                            draftItem.unit.trim().isEmpty()

                    if (isIncomplete) {
                        rHolder.itemView.setBackgroundResource(R.drawable.bg_item_orange_stroke)
                        rHolder.itemView.backgroundTintList = null
                    } else {
                        rHolder.itemView.setBackgroundResource(R.drawable.bg_dialog_custom)
                        rHolder.itemView.backgroundTintList = null
                    }

                    rHolder.btnDelete.setOnClickListener {
                        if (index in category.items.indices) {
                            category.items.removeAt(index)
                            rebuildSimpleModeItems()
                            notifyDataSetChanged()
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
                                inputProductName = rHolder.inputProductName,
                                inputPrice = rHolder.inputPrice,
                                inputUnit = rHolder.inputUnit,
                                inputDescription = rHolder.inputDescription,
                                tvErrorText = rHolder.tvErrorText,
                                itemView = rHolder.itemView
                            )
                        }
                    }

                    rHolder.itemView.tag = rowWatcher
                    rHolder.inputProductName.addTextChangedListener(rowWatcher)
                    rHolder.inputPrice.addTextChangedListener(rowWatcher)
                    rHolder.inputUnit.addTextChangedListener(rowWatcher)
                    rHolder.inputDescription.addTextChangedListener(rowWatcher)
                }
            }
        }

        private fun syncItemFromInputs(
            category: DraftCategory,
            index: Int,
            inputProductName: EditText,
            inputPrice: EditText,
            inputUnit: EditText,
            inputDescription: EditText,
            tvErrorText: TextView,
            itemView: View
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

            val isIncomplete = productName.isEmpty() || priceText.isEmpty() || unitVal.isEmpty()
            if (isIncomplete) {
                itemView.setBackgroundResource(R.drawable.bg_item_orange_stroke)
                itemView.backgroundTintList = null
            } else {
                itemView.setBackgroundResource(R.drawable.bg_dialog_custom)
                itemView.backgroundTintList = null
            }

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
