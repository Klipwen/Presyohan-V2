# 🚀 Phase 1: Play Store Readiness, Subscription Engine & Customer UX Implementation Plan

**Project:** Presyohan Mobile & Supabase Backend  
**Document Location:** `tempfiles/Phase 1 playstore readines and Subscription implementation plan/Phase_1_Implementation_Plan.md`  
**Last Updated:** September 22, 2026  
**Status:** Active Implementation Plan  

---

## 📌 Executive Summary

This document serves as the master blueprint for completing **Phase 1** of Presyohan Mobile. It addresses three core tracks:
1. **Google Play Store Technical & Policy Readiness** (Fixing 3 app blockers and fulfilling Google Play Console mandates).
2. **Subscription Engine Architecture & Business Tiering** (Implementing Free, PRO ₱99, and VIP ₱299 tiers, edge-case resolution, and database RLS enforcement).
3. **Customer Search-First UX & Onboarding Redesign** (Modified 3-step onboarding, zero-friction global search, and the 3-step Search-to-Suki conversion funnel).

---

## 🚨 Section 1: The 3 Technical Code Blockers (Play Store Readiness)

### 1. Missing Camera Permission (`AndroidManifest.xml`)
* **Problem:** [AddMultipleItemsActivity.kt](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/AddMultipleItemsActivity.kt) requests runtime permission for `android.Manifest.permission.CAMERA` during item photo scans, but the permission is **NOT declared** in `AndroidManifest.xml`.
* **Impact:** Causes silent failures or `SecurityException` crashes when opening the camera on real devices.
* **Fix:** Add `<uses-permission android:name="android.permission.CAMERA" />` to `AndroidManifest.xml`.

---

### 2. Mandatory In-App Account Deletion (Google Play Mandate)
* **Problem:** Google Play Policy strictly mandates that any app supporting user sign-up (`SignupActivity`) MUST provide:
  1. An in-app button allowing users to permanently delete their account and personal data.
  2. A public web link for users to submit account deletion requests outside the app.
* **Current State:** [AccountSecurityActivity.kt](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/AccountSecurityActivity.kt) only supports password changes.
* **Fix:**
  * Implement a **"Delete Account"** confirmation dialog in `AccountSecurityActivity.kt`.
  * Create a Supabase RPC `delete_user_account()` that purges user records from `auth.users`, `app_users`, and cascading store memberships.
  * Provide a public landing page on Presyohan Website (`https://presyohan.com/delete-account`).

---

### 3. Release Build Security & App Protection (`build.gradle.kts`)
* **Problem:** `isMinifyEnabled = false` in [build.gradle.kts](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/build.gradle.kts), and release signing keystore configuration (`.jks`) is missing.
* **Impact:** Vulnerable reverse engineering, large APK sizes, and inability to compile signed production Android App Bundles (`.aab`).
* **Fix:**
  * Enable R8 shrinker/obfuscator: set `isMinifyEnabled = true` and `isShrinkResources = true`.
  * Add release `signingConfigs` referencing `release.keystore`.
  * Ensure build environment injects production `GEMINI_API_KEY`, `SUPABASE_URL`, and `SUPABASE_ANON_KEY`.

---

## 💰 Section 2: Subscription Tier Matrix & Pricing Model

### Updated Pricing Structure
* **Free Tier:** **₱0** (Ideal for micro sari-sari stores & single vendors).
* **PRO Tier:** **₱99 / month** (Ideal for growing single & multi-branch retail stores).
* **VIP Tier:** **₱299 / month** (Ideal for high-volume businesses & enterprise managers).

### Subscription Feature Matrix

| Feature / Capacity Limit | Free (₱0) | PRO (₱99/mo) | VIP (₱299/mo) |
| :--- | :---: | :---: | :---: |
| **Owned Store Limit** | **1 Store** | **10 Stores** | **Unlimited** |
| **Member Limit / Store** | **3 Members** | **10 Members** | **Unlimited** |
| **Category Limit / Store** | **10 Categories** | **25 Categories** | **Unlimited** |
| **Items Limit / Store** | **100 Items** | **500 Items** | **Unlimited** |
| **AI Parser Quota** | 3 / day | 10 / day | **50 / day** *(Fair Use Cap)* |
| **Photo Scans Quota** | 3 / day | 10 / day | **50 / day** *(Fair Use Cap)* |
| **Store Items Cloning** | ❌ NO | ✅ Unlimited | ✅ Unlimited |
| **Customer Pairing** | ❌ NO | ✅ Unlimited | ✅ Unlimited |
| **Convert to Excel** | ❌ NO | ✅ Unlimited | ✅ Unlimited |
| **Convert to PDF** | ❌ NO | ✅ Unlimited | ✅ Unlimited |
| **Convert as Notes** | ✅ Unlimited | ✅ Unlimited | ✅ Unlimited |
| **Suking Tindahan Limit** | 5 | 15 | Unlimited |
| **Presyohan Store Public Items** | 5 | 15 | Unlimited |
| **Internet Search Quota** | 3 / day | 15 / day | Unlimited |

> ⚠️ **Protection Note (Fair Use Cap):** VIP AI Parser and Photo Scans are set to **50/day** instead of uncapped "Unlimited" to protect your Google Gemini API costs from script/bot exploitation.

