package com.presyohan.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

class UpdateCheckWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @Serializable
    private data class AppReleaseRow(
        val version_code: Int,
        val version_name: String,
        val download_url: String,
        val whats_new: String,
        val is_forced: Boolean
    )

    override suspend fun doWork(): Result {
        return try {
            // Check if Supabase client is initialized
            val isSupabaseInitialized = try {
                SupabaseProvider.client
                true
            } catch (e: Exception) {
                false
            }
            
            if (!isSupabaseInitialized) {
                SupabaseProvider.init(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
            }

            // Fetch the latest release from database using builder lambda block
            val latestRelease = SupabaseProvider.client.from("app_releases")
                .select {
                    order("version_code", Order.DESCENDING)
                    limit(1)
                }
                .decodeList<AppReleaseRow>()
                .firstOrNull()

            val currentVersionCode = getCurrentVersionCode()

            if (latestRelease != null && latestRelease.version_code > currentVersionCode) {
                showUpdateNotification(latestRelease)
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun showUpdateNotification(release: AppReleaseRow) {
        val channelId = "presyohan.updates"
        val name = "App Updates"
        val importance = NotificationManager.IMPORTANCE_HIGH
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel on Oreo+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (notificationManager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, name, importance).apply {
                    description = "Notifications when a new app version is released"
                    enableLights(true)
                    lightColor = android.graphics.Color.BLUE
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
        }

        // Tap action: opens SplashActivity, which will detect the update and pop up the dialog/overlay immediately
        val intent = Intent(context, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanChangelog = ReusableDialogHelper.parseHtml(release.whats_new).toString().trim()
        val displayChangelog = if (cleanChangelog.isNotEmpty()) cleanChangelog else "Tap to view release features."

        val title = if (release.is_forced) {
            "Critical Update Required! (v${release.version_name})"
        } else {
            "New Update Available! (v${release.version_name})"
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.icon_presyohan_launcher) // Reuses existing launcher logo resource
            .setContentTitle(title)
            .setContentText("Version ${release.version_name} is now available to download.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Version ${release.version_name} is now available!\n\nChangelog:\n$displayChangelog"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        notificationManager.notify(1829, builder.build())
    }
}
