package com.presyohan.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive

class AddMultipleItemsActivity : AppCompatActivity() {

    // UI Components
    private lateinit var viewFlipper: ViewFlipper
    private lateinit var inputRawText: android.widget.EditText
    private lateinit var btnPreview: MaterialButton
    private lateinit var btnEditRaw: MaterialButton
    private lateinit var btnConfirmImport: MaterialButton
    private lateinit var previewRecyclerView: RecyclerView
    private lateinit var previewSummary: TextView
    private lateinit var btnBack: ImageView
    private lateinit var loadingOverlay: View

    // Data
    private var storeId: String? = null
    private var storeName: String? = null

    // Logic
    private var parsedItems: List<PreviewItem> = emptyList()
    private var parsedCategories: List<ParsedCategory> = emptyList()
    private val categoryIdByName = mutableMapOf<String, String>()
    private var existingProductNames = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_multiple_items)

        loadingOverlay = LoadingOverlayHelper.attach(this)
        storeId = intent.getStringExtra("storeId")
        storeName = intent.getStringExtra("storeName")

        initViews()
        setupDrawer()

        // Initial Fetch for "Update" detection
        fetchExistingData()
    }

    private fun initViews() {
        viewFlipper = findViewById(R.id.viewFlipper)
        inputRawText = findViewById(R.id.inputRawText)
        btnPreview = findViewById(R.id.btnPreview)
        btnEditRaw = findViewById(R.id.btnEditRaw)
        btnConfirmImport = findViewById(R.id.btnConfirmImport)
        previewRecyclerView = findViewById(R.id.previewRecyclerView)
        previewSummary = findViewById(R.id.previewSummary)
        btnBack = findViewById(R.id.btnBack)

        previewRecyclerView.layoutManager = LinearLayoutManager(this)

        btnBack.setOnClickListener { onBackPressed() }

        btnPreview.setOnClickListener {
            val raw = inputRawText.text.toString()
            if (raw.isBlank()) {
                Toast.makeText(this, "Please type or paste items first.", Toast.LENGTH_SHORT).show()
            } else {
                performParse(raw)
            }
        }

        btnEditRaw.setOnClickListener {
            viewFlipper.displayedChild = 0 // Go back to input
        }

        btnConfirmImport.setOnClickListener {
            performImport()
        }
    }

    // --- LOGIC: Parsing ---

    private fun performParse(rawText: String) {
        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch(Dispatchers.Default) {
            val categories = AddMultipleItemsParser.parseRawToCategories(rawText, existingProductNames)

            // Build preview list from parsed categories (include invalid items for visibility)
            val finalDisplayList = mutableListOf<PreviewItem>()
            var validCount = 0
            categories.forEach { cat ->
                finalDisplayList.add(PreviewItem.Header(cat.name))
                cat.items.forEach { itm ->
                    finalDisplayList.add(
                        PreviewItem.Product(
                            name = itm.name,
                            description = itm.description,
                            price = itm.price ?: 0.0,
                            unit = itm.unit,
                            category = cat.name,
                            status = itm.status
                        )
                    )
                    if (itm.status == ItemStatus.NEW || itm.status == ItemStatus.UPDATE) validCount++
                }
            }

            parsedCategories = categories
            parsedItems = finalDisplayList

            withContext(Dispatchers.Main) {
                LoadingOverlayHelper.hide(loadingOverlay)
                updatePreviewUI(finalDisplayList, validCount, categories.size)
            }
        }
    }

    private fun updatePreviewUI(items: List<PreviewItem>, validItems: Int, catCount: Int) {
        previewSummary.text = "Found $validItems valid items in $catCount categories."
        previewRecyclerView.adapter = PreviewAdapter(items)
        viewFlipper.displayedChild = 1 // Show Preview
    }

    // --- LOGIC: Importing ---

    private fun performImport() {
        // Count valid items
        val validItemsCount = parsedCategories.sumOf { cat ->
            cat.items.count { it.status == ItemStatus.NEW || it.status == ItemStatus.UPDATE }
        }
        if (validItemsCount == 0) {
            Toast.makeText(this, "No valid items to save.", Toast.LENGTH_SHORT).show()
            return
        }

        LoadingOverlayHelper.show(loadingOverlay)
        lifecycleScope.launch {
            try {
                val sId = storeId ?: return@launch
                val manager = ImportManager(SupabaseImportRepository())
                val result = manager.performImport(sId, parsedCategories, categoryIdByName)

                val msg = "Saved ${result.savedCount} of ${result.attemptedCount} items across ${result.categoryCount} categories."
                Toast.makeText(this@AddMultipleItemsActivity, msg, Toast.LENGTH_LONG).show()
                if (result.failures.isNotEmpty()) {
                    android.util.Log.w("Import", "Failures: ${result.failures.map { it.first.name to it.second }}")
                }

                // Return to Home
                val intent = Intent(this@AddMultipleItemsActivity, HomeActivity::class.java)
                intent.putExtra("storeId", storeId)
                intent.putExtra("storeName", storeName)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@AddMultipleItemsActivity, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                LoadingOverlayHelper.hide(loadingOverlay)
            }
        }
    }

    // --- HELPERS ---

    private fun fetchExistingData() {
        val sId = storeId ?: return
        lifecycleScope.launch {
            try {
                // Fetch Categories
                @Serializable data class CatRow(val category_id: String, val name: String)
                val cats = SupabaseProvider.client.postgrest.rpc(
                    "get_user_categories",
                    buildJsonObject { put("p_store_id", JsonPrimitive(sId)) }
                ).decodeList<CatRow>()
                cats.forEach { categoryIdByName[it.name] = it.category_id }

                // Fetch Products for Update Detection
                @Serializable data class ProdRow(val name: String)
                val prods = SupabaseProvider.client.postgrest["products"]
                    .select(Columns.list("name")) { filter { eq("store_id", sId) } }
                    .decodeList<ProdRow>()

                existingProductNames.clear()
                existingProductNames.addAll(prods.map { it.name.lowercase() })

            } catch (e: Exception) {
                // Silent fail, just means status checks might be inaccurate
            }
        }
    }

    private fun setupDrawer() {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        findViewById<ImageView>(R.id.menuIcon).setOnClickListener { drawerLayout.open() }

        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_stores -> startActivity(Intent(this, StoreActivity::class.java))
                R.id.nav_logout -> { /* Handle logout */ }
            }
            drawerLayout.close()
            true
        }
    }

    override fun onBackPressed() {
        if (viewFlipper.displayedChild == 1) {
            // If in preview, go back to edit
            viewFlipper.displayedChild = 0
        } else {
            super.onBackPressed()
        }
    }

    // --- INNER CLASSES FOR PREVIEW ---

    sealed class PreviewItem {
        data class Header(val title: String) : PreviewItem()
        data class Product(
            val name: String,
            val description: String?,
            val price: Double,
            val unit: String,
            val category: String,
            val status: ItemStatus
        ) : PreviewItem()
    }

    inner class PreviewAdapter(private val items: List<PreviewItem>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        inner class HeaderHolder(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(android.R.id.text1)
        }

        inner class ProductHolder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.itemName)
            val details: TextView = v.findViewById(R.id.itemDetails)
            val price: TextView = v.findViewById(R.id.itemPrice)
            val tag: TextView = v.findViewById(R.id.itemTag)
        }

        override fun getItemViewType(position: Int): Int = when(items[position]) {
            is PreviewItem.Header -> 0
            is PreviewItem.Product -> 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                val v = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
                v.findViewById<TextView>(android.R.id.text1).apply {
                    setTextColor(Color.parseColor("#E65100")) // Presyo Orange
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textSize = 18f
                }
                HeaderHolder(v)
            } else {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_product_simple, parent, false)
                ProductHolder(v)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is HeaderHolder) {
                holder.title.text = (items[position] as PreviewItem.Header).title
            } else if (holder is ProductHolder) {
                val item = items[position] as PreviewItem.Product
                holder.name.text = item.name
                holder.details.text = "${item.unit} ${if(item.description != null) "(${item.description})" else ""}"
                holder.price.text = "₱${String.format("% ,.2f", item.price)}"

                // Status Tag Logic
                when(item.status) {
                    ItemStatus.NEW -> {
                        holder.tag.text = "NEW"
                        holder.tag.setTextColor(Color.parseColor("#4CAF50")) // Green
                        holder.tag.visibility = View.VISIBLE
                    }
                    ItemStatus.UPDATE -> {
                        holder.tag.text = "UPDATE"
                        holder.tag.setTextColor(Color.parseColor("#FF9800")) // Orange
                        holder.tag.visibility = View.VISIBLE
                    }
                    ItemStatus.DUPLICATE -> {
                        holder.tag.text = "DUPLICATE (In List)"
                        holder.tag.setTextColor(Color.GRAY)
                        holder.tag.visibility = View.VISIBLE
                    }
                    ItemStatus.ERROR_NO_PRICE -> {
                        holder.tag.text = "MISSING PRICE"
                        holder.tag.setTextColor(Color.RED)
                        holder.tag.visibility = View.VISIBLE
                    }
                    ItemStatus.ERROR_INVALID_FORMAT -> {
                        holder.tag.text = "INVALID FORMAT"
                        holder.tag.setTextColor(Color.RED)
                        holder.tag.visibility = View.VISIBLE
                    }
                    ItemStatus.ERROR_NO_CATEGORY -> {
                        holder.tag.text = "NO CATEGORY CONTEXT"
                        holder.tag.setTextColor(Color.RED)
                        holder.tag.visibility = View.VISIBLE
                    }
                    else -> holder.tag.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int = items.size
    }
}

