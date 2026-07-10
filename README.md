<p align="center">
  <img src="https://github.com/user-attachments/assets/f5a98486-8640-44c2-b671-26d06dbd6cbf" alt="Opal Hero Banner" width="800">
</p>

<p align="center">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Android/android1.svg" alt="Android" height="30">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Java/java1.svg" alt="Java" height="30">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/SQLite/sqlite1.svg" alt="SQLite" height="30">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/AndroidStudio/androidstudio1.svg" alt="Android Studio" height="30">
</p>

<div align="center">
  <h3>💎 Opal ~ One-Tap Link Saver</h3>
  <strong>Stop losing your important links in messy notes apps.</strong><br>
  Opal is a lightning-fast, zero-friction link saver for Android. Share anything, a tweet, a reel, a product, or an article—and Opal handles the rest instantly in the background. No extra taps, no interruptions.
</div>

## Supported Application Ecosystem

Opal provides broad compatibility across major internet protocols and native applications.

<p align="left">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Chrome/chrome1.svg" alt="Chrome" height="28">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Reddit/reddit1.svg" alt="Reddit" height="28">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Instagram/instagram1.svg" alt="Instagram" height="28">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Twitter/twitter1.svg" alt="Twitter" height="28">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Spotify/spotify1.svg" alt="Spotify" height="28">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/WhatsApp/whatsapp1.svg" alt="WhatsApp" height="28">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/LinkedIn/linkedin1.svg" alt="LinkedIn" height="28">
  <img src="https://ziadoua.github.io/m3-Markdown-Badges/badges/Telegram/telegram2.svg" alt="Telegram" height="28">
</p>

## 🚀 Key Features

* **Zero-Click Saving:** Click "Share" from any app, select Opal, and you're done. Your link is safely stored without ever leaving your current app.
* **Universal Support:** Works seamlessly with Chrome, Instagram Reels, YouTube Shorts, X (Twitter), Reddit, Amazon, Flipkart, and more.
* **Dynamic Price Protection:** Sharing items from e-commerce apps like Flipkart or Amazon to Opal lets you clear them from your active cart, helping you avoid dynamic price hikes driven by cart tracking.
* **🚀 Hash & Tracker Cleaner:** Built-in advanced query parameter stripper. Automatically cleans tracking junk (like `?si=...`, `?utm_source=...`, or `&s=...`) from your URLs to keep your data private and your links clean.

## Detailed Feature Matrix

### One-Tap Background Processing
Opal hooks straight into the `Intent.ACTION_SEND` pipeline inside Android. The absolute second you tap the Opal icon in your native device share panel, the system captures the data payload text stream, updates the internal SQLite index on a background worker thread, and terminates successfully without launching a disruptive UI transition context.

> [!IMPORTANT]
> **Performance Note:** Because Opal works purely on local background threads via the Android Share Intent pipeline, it consumes 0% idle CPU and does not run a persistent battery-draining foreground service.

### Dynamic Pricing Shielding
E-commerce tracking algorithms on platforms like Amazon and Flipkart rely on cookie presence tags and persistent intent indicators (like tracking items sitting live in your shared cart space) to dynamically raise unit values on high-demand merchandise. Sharing items out of your browser stack straight into Opal breaks this behavioral tracking pattern completely.

### The Hash-Cleaning Architecture
Modern links contain telemetry layers attached right to the URL structure (`?si=`, `?utm_`, `&s=`). Opal evaluates every incoming token, executing local sanitization routines to output high-fidelity canonical links.

**Incoming Stream :** https://youtu.be/vdbP_3o73qI?si=CwvXi0t_Km47W6K3

**Sanitized Index :** https://youtu.be/vdbP_3o73qI

> [!TIP]
> **Smart Shopper Hack:** Add items to your wishlist, hit share to Opal, and then clear your active app cart. Opal stores the sterile item URL out of sight of the platform's active tracker scripts, locking down baseline pricing until you are actually ready to purchase.

## Visual Application Flow

<p align="center">
  <img src="https://github.com/user-attachments/assets/82ec36f4-715c-4bbd-906f-070485e9910d" alt="Native Android Share Sheet Trigger" width="260" style="margin-right: 15px; border-radius: 8px;">
  <img src="https://github.com/user-attachments/assets/d1feb6ab-81f7-47f2-a8b4-baad66373568" alt="Clean Sandbox List View Dashboard" width="260" style="margin-right: 15px; border-radius: 8px;">
  <img src="https://github.com/user-attachments/assets/ff3a6928-7b4f-475d-9372-1340d6f94177" alt="Sanitization Filter Configuration Rules" width="260" style="border-radius: 8px;">
</p>

> [!IMPORTANT]
> **Privacy Architecture Verification:** All regex cleansing logic and persistent indices are contained exclusively on local flash allocations. Opal requests no telemetry system privileges or remote network handshake capacity.

## Deployment & Native Compilation

### System Requirements
* Android OS SDK Environment: Minimum Target Level 26 (Android 8.0 Oreo)
* Development Tools: Android Studio Jellyfish+ / Gradle Daemon Build Toolchain
* Native Compilation Language: Java Virtual Machine JDK 17 Reference Binary
