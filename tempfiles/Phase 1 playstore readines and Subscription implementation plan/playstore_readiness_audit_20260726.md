# 📱 Presyohan Mobile — Google Play Store Readiness Audit

**Document Date:** July 26, 2026  
**File Location:** `tempfiles/playstore_readiness_audit_20260726.md`  
**Description:** This document provides a deep-dive readiness analysis of the Presyohan Mobile Android application before publishing to the Google Play Store. It outlines critical technical bugs/omissions, mandatory Google Play Store policy requirements, store listing assets, and pre-release verification steps.

---

## 🟢 1. Executive Summary & Readiness Status

* **Status:** **Nearly Ready (Requires 5 Critical Adjustments)**
* **Project SDK Compliance:** `compileSdk = 35`, `targetSdk = 35`, `minSdk = 24`. Compliant with Google Play target API requirements.
* **Architecture:** Kotlin, Jetpack Compose + View System, Supabase Auth/PostgreSQL, Gemini API, FastExcel, Ktor.
* **Primary Blockers:** Missing `CAMERA` manifest permission, missing in-app Account Deletion feature (Google Play Mandate), missing signing configuration & minification setup for production release.

---

## 🛑 2. Critical Technical & Code Lacks

### 🚨 A. Missing Manifest Permission (`CAMERA`)
* **File:** `Presyohan Mobile/app/src/main/AndroidManifest.xml`
* **Issue:** [AddMultipleItemsActivity.kt](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/AddMultipleItemsActivity.kt#L1244) requests runtime permission for `android.Manifest.permission.CAMERA` to capture item photos.
* **Problem:** `android.permission.CAMERA` is **NOT declared** in `AndroidManifest.xml`.
* **Impact:** On Android 6.0+, requesting a permission not listed in the Manifest causes camera launches to fail silently or crash with a `SecurityException`.
* **Fix Required:** Add `<uses-permission android:name="android.permission.CAMERA" />` to `AndroidManifest.xml`.

---

### 🚨 B. In-App Account Deletion Requirement (Google Play Mandate)
* **Policy Requirement:** Google Play strictly enforces that any app allowing user account creation (`SignupActivity`) MUST provide an in-app method to delete the account and user data, as well as an external web URL for account deletion requests.
* **Current State:** [AccountSecurityActivity.kt](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/src/main/java/com/presyohan/app/AccountSecurityActivity.kt) only supports updating or creating passwords. There is currently no "Delete Account" button or API handler.
* **Fix Required:** 
  1. Add a **"Delete Account"** option in `SettingsActivity` or `AccountSecurityActivity` that purges user data from Supabase Auth & PostgreSQL database.
  2. Provide a web landing page URL explaining how users can request account deletion (required for the Play Console Data Safety form).

---

### ⚠️ C. Release Build & Signing Configuration (`build.gradle.kts`)
* **Minification / Obfuscation:** In [build.gradle.kts](file:///c:/Users/Gee%20Caliph/Desktop/Programming/System/Presyohan/Presyohan-V2/Presyohan%20Mobile/app/build.gradle.kts#L60), `isMinifyEnabled = false`.
  * *Recommendation:* Set `isMinifyEnabled = true` and `isShrinkResources = true` for the `release` build type to reduce APK/AAB size and obfuscate code.
* **Keystore Signing Config:** Ensure a keystore file (`.jks` / `.keystore`) is generated and linked in Gradle `signingConfigs` or configured in Play App Signing.

---

### ⚠️ D. Build Environment Secrets (`GEMINI_API_KEY`)
* **Issue:** `build.gradle.kts` reads `GEMINI_API_KEY` from `local.properties`.
* **Risk:** `local.properties` is local to the development machine. If building the production bundle (`.aab`) on another environment without configuring `local.properties` or environment variables, `GEMINI_API_KEY` will default to `""` (empty string) in the release build.

---

## 📜 3. Google Play Console Policy & Data Safety Requirements

| Requirement | Description | Status / Action Needed |
| :--- | :--- | :--- |
| **Privacy Policy URL** | Public HTTPS URL detailing data collection (emails, photos, price lists, device logs). | ❌ Required (Host URL) |
| **Data Safety Form** | Declare data types collected: User Email, Store Catalogs, Uploaded Images, Device IDs. | ❌ Required (Fill in Console) |
| **Target SDK Compliance** | `targetSdk = 35` (Android 15) configured in Gradle. | ✅ Compliant |
| **64-bit Architecture** | All libraries support 64-bit architectures (ARM64, x86_64). | ✅ Compliant |
| **Android App Bundle (.aab)** | Standard format for submission (`./gradlew bundleRelease`). | ⏳ Ready to build |
| **Content Rating (IARC)** | Answer questionnaire regarding app content, interactive features, and user communication. | ❌ Required (Fill in Console) |

> ℹ️ **Google 14-Tester Rule Notice:**  
> If your Google Play Developer Account was created after **November 2023** as a **Personal Account**, Google requires running a **Closed Test with at least 14 opt-in testers for 14 consecutive days** before production publishing access is granted.

---

## 🎨 4. Store Listing Graphic Assets Checklist

1. **App Title:** Up to 30 characters (`Presyohan`).
2. **Short Description:** Up to 80 characters (`Smart price tracking, store management, and price list scanner.`).
3. **Full Description:** Up to 4,000 characters detailing store features, QR code generation/scanning, member management, and AI price list parsing.
4. **App Icon:** 512 x 512 px (32-bit PNG with alpha channel).
5. **Feature Graphic:** 1024 x 500 px (JPG or 24-bit PNG).
6. **Phone Screenshots:** 4–8 screenshots (Minimum 2 required, 16:9 or 9:16 aspect ratio).
7. **Adaptive Icon:** Ensure `@drawable/icon_presyohan_launcher` supports modern launcher masks (`mipmap-anydpi-v26`).

---

## 📋 5. Final Action Checklist

```
[ ] 1. Add android.permission.CAMERA to AndroidManifest.xml
[ ] 2. Implement Account Deletion feature & API endpoint (Google Play Policy)
[ ] 3. Create Release Keystore (.jks) for signing .aab bundle
[ ] 4. Set isMinifyEnabled = true & isShrinkResources = true in build.gradle.kts
[ ] 5. Publish Privacy Policy webpage & prepare account deletion URL
[ ] 6. Prepare 512x512 icon, 1024x500 Feature Graphic, and high-res screenshots
[ ] 7. Ensure GEMINI_API_KEY & SUPABASE keys are provided during release build
[ ] 8. Run ./gradlew bundleRelease and upload app-release.aab to Play Console
```
