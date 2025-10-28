package com.presyohan.app

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import android.view.ViewGroup
import android.widget.ImageView

class ManageMembersActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MembersAdapter
    private var storeId: String? = null
    private val db = FirebaseFirestore.getInstance()

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

        // Set store name and branch in header
        val textStoreName = findViewById<TextView>(R.id.textStoreName)
        val textStoreBranch = findViewById<TextView>(R.id.textStoreBranch)
        db.collection("stores").document(storeId!!).get().addOnSuccessListener { doc ->
            textStoreName.text = doc.getString("name") ?: "Store Name"
            textStoreBranch.text = doc.getString("branch") ?: "Branch Name"
        }

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }
    }

    private fun fetchMembers() {
        db.collection("stores").document(storeId!!).collection("members").get().addOnSuccessListener { snapshot ->
            val memberDocs = snapshot.documents
            if (memberDocs.isEmpty()) {
                adapter.setMembers(emptyList())
                return@addOnSuccessListener
            }
            val members = mutableListOf<Member>()
            var fetched = 0
            for (doc in memberDocs) {
                val role = doc.getString("role") ?: "sales staff"
                val id = doc.id
                db.collection("users").document(id).get().addOnSuccessListener { userDoc ->
                    val name = userDoc.getString("name") ?: "Unknown"
                    members.add(Member(id, name, role))
                    fetched++
                    if (fetched == memberDocs.size) {
                        adapter.setMembers(members)
                    }
                }.addOnFailureListener {
                    members.add(Member(id, "Unknown", role))
                    fetched++
                    if (fetched == memberDocs.size) {
                        adapter.setMembers(members)
                    }
                }
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
            val newRole = spinner.selectedItem.toString()
            db.collection("stores").document(storeId!!).collection("members").document(member.id)
                .update("role", newRole)
                .addOnSuccessListener {
                    Toast.makeText(this, "Role updated!", Toast.LENGTH_SHORT).show()
                    fetchMembers()
                    dialog.dismiss()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to update role: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
        }
        dialog.show()
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