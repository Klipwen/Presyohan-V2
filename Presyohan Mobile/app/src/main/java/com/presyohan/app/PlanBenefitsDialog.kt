package com.presyohan.app

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat

object PlanBenefitsDialog {

    fun show(context: Context, tierId: String): Dialog {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_plan_benefits, null)
        dialog.setContentView(view)
        dialog.setCancelable(true)

        dialog.window?.let { window ->
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }

        val rootLayout = view as FrameLayout
        rootLayout.setOnClickListener { dialog.dismiss() }

        val cardDialogContainer = view.findViewById<LinearLayout>(R.id.cardDialogContainer)
        cardDialogContainer.setOnClickListener { /* Prevent dismiss when clicking the card */ }

        val info = SubscriptionManager.getTierInfo(tierId)
        val isVip = tierId.lowercase() == "vip"
        val primaryColor = ContextCompat.getColor(context, if (isVip) R.color.presyo_teal else R.color.presyo_orange)
        val checkIconRes = if (isVip) R.drawable.ic_check_circle_teal else R.drawable.ic_check_circle_orange

        // Header Views
        val imgTierIcon = view.findViewById<ImageView>(R.id.imgTierIcon)
        val tvTierTitle = view.findViewById<TextView>(R.id.tvTierTitle)
        val tvEffectivePrice = view.findViewById<TextView>(R.id.tvEffectivePrice)
        val tvOriginalPrice = view.findViewById<TextView>(R.id.tvOriginalPrice)
        val tvPromoBadge = view.findViewById<TextView>(R.id.tvPromoBadge)
        val tvPromoExpiration = view.findViewById<TextView>(R.id.tvPromoExpiration)
        val tvTierDescription = view.findViewById<TextView>(R.id.tvTierDescription)
        val layoutBenefitsContainer = view.findViewById<LinearLayout>(R.id.layoutBenefitsContainer)
        val btnGotIt = view.findViewById<AppCompatButton>(R.id.btnGotIt)

        // Bind Header Data
        imgTierIcon.setImageResource(if (isVip) R.drawable.icon_vip else R.drawable.icon_pro)
        imgTierIcon.clearColorFilter()

        tvTierTitle.text = "${info.name} Full Benefits"
        tvTierTitle.setTextColor(primaryColor)

        tvEffectivePrice.text = "${info.effectivePriceText} ${info.periodText}"
        tvEffectivePrice.setTextColor(primaryColor)

        if (info.originalPriceText != null) {
            tvOriginalPrice.text = info.originalPriceText
            tvOriginalPrice.paintFlags = tvOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            tvOriginalPrice.visibility = View.VISIBLE
        } else {
            tvOriginalPrice.visibility = View.GONE
        }

        if (!info.effectiveBadgeText.isNullOrBlank()) {
            tvPromoBadge.text = info.effectiveBadgeText
            tvPromoBadge.setBackgroundResource(if (isVip) R.drawable.bg_promo_chip_teal else R.drawable.bg_promo_chip_orange)
            tvPromoBadge.setTextColor(primaryColor)
            tvPromoBadge.visibility = View.VISIBLE
        } else {
            tvPromoBadge.visibility = View.GONE
        }

        if (!info.promoExpirationText.isNullOrBlank()) {
            tvPromoExpiration.text = info.promoExpirationText
            tvPromoExpiration.visibility = View.VISIBLE
        } else {
            tvPromoExpiration.visibility = View.GONE
        }

        tvTierDescription.text = info.description

        btnGotIt.backgroundTintList = ContextCompat.getColorStateList(context, if (isVip) R.color.presyo_teal else R.color.presyo_orange)
        btnGotIt.setOnClickListener { dialog.dismiss() }

        // Populate Dynamic Benefits from Admin input
        layoutBenefitsContainer.removeAllViews()
        val density = context.resources.displayMetrics.density

