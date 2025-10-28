package com.presyohan.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.R
import android.widget.LinearLayout

// Data model for an item
class ManageItemData(
    val id: String,
    var name: String,
    var unit: String,
    var price: Double,
    var description: String,
    var category: String
)

class ManageItemsAdapter(
    private var items: List<ManageItemData>,
    private val onItemChanged: (Int, ManageItemData) -> Unit,
    private val onDelete: (Int, ManageItemData) -> Unit
) : RecyclerView.Adapter<ManageItemsAdapter.ManageItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ManageItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_manage_item, parent, false)
        return ManageItemViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ManageItemViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    fun updateItems(newItems: List<ManageItemData>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ManageItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val name: EditText = itemView.findViewById(R.id.inputItemName)
        private val unit: EditText = itemView.findViewById(R.id.inputItemUnit)
        private val price: EditText = itemView.findViewById(R.id.inputItemPrice)
        private val description: EditText = itemView.findViewById(R.id.inputItemDescription)
        private val priceDisplay: TextView? = itemView.findViewById(R.id.priceDisplay)
        private val btnDelete: LinearLayout = itemView.findViewById(R.id.btnDeleteItem)

        fun bind(item: ManageItemData, position: Int) {
            name.setText(item.name)
            unit.setText(item.unit)
            price.setText(String.format("%.2f", item.price))
            description.setText(item.description)
            // Listen for changes
            name.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) { item.name = name.text.toString(); onItemChanged(position, item) } }
            unit.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) { item.unit = unit.text.toString(); onItemChanged(position, item) } }
            price.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) { item.price = price.text.toString().toDoubleOrNull() ?: 0.0; onItemChanged(position, item) } }
            description.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) { item.description = description.text.toString(); onItemChanged(position, item) } }
            btnDelete.setOnClickListener { onDelete(position, item) }
        }
    }
}