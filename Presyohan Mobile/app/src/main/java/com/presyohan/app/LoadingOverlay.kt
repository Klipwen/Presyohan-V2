package com.presyohan.app

import android.app.Activity
import android.view.View
import android.view.ViewGroup

object LoadingOverlayHelper {
    fun attach(activity: Activity): View {
        val overlay = activity.layoutInflater.inflate(R.layout.loading_overlay, null)
        activity.addContentView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        overlay.visibility = View.GONE
        return overlay
    }

    fun show(overlay: View) {
        overlay.visibility = View.VISIBLE
    }

    fun hide(overlay: View) {
        overlay.visibility = View.GONE
    }
}