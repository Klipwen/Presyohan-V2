package com.presyohan.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.R

// Store data class
data class Store(
    val id: String,
    val name: String,
    val branch: String,
    val type: String
)

class StoreAdapter(
    private var stores: List<Store>,
    private var storeRoles: Map<String, String> = emptyMap(),
    private val onMenuClick: (store: Store, anchor: View) -> Unit,
    private val onStoreClick: (store: Store) -> Unit
) : RecyclerView.Adapter<StoreAdapter.StoreViewHolder>() {

    inner class StoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.imageStoreIcon)
        val name: TextView = itemView.findViewById(R.id.textStoreName)
        val branch: TextView = itemView.findViewById(R.id.textStoreBranch)
        val type: TextView = itemView.findViewById(R.id.textStoreType)
        val menu: ImageView = itemView.findViewById(R.id.buttonOptions)
        val roleIndicator: androidx.cardview.widget.CardView = itemView.findViewById(R.id.viewRoleIndicator)
    }

    override fun getItemViewType(position: Int): Int {
        val store = stores[position]
        val role = storeRoles[store.id]
        return if (role != null && role != "owner") 1 else 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_store, parent, false)
        return StoreViewHolder(view)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val store = stores[position]
        holder.icon.setImageResource(R.drawable.icon_store)
        holder.name.text = store.name
        holder.branch.text = store.branch
        holder.type.text = store.type
        holder.menu.setOnClickListener { onMenuClick(store, holder.menu) }
        holder.itemView.setOnClickListener { onStoreClick(store) }
        holder.itemView.setOnLongClickListener {
            onMenuClick(store, holder.menu)
            true
        }
        val role = storeRoles[store.id]
        val ctx = holder.itemView.context
        val color = if (role == "owner") ctx.getColor(R.color.presyo_yellow) else android.graphics.Color.parseColor("#D9D9D9")
        holder.roleIndicator.setCardBackgroundColor(color)
    }

    override fun getItemCount(): Int = stores.size

    fun updateStores(newStores: List<Store>, newRoles: Map<String, String> = storeRoles) {
        stores = newStores
        storeRoles = newRoles
        notifyDataSetChanged()
    }

    fun setStoreRoles(newRoles: Map<String, String>) {
        storeRoles = newRoles
        notifyDataSetChanged()
    }
}