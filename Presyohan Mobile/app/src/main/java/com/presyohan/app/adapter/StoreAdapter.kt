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
    val type: String,
    val memberCount: Int = 0,
    val role: String = ""
)

class StoreAdapter(
    private var stores: List<Store>,
    private var storeRoles: Map<String, String> = emptyMap(),
    private val onStoreClick: (store: Store) -> Unit,
    private val onSettingsClick: (store: Store) -> Unit,
    private val onViewClick: (store: Store) -> Unit,
    private val onDeleteClick: (store: Store) -> Unit,
    private val onLeaveClick: (store: Store) -> Unit
) : RecyclerView.Adapter<StoreAdapter.StoreViewHolder>() {

    var swipedPosition: Int = -1
        private set

    inner class StoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val foregroundCardView: androidx.cardview.widget.CardView = itemView.findViewById(R.id.foregroundCardView)
        val backgroundActionCard: androidx.cardview.widget.CardView = itemView.findViewById(R.id.backgroundActionCard)
        val icon: ImageView = itemView.findViewById(R.id.imageStoreIcon)
        val name: TextView = itemView.findViewById(R.id.textStoreName)
        val branch: TextView = itemView.findViewById(R.id.textStoreBranch)
        val type: TextView = itemView.findViewById(R.id.textStoreType)
        val memberCount: TextView = itemView.findViewById(R.id.textMemberCount)
        val roleIndicator: TextView = itemView.findViewById(R.id.textStoreRole)
        val storeIconFrame: View = itemView.findViewById(R.id.storeIconFrame)

        val btnAction1: View = itemView.findViewById(R.id.btnAction1)
        val btnAction2: View = itemView.findViewById(R.id.btnAction2)
        val imageAction1: ImageView = itemView.findViewById(R.id.imageAction1)
        val imageAction2: ImageView = itemView.findViewById(R.id.imageAction2)
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
        holder.memberCount.text = store.memberCount.toString()

        val isOwner = store.role.lowercase() == "owner"
        holder.storeIconFrame.setBackgroundResource(
            if (isOwner) R.drawable.bg_store_icon_owner else R.drawable.bg_store_icon_other
        )

        val roleName = when (store.role.lowercase()) {
            "owner" -> "Owner"
            "manager" -> "Manager"
            else -> "Sales Staff"
        }
        holder.roleIndicator.text = "Role: $roleName"

        // Handle swiped translation
        val isSwiped = position == swipedPosition
        val density = holder.itemView.resources.displayMetrics.density
        val fallbackWidth = 120f * density
        val actionWidth = if (holder.backgroundActionCard.width > 0) holder.backgroundActionCard.width.toFloat() else fallbackWidth
        holder.foregroundCardView.translationX = if (isSwiped) -actionWidth else 0f

        // Action Button setup based on Role
        val ctx = holder.itemView.context
        if (store.role.lowercase() == "owner") {
            holder.imageAction1.setImageResource(R.drawable.icon_settings)
            holder.imageAction1.setColorFilter(ctx.getColor(R.color.presyo_teal))
            holder.btnAction1.setOnClickListener {
                closeSwipedItem()
                onSettingsClick(store)
            }

            holder.imageAction2.setImageResource(R.drawable.icon_delete)
            holder.imageAction2.setColorFilter(ctx.getColor(R.color.presyo_teal))
            holder.btnAction2.setOnClickListener {
                closeSwipedItem()
                onDeleteClick(store)
            }
        } else {
            holder.imageAction1.setImageResource(R.drawable.icon_view)
            holder.imageAction1.setColorFilter(ctx.getColor(R.color.presyo_teal))
            holder.btnAction1.setOnClickListener {
                closeSwipedItem()
                onViewClick(store)
            }

            holder.imageAction2.setImageResource(R.drawable.icon_leave_store)
            holder.imageAction2.setColorFilter(ctx.getColor(R.color.presyo_teal))
            holder.btnAction2.setOnClickListener {
                closeSwipedItem()
                onLeaveClick(store)
            }
        }

        // Tap Behavior on Card
        holder.foregroundCardView.setOnClickListener {
            if (isSwiped(position)) {
                closeSwipedItem()
            } else {
                if (swipedPosition != -1) {
                    closeSwipedItem()
                } else {
                    onStoreClick(store)
                }
            }
        }

        holder.foregroundCardView.setOnLongClickListener {
            onItemSwiped(position)
            true
        }
    }

    override fun getItemCount(): Int = stores.size

    fun updateStores(newStores: List<Store>, newRoles: Map<String, String> = storeRoles) {
        stores = newStores
        storeRoles = newRoles
        swipedPosition = -1 // Reset swiped state on data change
        notifyDataSetChanged()
    }

    fun onItemSwiped(position: Int) {
        val previousSwiped = swipedPosition
        if (swipedPosition == position) return
        swipedPosition = position
        if (previousSwiped != -1) {
            notifyItemChanged(previousSwiped)
        }
        notifyItemChanged(swipedPosition)
    }

    fun isSwiped(position: Int): Boolean {
        return swipedPosition == position
    }

    fun closeSwipedItem() {
        if (swipedPosition != -1) {
            val prev = swipedPosition
            swipedPosition = -1
            notifyItemChanged(prev)
        }
    }
}