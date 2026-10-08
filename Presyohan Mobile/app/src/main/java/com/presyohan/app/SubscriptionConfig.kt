package com.presyohan.app

/**
 * Central Configuration for Presyohan Subscription and Billing Architecture.
 *
 * Provides a 1-Action Switch between:
 * 1. PROMO_CLAIM_FREE (Phase 1 Mode: 100% policy-compliant, zero tax/bank blockers for Play Store approval).
 * 2. GOOGLE_PLAY_BILLING (Future Production Mode: Paid Google Play in-app purchases).
 */
enum class BillingMode {
    PROMO_CLAIM_FREE,
    GOOGLE_PLAY_BILLING
}

object SubscriptionConfig {
    /**
     * Active Billing Mode.
     * To activate paid Google Play in-app purchases in the future, change this to [BillingMode.GOOGLE_PLAY_BILLING].
     */
    val BILLING_MODE: BillingMode = BillingMode.PROMO_CLAIM_FREE

    /**
     * Controls the visibility of VIP tier cards, tabs, and upgrade prompts across the mobile app.
     * Temporarily set to false for Phase 1 to present a clean, single-tier (PRO) hero experience.
     * Set to true when ready to launch VIP tier in production.
     */
    const val IS_VIP_VISIBLE: Boolean = false
}
