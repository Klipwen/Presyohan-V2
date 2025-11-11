# Presyohan V2

A multi-platform store management system for pricing, categories, items, members, and notifications. This repository contains:

- Web Frontend (Vite + React)
- Web Backend (Java/Maven project)
- Android Mobile App (Kotlin)
- Supabase SQL migrations and RPCs

The system provides role-based access (Owner, Manager, Staff) with secure, schema-aware RPCs and Row Level Security (RLS) policies.

## Key Features

- Store management: create stores, view your stores, leave/delete with safeguards
- Member management: invite, list, update roles, remove, with confirmation flows
- Category management: create, rename/merge, safe deletion (auto-removes when empty)
- Product/item management: full CRUD with category mapping and auto-cleanup
- Notification system: invitations, join/leave events, role updates
- Consistent header UI with hamburger menu and notifications across pages

## Architecture Overview

- `Presyohan Website/frontend`: Vite + React single-page app
  - Uses Supabase JS client for authentication and RPC calls
  - Pages: `StorePage`, `StoreSettingsPage`, `ManageItemsPage`, `StoresPage`, etc.
  - Components: headers, category sidebar, products grid, item modals

- `Presyohan Website/backend`: Java/Maven project (service layer / future APIs)
  - Maven wrapper included (`mvnw`, `mvnw.cmd`)
  - `pom.xml` present for managing dependencies

- `Presyohan Mobile`: Android app written in Kotlin
  - Uses same data model and roles for consistent UX
  - Screens for editing items, categories, and quick store operations

- `supabase/migrations`: SQL files defining tables, policies, functions (RPCs)
  - Enforces security and business rules directly in the database

## Supabase Schema & RPCs

Schema and policies are managed via the SQL migrations under `supabase/migrations`. Highlights:

- Identity handling is schema-aware and supports both `auth.uid()` and legacy `app_users.auth_uid` patterns
- RLS policies for secure access to stores, members, categories, and products
- Core RPCs:
  - `get_user_stores`: returns stores accessible to the current user (role, owner name)
  - `get_store_members`: lists store members with roles/emails for authorized users
  - `create_store`: creates a store and establishes ownership
  - `remove_store_member` / `leave_store`: removes or allows departing with role validations
  - `get_user_categories`: lists accessible categories in a store
  - `get_store_products`: lists products with category and filter/search support
  - `add_category`, `rename_or_merge_category`, `delete_category_safe`: category utilities
  - `add_product`: upserts a product into a store with validation
  - `update_store_member_role`: securely updates roles (employee, manager, owner)
  - Notification helpers for invitations and membership changes

See filenames in `supabase/migrations` for exact definitions and evolution.

## Folder Structure

```
Presyohan-V2/
├── Presyohan Mobile/
│   └── app/ (Android Kotlin app)
├── Presyohan Website/
│   ├── backend/ (Java/Maven backend project)
│   └── frontend/ (Vite + React web app)
├── backend/ (Node scripts for DB setup/migrations)
├── supabase/migrations/ (SQL schema & RPCs)
└── README.md (this file)
```

## Prerequisites

- Node.js 18+
- npm (or pnpm)
- Java 17+ (for the Maven project)
- Android Studio (for the mobile app)
- Supabase project (URL + anon key)

## Setup

1. Clone the repository.
2. Configure Supabase environment variables for the web app:
   - Create `Presyohan Website/frontend/.env`
   - Set:
     - `VITE_SUPABASE_URL=<your_supabase_url>`
     - `VITE_SUPABASE_ANON_KEY=<your_supabase_anon_key>`
3. Apply Supabase migrations:
   - Option A: Use the Supabase SQL editor and run files under `supabase/migrations` in timestamp order.
   - Option B: Use the Node helper scripts under `backend/scripts` (e.g., `apply_supabase_migrations.js`).
4. Install frontend dependencies and run the web app:
   - `cd "Presyohan Website/frontend"`
   - `npm install`
   - `npm run dev`
5. (Optional) Run the website backend:
   - `cd "Presyohan Website/backend"`
   - `./mvnw spring-boot:run`
6. (Optional) Open the Android app in Android Studio and configure API keys.

## Usage Walkthrough

- Sign in (Supabase Auth) and land on your Stores view
- Open a store (`StorePage`):
  - Search, filter by category, and manage items
  - Use the floating action button or press `Ctrl+A`/`Cmd+A` to open “Add Item”
  - Adding an item can create a category if it doesn’t exist
  - Deleting the last item in a category auto-deletes that category

- Manage store settings (`StoreSettingsPage`):
  - Tabs for store, members, and items
  - Member modal (“Manage Member”) with role updates via `update_store_member_role`
  - Removal uses confirmation modal with secure server-side validation
  - Header uses `StoreHeader` with consistent hamburger + notification UI

- Manage items (`ManageItemsPage`):
  - Category sidebar, mobile selector, grouped items
  - “Add Item” modal supports creating categories inline
  - Item edits check and auto-delete empty previous categories
  - Shortcut `Ctrl+A`/`Cmd+A` opens the “Add Item” modal

## Security & Roles

- Roles: `OWNER`, `MANAGER`, `EMPLOYEE/STAFF` (view-only)
- Owners can assign owner role and manage sensitive operations
- RLS policies restrict data access by membership and role
- RPCs enforce privilege checks and invariant protections (e.g., cannot remove/leave sole owner)

## Important RPC Behaviors

- `get_user_stores`: includes store role and owner name for UX clarity
- `get_store_members`: schema-aware identity lookup and safe listing
- `remove_store_member`: forbids deleting the sole owner and validates actor role
- `leave_store`: prevents sole owner from leaving without transferring ownership
- `update_store_member_role`: only owners can grant owner role; updates with notifications
- Category operations (`rename_or_merge_category`, `delete_category_safe`) maintain data consistency

## Development Notes

- Frontend uses minimal alerts, preferring confirmation modals and toasts
- State refresh often sets `selectedCategory` back to `PRICELIST` then reloads to ensure latest data
- Header components are unified (`StoreHeader`) to match UI behavior across pages
- Keyboard shortcut handlers ignore inputs/textareas to avoid overriding Select All

## Scripts (Node)

Located in `backend/scripts`:

- `apply_supabase_migrations.js`: sequentially applies SQL migrations
- `create_test_user.js`: helper to create a test user record
- `ensure_tables.js`: initializes required tables for local/dev

## Contributing

- Create feature branches and keep changes scoped and focused
- Follow existing code style and component patterns
- For database changes, add new timestamped SQL files in `supabase/migrations`
- Avoid surface-only fixes when root causes are apparent

## Troubleshooting

- Authentication issues: verify `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY`
- RPC errors: check the latest SQL migrations and ensure they’re applied
- UI inconsistencies: confirm pages import `StoreHeader` and use the standardized layout
- Empty categories lingering: ensure the latest patches are applied on both `StorePage` and `ManageItemsPage` for auto-deletion logic

## Roadmap (Examples)

- Backend REST endpoints for selected operations (complementing RPCs)
- Improved notification delivery and in-app feed
- More granular staff roles and per-category permissions
- Unit and integration test coverage for critical flows

---

Presyohan V2 — secure, fast, and consistent store management across web and mobile.