        fun addSectionHeader(title: String) {
            val tvHeader = TextView(context).apply {
                text = title
                setTextColor(primaryColor)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (12 * density).toInt(), 0, (6 * density).toInt())
                }
                layoutParams = lp
            }
            layoutBenefitsContainer.addView(tvHeader)
        }

        fun addBenefitRow(benefitText: String) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (6 * density).toInt()
                }
                layoutParams = lp
            }

            val checkIcon = ImageView(context).apply {
                val iconSize = (16 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                setImageResource(checkIconRes)
            }

            val tvText = TextView(context).apply {
                val tvLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = (8 * density).toInt()
                }
                layoutParams = tvLp
                text = benefitText
                setTextColor(Color.parseColor("#1E293B"))
                textSize = 12f
                if (benefitText.contains("Unlimited", ignoreCase = true) || isVip) {
                    typeface = Typeface.DEFAULT_BOLD
                }
            }

            rowLayout.addView(checkIcon)
            rowLayout.addView(tvText)
            layoutBenefitsContainer.addView(rowLayout)
        }

        val hasCustomMerchant = info.merchantBenefits.isNotEmpty()
        val hasCustomCustomer = info.customerBenefits.isNotEmpty()

        if (hasCustomMerchant || hasCustomCustomer) {
            // Render exact Admin Inputted Merchant Benefits
            if (hasCustomMerchant) {
                addSectionHeader("Store & Merchant Features")
                info.merchantBenefits.forEach { raw ->
                    val clean = raw.replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\s•\\-]+"), "").trim()
                    if (clean.isNotEmpty()) addBenefitRow(clean)
                }
            }

            // Render exact Admin Inputted Customer Benefits
            if (hasCustomCustomer) {
                addSectionHeader("Customer & Suki Partner Features")
                info.customerBenefits.forEach { raw ->
                    val clean = raw.replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\s•\\-]+"), "").trim()
                    if (clean.isNotEmpty()) addBenefitRow(clean)
                }
            }
        } else {
            // Fallback: Synthesized dynamic database limits
            addSectionHeader("Store & Merchant Features")
            addBenefitRow(if (info.storeLimit > 900) "Unlimited Store Branches" else "Up to ${info.storeLimit} Store Branches")
            addBenefitRow(if (info.itemsPerStoreLimit > 9000) "Unlimited Items per Store" else "${info.itemsPerStoreLimit} Items / Store Catalog")
            addBenefitRow(if (info.categoriesPerStoreLimit > 900) "Unlimited Categories per Store" else "${info.categoriesPerStoreLimit} Categories / Store")
            addBenefitRow(if (info.membersPerStoreLimit > 900) "Unlimited Staff Accounts" else "${info.membersPerStoreLimit} Staff Accounts / Store")
            addBenefitRow("${info.aiQuotaDaily} Daily AI Price Parses")
            if (info.allowPriceCloning) addBenefitRow("Store Catalog Price Cloning")
            if (info.allowExcelExport) addBenefitRow("Export Pricelists to Excel (.xlsx)")
            if (info.allowPdfExport) addBenefitRow("Export Pricelists to PDF")
            if (info.allowNotesExport) addBenefitRow("Export Pricelists as Notes")

            addSectionHeader("Customer & Suki Partner Features")
            addBenefitRow(if (info.sukiLimit > 900) "Unlimited Suking Tindahan Partners" else "${info.sukiLimit} Suking Tindahan Partners")
            addBenefitRow(if (info.presyohanStoresLimit > 900) "Unlimited Presyohan Stores" else "${info.presyohanStoresLimit} Presyohan Stores")
            if (info.internetSearchQuota > 0) addBenefitRow("${info.internetSearchQuota} Daily Internet Price Searches")
            if (info.allowCustomerPairing) addBenefitRow("Customer Price Pairing")

            if (info.hasPrioritySupport || isVip) {
                addSectionHeader("Priority Unlocks")
                addBenefitRow("24/7 VIP Priority Support & Fast Parsing")
            }
        }

        dialog.show()
        return dialog
    }
}
