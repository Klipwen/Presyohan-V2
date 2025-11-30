package com.presyohan.app

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.navigation.NavigationView
import coil.load
import coil.transform.CircleCropTransformation

object HeaderUtils {
    fun updateHeader(activity: AppCompatActivity, navView: NavigationView) {
        val h = navView.getHeaderView(0)
        val uT = h.findViewById<TextView>(R.id.drawerUserName)
        val codeT = h.findViewById<TextView>(R.id.drawerUserCode)
        val img = h.findViewById<ImageView>(R.id.drawerUserIcon)

        uT.text = "User"
        codeT?.visibility = View.GONE
        img.setImageResource(R.drawable.icon_profile)
        img.setColorFilter(ContextCompat.getColor(activity, R.color.white))

        activity.lifecycleScope.launch {
            val profile = SupabaseAuthService.getUserProfile()
            if (profile != null) {
                if (!profile.name.isNullOrBlank()) {
                    uT.text = profile.name
                }
                if (!profile.user_code.isNullOrBlank()) {
                    codeT?.text = "ID: ${profile.user_code!!.uppercase()}"
                    codeT?.visibility = View.VISIBLE
                }
                val url = profile.avatar_url
                if (!url.isNullOrBlank()) {
                    img.clearColorFilter()
                    img.load(url) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        error(R.drawable.icon_profile)
                    }
                }
            } else {
                val simpleName = SupabaseAuthService.getDisplayName()
                if (!simpleName.isNullOrBlank()) uT.text = simpleName
            }
        }
    }
}
