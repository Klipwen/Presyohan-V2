# 🚀 Phase 1: Play Store Readiness, Dynamic Subscription Engine & Promotional Claim Mode

**Project:** Presyohan Mobile & Supabase Backend  
**Document Location:** `tempfiles/Phase 1 playstore readines and Subscription implementation plan/Phase_1_Implementation_Plan.md`  
**Last Updated:** October 8, 2026  
**Status:** Active Implementation Plan 🟡  

---

## 📌 Executive Summary

This document serves as the master blueprint for completing **Phase 1** of Presyohan Mobile. It addresses four core tracks:
1. **Google Play Store Technical & Policy Readiness** (Camera permissions, mandatory in-app account deletion, R8 minification, release signing, and developer identity verification).
2. **100% Dynamic Subscription Engine & Admin Tiering** (Prices, discounts, quotas, feature bullet points, and promo badges are dynamically controlled by the Web Admin without requiring app updates).
3. **Phase 1 Promotional "CLAIM NOW!" Launch Mode** (A 100% policy-compliant, frictionless in-app claim flow that eliminates early tax/banking blockers for Play Store approval while preserving value anchoring).
4. **The 1-Action Switch Architecture to Google Play In-App Billing** (A centralized configuration switch ready to turn on paid Google Play Billing and VIP tiers whenever the developer is ready with BIR TIN / bank accounts).

---

## 🚨 Section 1: Technical Code Blockers (Play Store Readiness)

### 1. Camera Permission (`AndroidManifest.xml`) — [COMPLETED ✅]
* **Problem:** [AddMultipleItemsActivity.kt](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/AddMultipleItemsActivity.kt) requested runtime permission for `android.Manifest.permission.CAMERA` during item photo scans, but the permission was not declared in `AndroidManifest.xml`.
* **Fix Applied:** Declared `<uses-permission android:name="android.permission.CAMERA" />` in `AndroidManifest.xml`.

---

### 2. Mandatory In-App Account Deletion (Google Play Mandate) — [COMPLETED ✅]
* **Problem:** Google Play Policy strictly mandates that any app supporting user sign-up (`SignupActivity`) MUST provide:
  1. An in-app button allowing users to permanently delete their account and personal data.
  2. A public web link for users to submit account deletion requests outside the app.
* **Fix Applied:**
  * Implemented a **"Delete Account"** confirmation dialog in `AccountSecurityActivity.kt`.
  * Created Supabase RPC `delete_user_account()` that purges user records from `auth.users`, `app_users`, and cascading store memberships.
  * Provided public landing page on Presyohan Website (`https://presyohan.com/delete-account`).

---

### 3. Release Build Security & App Protection (`build.gradle.kts`) — [COMPLETED ✅]
* **Fix Applied:**
  * Enabled R8 shrinker/obfuscator: `isMinifyEnabled = true` and `isShrinkResources = true`.
  * Configured `signingConfigs` block in `build.gradle.kts`.
  * Configured BuildConfig injection for `GEMINI_API_KEY`, `SUPABASE_URL`, and `SUPABASE_ANON_KEY`.

---

## 💰 Section 2: 100% Dynamic Subscription Tier Architecture

> 🌐 **Dynamic Admin Control Guarantee:**  
> **No price, quota, or promo text is hardcoded in the mobile app.** The mobile app continuously reads live values from the Supabase `subscription_tiers` database table. The Administrator can adjust prices, promo tags (`100% OFF`, `Free offer valid until Nov 30, 2026`), store/staff/item limits, and daily AI quotas directly from the **Web Admin Portal** ("Subscriptions & Tiers" tab).

### Dynamic Configuration Blueprint

