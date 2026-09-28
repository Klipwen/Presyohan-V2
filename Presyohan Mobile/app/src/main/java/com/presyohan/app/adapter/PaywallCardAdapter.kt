package com.presyohan.app.adapter

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.presyohan.app.R
import com.presyohan.app.SubscriptionTierInfo

class PaywallCardAdapter(
    private var tiers: List<SubscriptionTierInfo>,
    private val onOpenFullBenefits: (String) -> Unit
) : RecyclerView.Adapter<PaywallCardAdapter.CardViewHolder>() {

    fun updateTiers(newTiers: List<SubscriptionTierInfo>) {
        this.tiers = newTiers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.layout_paywall_tier_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val tier = tiers[position]
        holder.bind(tier, onOpenFullBenefits)
    }

    override fun getItemCount(): Int = tiers.size

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardContainer: LinearLayout = itemView.findViewById(R.id.cardContainer)
        private val imgCardTierIcon: ImageView = itemView.findViewById(R.id.imgCardTierIcon)
        private val tvCardTierTitle: TextView = itemView.findViewById(R.id.tvCardTierTitle)
        private val tvCardEffectivePrice: TextView = itemView.findViewById(R.id.tvCardEffectivePrice)
        private val tvCardOriginalPrice: TextView = itemView.findViewById(R.id.tvCardOriginalPrice)
        private val tvCardPeriod: TextView = itemView.findViewById(R.id.tvCardPeriod)
        private val tvCardPromoExpiration: TextView = itemView.findViewById(R.id.tvCardPromoExpiration)
        private val tvCardPromoBadge: TextView = itemView.findViewById(R.id.tvCardPromoBadge)
        private val tvCardDescription: TextView = itemView.findViewById(R.id.tvCardDescription)
        private val dividerCard: View = itemView.findViewById(R.id.dividerCard)
        private val layoutCardBenefitsContainer: LinearLayout = itemView.findViewById(R.id.layoutCardBenefitsContainer)
        private val btnCardViewFullBenefits: TextView = itemView.findViewById(R.id.btnCardViewFullBenefits)

        fun bind(tier: SubscriptionTierInfo, onOpenFullBenefits: (String) -> Unit) {
            val context = itemView.context
            val isVip = tier.id == "vip"

            // Card Border & Background
            cardContainer.setBackgroundResource(if (isVip) R.drawable.bg_paywall_card_vip else R.drawable.bg_paywall_card_pro)

            // Header Icon
            imgCardTierIcon.setImageResource(if (isVip) R.drawable.icon_vip else R.drawable.icon_pro)
            imgCardTierIcon.clearColorFilter()

            // Title & Accent Colors
            val primaryColor = ContextCompat.getColor(context, if (isVip) R.color.presyo_teal else R.color.presyo_orange)
            tvCardTierTitle.text = tier.name
            tvCardTierTitle.setTextColor(primaryColor)

            // Prices
            tvCardEffectivePrice.text = tier.effectivePriceText
            tvCardEffectivePrice.setTextColor(primaryColor)

            if (tier.originalPriceText != null) {
                tvCardOriginalPrice.text = tier.originalPriceText
                tvCardOriginalPrice.paintFlags = tvCardOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvCardOriginalPrice.visibility = View.VISIBLE
            } else {
                tvCardOriginalPrice.visibility = View.GONE
            }

            tvCardPeriod.text = tier.periodText

            // Promo expiration
            if (!tier.promoExpirationText.isNullOrBlank()) {
                tvCardPromoExpiration.text = tier.promoExpirationText
                tvCardPromoExpiration.visibility = View.VISIBLE
            } else {
                tvCardPromoExpiration.visibility = View.GONE
            }

            // Promo Badge Chip
            if (!tier.effectiveBadgeText.isNullOrBlank()) {
                tvCardPromoBadge.text = tier.effectiveBadgeText
                tvCardPromoBadge.setBackgroundResource(if (isVip) R.drawable.bg_promo_chip_teal else R.drawable.bg_promo_chip_orange)
                tvCardPromoBadge.setTextColor(primaryColor)
                tvCardPromoBadge.visibility = View.VISIBLE
            } else {
                tvCardPromoBadge.visibility = View.GONE
            }

            // Description
            tvCardDescription.text = tier.description

            // Divider color
            dividerCard.setBackgroundColor(Color.parseColor(if (isVip) "#99F6E4" else "#FED7AA"))

            // Render Dynamic Benefit Checkmarks (Up to 14 checks maximum to prevent screen overflow)
            renderBenefitsList(tier, isVip)

            // View Full Benefits Click
            btnCardViewFullBenefits.setOnClickListener {
                onOpenFullBenefits(tier.id)
            }
        }

        private fun renderBenefitsList(tier: SubscriptionTierInfo, isVip: Boolean) {
            val context = itemView.context
            layoutCardBenefitsContainer.removeAllViews()

            val rawList = mutableListOf<String>()
            if (tier.merchantBenefits.isNotEmpty() || tier.customerBenefits.isNotEmpty()) {
                rawList.addAll(tier.merchantBenefits)
                rawList.addAll(tier.customerBenefits)
            } else {
                rawList.add(if (tier.storeLimit > 900) "Unlimited Stores" else "Up to ${tier.storeLimit} Stores")
                rawList.add(if (tier.itemsPerStoreLimit > 9000) "Unlimited items / store" else "${tier.itemsPerStoreLimit} items / store")
                rawList.add(if (tier.categoriesPerStoreLimit > 900) "Unlimited Categories" else "${tier.categoriesPerStoreLimit} categories / store")
                rawList.add(if (tier.membersPerStoreLimit > 900) "Unlimited staff / store" else "${tier.membersPerStoreLimit} staff / store")
                rawList.add("${tier.aiQuotaDaily} AI parses / day")
                if (tier.allowPriceCloning) rawList.add("Price Cloning & Export")
                if (tier.allowCustomerPairing) rawList.add("Customer Pairing (suki)")
                if (tier.allowExcelExport) rawList.add("Convert to Excel & PDF")
                if (tier.sukiLimit > 0) rawList.add(if (tier.sukiLimit > 900) "Unlimited Suki Partners" else "${tier.sukiLimit} Suki Partners")
                if (tier.internetSearchQuota > 0) rawList.add("${tier.internetSearchQuota} Internet Searches / day")
                if (tier.hasPrioritySupport || isVip) rawList.add("24/7 VIP Priority Support")
            }

            // Clean leading emojis, bullets, and whitespace from admin strings
            val cleanList = rawList.mapNotNull { raw ->
                val clean = raw.replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\s•\\-]+"), "").trim()
                if (clean.isNotEmpty()) clean else null
            }.take(14) // Max 14 items to cleanly fit the card layout

            val checkIconRes = if (isVip) R.drawable.ic_check_circle_teal else R.drawable.ic_check_circle_orange
            val density = context.resources.displayMetrics.density

            cleanList.forEachIndexed { index, benefitText ->
                val rowLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    if (index > 0) lp.topMargin = (5 * density).toInt()
                    layoutParams = lp
                }

                val checkIcon = ImageView(context).apply {
                    val iconSize = (16 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                    setImageResource(checkIconRes)
                }

                val textView = TextView(context).apply {
                    val tvLp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = (8 * density).toInt()
                    }
                    layoutParams = tvLp
                    text = benefitText
                    setTextColor(Color.parseColor("#1E293B"))
                    textSize = 11.5f
                    if (benefitText.contains("Unlimited", ignoreCase = true) || isVip) {
                        typeface = Typeface.DEFAULT_BOLD
                    }
                }

                rowLayout.addView(checkIcon)
                rowLayout.addView(textView)
                layoutCardBenefitsContainer.addView(rowLayout)
            }
        }
    }
}
