package com.presyohan.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

object ShareAppHelper {

    @Serializable
    private data class AppReleaseRow(
        val version_code: Int,
        val version_name: String,
        val download_url: String,
        val whats_new: String,
        val is_forced: Boolean
    )

    fun shareApp(context: Context, scope: CoroutineScope) {
        context.showReusableDialog(
            title = "Share Presyohan App",
            message = "Would you like to share the download link of the latest version of Presyohan App with others?",
            positiveButtonText = "Share",
            positiveAction = {
                val progressDialog = context.showReusableDialog(
                    title = "Preparing Share Link",
                    message = "Fetching the latest Presyohan App download link...",
                    isCancelable = false
                )

                scope.launch {
                    val downloadUrl = withContext(Dispatchers.IO) {
                        try {
                            val latestRelease = SupabaseProvider.client.postgrest["app_releases"]
                                .select {
                                    order("version_code", Order.DESCENDING)
                                    limit(1)
                                }
                                .decodeList<AppReleaseRow>()
                                .firstOrNull()

                            latestRelease?.download_url ?: SupabaseProvider.client.storage.from("presyohan.apk").publicUrl("presyohan.apk")
                        } catch (e: Exception) {
                            try {
                                SupabaseProvider.client.storage.from("presyohan.apk").publicUrl("presyohan.apk")
                            } catch (ex: Exception) {
                                "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/presyohan.apk/presyohan.apk"
                            }
                        }
                    }

                    progressDialog.dismiss()

                    (context as? Activity)?.runOnUiThread {
                        try {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Presyohan App")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Download and install the latest Presyohan App to view or manage store pricing:\n$downloadUrl"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Presyohan App via"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to share app link.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            negativeButtonText = "Cancel"
        )
    }
}