| Parameter | Dynamic Source Column | Description |
| :--- | :--- | :--- |
| **Base Price & Discount** | `original_base_price`, `discount_percentage`, `manual_override_price` | Dynamic pricing displayed on mobile paywall and plans screen. |
| **Promotional Tag & Subtitle** | `promo_badge_text`, `expiry_subtitle_text` | E.g. "100% OFF", "Free offer valid until Nov 30, 2026". |
| **Custom CTA Button Label** | `cta_button_text` | E.g. "CLAIM NOW!", "START 7-DAY FREE TRIAL". |
| **Store & Staff Limits** | `max_stores`, `max_staff_per_store` | Soft-lock capacity thresholds for store creation and member invites. |
| **Item & Category Limits** | `max_items_per_store`, `max_categories_per_store` | Soft-lock capacity thresholds for product catalogue management. |
| **Daily AI & Photo Quotas** | `ai_quota_daily`, `photo_scans_quota_daily` | Dynamic daily caps resetting at midnight local time. |
| **Customer / Suki Limits** | `suki_stores_limit`, `public_items_limit`, `internet_search_quota_daily` | Public product discovery and suki partner connection limits. |

---

## 🎁 Section 3: Phase 1 Promotional "CLAIM NOW!" Engine & UI Flow

### Why this approach?
* **Zero Play Store Rejection Risk:** When Google Play testers tap **`[ CLAIM NOW! ]`**, it executes a real, working in-app activation flow without broken links or failing payment sheets.
* **No Day-1 Tax or Banking Blockers:** You do not need a BIR TIN, W-8BEN, or bank account setup on Day 1 to publish to Google Play.
* **Preserves ₱99 Value Anchoring:** Users see that PRO is a valuable premium service with a limited-time 100% discount, rather than assuming the app is permanently free.

### The 4-Step Claim Experience:
1. **User Taps `[ CLAIM NOW! ]`:**
   * Tapping the button on `SubscriptionPaywallDialog` or `SubscriptionStatusActivity` triggers a **2.5-second realistic loading state** (giving users a tangible sensation of account verification and upgrade processing).
2. **Backend Activation RPC (`claim_promotional_tier`):**
   * Supabase updates `app_users.subscription_tier = 'pro'`.
   * Sets `subscription_expires_at` synchronized dynamically with the Admin's promotional expiry date (e.g. *Nov 30, 2026* or default *+30 days*).
   * Automatically synchronizes store tiers and unlocks limits.
3. **Custom Presyohan Success Dialog:**
   * Uses the bespoke Presyohan dialog layout ([`dialog_export_complete.xml`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/res/layout/dialog_export_complete.xml)):
     * **Top-Left Logo:** Authentic Presyohan brand header (`atong presyohan?`).
     * **Graphic:** Clean grey checkmark (`ic_checkmark`).
     * **Title:** `"PRO Tier Activated!"` (Font: *Balsamiq Sans*, Bold, 20sp).
     * **Dynamic Message (100% Data-Driven):**  
       `"You have successfully claimed the promotional ${proTier.name} tier. Enjoy ${proTier.maxStores} stores, ${proTier.itemsPerStoreLimit} items per store, and ${proTier.aiQuotaDaily} daily AI parses."` (Font: *Radio Canada Big*, 14sp).
     * **Done Button:** Full-width pill **`[ Done ]`** button in Market Cyan (`#00bcd4`) / Warm Tangerine (`#ff8c00`).
4. **Instant Unlock:**
   * Dismissing the dialog immediately refreshes the UI and unlocks PRO limits across the app.

---

## 👁️ Section 4: Temporary VIP Tier & Pill Tab Visibility Cleanup

To ensure the app looks visually polished, complete, and purposeful for Google Play review:
1. **`SubscriptionPaywallDialog.kt` / `activity_subscription_paywall.xml`:**
   * **Hide the entire top Pill Tab container** (`btnTabPro` / `btnTabVip` toggle bar).
   * Display a single, beautifully centered **PRO Tier Hero Card** (no swipe carousel or half-cut side cards).
2. **`SubscriptionStatusActivity.kt`:**
   * Hide the VIP plan card (showing only the **Free** tier card and the **PRO** hero card).
