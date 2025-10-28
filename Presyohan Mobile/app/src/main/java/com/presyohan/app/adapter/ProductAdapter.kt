package com.presyohan.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.R

// Product data class
data class Product(
    val id: String,
    val name: String,
    val description: String,
    val price: Double,
    val volume: String,
    val category: String
)

class ProductAdapter(
    private var products: List<Product>,
    private var userRole: String?,
    private val onOptionsClick: (product: Product, anchor: View) -> Unit,
    private val onLongPress: (product: Product, anchor: View) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.textProductName)
        val description: TextView = itemView.findViewById(R.id.textProductDescription)
        val price: TextView = itemView.findViewById(R.id.textProductPrice)
        val volume: TextView = itemView.findViewById(R.id.textProductVolume)
        val options: ImageView = itemView.findViewById(R.id.buttonProductOptions)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]
        holder.name.text = product.name
        holder.description.text = product.description
        holder.price.text = "₱%.2f".format(product.price)
        holder.volume.text = product.volume
        // Hide options if userRole is 'sales staff'
        if (userRole == "sales staff") {
            holder.options.visibility = View.GONE
        } else {
            holder.options.visibility = View.VISIBLE
            holder.options.setOnClickListener { onOptionsClick(product, holder.options) }
        }
        holder.itemView.setOnLongClickListener {
            onLongPress(product, holder.itemView)
            true
        }
    }

    override fun getItemCount(): Int = products.size

    fun updateProducts(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
    }

    fun setUserRole(role: String?) {
        userRole = role
        notifyDataSetChanged()
    }
}