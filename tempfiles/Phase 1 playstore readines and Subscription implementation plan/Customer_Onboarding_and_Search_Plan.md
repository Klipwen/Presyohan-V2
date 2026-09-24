# 🛒 Phase 1: Customer Onboarding & Search-First Experience Plan

**Project:** Presyohan Mobile & Supabase Backend  
**Document Location:** `tempfiles/Phase 1 playstore readines and Subscription implementation plan/Customer_Onboarding_and_Search_Plan.md`  
**Last Updated:** September 24, 2026  
**Status:** Implemented ✅  

---

## 📌 Executive Summary

This plan re-architects the **Customer Onboarding** and **Home Screen UX** for Presyohan Mobile. It removes forced store selection on first launch, introduces a 3-step personalized onboarding flow, provides instant global product search across all public stores, and establishes a seamless **Search-to-Suki conversion funnel**.

---

## 📱 Section 1: Modified 3-Step Customer Onboarding Flow

The onboarding sequence retains the standard **3-Step Layout** ending with the **"LAUNCH APP"** button, but adapts Step 2 for Customer preferences:

```
┌──────────────────────────┐    ┌──────────────────────────────────┐    ┌──────────────────────────┐
│  STEP 1: ROLE SELECTION  │ ──►│    STEP 2: CUSTOMER FOCUS CARD   │ ──►│  STEP 3: SUCCESS SCREEN  │
│  • Merchant (Store Owner)│    │  • Card A: Direct Price Search   │    │  • CEO Welcome Message   │
│  • Customer (Shopper)    │    │  • Card B: Become a Suki         │    │  • "LAUNCH APP" Button   │
└──────────────────────────┘    └──────────────────────────────────┘    └──────────────────────────┘
```

### 1. Step 1 — Role Selection
* User selects **Merchant** or **Customer**.

### 2. Step 2 — Customer Mode Selection
* **Card A: "Direct Price Search"**
  * *Subtitle:* "Search and compare prices across all public stores immediately."
* **Card B: "Become a Suki"**
  * *Subtitle:* "Partner with local stores to view their full catalogs & get price updates."

### 3. Step 3 — Success Screen & Launch Behavior
* CEO Welcome Avatar + Message: *"I hope this app helps you stay updated on daily price changes with ease. Enjoy using the app! :)"*
* **"LAUNCH APP"** button triggers app routing based on Step 2 selection:

| Selection | Target Destination | Initial Screen State |
| :--- | :--- | :--- |
| **Card A Selected** | `CustomerHomeActivity` | **Pure Search Mode** (Clean search home screen, no forced modals). |
| **Card B Selected** | `CustomerHomeActivity` | **Store Link Mode** (Auto-pops up the Bottom Sheet to *Add Presyohan Store* or *Add Suking Tindahan*). |

---

## 🔍 Section 2: Customer Home Screen Architecture (Pure Search Mode)

When a customer enters the home screen via **Card A**:

1. **Header Layout Scoping**:
   * Header container strictly contains ONLY: Presyohan logo, title, notifications icon, profile avatar, and search bar.
2. **Prices Tab Main Content Quick-Search Cards**:
   * **2-Column Minimalist Quick-Search Grid Cards** (Clean text-only design without emojis/icons) are located at the top of the Prices tab main content area:
     ```
     ┌───────────────────────────┐  ┌───────────────────────────┐
     │           Rice            │  │        Softdrinks         │
     └───────────────────────────┘  └───────────────────────────┘
     ┌───────────────────────────┐  ┌───────────────────────────┐
     │       Canned Goods        │  │       Mineral Water       │
     └───────────────────────────┘  └───────────────────────────┘
     ┌───────────────────────────┐  ┌───────────────────────────┐
     │      Instant Noodles      │  │          Coffee           │
     └───────────────────────────┘  └───────────────────────────┘
     ┌───────────────────────────┐  ┌───────────────────────────┐
     │       Powdered Milk       │  │         Biscuits          │
     └───────────────────────────┘  └───────────────────────────┘
     ```
   * Tapping any of the 8 cards instantly populates the search bar and executes the search query.
3. **Dynamic Card Visibility Rules**:
   * Cards are **VISIBLE ONLY WHEN**: User has zero Suki partners (`linkedStoreIds.isEmpty()`) **AND** search query is empty (inactive search).
   * Cards are **HIDDEN WHEN**: User has 1+ linked Suki stores **OR** search is active (query typed in search bar).
4. **Global Public Item Search**:
   * Search queries execute against **ALL public items (`is_public = true`) across ALL public stores** in the Supabase database—**zero store partnership required!**

---

## 🔄 Section 3: The 3-Step Customer Conversion Funnel

This flow seamlessly converts casual price-seekers into loyal store Sukis:

```
┌────────────────────────────────┐    ┌────────────────────────────────┐    ┌────────────────────────────────┐
│      STEP 1: DISCOVERY         │ ──►│      STEP 2: ENGAGEMENT        │ ──►│      STEP 3: RETENTION         │
│ Search "Rice" ➔ View Item Card │    │ Click "Request Suki" on Item   │    │ Partnership Active ➔ Unlock    │
│ from "Aling Nena's Store"      │    │ Card / Store Detail View       │    │ Store's Full Catalog & Feeds   │
└────────────────────────────────┘    └────────────────────────────────┘    └────────────────────────────────┘
```

### Step 1: Discovery (Item Search & Detail View)
* Customer searches for *"Rice"* on the Home Search Bar.
* Search results display item cards from various public stores.
* Clicking an item card opens the **Item Detail View**, showing:
  * Item Name, Unit, Price, and Store Name (*e.g., Aling Nena's Store*).
  * A prominent action button: **`[ 🤝 Request Suki ]`** (if not yet partnered).

### Step 2: Engagement (Suki Request)
* Customer clicks **`Request Suki`**.
* The app invokes `send_suki_request(store_id)` RPC to notify the store owner.
* UI button updates to **`[ ⏳ Suki Request Pending ]`**.

### Step 3: Retention (Full Catalog & Category Access)
* Once paired/accepted:
  * Customer unlocks **Aling Nena's Store's complete catalog & category list**.
  * The store is pinned under the customer's **"My Suki Stores"** tab for 1-tap browsing and live price update notifications.

---

## 🛠️ Section 4: Code Implementation Tasks

### 1. Android Frontend (`Presyohan Mobile`)
* **`OnboardingActivity.kt`**:
  * Update Step 2 Customer Layout with **Card A** (*Direct Price Search*) and **Card B** (*Become a Suki*).
  * Update `launchMainApp()` to route Card A to `CustomerHomeActivity` directly, and Card B to `CustomerHomeActivity` with intent flag `extra_open_add_store_sheet = true`.
* **`CustomerHomeActivity.kt`**:
  * Implement intent check in `onCreate()` to display the Add Store Bottom Sheet if `extra_open_add_store_sheet` is `true`.
  * Update `rvCustomerPrices` search adapter to include a **`Request Suki`** action on item details for unpartnered public stores.

### 2. Database & Supabase Backend
* **Public Search RPC / Query**:
  ```sql
  -- Ensure high-performance search across all public items
  CREATE INDEX IF NOT EXISTS products_public_search_idx 
  ON public.products USING gin(to_tsvector('english', name)) 
  WHERE is_public = true;
  ```
* **Suki Request Handler**:
  * Utilize `send_suki_request` SQL RPC (`20260707_223600_add_send_suki_request.sql`) to manage customer-store pairing requests.

---
