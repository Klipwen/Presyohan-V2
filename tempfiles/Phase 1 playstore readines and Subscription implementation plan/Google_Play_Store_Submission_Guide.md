# 📱 Google Play Console Official Store Listing & Submission Guide

**App Name:** Presyohan  
**Package Name:** `com.presyohan.app`  
**Developer Name:** SpennyWise  
**Developer Account ID:** `8486090963981976962`  
**Target Category:** Business / Shopping / Productivity  

---

## 📝 1. Store Listing Metadata

### A. App Name & Tagline
- **App Name (Max 30 chars):** `Presyohan`
- **Alternative (Keyword Rich):** `Presyohan - POS & Price Check`

### B. Short Description (Max 80 chars)
```text
Smart Sari-Sari Store POS, Barcode Scanner, AI Digitizer & Price Transparency.
```

### C. Full Description (Max 4,000 chars)
```text
Presyohan (Atong Presyohan?) is the all-in-one smart POS, inventory manager, and retail price transparency app built specifically for Filipino sari-sari stores, grocery merchants, and savvy shoppers.

Whether you run a community store or want to find the best prices in your neighborhood, Presyohan makes managing inventory and checking prices fast, seamless, and accurate.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✨ FOR STORE OWNERS & MERCHANTS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📦 Fast Barcode Scanning: Scan product barcodes instantly using your phone's camera. Look up items and check prices in milliseconds without expensive external hardware.
🤖 AI Receipt & Price List Scanner: Digitize supplier receipts, handwritten price sheets, or wholesale invoices in seconds using built-in Google Gemini AI parsing.
📊 Multi-Store & Staff Management: Manage multiple store branches, assign Cashier or Manager roles, and track store inventory from a single account.
📑 Excel & PDF Catalog Export: Export clean, professional price lists and inventory reports directly to your device or share with customers.
🏷️ Suki Management & Customer Requests: Connect directly with loyal customers (Suki), approve price check requests, and increase neighborhood foot traffic.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✨ FOR CUSTOMERS & SHOPPERS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔍 Direct Price Search: Search everyday grocery items (Rice, Canned Goods, Instant Noodles, Beverages, Biscuits) to view updated prices in nearby partnered stores.
🤝 Suki Connection: Request partnership with your favorite neighborhood stores to stay updated with real-time price changes and exclusive stock updates.
💡 Community Transparency: Fair, clear, and up-to-date prices right at your fingertips.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔒 DATA PRIVACY & SECURITY:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Your data is securely stored with enterprise-grade encryption (TLS 1.3 & AES-256) powered by Supabase and hosted in secure Singapore cloud facilities. You have 100% control over your account and data, with instant in-app and web account deletion available anytime.

Download Presyohan today and bring modern, smart inventory power to your sari-sari store!
```

---

## 🛡️ 2. Google Play "Data Safety" Questionnaire Answers

When completing the **Data Safety** form in Google Play Console, use these exact answers:

| Question / Section | Exact Answer | Explanation |
| :--- | :---: | :--- |
| **Does your app collect or share any user data?** | **Yes** | Standard for any app with user accounts. |
| **Is all user data encrypted in transit?** | **Yes** | All network traffic uses HTTPS / TLS 1.3. |
| **Do you provide a way for users to request data deletion?** | **Yes** | In-app deletion + Web URL (`https://presyohan.onrender.com/delete-account`). |

### Data Types Collected:
1. **Personal Info & Contact Info:**
   - **Name:** Collected for user identity / store owner name (Optional/Required for account). *Purpose: App functionality, Account management.*
   - **Email address:** Collected for authentication and login. *Purpose: App functionality, Account management.*
   - **User IDs:** Account ID (`auth.users.id`). *Purpose: App functionality.*
2. **Photos & Videos:**
   - **Photos:** Captured via camera solely when using the AI Invoice Parser. *Purpose: App functionality (Ephemeral OCR processing).* Not shared, not stored permanently for other uses.
3. **App Info & Performance:**
   - **Crash logs & diagnostics:** Anonymous crash reports. *Purpose: Analytics, Developer communications.*

---

## 📋 3. Google Play "App Content" Policy Checklist

1. **Privacy Policy URL:** `https://presyohan.onrender.com/privacy`
2. **Account Deletion URL:** `https://presyohan.onrender.com/delete-account`
3. **Ads:** Select **"No, my app does not contain ads"**.
4. **App Access / Test Credentials:**
   - Provide a demo merchant login for Google Play Reviewers:
   - **Email:** `demo.merchant@presyohan.com`
   - **Password:** `DemoPass123!` (or create a dedicated test account in Supabase)
5. **Target Audience & Content:**
   - Select **18 and older** (Avoids strict Google Play "Designed for Families" child policies).
6. **Government Apps:**
   - Select **"No, this app is not developed by or on behalf of a government entity"**.
7. **Financial Features:**
   - Select **"My app doesn't provide any financial features"** (or select "In-app promotions / store catalog" since transactions are promotional ₱0 claim).

---

## 👥 4. Closed Testing Track (14-Day Mandatory Testing)

For personal Google Play developer accounts created after November 2023, Google requires:
- **At least 14 testers** who opt-in to your Closed Testing track.
- The testers must remain opted-in for **14 consecutive days**.

### Steps to Launch Closed Beta:
1. Navigate to **Release &rarr; Testing &rarr; Closed testing** in Google Play Console.
2. Click **Create track** or select the default closed track.
3. Under **Testers**, create an email list (e.g. `Presyohan Beta Testers`) and enter 14+ Gmail addresses (friends, family, team).
4. Click **Create new release**, upload `app-release.aab`.
5. Release Name: `4.13.11 (92) - Production Closed Beta`.
6. Save and submit the release for Google review.
7. Once approved by Google (typically 24–48 hours), share the **Opt-in Web Link** / **Android Link** with your 14 testers to download and install the app!
