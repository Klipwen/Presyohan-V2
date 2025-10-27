package com.project.presyohan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.project.presyohan.R

class ManageCategoryAdapter(
    private var categories: List<String>,
    private var itemCounts: Map<String, Int> = emptyMap(),
    private val onViewItems: ((String) -> Unit)? = null,
    private val onRename: ((String) -> Unit)? = null,
    private val onDelete: ((String) -> Unit)? = null
) : RecyclerView.Adapter<ManageCategoryAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.textCategoryName)
        val itemCount: TextView = itemView.findViewById(R.id.textItemCount)
        val btnViewItems: Button = itemView.findViewById(R.id.btnViewItems)
        val btnRename: Button = itemView.findViewById(R.id.btnRename)
        val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_manage, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.name.text = category
        val count = itemCounts[category] ?: 0
        holder.itemCount.text = "$count item" + if (count == 1) "" else "s"
        holder.btnViewItems.setOnClickListener { onViewItems?.invoke(category) }
        holder.btnRename.setOnClickListener { onRename?.invoke(category) }
        holder.btnDelete.setOnClickListener { onDelete?.invoke(category) }
    }

    override fun getItemCount(): Int = categories.size

    fun updateCategories(newCategories: List<String>, newCounts: Map<String, Int> = itemCounts) {
        categories = newCategories
        itemCounts = newCounts
        notifyDataSetChanged()
    }
} 