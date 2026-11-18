<div align="center">

# 🍅 洋柿子ToDo

### 把每一件大事，拆成一个又一个 25 分钟

一款 Android 原生专注管理应用。番茄钟 × 任务清单 × 沉浸壁纸 × 环境白噪音，
用克制的界面，守住你完整的心流。

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF.svg?logo=kotlin&logoColor=white)](https://developer.android.com/compose)
[![CI](https://github.com/zhikanyeye/todowork/actions/workflows/build.yml/badge.svg)](https://github.com/zhikanyeye/todowork/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/zhikanyeye/todowork?include_prereleases&label=release)](https://github.com/zhikanyeye/todowork/releases/latest)

</div>

---

## ✨ 功能一览

### ⏳ 番茄钟 · 一呼一吸都有节奏
- **三种周期**：工作 / 短休息 / 长休息，每 4 个番茄自动进入长休息
- **倒计时与正向计时** 双模式，正计时记录真实的专注时长
- **墙钟锚定**：息屏、Doze 下计时依然精准，不会偷跑也不会停表
- 最后 10 秒滴答提示，完成时叮咚一声，温柔收尾

### 📝 任务管理 · 每件事都有自己的节奏
- 分类管理（工作 / 学习 / 生活 / 其他），番茄数预估与完成统计
- **每个任务可单独设置番茄时长**——写论文 45 分钟，回消息 15 分钟，互不将就
- 到期日期自动同步至**系统日历**：从设定日起每天一条行程，直到到期日
- 任务编辑、完成、删除，日历事件始终同步更新，不留孤儿事件

### 🎨 沉浸模式 · 让世界只剩你和这件事
- 程序化动态壁纸：晨曦森林、深空星云、窗前夜雨、温暖可可，无需下载零体积
- 自定义壁纸、自定义背景音乐，打造你的专属心流角落
- 全屏隐藏系统栏，大字时钟 + 环形进度，剩余时间一目了然

### 🎵 环境音 · 在声音里安定下来
- 合成白噪音实时生成：雨声、海浪、太空氛围……零体积无限循环
- 真实录音：咖啡馆清晨、翻动书页、风铃、鸟鸣花园，高保真更自然
- 可导入本地音频，也支持合成滴答声增强时间感

### 🔒 严格模式 · 温柔而坚定的守护者
- 专注中切出应用会被拉回，配合免打扰、通知拦截，筑起专注结界
- 误触保护：音量键等系统界面不会触发警报，确认违规才提醒
- 警报最多响两次即转静默，回到专注页立即静音 + 3 秒缓冲横幅
- 白名单应用一键直达，放弃专注需 8 秒倒计时确认（可取消）

### ☁️ 数据与更新 · 你的努力不会丢
- JSON 备份文本一键复制，云端备份码跨设备恢复
- 多渠道云端存储自动回退，旧备份码持续兼容
- 启动时自动检查 GitHub Releases，新版本弹窗提醒，设置页也可手动检查

---

## 📊 专注统计

按天 / 周 / 月 / 年四个维度回顾你的专注：
- **热力图**：每一天的投入深浅可见，坚持是最好的勋章
- **趋势曲线**：专注时长的起承转合，见证习惯的养成
- **分类饼图**：时间去哪儿了，一图了然

---

## 🛠️ 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose · Material 3 · 动态取色 |
| 架构 | MVVM · 单 Activity 多页面 |
| 数据 | Room (KSP) · SharedPreferences |
| 异步 | Kotlin Coroutines · Flow |
| 网络 | OkHttp（云备份 / GitHub Releases 更新检测） |
| 图像 | Coil |
| 测试 | JUnit · Robolectric · Roborazzi 截图测试 |
| 构建 | Gradle Kotlin DSL · Version Catalog |

<details>
<summary>📁 项目结构</summary>

```
app/src/main/java/com/yangshizi/todo/
├── MainActivity.kt                 # 入口：底部导航、生命周期联动、更新弹窗
├── data/
│   ├── audio/                      # 合成环境音 / 滴答声 / 真实录音播放
│   ├── database/                   # Room：任务与专注日志（含版本迁移）
│   ├── repository/                 # 数据仓储
│   ├── sync/                       # 日历同步、云备份（多渠道回退）
│   └── update/                     # GitHub Releases 更新检测
├── service/                        # 严格模式：前台保活、无障碍拦截、悬浮锁屏、通知拦截、白名单
└── ui/
    ├── screens/                    # 专注 / 任务 / 统计 / 设置 / 动态壁纸
    ├── theme/                      # 主题 + UiKit 设计令牌
    └── viewmodel/                  # 番茄钟业务状态机
```

</details>

---

## 🚀 快速开始

### 环境要求
- Android Studio（Hedgehog 及以上）
- JDK 17+
- Android SDK 36，minSdk 24（Android 7.0 及以上设备可安装）

### 本地运行
1. 克隆仓库，用 Android Studio 打开项目根目录，等待 Gradle 同步
2. 从 `debug.keystore.base64` 还原 debug 签名：
   ```bash
   base64 -d debug.keystore.base64 > debug.keystore
   ```
3. 选择设备，运行 `app`

### 构建发布包
```bash
export STORE_PASSWORD=你的密钥库密码
export KEY_PASSWORD=同上
./gradlew :app:assembleRelease
```

推送到 `main` 分支后，CI 会自动构建签名 Release APK，命名为
**`洋柿子ToDo-v{versionName}.apk`** 并发布到 GitHub Releases。
应用内的更新检测正是读取 Releases 最新版本。

---

## 🔐 权限说明

| 权限 | 用途 |
|---|---|
| `INTERNET` | 云端备份恢复、更新检测 |
| `POST_NOTIFICATIONS` | 专注计时常驻通知（Android 13+ 运行时申请） |
| `VIBRATE` | 番茄完成与严格模式提醒 |
| `READ/WRITE_CALENDAR` | 任务同步至系统日历 |
| `ACCESS_NOTIFICATION_POLICY` | 专注时开启系统免打扰 |
| `SYSTEM_ALERT_WINDOW` | 严格模式锁屏浮层 |
| `PACKAGE_USAGE_STATS` | 严格模式前台应用检测（需在系统设置授权） |
| 无障碍服务 | 严格模式拦截应用切换（需在系统设置手动开启） |

除通知权限外，敏感权限均在「系统」页提供一键跳转授权入口，不授权不影响基础功能。

---

## 📦 下载

前往 [**Releases**](https://github.com/zhikanyeye/todowork/releases/latest) 下载最新版
**洋柿子ToDo-vX.Y.Z.apk**。

每次发布新版本后，旧版本应用会在启动时收到更新提醒。

---

## 📄 开源协议

本项目基于 [Apache License 2.0](LICENSE) 开源。
你可以自由使用、修改和分发，请保留版权与协议声明。

---

<div align="center">

**专注不是苦役，是把时间还给自己。**

Made with 🍅 and Kotlin

</div>
