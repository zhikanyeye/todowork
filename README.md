# 番茄智伴

番茄智伴是一款 Android 原生专注管理应用，结合番茄钟、任务清单、专注记录、沉浸式背景、环境白噪音、系统日历同步和数据备份恢复，帮助用户把任务拆成可执行的专注周期。

## 功能特性

- 番茄钟计时：支持工作、短休息、长休息三种周期，并可在设置中自定义时长。
- 任务管理：支持新增任务、分类、完成状态、预估番茄数和当前专注任务选择。
- 专注记录：完成工作番茄后自动记录日志，并累加关联任务的已完成番茄数。
- 沉浸模式：提供全屏专注界面、动态氛围壁纸、自定义壁纸和自定义背景音乐。
- 环境音：内置白噪音、雨声、海浪、太空氛围音，也支持导入本地音频。
- 严格模式：工作计时中限制切换页面和退出，尝试离开应用时可警告并拉回前台。
- 系统集成：支持系统免打扰模式和任务同步至手机系统日历。
- 数据备份：支持复制 JSON 备份文本，也支持通过云端备份码恢复任务和专注记录。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Room
- Kotlin Coroutines / Flow
- OkHttp
- Coil
- Robolectric / Roborazzi

## 项目结构

```text
app/src/main/java/com/example/
├── MainActivity.kt                 # 应用入口和底部导航
├── data/
│   ├── audio/AmbientAudioSynth.kt  # 程序化环境音生成
│   ├── database/                   # Room 数据库、DAO、实体
│   ├── repository/                 # 任务与日志仓储
│   └── sync/                       # 日历同步和备份恢复
└── ui/
    ├── screens/                    # 专注、任务、设置页面
    ├── theme/                      # Compose 主题
    └── viewmodel/                  # 番茄钟业务状态
```

## 运行项目

**前置条件：**

- Android Studio
- Android SDK
- JDK 11 或以上

**步骤：**

1. 使用 Android Studio 打开本项目目录。
2. 等待 Gradle 同步完成。
3. 如使用当前仓库的 debug 签名配置，请先从 `debug.keystore.base64` 还原 `debug.keystore`，或移除 `app/build.gradle.kts` 中 debug 构建使用的 `debugConfig` 签名配置。
4. 在模拟器或真机上运行 `app`。

## 权限说明

应用会按功能使用以下系统权限：

- `INTERNET`：云端备份和恢复。
- `VIBRATE`：番茄钟完成提醒和严格模式提醒。
- `READ_CALENDAR` / `WRITE_CALENDAR`：将任务同步到系统日历。
- `ACCESS_NOTIFICATION_POLICY`：开启或关闭系统免打扰。
- `SYSTEM_ALERT_WINDOW`：严格模式下尝试将应用拉回前台。

部分权限需要用户在系统设置中手动授权。

## 说明

项目中保留了 AI Studio 导出模板相关文件，例如 `.env.example` 中的 `GEMINI_API_KEY` 占位内容；当前应用主流程没有实际调用 Gemini API。现有云备份功能通过公开 JSON 存储接口实现。
