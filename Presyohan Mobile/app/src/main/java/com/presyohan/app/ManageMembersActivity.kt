package com.presyohan.app

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import android.view.ViewGroup
import android.widget.ImageView

class ManageMembersActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MembersAdapter
    private var storeId: String? = null
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_members)

        storeId = intent.getStringExtra("storeId")
        if (storeId.isNullOrBlank()) {
            Toast.makeText(this, "No store ID provided.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        recyclerView = findViewById(R.id.recyclerViewMembers)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MembersAdapter { member ->
            if (member.role == "manager" || member.role == "sales staff") {
                showRoleChangeDialog(member)
            }
        }
        recyclerView.adapter = adapter

        fetchMembers()

        // Set store name and branch in header via Supabase
        val textStoreName = findViewById<TextView>(R.id.textStoreName)
        val textStoreBranch = findViewById<TextView>(R.id.textStoreBranch)
        lifecycleScope.launch {
            try {
                @Serializable
                data class StoreRow(val id: String, val name: String, val branch: String? = null)
                val rows = SupabaseProvider.client.postgrest["stores"].select {
                    filter { eq("id", storeId!!) }
                    limit(1)
                }.decodeList<StoreRow>()
                val s = rows.firstOrNull()
                textStoreName.text = s?.name ?: "Store Name"
                textStoreBranch.text = s?.branch ?: "Branch Name"
            } catch (_: Exception) {
                // fallback labels already set
            }
        }

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }
    }

    private fun fetchMembers() {
        val sId = storeId ?: return
        lifecycleScope.launch {
            try {
                @Serializable
                data class StoreMemberUser(val user_id: String, val name: String, val role: String)
                val rows = SupabaseProvider.client.postgrest.rpc(
                    "get_store_members",
                    buildJsonObject { put("p_store_id", sId) }
                ).decodeList<StoreMemberUser>()

                val members = rows.map { r ->
                    Member(r.user_id, r.name, mapRoleToUi(r.role))
                }
                adapter.setMembers(members)
            } catch (e: Exception) {
                Toast.makeText(this@ManageMembersActivity, "Unable to load members.", Toast.LENGTH_LONG).show()
                adapter.setMembers(emptyList())
            }
        }
    }

    private fun showRoleChangeDialog(member: Member) {
        val dialog = Dialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_role_change, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        view.findViewById<TextView>(R.id.dialogTitle).text = "Change Role"
        val spinner = view.findViewById<Spinner>(R.id.spinnerRole)
        val roles = arrayOf("owner", "manager", "sales staff")
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        spinner.setSelection(roles.indexOf(member.role))
        view.findViewById<Button>(R.id.btnCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnChange).setOnClickListener {
            val newRoleUi = spinner.selectedItem.toString()
            val sId = storeId ?: return@setOnClickListener
            lifecycleScope.launch {
                try {
                    // Map UI role to Supabase role keywords
                    val newRole = mapRoleToSupabase(newRoleUi)
                    SupabaseProvider.client.postgrest.rpc(
                        "update_store_member_role",
                        buildJsonObject {
                            put("p_store_id", sId)
                            put("p_member_id", member.id)
                            put("p_new_role", newRole)
                        }
                    )
                    Toast.makeText(this@ManageMembersActivity, "Role updated.", Toast.LENGTH_SHORT).show()
                    fetchMembers()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(this@ManageMembersActivity, "Unable to update role.", Toast.LENGTH_LONG).show()
                }
            }
        }
        dialog.show()
    }

    private fun mapRoleToUi(role: String): String {
        return when (role.lowercase()) {
            "employee" -> "sales staff"
            else -> role.lowercase()
        }
    }

    private fun mapRoleToSupabase(roleUi: String): String {
        return when (roleUi.lowercase()) {
            "sales staff" -> "employee"
            else -> roleUi.lowercase()
        }
    }
}

data class Member(val id: String, val name: String, val role: String)

class MembersAdapter(private val onMemberClick: (Member) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var groupedMembers: Map<String, List<Member>> = emptyMap()
    private var sectionOrder: List<String> = listOf("owner", "manager", "sales staff")

    fun setMembers(members: List<Member>) {
        groupedMembers = members.groupBy { it.role }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return sectionOrder.sumOf { groupedMembers[it]?.size ?: 0 } + sectionOrder.count { (groupedMembers[it]?.isNotEmpty() == true) }
    }

    override fun getItemViewType(position: Int): Int {
        var count = 0
        for (role in sectionOrder) {
            val members = groupedMembers[role] ?: emptyList()
            if (members.isNotEmpty()) {
                if (position == count) return 0 // header
                count++
                if (position < count + members.size) return 1 // member
                count += members.size
            }
        }
        return 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == 0) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member_section_header, parent, false)
            SectionHeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_member, parent, false)
            MemberViewHolder(view, onMemberClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        var count = 0
        for (role in sectionOrder) {
            val members = groupedMembers[role] ?: emptyList()
            if (members.isNotEmpty()) {
                if (position == count) {
                    (holder as SectionHeaderViewHolder).bind(role)
                    return
                }
                count++
                if (position < count + members.size) {
                    (holder as MemberViewHolder).bind(members[position - count])
                    return
                }
                count += members.size
            }
        }
    }

    class SectionHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(role: String) {
            itemView.findViewById<TextView>(R.id.sectionHeader).text = role.replaceFirstChar { it.uppercase() }
        }
    }

    class MemberViewHolder(itemView: View, val onClick: (Member) -> Unit) : RecyclerView.ViewHolder(itemView) {
        fun bind(member: Member) {
            itemView.findViewById<TextView>(R.id.memberName).text = member.name
            itemView.findViewById<TextView>(R.id.memberRole).text = member.role.replaceFirstChar { it.uppercase() }
            itemView.setOnClickListener { onClick(member) }
        }
    }
}