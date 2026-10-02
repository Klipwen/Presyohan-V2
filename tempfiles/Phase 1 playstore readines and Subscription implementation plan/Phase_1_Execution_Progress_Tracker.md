# 🚀 Phase 1 Execution Roadmap & Progress Checklist

**Project:** Presyohan Mobile & Supabase Backend  
**File Location:** `tempfiles/Phase 1 playstore readines and Subscription implementation plan/Phase_1_Execution_Progress_Tracker.md`  
**Created:** September 24, 2026  
**Status:** In Progress 🟡  

---

## 📊 Overall Progress Summary

| Phase / Sprint | Total Tasks | Completed | Status |
| :--- | :---: | :---: | :---: |
| **Sprint 1: Google Play Store & Technical Blockers** | 5 | 5 | ✅ Completed |
| **Sprint 2: Customer Search-First UX & Onboarding** | 6 | 6 | ✅ Completed |
| **Sprint 3: Subscription Engine & Database Tiering** | 7 | 5 | 🟡 In Progress |
| **Total** | **18** | **16** | **89% Completed** |

---

## 🏆 Detailed Sprint Checklist

### 🏃‍♂️ Sprint 1: Google Play Store & Technical Blockers (High Priority)

- [x] **Task 1.1: Camera Manifest Permission Fix**
  - **Target File:** [`AndroidManifest.xml`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/AndroidManifest.xml)
  - **Details:** Add `<uses-permission android:name="android.permission.CAMERA" />` to fix camera crash in `AddMultipleItemsActivity.kt`.
  - **Status:** ✅ Completed

- [x] **Task 1.2: In-App Account Deletion UI & Confirmation Dialog**
  - **Target Files:** [`AccountSecurityActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/AccountSecurityActivity.kt), [`activity_account_security.xml`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/res/layout/activity_account_security.xml)
  - **Details:** Add "Danger Zone: Delete Account" button and confirmation dialog compliant with Google Play Data Safety policy.
  - **Status:** ✅ Completed

- [x] **Task 1.3: Supabase Account Deletion Backend RPC**
  - **Target Location:** [`20260924_000000_add_delete_user_account_rpc.sql`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/supabase/migrations/20260924_000000_add_delete_user_account_rpc.sql)
  - **Details:** Write `delete_user_account()` RPC purging record from `auth.users`, `app_users`, store memberships, and suki customers.
  - **Status:** ✅ Completed

- [x] **Task 1.4: Release Build Security & Minification Setup**
  - **Target Files:** [`build.gradle.kts`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/build.gradle.kts), [`proguard-rules.pro`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/proguard-rules.pro)
  - **Details:** Enable R8 shrinker (`isMinifyEnabled = true`, `isShrinkResources = true`) and add ProGuard rules for Supabase/Ktor/FastExcel.
  - **Status:** ✅ Completed

- [x] **Task 1.5: Release Keystore & Environment Secrets Audit**
  - **Target Files:** [`build.gradle.kts`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/build.gradle.kts)
  - **Details:** Audited BuildConfig fallback injection for `GEMINI_API_KEY`, `SUPABASE_URL`, and `SUPABASE_ANON_KEY`.
  - **Status:** ✅ Completed

---

### 🎨 Sprint 2: Customer Search-First UX & Onboarding Flow

- [x] **Task 2.1: Onboarding Step 2 Card A & Card B Redesign**
  - **Target File:** [`OnboardingActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/OnboardingActivity.kt), [`activity_onboarding.xml`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/res/layout/activity_onboarding.xml)
  - **Details:** Updated Customer Step 2 UI with Card A ("Direct Price Search") and Card B ("Become a Suki").
  - **Status:** ✅ Completed

- [x] **Task 2.2: Customer App Launch Intent Routing**
  - **Target File:** [`OnboardingActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/OnboardingActivity.kt)
  - **Details:** Route Card A directly to pure `CustomerHomeActivity` (`extra_pure_search_mode = true`) and Card B with `extra_open_add_store_sheet = true`.
  - **Status:** ✅ Completed

- [x] **Task 2.3: Pure Search Mode Customer Home Screen**
  - **Target File:** [`CustomerHomeActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/CustomerHomeActivity.kt), [`activity_customer_home.xml`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/res/layout/activity_customer_home.xml)
  - **Details:** Clean hero search bar UI with 2-column minimalist text card grid (`Rice`, `Softdrinks`, `Canned Goods`, `Mineral Water`, `Instant Noodles`, `Coffee`, `Powdered Milk`, `Biscuits`) without forced random item dumping.
  - **Status:** ✅ Completed

