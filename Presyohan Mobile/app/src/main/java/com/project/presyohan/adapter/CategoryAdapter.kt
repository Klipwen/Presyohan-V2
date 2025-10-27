package com.project.presyohan.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.AppCompatButton
import com.project.presyohan.R
import com.project.presyohan.adapter.ItemFormAdapter
import com.project.presyohan.adapter.ItemFormData

// Data model for a category and its items
class CategoryWithItems(
    val categoryName: String,
    val items: MutableList<ItemFormData>
)

class CategoryAdapter(
    private val categories: MutableList<CategoryWithItems>,
    private val onAddItem: (Int) -> Unit, // category position
    private val onDeleteItem: (Int, Int) -> Unit // category position, item position
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_card, parent, false)
        return CategoryViewHolder(view)
    }

    override fun getItemCount(): Int = categories.size

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position], position)
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val header: TextView = itemView.findViewById(R.id.textCategoryHeader)
        private val recyclerView: RecyclerView = itemView.findViewById(R.id.recyclerViewItems)
        private val buttonAddItem: AppCompatButton = itemView.findViewById(R.id.buttonAddItem)
        private lateinit var itemAdapter: ItemFormAdapter

        fun bind(category: CategoryWithItems, categoryPosition: Int) {
            header.text = category.categoryName.uppercase()
            itemAdapter = ItemFormAdapter(category.items) { itemPos ->
                onDeleteItem(categoryPosition, itemPos)
            }
            recyclerView.layoutManager = LinearLayoutManager(itemView.context)
            recyclerView.adapter = itemAdapter
            buttonAddItem.setOnClickListener {
                onAddItem(categoryPosition)
            }
        }
    }
} 