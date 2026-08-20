# DouyinJieXi · Aggregated Multi-Platform Downloader

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![minSdk](https://img.shields.io/badge/minSdk-24-brightgreen)](app/build.gradle.kts)
[![targetSdk](https://img.shields.io/badge/targetSdk-36-blue)](app/build.gradle.kts)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**English · [简体中文](README.md)**

> Watermark-free video & photo-set parsing and download for **20+ platforms**, built with **Kotlin + Jetpack Compose** — engineered around multi-source failover, data stitching, and over-the-air updates.

---

## ✨ Why it stands out

**1. Multi-source aggregation with intelligent failover — no single point of failure**
We never bet on a single parsing API. A pool of third-party sources is tried in order: high-concurrency **keyed channels first**, gracefully degrading to **keyless backup sources** on failure. If one source drops, the next takes over seamlessly — far more reliable than single-endpoint solutions. Each source can be toggled independently in Settings.

**2. Data stitching**
When source A returns media but only source B carries author/statistics, results are **merged automatically into the most complete payload**. A "make-it-whole" capability few tools have.

**3. 20+ platforms, one paste**
Douyin · Kuaishou · Xiaohongshu · Doubao · Bilibili · Xigua · WeChat Channels + more. Paste the **whole share text** — the link is extracted and short-links expanded automatically.

**4. Over-the-air updates — features ship instantly**
A self-hosted server compares versions via `update.json`; new features and fixes push **without waiting for a store release cycle**, with force-update support and CN/overseas dual download links.

**5. In-app music streaming**
QQ Music & NetEase Cloud Music results are **playable right in the app** — no download required.

**6. AI error analysis**
When parsing fails, one tap asks an LLM to explain the cause in plain, friendly language and suggest fixes.

**7. Stable release signature**
Every release is signed with the same key, so users get **smooth silent upgrades** — no "signature mismatch" prompts.

---

## ✅ Features

- 🎬 Multi-platform, watermark-free **video / photo-set / live-photo** parse & download
- 🔀 Multi-source aggregation · intelligent failover · **data stitching**
- 🧷 Paste full share-text, auto-extract link & expand short-links
- 🖼️ Batch photo-set download, per-frame live-photo preview & download
- 🎵 QQ Music / NetEase Cloud Music: **search · parse · stream · download** (multi-quality)
- 🤖 AI error analysis · built-in 200-line debug log viewer
- 🔄 Self-hosted update channel (force update · CN/overseas dual links)
- 📁 Custom per-type download folders, switchable background art

## 📦 Supported Platforms

Douyin · Kuaishou · Xiaohongshu · Doubao · Bilibili … extended to **20+ platforms** via aggregate sources.

## 🛠️ Tech Stack

Kotlin · Jetpack Compose · Material 3 · OkHttp · Gson · Media3 (ExoPlayer) · Coil · DataStore
AGP 9.2 + Gradle 9.4.1 · minSdk 24 · targetSdk 36

## 🔨 Build

Requires Android Studio + JDK 17+.

```bash
# Release APK (uses keystore/ release signing automatically if present, else falls back to debug)
./gradlew assembleRelease

# Debug APK
./gradlew assembleDebug
```

Artifacts land in `app/build/outputs/apk/<variant>/`.

## 📱 Quick Start

1. Paste a share link or the full share-text on the "Video" tab → parse
2. Video → "Download video"; photo-set → "Download all images"; live-photo → preview & download per-frame
3. "Music" tab → search / paste a link → **stream or download**
4. "Settings" → toggle parse sources, change download folder, view logs, run AI analysis

## 🐛 Reporting Issues

Bugs and suggestions via GitHub Issues. For fast triage, please:

- **Search first** — the issue may already be reported;
- When filing a **bug**, use the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md) and include:
  - Device model / Android version, App version
  - The **share link** that failed (PII can be masked)
  - **Reproduction steps**, expected vs. actual
  - **Logs**: App → Settings → Developer options → View logs → Copy all
- Feature ideas → [feature request template](.github/ISSUE_TEMPLATE/feature_request.md);
- **Never** paste API keys or secrets into an Issue.

## ⚖️ Disclaimer

For learning and technical research only. The app stores or caches no third-party content; parsing relies on third-party public APIs whose availability is outside our control. Please respect platform copyrights and do not use this for commercial or unlawful purposes.

## 📄 License

Released under the [MIT](LICENSE) license.