- [x] **Task 2.4: Public Product Full-Text Search Database Index**
  - **Target Location:** [`20260924_010000_add_products_public_search_idx.sql`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/supabase/migrations/20260924_010000_add_products_public_search_idx.sql)
  - **Details:** Created GIN index `products_public_search_idx` on `public.products(name)` WHERE `is_public = true` for fast multi-store public item search.
  - **Status:** ✅ Completed

- [x] **Task 2.5: Search-to-Suki Conversion Funnel (`[ 🤝 Request Suki ]`)**
  - **Target File:** [`item_customer_product.xml`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/res/layout/item_customer_product.xml), [`CustomerHomeActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/CustomerHomeActivity.kt)
  - **Details:** Displayed `Request Suki` button on public product rows for unpartnered stores.
  - **Status:** ✅ Completed

- [x] **Task 2.6: Suki Request RPC Integration**
  - **Target Location:** [`CustomerHomeActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/CustomerHomeActivity.kt)
  - **Details:** Wired `send_suki_request(store_id)` RPC to notify store merchant and update customer state to pending.
  - **Status:** ✅ Completed

---

### 💳 Sprint 3: Subscription Engine & Database Tiering

#### 📐 Core Subscription & Role Ownership Rules:
1. **Rule 1: Role Cap on Store Ownership (Option 1):**
   - In Presyohan's role hierarchy (`sales staff`, `manager`, `owner`), the **`owner`** role directly consumes a store slot on the user's subscription tier.
   - **Free Tier:** A user can hold the `owner` role in **at most 1 store** across the platform.
   - **Promotion Blocker:** If Store A already has P2 as `owner`, and P3 attempts to promote P2 to `owner` in Store B, the backend RPC (`update_store_member_role`) **blocks the promotion** with a clear message: *"P2 has reached the 1-store limit for Free tier. Assign P2 as Manager instead or ask P2 to upgrade to PRO."*
   - **Manager Role Alternative:** Managers have complete operational capabilities (managing items, categories, staff, and pricing) without consuming a store ownership slot.
2. **Rule 2: Owner Departure & Free Tier Downgrade (Soft Lock Principle):**
   - If a PRO owner (P1) leaves or transfers primary billing ownership of a PRO store to a Free user (P2), the store status resets to **Free Tier** limits (100 items, 10 categories, 3 staff).
   - **Golden Rule (Never Delete Data):** If the store already has >100 items or >3 staff, existing data is **never deleted**. All products remain live for POS/sales and search, but adding *new* items/staff is soft-locked until P2 upgrades to PRO or reduces counts below Free tier limits.
3. **Rule 3: Secondary Store Archival on Multi-Store Downgrade:**
   - If a PRO owner with multiple stores downgrades to Free, their primary store stays active, while secondary stores transition to **Archived / Read-Only** mode until re-subscribed.

- [x] **Task 3.1: Database Schema Migration & Dynamic Subscription Tiers Table**
  - **Target Location:** [`20260930_000000_enforce_subscription_rules_and_quotas.sql`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/supabase/migrations/20260930_000000_enforce_subscription_rules_and_quotas.sql)
  - **Details:** Created quota checks in `create_store()`, Role Cap enforcement in `update_store_member_role()`, billing owner re-assignment and tier synchronization in `leave_store()`, and automatic database trigger `trigger_sync_user_stores_tier` on `app_users`.
  - **Status:** ✅ Completed

- [x] **Task 3.2: Capacity & Feature Gating Validator (Mobile & Web)**
  - **Target Location:** [`SubscriptionManager.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/SubscriptionManager.kt), [`StoreActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/StoreActivity.kt), [`ManageStoreActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/ManageStoreActivity.kt), [`ManageMembersActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/ManageMembersActivity.kt)
  - **Details:** Implemented `fetchStoreSubscriptionTier()` to dynamically resolve active store tier & expiration. Added feature gating for Excel/PDF export and price cloning. Enforced staff capacity check on member invites and Role Cap handling on promotions.
  - **Status:** ✅ Completed

