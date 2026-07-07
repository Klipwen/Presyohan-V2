package com.presyohan.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.R

data class MembershipItem(
    val id: String,
    val name: String,
    val branch: String?,
    val type: String?,
    val role: String?, // "owner", "manager", "sale staff", or null for customer suki/presyohan links
    val isStandard: Boolean,
    val hasOtherOwner: Boolean = false
)

class MembershipsAdapter(
    private var items: List<MembershipItem> = emptyList(),
    private val onViewStore: (MembershipItem) -> Unit,
    private val onSettings: (MembershipItem) -> Unit,
    private val onAction: (MembershipItem) -> Unit
) : RecyclerView.Adapter<MembershipsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStoreName: TextView = view.findViewById(R.id.tvStoreName)
        val tvStoreType: TextView = view.findViewById(R.id.tvStoreType)
        val tvStoreBranchRole: TextView = view.findViewById(R.id.tvStoreBranchRole)
        val btnViewStore: AppCompatButton = view.findViewById(R.id.btnViewStore)
        val btnSettings: AppCompatButton = view.findViewById(R.id.btnSettings)
        val btnAction: AppCompatButton = view.findViewById(R.id.btnAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_membership_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvStoreName.text = item.name
        holder.tvStoreType.text = if (item.type.isNullOrBlank()) "General Merchandise" else item.type

        // Format branch and role details
        if (item.role != null) {
            val branchStr = if (item.branch.isNullOrBlank()) "Main Branch" else item.branch
            holder.tvStoreBranchRole.text = "$branchStr • ${item.role}"
        } else {
            holder.tvStoreBranchRole.text = if (item.branch.isNullOrBlank()) "Main Branch" else item.branch
        }

        // Configure buttons based on item role
        if (item.role != null) {
            // Store Membership Tab
            holder.btnViewStore.visibility = View.VISIBLE
            if (item.role == "owner") {
                holder.btnSettings.visibility = View.VISIBLE
                if (item.hasOtherOwner) {
                    holder.btnAction.text = "Leave"
                } else {
                    holder.btnAction.text = "Delete"
                }
            } else {
                holder.btnSettings.visibility = View.GONE
                holder.btnAction.text = "Leave"
            }
            holder.btnAction.visibility = View.VISIBLE
        } else {
            // Suki / Presyohan Tabs
            holder.btnViewStore.visibility = View.VISIBLE
            holder.btnSettings.visibility = View.GONE
            holder.btnAction.text = "Remove"
            holder.btnAction.visibility = View.VISIBLE
        }

        // Click listeners
        holder.btnViewStore.setOnClickListener { onViewStore(item) }
        holder.btnSettings.setOnClickListener { onSettings(item) }
        holder.btnAction.setOnClickListener { onAction(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<MembershipItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
