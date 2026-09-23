package com.presyohan.app.adapter

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.R

class CategoryTabAdapter(
    private var categories: List<String>,
    private var selectedCategory: String?,
    private val onCategorySelected: (String?) -> Unit
) : RecyclerView.Adapter<CategoryTabAdapter.CategoryTabViewHolder>() {

    private var selectedIndex: Int = 0

    init {
        updateSelectedIndex()
    }

    private fun updateSelectedIndex() {
        val current = selectedCategory?.trim()
        if (current.isNullOrEmpty() || current.equals("ALL ITEMS", ignoreCase = true) || current.equals("PRICELIST", ignoreCase = true)) {
            selectedIndex = 0
        } else {
            val idx = categories.indexOfFirst { it.equals(current, ignoreCase = true) }
            selectedIndex = if (idx >= 0) idx else 0
        }
    }

    fun updateCategories(newCategories: List<String>, newSelectedCategory: String?) {
        categories = newCategories
        selectedCategory = newSelectedCategory
        updateSelectedIndex()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryTabViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_tab, parent, false)
        return CategoryTabViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryTabViewHolder, position: Int) {
        val categoryName = categories[position]
        val isSelected = position == selectedIndex

        holder.tabTitle.text = categoryName.uppercase()

        if (isSelected) {
            val orangeColor = ContextCompat.getColor(holder.itemView.context, R.color.presyo_orange)
            holder.tabTitle.setTextColor(orangeColor)
            holder.tabTitle.setTypeface(null, Typeface.BOLD)
            holder.tabIndicator.visibility = View.VISIBLE
        } else {
            holder.tabTitle.setTextColor(Color.parseColor("#888888"))
            holder.tabTitle.setTypeface(null, Typeface.NORMAL)
            holder.tabIndicator.visibility = View.INVISIBLE
        }

        holder.itemView.setOnClickListener {
            if (selectedIndex != position) {
                val previousIndex = selectedIndex
                selectedIndex = position
                notifyItemChanged(previousIndex)
                notifyItemChanged(selectedIndex)

                val selectedName = if (position == 0) null else categories[position]
                selectedCategory = selectedName
                onCategorySelected(selectedName)
            }
        }
    }

    override fun getItemCount(): Int = categories.size

    class CategoryTabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tabTitle: TextView = itemView.findViewById(R.id.tabTitle)
        val tabIndicator: View = itemView.findViewById(R.id.tabIndicator)
    }
}
