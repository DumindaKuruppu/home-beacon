# Home Beacon

Home Beacon is a consent-based native Android family location tool built with Kotlin and plain Android Views. When a configured trusted phone number sends a specific SMS command, the app automatically replies via SMS with the device's current GPS coordinates and a Google Maps link.

Home Beacon works entirely offline without mobile data or Wi-Fi (GPS and normal SMS only).

---

## Deliverables & Features

- **Navigation Drawer (☰)**: Slide-out drawer menu providing quick access to App System Settings, Battery Optimization toggles, and Theme preferences.
- **Dark Mode Support**: Seamless support for Light Mode, Dark Mode, and System Default theme modes.
- **Multiple Trusted Numbers**: Add and manage multiple trusted family phone numbers. Trigger SMS received from any listed trusted number will be processed.
- **Trigger Command**: `WHERE <secret code>` (case-insensitive, trimmed).
- **Format of SMS Reply**:
  ```text
  Lat 6.927079, Lon 79.861244 (+/-12 m, just now)
  https://maps.google.com/?q=6.927079,79.861244
  ```
  *(If fallback to last known location is used, says "N min ago" instead of "just now". If no GPS fix is available, replies "No GPS fix yet. Please try again in a minute.")*
- **Full Transparency**: Every single SMS reply posts a visible notification on the device ("Location sent", "Replied to <number> with your current location").
- **Zero Background Tracking**: No foreground service, no polling, no background location tracking, no network calls, and no analytics. The app stays idle until a trigger SMS is received.

---

## Installation & Sideloading Steps

1. **Build the Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
2. **Locate the generated APK**:
   ```text
   app/build/outputs/apk/debug/app-debug.apk
   ```
3. **Install onto Target Device via ADB or File Transfer**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   Or transfer `app-debug.apk` directly to the device and open it via the File Manager app to sideload.

---

## Xiaomi Redmi / HyperOS Setup Checklist

Xiaomi / HyperOS / MIUI devices enforce strict background broadcast restrictions and security policies. Follow this checklist on the target device:

1. **Enable Autostart**:
   - Go to **Settings** -> **Apps** -> **Permissions** -> **Autostart** (or open the **Security** app -> **Permissions** -> **Autostart**).
   - Locate **Home Beacon** and toggle **Autostart** to **ON**.
2. **Set Battery Saver to No Restrictions**:
   - Go to **Settings** -> **Apps** -> **Manage apps** -> **Home Beacon** -> **Battery saver**.
   - Select **No restrictions**.
3. **Lock the App in Recents**:
   - Open the **Recents** (Task Switcher) screen.
   - Long-press the **Home Beacon** app preview card and tap the **Lock icon** to prevent HyperOS task killer from closing it.
4. **Grant "Other Permissions" (SMS & Background Popups)**:
   - Go to **Settings** -> **Apps** -> **Manage apps** -> **Home Beacon** -> **Other permissions**.
   - Ensure **Send SMS**, **Service SMS**, and **Display pop-up windows while running in the background** are set to **Always allow**.
5. **Allow Restricted Settings (Android 13+ / 14+)**:
   - If sideloaded permissions (like SMS or Location) appear greyed out, go to **Settings** -> **Apps** -> **Manage apps** -> **Home Beacon**.
   - Tap the **3-dots menu (⋮)** in the top-right corner.
   - Tap **Allow restricted settings** and confirm with device PIN/Fingerprint.
6. **Default SMS SIM Configuration**:
   - Go to **Settings** -> **SIM cards & mobile networks**.
   - Ensure the preferred active SIM card is assigned for SMS transactions.

---

## Manual Test Plan

### Prerequisites
- Target device running Home Beacon with:
  - At least one Trusted Phone Number configured (e.g. `+94771234567` or `0771234567`).
  - Secret Code configured (e.g. `1234`).
  - Switch set to **Location replies ON**.
  - All permissions granted via "Save & set up permissions" button (SMS, Fine Location, "Allow all the time" Background Location, Post Notifications, Battery Unrestricted).

---

### Test Case 1: Valid Trigger SMS (Device Locked, Data & Wi-Fi OFF)
1. Turn **OFF** Mobile Data and Wi-Fi on the target device (leave GPS / Location enabled).
2. Lock the target device screen.
3. From any configured trusted phone number, send the SMS:
   ```text
   WHERE 1234
   ```
4. **Expected Result**:
   - Target device receives the SMS, requests a fresh high-accuracy GPS fix (or falls back within 8s to last location if GPS signal is weak).
   - Replies via SMS with:
     ```text
     Lat <latitude>, Lon <longitude> (+/-<accuracy> m, just now)
     https://maps.google.com/?q=<latitude>,<longitude>
     ```
   - Target device displays a visible status bar notification:
     - Title: `Location sent`
     - Content: `Replied to +94771234567 with your current location`

---

### Test Case 2: Untrusted Sender Number
1. From any phone number **not included** in the saved trusted numbers list, send:
   ```text
   WHERE 1234
   ```
2. **Expected Result**:
   - The message is ignored. No SMS reply is sent, no location is fetched, and no notification is posted.

---

### Test Case 3: Incorrect Secret Code
1. From a trusted phone number, send:
   ```text
   WHERE wrongcode
   ```
2. **Expected Result**:
   - The message is ignored. No SMS reply is sent.

---

### Test Case 4: Location Replies Disabled
1. In Home Beacon, toggle **Location replies ON** to **OFF**.
2. From a trusted phone number, send:
   ```text
   WHERE 1234
   ```
3. **Expected Result**:
   - The message is ignored because replies are turned off.
