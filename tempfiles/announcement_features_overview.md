# Overview: Announcement Campaign Screens & Smart Filters

This document explains the available screen destinations you can target in your announcement campaigns, alongside the automatic filters that ensure popups are shown only to the right users at the right time.

---

## 1. Supported App Screen Destinations

When designing an announcement campaign, you can configure buttons to open any of the following 14 screen destinations:

1. **App Settings (Internet Price Search)**
   - Opens the settings menu where users can turn the internet grocery price search option on or off.
2. **Regular User Portal (Search Prices Dashboard)**
   - Takes the user directly to the customer search dashboard to look up items and compare local store prices.
3. **Store Portal (Select or Join Store list)**
   - Navigates the user to their store management dashboard where they can select a store to manage or join an existing store.
4. **Manage Members Screen (Staff Settings - Owner Only)**
   - Takes store owners to their staff management page to view, add, invite, or manage their store employees.
5. **Manage Store Details Screen (Owner Only)**
   - Takes store owners directly to the dashboard where they can update their store name, location details, or branches.
6. **User Account & Security Settings**
   - Opens the security settings menu where users can update their account passwords or security preferences.
7. **Edit User Profile Details (Name, Photo)**
   - Directs users to the profile page to update their display name or profile avatar picture.
8. **App Notification Inbox**
   - Opens the message drawer showing a history of all notifications and alerts sent to the user.
9. **Store Memberships & Loyalty Programs**
   - Opens the loyalty dashboard where users can view digital membership cards, accumulated points, or member tiers.
10. **Contact Us / Support Helpdesk**
    - Opens the direct support channel where users can submit feedback, ask questions, or report bugs.
11. **Manage Product Categories**
    - Takes store employees to the organization screen where they can group products into categories (like Beverages or Canned Goods).
12. **View/Share Store QR Code**
    - Opens the scannable store QR code sheet, allowing employees to share their digital store code with others.
13. **Manage Store Products & Prices**
    - Opens the active store's price list inventory, where staff can add new products or update retail prices.
14. **Bulk Add / Import Products**
    - Directs store staff to the bulk uploader page to import multiple products at once using spreadsheet files.

---

## 2. Smart Filtering System (Zero Display Bugs)

To prevent irrelevant popups and provide a smooth, professional user experience, the app runs three automatic checks before displaying any screen-targeted announcement:

* **Already Visited Protection:**
  - If an announcement tells the user to "Update your profile details," the app automatically hides this announcement if the user is *already* viewing their profile page. This prevents annoying and repetitive dialogs from blocking their view.
* **Store Context Verification:**
  - If a destination requires an active store to work (such as managing product prices, bulk importing files, managing categories, or displaying QR codes), the app checks if the user is currently working inside their store dashboard. If the user is on the customer portal or browsing general store lists, the app skips the announcement.
* **Owner Privilege Verification:**
  - Screen actions that manage store configurations or invite staff (like staff settings or store profiles) are restricted to the store's primary owner. The app automatically verifies if the current user is the registered "Owner" of that store before showing the dialog. If they are a standard employee or staff member, they won't see the popup.