---

## 🧠 Section 3: Subscription Edge Case Rules & Architecture Solutions

### Rule 1: The "Role Cap on Store Ownership" (Option 1 - Promotion Rule)
* **Question:** *What happens if a Free tier user (P2) is invited and promoted to `owner` in Store A by P1, and then promoted to `owner` in Store B by P3?*
* **Architecture Solution:** 
  * In Presyohan's role hierarchy (`sales staff`, `manager`, `owner`), holding the **`owner`** role directly consumes a store slot on that user's subscription tier.
  * A **Free Tier user can hold the `owner` role in at most 1 store** across the entire platform.
  * If another store attempts to promote P2 to `owner`, the backend RPC (`update_store_member_role`) **blocks the promotion** with an informative error.
  * **Manager Alternative:** The store owner can promote P2 to **`manager`** instead. Managers possess complete operational capabilities (managing products, prices, categories, and staff) without consuming a store ownership quota.
  * To be an `owner` of multiple stores, P2 must upgrade to PRO (up to 10 stores) or VIP (unlimited).

### Rule 2: The "Soft Lock / Read-Only Over-Limit" Principle (Owner Departure & Downgrade)
* **Question:** *What happens when a PRO owner (P1) leaves a store with >100 items or >3 staff, leaving a Free user (P2) as the sole owner?*
* **Golden Rule:** **NEVER DELETE USER DATA.** 
  * The store status resets to **Free Tier** limits.
  * Existing products and staff remain 100% active and searchable for cashiering/POS.
  * Adding *new* items, categories, or staff is **soft-locked** until P2 upgrades to PRO or reduces counts below Free tier limits.

### Rule 3: Multiple Store Downgrade Handling
* **Behavior:** When dropping from PRO to Free (limit 1 store), the designated Primary Store remains active; the remaining secondary stores switch to **Archived / Read-Only** mode until re-subscribed.

---

## 🛒 Section 4: Customer Search-First UX & Onboarding Redesign

*(Detailed guide available in [`Customer_Onboarding_and_Search_Plan.md`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/tempfiles/Phase%201%20playstore%20readines%20and%20Subscription%20implementation%20plan/Customer_Onboarding_and_Search_Plan.md))*

### 1. Modified 3-Step Customer Onboarding
* **Step 1:** Role Selection (**Merchant** vs **Customer**).
* **Step 2:** Customer Focus Cards:
  * **Card A:** `"Direct Price Search"` (*Search and compare prices across all public stores immediately*).
  * **Card B:** `"Become a Suki"` (*Partner with local stores to view their full catalogs & get price updates*).
* **Step 3:** Success Screen (CEO Welcome Message) + **"LAUNCH APP"** button.
* **Launch Behavior:**
  * **Card A:** Launches directly to `CustomerHomeActivity` in Pure Search Mode (Zero blockers).
  * **Card B:** Launches `CustomerHomeActivity` with the *Add Store Bottom Sheet* auto-popped up (`Add Presyohan Store` / `Add Suking Tindahan`).

### 2. Search-to-Suki Conversion Funnel
1. **Discovery (Step 1):** Customer searches *"Rice"* $\rightarrow$ Views item result from *Aling Nena's Store* $\rightarrow$ Opens Item Detail View with **`[ 🤝 Request Suki ]`** button.
2. **Engagement (Step 2):** Customer clicks **`Request Suki`** $\rightarrow$ Invokes `send_suki_request` RPC to notify merchant.
3. **Retention (Step 3):** Merchant approves request $\rightarrow$ Customer unlocks *Aling Nena's* complete store catalog & category tree.

---

## 🛠️ Section 5: Database & Backend Technical Blueprint

```sql
-- Migration: Subscription billing fields & daily AI usage tracking

ALTER TABLE public.stores 
ADD COLUMN IF NOT EXISTS billing_owner_id UUID REFERENCES public.app_users(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS subscription_tier TEXT NOT NULL DEFAULT 'free',
ADD COLUMN IF NOT EXISTS subscription_expires_at TIMESTAMPTZ NULL;

ALTER TABLE public.app_users
ADD COLUMN IF NOT EXISTS subscription_tier TEXT NOT NULL DEFAULT 'free',
ADD COLUMN IF NOT EXISTS subscription_expires_at TIMESTAMPTZ NULL;

-- Public Product Search Index
CREATE INDEX IF NOT EXISTS products_public_search_idx 
ON public.products USING gin(to_tsvector('english', name)) 
WHERE is_public = true;
```

---

## 📅 Section 6: Master Execution Roadmap

```
[ ] Step 1: Fix Manifest CAMERA Permission
[ ] Step 2: Implement Account Deletion RPC & UI
[ ] Step 3: Configure build.gradle.kts minification & signing
[ ] Step 4: Apply Database Migrations (Subscriptions & Public Search Index)
[ ] Step 5: Update OnboardingActivity.kt Step 2 Cards (Card A & Card B)
[ ] Step 6: Update CustomerHomeActivity.kt Search-First UI & Request Suki Funnel
[ ] Step 7: Integrate In-App Billing (₱99 PRO & ₱299 VIP)
[ ] Step 8: Perform 14-day Closed Beta Testing & Submit to Play Console
```
