package com.presyohan.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.R

class ProductChildAdapter(
    private var products: List<Product>,
    private var userRole: String?,
    private var activeOverlayProductId: String?,
    private val onEditClick: (Product) -> Unit,
    private val onDeleteClick: (Product) -> Unit,
    private val onItemLongPressed: (Product) -> Unit,
    private val onItemClicked: () -> Unit
) : RecyclerView.Adapter<ProductChildAdapter.ProductViewHolder>() {

    inner class ProductViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val normalLayout: View = itemView.findViewById(R.id.layoutNormal)
        val overlayLayout: View = itemView.findViewById(R.id.layoutOverlay)

        // Normal Layout views
        val productName: TextView = itemView.findViewById(R.id.textProductName)
        val productDescription: TextView = itemView.findViewById(R.id.textProductDescription)
        val productPrice: TextView = itemView.findViewById(R.id.textProductPrice)
        val productVolume: TextView = itemView.findViewById(R.id.textProductVolume)
        val imagePublicIndicator: ImageView = itemView.findViewById(R.id.imagePublicIndicator)

        // Overlay Layout views
        val overlayProductName: TextView = itemView.findViewById(R.id.overlayProductName)
        val overlayProductDescription: TextView = itemView.findViewById(R.id.overlayProductDescription)
        val overlayProductPrice: TextView = itemView.findViewById(R.id.overlayProductPrice)
        val overlayProductVolume: TextView = itemView.findViewById(R.id.overlayProductVolume)
        val btnEdit: ImageView = itemView.findViewById(R.id.btnEditProduct)
        val btnDelete: ImageView = itemView.findViewById(R.id.btnDeleteProduct)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_product_new, parent, false)
        return ProductViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        val product = products[position]

        // --- Normal State Binding ---
        holder.productName.text = product.name
        if (product.description.isBlank()) {
            holder.productDescription.visibility = View.GONE
        } else {
            holder.productDescription.visibility = View.VISIBLE
            holder.productDescription.text = product.description
        }
        holder.productPrice.text = "₱%,.2f".format(java.util.Locale.US, product.price)
        holder.productVolume.text = product.volume

        // Public Indicator
        if (product.is_public) {
            holder.imagePublicIndicator.visibility = View.VISIBLE
        } else {
            holder.imagePublicIndicator.visibility = View.GONE
        }

        // --- Overlay State Binding ---
        holder.overlayProductName.text = product.name
        if (product.description.isBlank()) {
            holder.overlayProductDescription.visibility = View.GONE
        } else {
            holder.overlayProductDescription.visibility = View.VISIBLE
            holder.overlayProductDescription.text = product.description
        }
        holder.overlayProductPrice.text = "₱%,.2f".format(java.util.Locale.US, product.price)
        holder.overlayProductVolume.text = product.volume

        // Check if this item is currently showing the overlay (only if owner/manager)
        if (activeOverlayProductId == product.id && (userRole == "owner" || userRole == "manager")) {
            holder.normalLayout.visibility = View.INVISIBLE
            holder.overlayLayout.visibility = View.VISIBLE
        } else {
            holder.normalLayout.visibility = View.VISIBLE
            holder.overlayLayout.visibility = View.GONE
        }

        // --- Long Press to Show Overlay ---
        val longClickListener = View.OnLongClickListener {
            if (userRole == "owner" || userRole == "manager") {
                onItemLongPressed(product)
                true
            } else {
                false
            }
        }
        holder.itemView.setOnLongClickListener(longClickListener)
        holder.normalLayout.setOnLongClickListener(longClickListener)

        // --- Normal Click to clear overlay if one is open ---
        holder.normalLayout.setOnClickListener {
            onItemClicked()
        }

        // --- Click Overlay to close it ---
        holder.overlayLayout.setOnClickListener {
            onItemClicked()
        }

        // --- Edit Action ---
        holder.btnEdit.setOnClickListener {
            onEditClick(product)
        }

        // --- Delete Action ---
        holder.btnDelete.setOnClickListener {
            onDeleteClick(product)
        }
    }

    override fun getItemCount(): Int = products.size

    fun updateProducts(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
    }

    fun updateActiveOverlayProductId(newId: String?) {
        val oldId = activeOverlayProductId
        activeOverlayProductId = newId
        
        if (oldId == newId) return
        
        val oldPos = products.indexOfFirst { it.id == oldId }
        val newPos = products.indexOfFirst { it.id == newId }
        
        if (oldPos != -1) {
            notifyItemChanged(oldPos)
        }
        if (newPos != -1 && newPos != oldPos) {
            notifyItemChanged(newPos)
        }
    }

    fun updateData(newProducts: List<Product>, role: String?, activeId: String?) {
        this.products = newProducts
        this.userRole = role
        this.activeOverlayProductId = activeId
        notifyDataSetChanged()
    }
}
