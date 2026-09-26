package com.presyohan.app

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat

object PlanBenefitsDialog {

    fun show(context: Context, tierId: String): Dialog {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        val info = SubscriptionManager.getTierInfo(tierId)

        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#99000000")) // Semi-transparent dim background
            setOnClickListener { dialog.dismiss() }
        }

        val cardDensity = context.resources.displayMetrics.density

        // Card Container
        val cardView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bgDrawable = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 20 * cardDensity
            }
            background = bgDrawable
            setPadding(
                (20 * cardDensity).toInt(),
                (20 * cardDensity).toInt(),
                (20 * cardDensity).toInt(),
                (20 * cardDensity).toInt()
            )
            setOnClickListener { /* prevent dismiss on clicking card */ }
        }

        val cardLayoutParams = FrameLayout.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.90).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        // Header Section
        val isVip = tierId.lowercase() == "vip"
        val isPro = tierId.lowercase() == "pro"

        val primaryColor = when {
            isVip -> ContextCompat.getColor(context, R.color.presyo_teal)
            isPro -> ContextCompat.getColor(context, R.color.presyo_orange)
            else -> Color.parseColor("#64748B")
        }

        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconRes = when {
            isVip -> R.drawable.icon_vip
            isPro -> R.drawable.icon_pro
            else -> 0
        }

        if (iconRes != 0) {
            val imgIcon = ImageView(context).apply {
                setImageResource(iconRes)
                layoutParams = LinearLayout.LayoutParams((32 * cardDensity).toInt(), (32 * cardDensity).toInt()).apply {
                    marginEnd = (12 * cardDensity).toInt()
                }
            }
            headerLayout.addView(imgIcon)
        }

        val headerTextLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tvTitle = TextView(context).apply {
            text = "${info.name} Full Benefits"
            setTextColor(primaryColor)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val tvSubtitle = TextView(context).apply {
            text = "${info.priceText} ${info.periodText} • ${if (info.trialDays > 0) "${info.trialDays}-Day Trial" else "Instant Access"}"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
        }

        headerTextLayout.addView(tvTitle)
        headerTextLayout.addView(tvSubtitle)
        headerLayout.addView(headerTextLayout)
        cardView.addView(headerLayout)

        // Divider
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * cardDensity).toInt()
            ).apply {
                setMargins(0, (14 * cardDensity).toInt(), 0, (14 * cardDensity).toInt())
            }
            setBackgroundColor(Color.parseColor("#E2E8F0"))
        }
        cardView.addView(divider)

        // Scrollable Content
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.50).toInt()
            )
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun addSectionHeader(title: String) {
            val tvHeader = TextView(context).apply {
                text = title
                setTextColor(primaryColor)
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (10 * cardDensity).toInt(), 0, (6 * cardDensity).toInt())
                }
            }
            contentLayout.addView(tvHeader)
        }

        fun addBulletPoint(itemText: String, isAvailable: Boolean = true) {
            val itemLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (4 * cardDensity).toInt()
                }
            }

            val tvSymbol = TextView(context).apply {
                this.text = if (isAvailable) "✓ " else "✗ "
                setTextColor(if (isAvailable) primaryColor else Color.parseColor("#94A3B8"))
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val tvText = TextView(context).apply {
                this.text = itemText
                setTextColor(if (isAvailable) Color.parseColor("#1E293B") else Color.parseColor("#94A3B8"))
                textSize = 12f
            }

            itemLayout.addView(tvSymbol)
            itemLayout.addView(tvText)
            contentLayout.addView(itemLayout)
        }

        // Section 1: Merchant Benefits
        addSectionHeader("🏪 Store & Merchant Features")
        if (info.merchantBenefits.isNotEmpty()) {
            info.merchantBenefits.forEach { addBulletPoint(it) }
        } else {
            addBulletPoint(if (info.storeLimit > 900) "Unlimited Store Branches" else "Up to ${info.storeLimit} Store Branches")
            addBulletPoint(if (info.itemsPerStoreLimit > 9000) "Unlimited Items per Store" else "${info.itemsPerStoreLimit} Items / Store Catalog")
            addBulletPoint(if (info.membersPerStoreLimit > 900) "Unlimited Staff Accounts" else "${info.membersPerStoreLimit} Staff Accounts / Store")
            addBulletPoint("${info.aiQuotaDaily} AI Price Parses per Day")
        }

        // Section 2: Customer Benefits
        addSectionHeader("🤝 Customer & Suki Partner Features")
        if (info.customerBenefits.isNotEmpty()) {
            info.customerBenefits.forEach { addBulletPoint(it) }
        } else {
            addBulletPoint(if (info.sukiLimit > 900) "Unlimited Suking Tindahan Partners" else "${info.sukiLimit} Suking Tindahan Partners")
            addBulletPoint("AI Online Price Search")
        }

        // Section 3: Advanced Unlocks
        addSectionHeader("⚡ Platform Capabilities & Unlocks")
        addBulletPoint("Store Catalog Price Cloning", info.allowPriceCloning)
        addBulletPoint("Export Pricelists to Excel (.xlsx)", info.allowExcelExport)
        addBulletPoint("Export Pricelists to PDF", info.allowPdfExport)
        addBulletPoint("24/7 VIP Priority Support & Fast Parsing", info.hasPrioritySupport || isVip)

        scrollView.addView(contentLayout)
        cardView.addView(scrollView)

        // Close Button
        val btnClose = AppCompatButton(context).apply {
            text = "GOT IT"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val btnBg = GradientDrawable().apply {
                setColor(primaryColor)
                cornerRadius = 10 * cardDensity
            }
            background = btnBg
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (44 * cardDensity).toInt()
            ).apply {
                topMargin = (16 * cardDensity).toInt()
            }
            setOnClickListener { dialog.dismiss() }
        }
        cardView.addView(btnClose)

        rootLayout.addView(cardView, cardLayoutParams)
        dialog.setContentView(rootLayout)
        dialog.show()

        return dialog
    }
}
