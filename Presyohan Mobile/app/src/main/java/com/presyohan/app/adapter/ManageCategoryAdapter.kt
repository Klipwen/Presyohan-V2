package com.presyohan.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.R

class ManageCategoryAdapter(
    private var categories: List<String>,
    private var itemCounts: Map<String, Int> = emptyMap(),
    private var publicCategories: Set<String> = emptySet(),
    private val onViewItems: ((String) -> Unit)? = null,
    private val onRename: ((String) -> Unit)? = null,
    private val onDelete: ((String) -> Unit)? = null,
    private val onCopy: ((String) -> Unit)? = null,
    private val onConvert: ((String) -> Unit)? = null,
    private val onPublicToggle: ((String, Boolean) -> Unit)? = null
) : RecyclerView.Adapter<ManageCategoryAdapter.CategoryViewHolder>() {

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.textCategoryName)
        val itemCount: TextView = itemView.findViewById(R.id.textItemCount)
        val btnCopy: View = itemView.findViewById(R.id.btnCopyCategory)
        val btnConvert: View = itemView.findViewById(R.id.btnConvertCategory)
        val layoutPublic: View = itemView.findViewById(R.id.layoutPublicContainer)
        val checkboxPublic: ImageView = itemView.findViewById(R.id.checkboxPublic)
        val btnViewItems: TextView = itemView.findViewById(R.id.btnViewItems)
        val btnRename: TextView = itemView.findViewById(R.id.btnRename)
        val btnDelete: TextView = itemView.findViewById(R.id.btnDelete)
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

        val isPublic = publicCategories.contains(category)
        if (isPublic) {
            holder.checkboxPublic.setImageResource(R.drawable.ic_radio_checked_orange)
        } else {
            holder.checkboxPublic.setImageResource(R.drawable.ic_radio_unchecked)
        }

        holder.btnCopy.setOnClickListener { onCopy?.invoke(category) }
        holder.btnConvert.setOnClickListener { onConvert?.invoke(category) }
        holder.layoutPublic.setOnClickListener {
            onPublicToggle?.invoke(category, !isPublic)
        }

        holder.btnViewItems.setOnClickListener { onViewItems?.invoke(category) }
        holder.btnRename.setOnClickListener { onRename?.invoke(category) }
        holder.btnDelete.setOnClickListener { onDelete?.invoke(category) }
    }

    override fun getItemCount(): Int = categories.size

    fun updateCategories(
        newCategories: List<String>,
        newCounts: Map<String, Int> = itemCounts,
        newPublic: Set<String> = publicCategories
    ) {
        categories = newCategories
        itemCounts = newCounts
        publicCategories = newPublic
        notifyDataSetChanged()
    }
}