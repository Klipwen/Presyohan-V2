# Presyohan Web Admin Portal — Architectural Plan & Design Specification

This document outlines the design, user flows, database changes, and UI/UX specification for the new **Presyohan Web Admin Portal**. The portal will integrate seamlessly with the existing React frontend, Kotlin mobile app, and Supabase database.

---

## 🎨 Visual Theme & Design System

The Admin Portal will maintain the **Presyohan core theme of simplicity, minimalism, and premium utility** by employing a highly-structured sidebar interface, sleek charts, glassmorphic touches, and clean tables.

### Brand Color Palette
We base the colors on the established mobile and web assets:
- **Primary Teal (Action & Accent):** `#1FA5C4` (HSL: `191°, 70%, 45%`)
- **Accent Amber/Orange (Highlights & Notifications):** `#FFB703` (HSL: `43°, 100%, 50%`) and `#F48C06` (HSL: `34°, 91%, 49%`)
- **Primary Text:** `#2E3E50` (Slate charcoal for professional contrast)
- **Backgrounds:** `#F8FAFC` (Slate-light) for body and dashboard panels; `#FFFFFF` (White) for cards and modals.
- **Borders & Separators:** `#E2E8F0` (Soft gray border)

### Typography & Layout
- **Font-Family:** `Outfit` or `Inter` (via Google Fonts) to align with a modern, high-grade interface.
- **Layout:** Fixed left sidebar navigation, top header (displaying admin profile and status), and a flexible central workspace.
- **Animations:** Subtle hover transitions on buttons and sidebar elements (`transition: all 0.2s ease`).

---

## 🗄️ Database Schema Updates (Supabase Migrations)

To support the new features, we will create a new timestamped Supabase migration (e.g., `20260622_000000_admin_portal_schema.sql`). Below is the SQL blueprint:

```sql
-- 1. Extend app_users with Admin Roles and Activity Tracking
ALTER TABLE public.app_users 
ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'user',
ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Create index to optimize active-user queries
CREATE INDEX IF NOT EXISTS app_users_activity_idx ON public.app_users (last_activity_at);

-- 2. Create app_releases table for version checking and force-updates
CREATE TABLE IF NOT EXISTS public.app_releases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version_code INT UNIQUE NOT NULL,
    version_name TEXT NOT NULL,
    download_url TEXT NOT NULL,
    whats_new TEXT NOT NULL,
    is_forced BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL REFERENCES public.app_users(id) ON DELETE SET NULL
);

-- Index for lookup of the latest version code
CREATE INDEX IF NOT EXISTS app_releases_version_idx ON public.app_releases (version_code DESC);

-- 3. Create announcements table for broadcasting alerts
CREATE TABLE IF NOT EXISTS public.announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL REFERENCES public.app_users(id) ON DELETE SET NULL
);

-- 4. Support multiple Standard Stores & an Active Default Store
ALTER TABLE public.stores
ADD COLUMN IF NOT EXISTS is_standard_store BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS is_default_standard BOOLEAN NOT NULL DEFAULT FALSE;

-- Ensure only ONE store can be marked as the default standard store at any time
CREATE UNIQUE INDEX IF NOT EXISTS unique_default_standard_store ON public.stores (is_default_standard) 
WHERE (is_default_standard = TRUE);
```

### Row Level Security (RLS) Policy Adjustments

To protect the admin endpoints and allow the mobile app to read updates/announcements:

```sql
-- Allow authenticated users to view active announcements and releases
ALTER TABLE public.app_releases ENABLE ROW LEVEL SECURITY;
CREATE POLICY select_releases_auth ON public.app_releases 
    FOR SELECT TO authenticated USING (true);

ALTER TABLE public.announcements ENABLE ROW LEVEL SECURITY;
CREATE POLICY select_announcements_auth ON public.announcements 
    FOR SELECT TO authenticated USING (is_active = true);

-- restrict writes to app_releases and announcements to admins only
CREATE POLICY admin_all_releases ON public.app_releases 
    FOR ALL TO authenticated 
    USING (EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'));

CREATE POLICY admin_all_announcements ON public.announcements 
    FOR ALL TO authenticated 
    USING (EXISTS (SELECT 1 FROM public.app_users WHERE id = auth.uid() AND role = 'admin'));
```

