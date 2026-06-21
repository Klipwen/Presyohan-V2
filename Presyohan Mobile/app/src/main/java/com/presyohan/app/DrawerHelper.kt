package com.presyohan.app

import android.app.Dialog
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.customview.widget.ViewDragHelper
import coil.load
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.launch
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable

object DrawerHelper {

    @Serializable
    data class UserStoreRow(
        val store_id: String,
        val name: String,
        val branch: String? = null,
        val type: String? = null,
        val role: String,
        val is_public: Boolean = false,
        val member_count: Int = 0
    )

    fun setupDrawer(activity: AppCompatActivity, drawerLayout: DrawerLayout) {
        val root = drawerLayout.findViewById<View>(R.id.customDrawerRoot) ?: return

        // Expand edge swipe region for gesture-based devices
        setDrawerLeftEdgeSize(activity, drawerLayout)

        val uT = root.findViewById<TextView>(R.id.drawerUserName)
        val emailT = root.findViewById<TextView>(R.id.drawerUserEmail)
        val img = root.findViewById<ImageView>(R.id.drawerUserIcon)

        val toolbarAvatar = activity.findViewById<ImageView>(R.id.profileIcon)

        uT.text = "User"
        emailT.text = ""
        img.load(R.drawable.avatar_default) {
            transformations(CircleCropTransformation())
        }
        toolbarAvatar?.load(R.drawable.avatar_default) {
            transformations(CircleCropTransformation())
        }

        activity.lifecycleScope.launch {
            try {
                val profile = SupabaseAuthService.getUserProfile()
                if (profile != null) {
                    if (!profile.name.isNullOrBlank()) {
                        uT.text = profile.name.uppercase()
                    }
                    val currentUser = SupabaseProvider.client.auth.currentUserOrNull()
                    if (currentUser != null && !currentUser.email.isNullOrBlank()) {
                        emailT.text = currentUser.email
                    }
                    val url = profile.avatar_url
                    if (!url.isNullOrBlank()) {
                        img.load(url) {
                            crossfade(true)
                            transformations(CircleCropTransformation())
                            error(R.drawable.avatar_default)
                        }
                        toolbarAvatar?.load(url) {
                            crossfade(true)
                            transformations(CircleCropTransformation())
                            error(R.drawable.avatar_default)
                        }
                    }
                } else {
                    val simpleName = SupabaseAuthService.getDisplayName()
                    if (!simpleName.isNullOrBlank()) uT.text = simpleName.uppercase()
                    val currentUser = SupabaseProvider.client.auth.currentUserOrNull()
                    if (currentUser != null && !currentUser.email.isNullOrBlank()) {
                        emailT.text = currentUser.email
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DrawerHelper", "Error loading drawer header profile", e)
            }
        }

        root.findViewById<View>(R.id.drawerItemNotifications).setOnClickListener {
            if (activity !is NotificationActivity) {
                activity.startActivity(Intent(activity, NotificationActivity::class.java))
            }
            drawerLayout.closeDrawers()
        }

        // Store dropdown logic
        val yourStoresBtn = root.findViewById<View>(R.id.drawerItemYourStores)
        val storesListContainer = root.findViewById<LinearLayout>(R.id.drawerStoresListContainer)
        val chevron = root.findViewById<ImageView>(R.id.drawerYourStoresChevron)
        val yourStoresDivider = root.findViewById<View>(R.id.drawerYourStoresDivider)

        if (activity is StoreActivity) {
            yourStoresBtn.visibility = View.GONE
            storesListContainer.visibility = View.GONE
            yourStoresDivider?.visibility = View.GONE
        } else {
            var isExpanded = false
            yourStoresBtn.setOnClickListener {
                isExpanded = !isExpanded
                if (isExpanded) {
                    storesListContainer.visibility = View.VISIBLE
                    chevron.rotation = 180f
                    loadDrawerStores(activity, storesListContainer, drawerLayout)
                } else {
                    storesListContainer.visibility = View.GONE
                    chevron.rotation = 0f
                }
            }
        }

        root.findViewById<View>(R.id.drawerItemPresyohan).setOnClickListener {
            drawerLayout.closeDrawers()
            if (activity !is CustomerHomeActivity) {
                val intent = Intent(activity, CustomerHomeActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
            }
        }

        root.findViewById<View>(R.id.drawerItemSettings).setOnClickListener {
            Toast.makeText(activity, "Settings under construction", Toast.LENGTH_SHORT).show()
            drawerLayout.closeDrawers()
        }

        root.findViewById<View>(R.id.drawerItemSupport).setOnClickListener {
            Toast.makeText(activity, "Support under construction", Toast.LENGTH_SHORT).show()
            drawerLayout.closeDrawers()
        }

        root.findViewById<View>(R.id.drawerItemLogout).setOnClickListener {
            drawerLayout.closeDrawers()
            showLogoutDialog(activity)
        }
    }

    private fun loadDrawerStores(
        activity: AppCompatActivity,
        container: LinearLayout,
        drawerLayout: DrawerLayout
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(activity)

        // Show a loading text item
        val loadingView = inflater.inflate(R.layout.item_drawer_store, container, false)
        loadingView.findViewById<TextView>(R.id.txtStoreName).text = "Loading stores..."
        container.addView(loadingView)

        val activeStoreId = activity.intent.getStringExtra("storeId")

        activity.lifecycleScope.launch {
            try {
                val rows = SupabaseProvider.client.postgrest.rpc("get_user_stores").decodeList<UserStoreRow>()
                container.removeAllViews()

                if (rows.isEmpty()) {
                    val emptyView = inflater.inflate(R.layout.item_drawer_store, container, false)
                    emptyView.findViewById<TextView>(R.id.txtStoreName).text = "No stores joined yet"
                    container.addView(emptyView)
                } else {
                    for (row in rows) {
                        val storeView = inflater.inflate(R.layout.item_drawer_store, container, false)

                        val txtName = storeView.findViewById<TextView>(R.id.txtStoreName)
                        val txtBranch = storeView.findViewById<TextView>(R.id.txtStoreBranch)

                        txtName.text = row.name
                        if (!row.branch.isNullOrBlank()) {
                            txtBranch.text = row.branch
                            txtBranch.visibility = View.VISIBLE
                        }

                        val isActive = row.store_id == activeStoreId
                        if (isActive) {
                            txtName.setTextColor(activity.getColor(R.color.presyo_orange))
                        }

                        storeView.setOnClickListener {
                            drawerLayout.closeDrawers()
                            val intent = Intent(activity, HomeActivity::class.java).apply {
                                putExtra("storeId", row.store_id)
                                putExtra("storeName", row.name)
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            activity.startActivity(intent)
                        }

                        container.addView(storeView)
                    }
                }



            } catch (e: Exception) {
                container.removeAllViews()
                val errorView = inflater.inflate(R.layout.item_drawer_store, container, false)
                errorView.findViewById<TextView>(R.id.txtStoreName).text = "Failed to load stores"
                container.addView(errorView)
                android.util.Log.e("DrawerHelper", "Error loading stores in drawer", e)
            }
        }
    }

    private fun showLogoutDialog(activity: AppCompatActivity) {
        val dialog = Dialog(activity)
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_reusable_template, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        view.findViewById<TextView>(R.id.dialogTitle).text = "Logout"
        view.findViewById<TextView>(R.id.dialogMessage).text = "Are you sure you want to log out?\n\nYou can sign back in anytime. See you again soon!"

        val btnCancel = view.findViewById<Button>(R.id.btnNegative)
        btnCancel.text = "Cancel"
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        val btnLogout = view.findViewById<Button>(R.id.btnPositive)
        btnLogout.text = "Logout"
        btnLogout.setOnClickListener {
            var overlay = activity.findViewById<View>(R.id.loadingOverlay)
            if (overlay == null) {
                overlay = LoadingOverlayHelper.attach(activity)
            }
            LoadingOverlayHelper.show(overlay)

            activity.lifecycleScope.launch {
                try {
                    SupabaseAuthService.signOut()
                } catch (_: Exception) { }
                val intent = Intent(activity, LoginActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                activity.startActivity(intent)
                activity.finish()
            }
            dialog.dismiss()
        }
        dialog.show()
        val width = (activity.resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /**
     * Widen the left swipe drag region of the DrawerLayout using reflection.
     * This bypasses conflicts with modern Android gesture navigation (back swipe).
     */
    private fun setDrawerLeftEdgeSize(activity: AppCompatActivity, drawerLayout: DrawerLayout) {
        try {
            val leftDraggerField = drawerLayout.javaClass.getDeclaredField("mLeftDragger")
            leftDraggerField.isAccessible = true
            val leftDragger = leftDraggerField.get(drawerLayout) as ViewDragHelper

            val edgeSizeField = leftDragger.javaClass.getDeclaredField("mEdgeSize")
            edgeSizeField.isAccessible = true

            // Set size to a generous 100dp
            val density = activity.resources.displayMetrics.density
            val newEdgeSize = (100 * density).toInt()

            edgeSizeField.setInt(leftDragger, newEdgeSize)
        } catch (e: Exception) {
            android.util.Log.e("DrawerHelper", "Could not set drawer edge size: ${e.message}")
        }
    }
}
