# 聚合解析 · DouyinJieXi

[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![minSdk](https://img.shields.io/badge/minSdk-24-brightgreen)](app/build.gradle.kts)
[![targetSdk](https://img.shields.io/badge/targetSdk-36-blue)](app/build.gradle.kts)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**🌐 [English Readme](README.en.md) · 简体中文**

> 多平台无水印解析下载 · 多源智能容灾 · 随时在线更新

一款基于 **Kotlin + Jetpack Compose** 的多平台**视频/图集无水印解析与下载**工具：粘贴分享链接即可解析下载，支持抖音、快手、小红书、豆包、B 站等 20+ 平台；同时内置 QQ 音乐 / 网易云搜索、在线试听与下载。

---

## 🚀 我们与众不同的特色

**1. 多源聚合 + 智能容灾，绝不单点依赖**
不押宝在单一解析接口上。内置多套第三方解析源，**带 Key 高并发通道优先、失败自动降级到免 Key 兜底源**，一个挂了自动切下一个——稳定性远超"单接口"方案。可在设置页自由开关每个解析源。

**2. 数据缝合（Data Stitching）**
接口 A 只出视频、接口 B 只出作者/统计时，**自动把两者合并成最完整的结果**并展示，这是少数工具才具备的"拼全"能力。

**3. 跨 20+ 平台，口令直达**
抖音 / 快手 / 小红书 / 豆包 / B 站 / 西瓜 / 视频号等 20+ 平台；直接粘贴**整段带文案的分享口令**，自动提取链接即可解析。

**4. 随时更新机制，功能即时推送**
自建服务器通过 `update.json` 做版本比对，新功能与修复**无需等应用商店周期**即可推送；支持强制更新与国内/海外双下载链路智能切换。

**5. 音乐在线试听**
QQ 音乐、网易云解析结果**即点即播**，不用先下载。

**6. AI 错误分析**
解析失败时一键调用大模型，用通俗语言解释原因、给解决建议——报错不慌。

**7. 固定发布签名，升级平滑**
所有版本使用同一把发布密钥签名，用户**覆盖升级不弹"签名不一致"**。

---

## ✨ 功能特性

- ✅ 多平台无水印视频 / 图集 / 实况解析下载
- ✅ 多源聚合 · 智能容灾 Fallback · 数据缝合
- ✅ 口令文本自动提取链接，短链自动展开
- ✅ 图集批量下载、实况逐帧预览下载
- ✅ QQ / 网易云音乐 搜索 · 解析 · 在线试听 · 下载（多音质）
- ✅ AI 错误分析 · 内置 200 条运行日志
- ✅ 自建更新通道（强制更新 / 双链路下载）
- ✅ 下载路径自定义、背景图切换

## 📦 支持平台

抖音 / 快手 / 小红书 / 豆包 / B 站 …（经聚合源扩展至 20+ 平台）

## 🛠️ 技术栈

Kotlin · Jetpack Compose · Material 3 · OkHttp · Gson · Media3(ExoPlayer) · Coil · DataStore
AGP 9.2 + Gradle 9.4.1，minSdk 24，targetSdk 36

## 🔨 构建

环境：Android Studio + JDK 17+，Sync 后直接构建：

```bash
# 正式包（配置 keystore/release 签名后自动使用；否则回退 debug 签名）
./gradlew assembleRelease

# 调试包
./gradlew assembleDebug
```

产物位于 `app/build/outputs/apk/<variant>/`。

## 📱 快速上手

1. 「视频/图文」页粘贴分享链接或整段口令 → 解析
2. 视频「下载视频」、图集「下载全部图片」、实况逐张预览下载
3. 「音乐下载」搜歌 / 贴链接 → 直接在线试听或下载
4. 「设置」开关解析接口、改下载目录、看日志、用 AI 分析

## 🐛 问题反馈规范

欢迎使用 GitHub Issues 反馈问题或建议。为高效排查，请：

- **先搜索**是否已有人报告过同类问题；
- 报告 Bug 请使用 **[问题模板](.github/ISSUE_TEMPLATE/bug_report.md)**，尽量附上：
  - 机型 / Android 系统版本、App 版本号
  - 造成问题的**分享链接**（可部分脱敏）
  - **操作复现步骤**，以及**期望 vs 实际**结果
  - **运行日志**：App 内「设置 → 开发者选项 → 查看日志 → 复制全部日志」
- 特性建议走 **[功能建议模板](.github/ISSUE_TEMPLATE/feature_request.md)**；
- **请勿**在 Issue 中粘贴任何第三方接口 Key / 密钥等敏感信息。

## ⚖️ 免责声明

本项目仅供个人学习与技术交流使用。App 本身不存储、不缓存任何第三方内容，解析能力依赖第三方公开接口，其可用性与稳定性不受本项目控制。请尊重各平台内容版权，勿将解析结果用于商业用途或非法传播。

## 📄 License

本项目基于 [MIT](LICENSE) 协议开源。