---

## 📈 Active User Calculation Logic

The active user count must be highly accurate and lightweight.
- **Metric definition:** Active if their `last_activity_at` was updated within the last **1 hour**.
- **Mobile implementation:**
  - Whenever the mobile app initializes or resumes, it sends a heartbeat query to Supabase.
  - We can call a lightweight RPC or update the profile row:
    ```kotlin
    // Inside SplashActivity or HomeActivity
    suspend fun updateActivityHeartbeat() {
        val uid = client.auth.currentUserOrNull()?.id ?: return
        client.postgrest["app_users"].update(mapOf("last_activity_at" to "now()")) {
            filter { eq("id", uid) }
        }
    }
    ```
- **Admin Query:**
  ```sql
  -- Total Active Users (within 1 hour)
  SELECT COUNT(*) FROM public.app_users WHERE last_activity_at >= NOW() - INTERVAL '1 hour';
  
  -- Total Registered Users
  SELECT COUNT(*) FROM public.app_users;
  ```

---

## 🚪 Option 1: Secret Link & Gatekeeper Password Flow

To preserve security and hide the admin interface from unauthorized visitors:

### User Flow
1. **The Secret URL:** The admin enters a hidden route (e.g. `/secret-admin-entrance`).
2. **Passcode Screen:** A clean, minimal layout with a single password box is displayed.
3. **Validation & Redirection Logic:**
   - **Correct Passcode:** The passcode is checked. If it matches, the screen opens the actual Admin Login page (where the admin authenticates using their Supabase admin email and password).
   - **Incorrect Passcode:** If the code entered is wrong, the website immediately redirects the user to the public, regular user login page:
     👉 `https://presyohan.onrender.com/login`

### Passcode Placement & Recovery Configuration
To prevent anyone from inspecting your website source code and finding the passcode, we will store it securely outside the source files:
- **Local Development:** The passcode is placed in a local `.env` configuration file:
  ```env
  VITE_ADMIN_GATEKEEPER_PASSCODE="your_secret_passcode"
  ```
- **Production (Render Dashboard):**
  1. Log into your Render Dashboard (`dashboard.render.com`).
  2. Click on your **Presyohan Website** service.
  3. Go to the **Environment** tab on the left menu.
  4. Add or edit a new variable:
     - **Key:** `VITE_ADMIN_GATEKEEPER_PASSCODE`
     - **Value:** `your_new_secret_passcode`
  5. Click **Save Changes**. Render will reload your website automatically with the new passcode in 1-2 minutes.
- **Forgot Passcode:** Since the code is not hardcoded, if you ever forget it, you simply log into your Render dashboard, navigate to the Environment tab, and view or change the value.


---

## 🏷️ Standard Price Stores ("Presyohan" System Stores)

Instead of a default price sheet, the system supports **multiple standard stores** that you manage directly.

### Admin Experience
1. **Multiple Standard Stores:** You can create several standard/official stores (e.g., *Presyohan Supermarket*, *Presyohan Wet Market*, *Presyohan Electronics*).
2. **Default Store Selection:** In the Admin Panel, you will see a list of your standard stores with a toggle button: **"Set as Active Default"**. Clicking this toggles the `is_default_standard` flag in the database.
3. **Content Control:** You have full control over the categories, products, and standard pricing within each of these stores.

### Customer Experience (Mobile App)
- **First Launch Fallback:** When a regular user first registers, if they have not established any "Suking Tindahan" relationships yet, the mobile app queries the store where `is_default_standard = true` and shows its pricing. This ensures **they see no empty screen on first use**.
- **User Choice:** Since there are multiple standard stores available in the system (marked with `is_standard_store = true`), the mobile app provides an option for users to browse the catalog of standard stores and choose which ones to add to their personal dashboard.

---

