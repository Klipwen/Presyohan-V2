package com.presyohan.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.HomeActivity.ProductGroup
import com.presyohan.app.R

class CategoryGroupAdapter(
    private var groups: List<ProductGroup>,
    private var userRole: String?,
    private var activeOverlayProductId: String?,
    private val onEditClick: (Product) -> Unit,
    private val onDeleteClick: (Product) -> Unit,
    private val onItemLongPressed: (Product) -> Unit,
    private val onItemClicked: () -> Unit
) : RecyclerView.Adapter<CategoryGroupAdapter.CategoryViewHolder>() {

    private val childAdapters = java.util.Collections.newSetFromMap(java.util.WeakHashMap<ProductChildAdapter, Boolean>())

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val categoryName: TextView = itemView.findViewById(R.id.textCategoryName)
        val itemCount: TextView = itemView.findViewById(R.id.textItemCount)
        val childRecyclerView: RecyclerView = itemView.findViewById(R.id.childRecyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_card, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val group = groups[position]
        holder.categoryName.text = group.categoryName
        holder.itemCount.text = "${group.itemCount} ${if (group.itemCount == 1) "item" else "items"}"

        // Set up child RecyclerView
        if (holder.childRecyclerView.layoutManager == null) {
            holder.childRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
        }
        holder.childRecyclerView.isNestedScrollingEnabled = false // Crucial for performance
        
        val existingAdapter = holder.childRecyclerView.adapter as? ProductChildAdapter
        if (existingAdapter != null) {
            existingAdapter.updateData(group.products, userRole, activeOverlayProductId)
            childAdapters.add(existingAdapter)
        } else {
            val childAdapter = ProductChildAdapter(
                products = group.products,
                userRole = userRole,
                activeOverlayProductId = activeOverlayProductId,
                onEditClick = onEditClick,
                onDeleteClick = onDeleteClick,
                onItemLongPressed = onItemLongPressed,
                onItemClicked = onItemClicked
            )
            holder.childRecyclerView.adapter = childAdapter
            childAdapters.add(childAdapter)
        }
    }

    override fun getItemCount(): Int = groups.size

    fun updateGroups(newGroups: List<ProductGroup>, newOverlayProductId: String?) {
        groups = newGroups
        activeOverlayProductId = newOverlayProductId
        notifyDataSetChanged()
    }

    fun updateActiveOverlayProductId(newId: String?) {
        activeOverlayProductId = newId
        for (adapter in childAdapters) {
            adapter.updateActiveOverlayProductId(newId)
        }
    }

    fun setUserRole(role: String?) {
        userRole = role
        notifyDataSetChanged()
    }
}