3. **Capacity Reached Alerts (`SubscriptionManager.kt`):**
   * Update soft-lock prompts to suggest *"Upgrade to PRO"* (removing premature mentions of VIP).

---

## ⚡ Section 5: The "1-Action Turn-On Switch" (Future Google Play Billing Mode)

When you are ready with your BIR TIN, W-8BEN form, and payout bank account, turning on Google Play In-App Billing requires **just 1 configuration switch**:

```kotlin
// In com/presyohan/app/SubscriptionConfig.kt
object SubscriptionConfig {
    // Current Phase 1 Mode (Zero Tax/Bank Blockers, 100% Functional for Play Store Review)
    const val BILLING_MODE = BillingMode.PROMO_CLAIM_FREE
    const val IS_VIP_VISIBLE = false

    // Future Mode (When ready with BIR TIN / Bank Account)
    // const val BILLING_MODE = BillingMode.GOOGLE_PLAY_BILLING
    // const val IS_VIP_VISIBLE = true
}
```

### What Happens When You Switch to `GOOGLE_PLAY_BILLING`:
1. `SubscriptionPaywallDialog` automatically shows the VIP tab and 2-card carousel again.
2. `SubscriptionStatusActivity` automatically displays the VIP card.
3. Tapping "Subscribe" launches the native **Google Play Billing 1-Tap Checkout** (`billingClient.launchBillingFlow()`).
4. Supabase Edge Function `verify-google-play-subscription` validates receipts with the Google Play Developer API.

---

## 🎨 Section 6: Bespoke UI/UX Design System Compliance

* **Typography:**
  * Brand & Section Headings: **Balsamiq Sans**
  * Numbers, Graphs, Tables & Microcopy: **Radio Canada Big**
* **Color Hierarchy:**
  * Primary Accent: **Presyohan Warm Tangerine** (`#ff8c00`, `#ea580c`)
  * Secondary Accent: **Market Cyan** (`#00bcd4`, `#0891b2`)
  * Data Accents: **Emerald** (`#10b981`), Neutral Slate (`#0f172a`, `#475569`, `#64748b`)
* **Visual Rules:**
  * Zero default AI aesthetics (no purple/blue gradients, no floating blobs, no emojis in buttons).
  * Clean, authentic layouts with purposeful spacing and native elevation.

---

## 📅 Section 7: Master Execution Roadmap

```
[x] Step 1: Fix Manifest CAMERA Permission (AndroidManifest.xml)
[x] Step 2: Implement Account Deletion RPC & UI (AccountSecurityActivity.kt & Web Landing)
[x] Step 3: Configure build.gradle.kts minification, R8 shrinking & release signing
[x] Step 4: Apply Database Migrations (Dynamic subscription_tiers, Role Caps & Public Search Index)
[x] Step 5: Update OnboardingActivity.kt Step 2 Cards (Card A & Card B)
[x] Step 6: Update CustomerHomeActivity.kt Search-First UI & Request Suki Funnel
[x] Step 7: Build Web Admin Portal Dynamic Tier Manager (Prices, Promos, Quotas, Features)
[x] Step 8: Implement Dynamic Quota Gating on Mobile (AI, Photo Scans, Store/Staff limits)
[x] Step 9: Complete Google Play Developer Identity Verification (Oct 8, 2026)
[ ] Step 10: Implement Phase 1 Promotional Claim Flow & 2.5s Loading State
[ ] Step 11: Implement Dynamic Success Dialog (dialog_export_complete.xml template + live quotas)
[ ] Step 12: Clean Paywall UI (Hide Pill Tab Bar, Center PRO Card, Hide VIP on Mobile)
[ ] Step 13: Generate Production Release Keystore (.jks) & Compile Signed Release App Bundle (.aab)
[ ] Step 14: Setup Closed Testing Track & Submit to Play Console for 14-Tester Opt-In
```
