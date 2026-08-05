# KilViewer

<img src="app/src/main/res/drawable/appicon.png" width="120" alt="App Icon">

一个基于 Jetpack Compose 的 Android WebView 浏览器应用。

## 📱 功能特性

- **🌐 WebView 浏览**: 内置 WebView 浏览器,支持 JavaScript 和 DOM 存储
- **🎬 视频全屏播放**: 支持 HTML5 视频全屏播放,自动切换横屏
- **⚠️ 网络错误处理**: 友好的网络错误提示和重试机制
- **🎨 Material Design**: 使用 Material You 设计风格
- **📱 现代化 UI**: 基于 Jetpack Compose 构建的原生界面

## 🛠️ 技术栈

- **Kotlin** - 主要编程语言
- **Jetpack Compose** - 现代 Android UI 工具包
- **Material Design 3** - UI 设计系统
- **WebView** - 内置浏览器组件
- **AndroidX** - Android 支持库

## 📋 项目结构

```
KilViewer/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/miwafi/kilviewer/
│   │   │   │   ├── MainActivity.kt        # 主 Activity
│   │   │   │   └── ui/theme/              # 主题相关
│   │   │   ├── res/                        # 资源文件
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                           # 单元测试
│   │   └── androidTest/                    # 仪器测试
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
└── settings.gradle.kts
```

## 🚀 安装与使用

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- Android SDK 37
- Kotlin 1.9.0+
- Gradle 8.0+

### 构建步骤

1. **克隆仓库**
   ```bash
   git clone https://github.com/your-username/KilViewer.git
   cd KilViewer
   ```

2. **打开项目**
   - 使用 Android Studio 打开项目根目录

3. **同步 Gradle**
   - 等待 Gradle 同步完成

4. **运行应用**
   - 点击运行按钮或使用快捷键 `Shift + F10`
   - 选择目标设备(真机或模拟器)

### 系统要求

- **最低 Android 版本**: Android 7.0 (API 24)
- **目标 Android 版本**: Android 14 (API 37)

## 📸 应用截图

应用包含以下主要界面:

- **浏览页面**: WebView 浏览器主界面
- **下载页面**: 下载管理(占位页面)
- **设置页面**: 应用设置(占位页面)

## 🎯 主要功能说明

### 视频全屏播放

应用支持 HTML5 视频全屏播放功能:
- 自动检测视频全屏请求
- 横屏播放视频
- 返回键优先退出全屏
- 自动恢复竖屏方向

### 网络错误处理

当网络连接失败时:
- 显示友好的错误提示界面
- 提供重试按钮
- 仅针对主框架加载失败显示错误页

## 📝 开发说明

### 代码规范

- 遵循 Kotlin 官方编码规范
- 使用 Jetpack Compose 构建 UI
- 采用 Material Design 3 设计系统

### 架构设计

- 单 Activity 架构
- Compose UI 组件化
- 响应式编程(使用 State 和 Composable)

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request!

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 Apache License 2.0 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 👨‍💻 作者

**miwafi**

## 🙏 致谢

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design](https://material.io/)
- [Android Developer](https://developer.android.com/)

---

⭐ 如果这个项目对你有帮助,欢迎 Star!