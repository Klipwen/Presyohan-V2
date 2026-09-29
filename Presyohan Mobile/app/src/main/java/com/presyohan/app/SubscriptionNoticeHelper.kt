package com.presyohan.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

object SubscriptionNoticeHelper {

    private var isDismissedThisSession = false

    fun bindBanner(
        activity: Activity,
        bannerCard: CardView?,
        details: UserSubscriptionDetails,
        onActionClicked: (() -> Unit)? = null
    ) {
        if (bannerCard == null) return

        val container = bannerCard.findViewById<View>(R.id.layoutSubscriptionBannerContainer)
        val imgIcon = bannerCard.findViewById<ImageView>(R.id.imgBannerIcon)
        val tvTitle = bannerCard.findViewById<TextView>(R.id.tvBannerTitle)
        val tvMessage = bannerCard.findViewById<TextView>(R.id.tvBannerMessage)
        val btnAction = bannerCard.findViewById<AppCompatButton>(R.id.btnBannerAction)
        val btnClose = bannerCard.findViewById<ImageView>(R.id.btnBannerClose)

        if (isDismissedThisSession) {
            bannerCard.visibility = View.GONE
            return
        }

        val presyoOrange = ContextCompat.getColor(activity, R.color.presyo_orange)
        val mutedText = Color.parseColor("#4B5563")
        val darkText = Color.parseColor("#374151")
        val warmDark = Color.parseColor("#C2410C")
        val iconGray = Color.parseColor("#9CA3AF")

        // Case 1: Expired Notice (Clean Presyohan slate tone, no red/black)
        if (details.isExpired || details.statusType == SubscriptionStatusType.EXPIRED) {
            val tierName = details.expiredTierName ?: "PRO"
            bannerCard.visibility = View.VISIBLE
            container?.setBackgroundResource(R.drawable.bg_subscription_notice_expired)
            
            imgIcon?.setImageResource(R.drawable.icon_subscriptions)
            imgIcon?.setColorFilter(presyoOrange)

            tvTitle?.text = "Plan Expired"
            tvTitle?.setTextColor(darkText)

            tvMessage?.text = "Your $tierName trial has expired. Renew to continue with higher limits."
            tvMessage?.setTextColor(mutedText)

            btnAction?.text = "Renew Plan"
            btnAction?.setBackgroundResource(R.drawable.bg_solid_button_orange)
            btnAction?.setOnClickListener {
                if (onActionClicked != null) {
                    onActionClicked()
                } else {
                    val intent = Intent(activity, SubscriptionStatusActivity::class.java)
                    activity.startActivity(intent)
                }
            }

            btnClose?.setColorFilter(iconGray)
            btnClose?.setOnClickListener {
                bannerCard.visibility = View.GONE
                isDismissedThisSession = true
                SubscriptionManager.clearExpiredNotice(activity)
            }
            return
        }

        // Case 2: 3 Days (or less) Before Expiration Notice (Warm Presyohan amber tone)
        if (details.isExpiringSoon && details.daysRemaining != null && details.daysRemaining in 0..3) {
            val tierName = details.tierInfo.name.replace("Tier", "").trim().ifEmpty { "PRO" }
            val daysText = when (details.daysRemaining) {
                0L -> "today"
                1L -> "tomorrow"
                else -> "in ${details.daysRemaining} days"
            }

            bannerCard.visibility = View.VISIBLE
            container?.setBackgroundResource(R.drawable.bg_subscription_notice_warning)

            imgIcon?.setImageResource(R.drawable.ic_calendar_clock)
            imgIcon?.setColorFilter(presyoOrange)

            tvTitle?.text = "Trial Ending Soon"
            tvTitle?.setTextColor(presyoOrange)

            tvMessage?.text = "Your $tierName trial ends $daysText. Upgrade to maintain your limits."
            tvMessage?.setTextColor(mutedText)

            btnAction?.text = "Upgrade Plan"
            btnAction?.setBackgroundResource(R.drawable.bg_solid_button_orange)
            btnAction?.setOnClickListener {
                if (onActionClicked != null) {
                    onActionClicked()
                } else {
                    val intent = Intent(activity, SubscriptionStatusActivity::class.java)
                    activity.startActivity(intent)
                }
            }

            btnClose?.setColorFilter(iconGray)
            btnClose?.setOnClickListener {
                bannerCard.visibility = View.GONE
                isDismissedThisSession = true
            }
            return
        }

        // Default: Hidden
        bannerCard.visibility = View.GONE
    }
}