- [x] **Task 3.3: Soft Lock UI & Capacity Validator**
  - **Target Location:** [`AddEditItemDialogHelper.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/AddEditItemDialogHelper.kt), [`ReviewImportActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/ReviewImportActivity.kt), [`CreateStoreDialogHelper.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/CreateStoreDialogHelper.kt)
  - **Details:** Enforced soft-lock capacity checks on single item creation, category creation, and bulk draft import. Existing data is preserved unconditionally; creation past tier limit displays standard upgrade dialog without harsh colors or emojis.
  - **Status:** ✅ Completed

- [ ] **Task 3.4: Google Play In-App Billing Integration & Purchase Verification**
  - **Target Location:** `build.gradle.kts`, `PlayBillingHelper.kt`, `SubscriptionManager.kt`, Supabase Edge Function (`verify-play-purchase`)
  - **Details:** Add `com.android.billingclient:billing-ktx:6.2.1` to Android project. Implement `PlayBillingHelper.kt` for `presyohan_pro_monthly` and `presyohan_vip_monthly` subscription SKUs. Build dual-mode fallback in `SubscriptionManager.kt` (Local Dev Mode updates Supabase directly for unreleased app testing; Production Mode launches Google Play Billing bottom sheet). Implement receipt token verification with Supabase Edge Functions.
  - **Status:** 📋 Documented & Ready for Implementation

- [ ] **Task 3.5: AI Usage & Internet Search Daily Quota Enforcement**
  - **Target Location:** `AiParserService.kt` & Rate Limiter RPCs
  - **Details:** Enforce daily AI parser caps and public internet search quotas based on `subscription_tiers` configuration.
  - **Status:** Pending

- [x] **Task 3.6: Settings Entry Point, Subscription Status UI & Paywall Modal**
  - **Target Location:** [`SettingsActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/SettingsActivity.kt), [`activity_settings.xml`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/res/layout/activity_settings.xml), [`SubscriptionStatusActivity.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/SubscriptionStatusActivity.kt), [`activity_subscription_status.xml`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/res/layout/activity_subscription_status.xml), [`SubscriptionManager.kt`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/SubscriptionManager.kt)
  - **Details:** Subscriptions card added to `activity_settings.xml` under Account section using `icon_subscriptions.png` with live active tier badge. Wired `SettingsActivity.kt` to launch `SubscriptionStatusActivity.kt` displaying active plan, live capacity usage progress bars, and Free (₱0), PRO (₱99/mo with `icon_pro.png`), and VIP (₱299/mo with `icon_vip.png`) plan cards. Integrated paywall upgrade confirmation dialogs and Supabase tier syncing.
  - **Status:** ✅ Completed

- [x] **Task 3.7: Web Admin Portal Subscription Plan Manager & Manual Overrides**
  - **Target Location:** [`AdminDashboard.jsx`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Website/frontend/src/pages/AdminDashboard.jsx), [`SubscriptionManagement.jsx`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Website/frontend/src/components/admin/SubscriptionManagement.jsx), [`20260925_000000_subscription_tiers_and_admin.sql`](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/supabase/migrations/20260925_000000_subscription_tiers_and_admin.sql)
  - **Details:** Built Subscription Tier Configuration Manager (Admin can edit prices, trial days, limits, merchant & customer bullet lists dynamically without app releases), Manual User & Store Tier Override Modal (granting PRO ⭐ or VIP 💎 access to beta testers / manual subscribers), and AI Usage monitor.
  - **Status:** ✅ Completed

---

## 📜 Execution & Completion Log

| Date & Time | Task ID | Description | Changed Files | Status | Verified By |
| :--- | :---: | :--- | :--- | :---: | :---: |
| 2026-09-24 15:26 | **Task 1.1** | Added `android.permission.CAMERA` and camera hardware feature to Manifest | `AndroidManifest.xml` | ✅ Completed | Gradle Build |
| 2026-09-24 16:05 | **Task 1.2** | Implemented In-App & Web Account Deletion UI with RPC status validation & error checking | `AccountSecurityActivity.kt`, `activity_account_security.xml` | ✅ Completed | Code & UI Audit |
| 2026-09-24 16:04 | **Task 1.3** | Hardened `delete_user_account()` RPC with cascading cleanup across notifications, suki, ratings & stores | `20260924_000000_add_delete_user_account_rpc.sql` | ✅ Completed | SQL Schema Audit |
| 2026-09-24 15:29 | **Task 1.4** | Enabled R8 Shrinking, Minification & ProGuard Rules | `build.gradle.kts`, `proguard-rules.pro` | ✅ Completed | Gradle Build |
| 2026-09-24 16:09 | **Task 1.5** | Added Release Keystore signingConfigs block and BuildConfig secrets injection | `build.gradle.kts` | ✅ Completed | Gradle Build |
| 2026-09-24 17:00 | **Task 2.1** | Redesigned Onboarding Step 2 Card A ("Direct Price Search") and Card B ("Become a Suki") | `activity_onboarding.xml`, `OnboardingActivity.kt` | ✅ Completed | Code & Layout Audit |
| 2026-09-24 17:02 | **Task 2.2** | Implemented Intent Routing (`extra_pure_search_mode` & `extra_open_add_store_sheet`) | `OnboardingActivity.kt` | ✅ Completed | Code Audit |
| 2026-09-24 17:05 | **Task 2.3** | Built 2-Column Minimalist Quick Search Text Card Grid (8 items) & Search Integration | `activity_customer_home.xml`, `CustomerHomeActivity.kt` | ✅ Completed | Code & Layout Audit |
| 2026-09-24 17:06 | **Task 2.4** | Created SQL Migration for `products_public_search_idx` GIN index on public products | `20260924_010000_add_products_public_search_idx.sql` | ✅ Completed | SQL Schema Audit |
| 2026-09-24 17:07 | **Task 2.5** | Added `btnRequestSuki` UI component to customer product list items for unpartnered stores | `item_customer_product.xml`, `CustomerHomeActivity.kt` | ✅ Completed | Code & Layout Audit |
| 2026-09-24 17:08 | **Task 2.6** | Wired Supabase RPC `send_suki_request(store_id)` for search-to-suki conversion | `CustomerHomeActivity.kt` | ✅ Completed | Code Audit |
| 2026-09-25 19:05 | **Task 3.6** | Implemented Subscriptions Settings Card and SubscriptionStatusActivity UI screen with plan comparison cards, live capacity progress bars & paywall upgrade modals | `activity_settings.xml`, `SettingsActivity.kt`, `SubscriptionManager.kt`, `SubscriptionStatusActivity.kt`, `activity_subscription_status.xml` | ✅ Completed | Gradle Build & Code Audit |
| 2026-09-25 21:54 | **Task 3.7** | Built Web Admin Portal Subscription Plan Manager & Manual Overrides | `AdminDashboard.jsx`, `SubscriptionManagement.jsx`, `20260925_000000_subscription_tiers_and_admin.sql` | ✅ Completed | Vite Production Build |
| 2026-09-30 21:40 | **Task 3.1** | Implemented Database Tier Quota & Role Cap Migration (create_store quota, update_store_member_role role cap, leave_store billing transfer & sync trigger) | `20260930_000000_enforce_subscription_rules_and_quotas.sql` | ✅ Completed | SQL Schema Audit |
| 2026-09-30 21:45 | **Task 3.2** | Implemented dynamic store tier resolution (`fetchStoreSubscriptionTier`), feature gating (clone, export) and staff limit checks | `SubscriptionManager.kt`, `StoreActivity.kt`, `ManageStoreActivity.kt`, `ManageMembersActivity.kt` | ✅ Completed | Gradle Compile Build (0 errors) |
| 2026-09-30 21:45 | **Task 3.3** | Implemented soft-lock capacity enforcement across item/category creation and bulk import with clean reusable dialogs | `AddEditItemDialogHelper.kt`, `ReviewImportActivity.kt`, `CreateStoreDialogHelper.kt` | ✅ Completed | Gradle Compile Build (0 errors) |
| 2026-09-30 22:50 | **Task 3.2** | Implemented Store Publishing Gating (`allowCustomerPairing`), auto-private store mode on owner downgrade, and updated Publish Store confirmation copy | `ManageStoreActivity.kt`, `SubscriptionManager.kt`, `20260930_000000_enforce_subscription_rules_and_quotas.sql` | ✅ Completed | Gradle AssembleDebug (0 errors) |

---

## 💡 How We Will Use This Tracker

1. **Before Starting a Task:** We will mark the task as `🟡 In Progress`.
2. **After Implementation:** We will test and verify the changes with `./gradlew assembleDebug` or Supabase tests.
3. **Upon Completion:** We will check `[x]` the box, update the summary table, and append a formal log entry in the **Execution & Completion Log** table above!
