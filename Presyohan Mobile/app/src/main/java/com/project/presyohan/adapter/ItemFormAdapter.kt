package com.project.presyohan.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.project.presyohan.R
import android.text.Editable
import android.text.TextWatcher

// Data model for an item form
class ItemFormData(
    var itemNumber: Int,
    var name: String = "",
    var unit: String = "",
    var price: String = "",
    var description: String = ""
)

class ItemFormAdapter(
    private val items: MutableList<ItemFormData>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<ItemFormAdapter.ItemFormViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemFormViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_multiple_item_form, parent, false)
        return ItemFormViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ItemFormViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    inner class ItemFormViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val itemNumber: TextView = itemView.findViewById(R.id.textItemNumber)
        private val name: EditText = itemView.findViewById(R.id.inputItemName)
        private val unit: EditText = itemView.findViewById(R.id.inputItemUnit)
        private val price: EditText = itemView.findViewById(R.id.inputItemPrice)
        private val description: EditText = itemView.findViewById(R.id.inputItemDescription)
        private val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteItem)
        private val priceDisplay: TextView? = itemView.findViewById(R.id.priceDisplay)

        fun bind(item: ItemFormData, position: Int) {
            itemNumber.text = "#${item.itemNumber}"
            name.setText(item.name)
            unit.setText(item.unit)
            price.setText(if (item.price.isNotBlank()) String.format("%.2f", item.price.toDoubleOrNull() ?: 0.0) else "")
            description.setText(item.description)
            btnDelete.setOnClickListener { onDelete(adapterPosition) }
            // Update model as user types
            name.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    item.name = name.text.toString()
                }
            })
            unit.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    item.unit = unit.text.toString()
                }
            })
            description.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    item.description = description.text.toString()
                }
            })
            // Format price as ₱ X.00 as user types
            price.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val value = s?.toString() ?: ""
                    item.price = value
                    if (value.isNotBlank()) {
                        try {
                            val formatted = String.format("%.2f", value.toDouble())
                            priceDisplay?.text = "₱ $formatted"
                            priceDisplay?.visibility = View.VISIBLE
                        } catch (_: Exception) {
                            priceDisplay?.visibility = View.GONE
                        }
                    } else {
                        priceDisplay?.visibility = View.GONE
                    }
                }
            })
        }
    }
} 