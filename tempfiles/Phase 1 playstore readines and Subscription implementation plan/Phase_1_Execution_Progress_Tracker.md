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
| **Sprint 3: Subscription Engine & Database Tiering** | 6 | 0 | ⚪ Queued |
| **Total** | **17** | **11** | **65% Completed** |

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

- [ ] **Task 3.1: Database Schema Migration for Subscriptions**
  - **Target Location:** Supabase SQL Migrations
  - **Details:** Add `billing_owner_id`, `subscription_tier`, and `subscription_expires_at` to `public.stores` and `public.app_users`.
  - **Status:** Pending

- [ ] **Task 3.2: Subscription Capacity Validator Helper**
  - **Target Location:** Kotlin Backend Utility / Store Repository
  - **Details:** Check tier capacity limits (Free: 1 Store, 3 Members, 10 Categories, 100 Items) before insert operations.
  - **Status:** Pending

- [ ] **Task 3.3: Soft Lock / Read-Only Downgrade Handler**
  - **Target Location:** Store Management View System
  - **Details:** Prevent data deletion on sub expiry; disable "Add Item / Add Staff" buttons when over-limit.
  - **Status:** Pending

- [ ] **Task 3.4: Google Play In-App Billing Integration**
  - **Target Location:** Android Play Billing Library (v6/v7)
  - **Details:** Integrate billing client for PRO (₱99/mo) and VIP (₱299/mo) SKUs.
  - **Status:** Pending

- [ ] **Task 3.5: AI Usage Daily Quota Enforcement (Fair Use Cap)**
  - **Target Location:** AI Parser Service & Supabase Rate Limiter
  - **Details:** Enforce daily caps (Free: 3/day, PRO: 10/day, VIP: 50/day).
  - **Status:** Pending

- [ ] **Task 3.6: Subscription Status Management Screen**
  - **Target File:** `ManageStoreActivity.kt` / Store Settings
  - **Details:** Display current subscription tier badge, renewal date, and upgrade CTA buttons.
  - **Status:** Pending

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

---

## 💡 How We Will Use This Tracker

1. **Before Starting a Task:** We will mark the task as `🟡 In Progress`.
2. **After Implementation:** We will test and verify the changes with `./gradlew assembleDebug` or Supabase tests.
3. **Upon Completion:** We will check `[x]` the box, update the summary table, and append a formal log entry in the **Execution & Completion Log** table above!
