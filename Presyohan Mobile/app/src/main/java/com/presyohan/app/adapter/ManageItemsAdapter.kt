package com.presyohan.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.R

// Data model for an item
class ManageItemData(
    val id: String,
    var name: String,
    var unit: String,
    var price: Double,
    var description: String,
    var category: String,
    var is_public: Boolean = false
)

class ManageItemsAdapter(
    private var rawItems: List<ManageItemData>,
    private val listener: OnManageItemsListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface OnManageItemsListener {
        fun onProductEditClick(item: ManageItemData)
        fun onProductDeleteClick(item: ManageItemData)
        fun onProductInfoClick(item: ManageItemData)
        fun onCategoryRenameClick(category: String)
        fun onCategoryDeleteClick(category: String)
        fun onAddItemClick(category: String)
        fun onSelectionChanged()
        fun onProductLongClick(item: ManageItemData)
        fun onCategoryLongClick(category: String)
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_PRODUCT = 1
    }

    sealed class FlatItem {
        data class CategoryHeader(
            val categoryName: String,
            val categoryNumber: Int,
            val itemCount: Int
        ) : FlatItem()

        data class ProductCard(
            val product: ManageItemData,
            val productNumber: Int
        ) : FlatItem()
    }

    private var flatItems: List<FlatItem> = emptyList()
    var isSelectionMode: Boolean = false
        private set
    val selectedProductIds = mutableSetOf<String>()

    init {
        rebuildFlatItems()
    }

    private fun rebuildFlatItems() {
        val result = mutableListOf<FlatItem>()
        // Group by category, ignore case/whitespace for grouping
        val grouped = rawItems.groupBy { it.category.trim() }
        val sortedCategories = grouped.keys.sortedBy { it.lowercase() }

        var catNumber = 1
        for (category in sortedCategories) {
            val products = grouped[category].orEmpty().sortedBy { it.name.lowercase() }
            if (category.isNotBlank() || products.isNotEmpty()) {
                val displayName = if (category.isBlank()) "Uncategorized" else category
                result.add(FlatItem.CategoryHeader(displayName, catNumber, products.size))

                var prodNumber = 1
                for (product in products) {
                    result.add(FlatItem.ProductCard(product, prodNumber))
                    prodNumber++
                }

                catNumber++
            }
        }
        flatItems = result
        notifyDataSetChanged()
    }

    fun updateItems(newItems: List<ManageItemData>) {
        this.rawItems = newItems
        rebuildFlatItems()
    }

    fun setSelectionMode(enabled: Boolean) {
        this.isSelectionMode = enabled
        if (!enabled) {
            selectedProductIds.clear()
        }
        rebuildFlatItems()
    }

    fun selectAll() {
        selectedProductIds.clear()
        selectedProductIds.addAll(rawItems.map { it.id })
        notifyDataSetChanged()
        listener.onSelectionChanged()
    }

    fun clearSelection() {
        selectedProductIds.clear()
        notifyDataSetChanged()
        listener.onSelectionChanged()
    }

    fun getSelectedItems(): List<ManageItemData> {
        return rawItems.filter { selectedProductIds.contains(it.id) }
    }

    override fun getItemViewType(position: Int): Int {
        return when (flatItems[position]) {
            is FlatItem.CategoryHeader -> VIEW_TYPE_HEADER
            is FlatItem.ProductCard -> VIEW_TYPE_PRODUCT
        }
    }

    override fun getItemCount(): Int = flatItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = inflater.inflate(R.layout.item_manage_category_header, parent, false)
                CategoryHeaderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_manage_product_card, parent, false)
                ProductCardViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = flatItems[position]) {
            is FlatItem.CategoryHeader -> (holder as CategoryHeaderViewHolder).bind(item)
            is FlatItem.ProductCard -> (holder as ProductCardViewHolder).bind(item)
        }
    }

    inner class CategoryHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textCatNumber: TextView = view.findViewById(R.id.textCategoryNumber)
        private val textCatName: TextView = view.findViewById(R.id.textCategoryName)
        private val textCount: TextView = view.findViewById(R.id.textCategoryItemCount)
        private val layoutNormal: View = view.findViewById(R.id.layoutNormalActions)
        private val btnRename: View = view.findViewById(R.id.btnRenameCategory)
        private val btnDelete: View = view.findViewById(R.id.btnDeleteCategory)
        private val checkbox: ImageView = view.findViewById(R.id.checkboxCategory)
        private val btnAddItem: View = view.findViewById(R.id.btnAddItemCategory)
        private val divider: View = view.findViewById(R.id.categoryDivider)

        fun bind(item: FlatItem.CategoryHeader) {
            textCatNumber.text = item.categoryNumber.toString()
            textCatName.text = item.categoryName
            textCount.text = "${item.itemCount} items"

            // Hide the divider for the first category section, show for subsequent ones
            divider.visibility = if (item.categoryNumber == 1) View.GONE else View.VISIBLE

            if (isSelectionMode) {
                layoutNormal.visibility = View.GONE
                checkbox.visibility = View.VISIBLE
                btnAddItem.visibility = View.GONE

                // Determine if all items in this category are selected
                val catProducts = rawItems.filter { it.category.trim() == item.categoryName.trim() }
                val allSelected = catProducts.isNotEmpty() && catProducts.all { selectedProductIds.contains(it.id) }

                checkbox.setImageResource(
                    if (allSelected) R.drawable.ic_radio_checked_orange else R.drawable.ic_radio_unchecked
                )

                itemView.setOnClickListener {
                    if (allSelected) {
                        // Deselect all in category
                        catProducts.forEach { selectedProductIds.remove(it.id) }
                    } else {
                        // Select all in category
                        catProducts.forEach { selectedProductIds.add(it.id) }
                    }
                    notifyDataSetChanged()
                    listener.onSelectionChanged()
                }
            } else {
                layoutNormal.visibility = View.VISIBLE
                checkbox.visibility = View.GONE
                btnAddItem.visibility = View.VISIBLE
                itemView.setOnClickListener(null)

                btnRename.setOnClickListener { listener.onCategoryRenameClick(item.categoryName) }
                btnDelete.setOnClickListener { listener.onCategoryDeleteClick(item.categoryName) }
                btnAddItem.setOnClickListener { listener.onAddItemClick(item.categoryName) }
            }

            itemView.setOnLongClickListener {
                listener.onCategoryLongClick(item.categoryName)
                true
            }
        }
    }

    inner class ProductCardViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textNumber: TextView = view.findViewById(R.id.textProductNumber)
        private val textName: TextView = view.findViewById(R.id.textProductName)
        private val iconGlobe: ImageView = view.findViewById(R.id.iconPublicGlobe)
        private val textDescription: TextView = view.findViewById(R.id.textProductDescription)
        private val textPrice: TextView = view.findViewById(R.id.textProductPrice)
        private val textUnit: TextView = view.findViewById(R.id.textProductUnit)
        private val layoutNormal: View = view.findViewById(R.id.layoutNormalActions)
        private val btnEdit: View = view.findViewById(R.id.btnEditProduct)
        private val btnDelete: View = view.findViewById(R.id.btnDeleteProduct)
        private val checkbox: ImageView = view.findViewById(R.id.checkboxProduct)

        fun bind(item: FlatItem.ProductCard) {
            val product = item.product
            textNumber.text = item.productNumber.toString()
            textName.text = product.name
            iconGlobe.visibility = if (product.is_public) View.VISIBLE else View.GONE
            textDescription.text = product.description.ifBlank { "No description" }
            textPrice.text = "₱%,.2f".format(java.util.Locale.US, product.price)
            textUnit.text = product.unit.ifBlank { "pcs" }

            if (isSelectionMode) {
                layoutNormal.visibility = View.GONE
                checkbox.visibility = View.VISIBLE

                val isSelected = selectedProductIds.contains(product.id)
                checkbox.setImageResource(
                    if (isSelected) R.drawable.ic_radio_checked_orange else R.drawable.ic_radio_unchecked
                )

                itemView.setOnClickListener {
                    if (isSelected) {
                        selectedProductIds.remove(product.id)
                    } else {
                        selectedProductIds.add(product.id)
                    }
                    notifyItemChanged(adapterPosition)
                    // We also need to notify headers to update their checked status
                    notifyDataSetChanged()
                    listener.onSelectionChanged()
                }
            } else {
                layoutNormal.visibility = View.VISIBLE
                checkbox.visibility = View.GONE
                itemView.setOnClickListener(null)

                btnEdit.setOnClickListener { listener.onProductEditClick(product) }
                btnDelete.setOnClickListener { listener.onProductDeleteClick(product) }
            }

            itemView.setOnLongClickListener {
                listener.onProductLongClick(product)
                true
            }
        }
    }
}