## 📱 App Release & Force-Update Flow (Supabase Storage integration)

Instead of having a single static file where users do not know if an update is available, we will track releases dynamically:

### Supabase Storage Setup
- We will continue using the existing public storage bucket named **`presyohan.apk`**.
- When you upload a new version through the Admin Web Panel:
  1. The file is uploaded to the public bucket with a versioned filename (e.g. `releases/presyohan_v3.26.213.apk`).
  2. The Admin Web Panel records the newly created public URL (e.g. `https://sawwqpwnqilihlqiyurx.supabase.co/storage/v1/object/public/presyohan.apk/releases/presyohan_v3.26.213.apk`), along with the `version_code`, `version_name`, `whats_new` text, and `is_forced` boolean in the `public.app_releases` table.

### Mobile App Integration
1. On app launch, the mobile client reads the latest row in the `public.app_releases` table (ordered by `version_code` DESC).
2. It compares the database `version_code` with its own internal code (`BuildConfig.VERSION_CODE`).
3. If the database code is higher:
   - **Forced Update (`is_forced = true`):** Displays the "New Version" update overlay, blocking all interaction until the user clicks "Update Now" (which opens the version-specific download URL).
   - **Soft Update (`is_forced = false`):** Displays a dismissible banner or dialog explaining what's new and inviting them to update.

---

## 🖥️ Layout & Screen Previews (Wireframe Blueprint)

### 1. Main Dashboard (Analytics)
```
+-----------------------------------------------------------------------+
|  [Presyohan Logo]  | Active Users (1hr)    Total Registered Users    |
|                    | +-------------------+ +-------------------+      |
|  * Dashboard       | |       42          | |       820         |      |
|  * Users           | +-------------------+ +-------------------+      |
|  * Stores          |                                                   |
|  * Standard Stores | User Registrations Trend (Line Chart)            |
|  * App Releases    | +-----------------------------------------------+|
|  * Announcements   | |                                               ||
|                    | |                                               ||
|  [Admin Profile]   | +-----------------------------------------------+|
+-----------------------------------------------------------------------+
```

### 2. Standard Store Management (Default Toggle)
```
+-----------------------------------------------------------------------+
|  Manage Standard Stores                                               |
|  [ + Create New Standard Store ]                                      |
|                                                                       |
|  Store Name               Status              Action                  |
|  -------------------------------------------------------------------  |
|  Presyohan Grocery        [ DEFAULT ACTIVE ]  (Set as Default)        |
|  Presyohan Wet Market     [ Standard Store ]  [ Set as Default ]      |
|  Presyohan Tech Shop      [ Standard Store ]  [ Set as Default ]      |
+-----------------------------------------------------------------------+
```

### 3. App Releases View
```
+-----------------------------------------------------------------------+
|  Publish New Release                                                  |
|                                                                       |
|  [ Drag & Drop APK File here ]                                         |
|                                                                       |
|  Version Code: [ 3  ]           Version Name: [ v3.26.213 ]           |
|                                                                       |
|  What's New:                                                          |
|  +-----------------------------------------------------------------+  |
|  | - AI parsed uploading                                           |  |
|  | - UI improvements for public stores                             |  |
|  +-----------------------------------------------------------------+  |
|                                                                       |
|  Force Update: ( ) Yes  (o) No                      [ Publish Release ]|
+-----------------------------------------------------------------------+
```

---

## 🗓️ Next Steps & Roadmap

1. **Step 1: SQL Migration Setup**
   Apply the table creation scripts and RLS updates to the Supabase database.
2. **Step 2: Create Admin Auth Context & Layout**
   Build the hidden admin login path with passcode redirection, navigation sidebar, and basic responsive layout.
3. **Step 3: Develop Admin Features sequentially**
   - User Activity heartbeat and stats dashboard.
   - APK Uploader & Release history page.
   - Standard Prices store editor.
   - Announcements panel.
4. **Step 4: Update Mobile App**
   - Add the version verification interceptor on startup.
   - Integrate the heartbeat payload to update user activity.
   - Implement the Forced Update screen overlay.
