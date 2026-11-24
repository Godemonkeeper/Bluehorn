# BlueHorn 蓝牙扩音喇叭 (Android Open-Source Megaphone)

**BlueHorn** 是一款基于 **Kotlin + Jetpack Compose** 开发的开源、无广告、零联网 Android 实时扩音喇叭应用。它通过手机内置麦克风采集你的声音，经过实时降噪门限与防破音增益处理后，直接通过蓝牙 **A2DP / LE Audio** 高音质媒体通道输出到蓝牙音响。

## 核心特性

- **蓝牙音响精准路由**：强制锁定使用**手机内置麦克风**录音 + `USAGE_MEDIA` 蓝牙 A2DP/BLE 媒体通道播放，避免 Android 误切入低音质蓝牙通话（HFP/SCO）模式。
- **双工作模式**：
  - **持续扩音**：一键开启，支持前台服务（Foreground Service）与通知栏快捷停止，息屏也能稳定运行。
  - **按住说话（对讲模式）**：按住发声、松手平滑静音，大幅降低啸叫风险。
- **实时音频 DSP 处理**：
  - **50%–400% 扩音增益**：内置 `tanh` 软削波限幅器（Soft-Clipping Limiter），大声说话也不破音。
  - **防底噪门限（Noise Gate）**：不说话时自动平滑静音，过滤环境底噪。
  - **硬件回声消除（AEC）& 环境降噪（NS）**：自动检测设备硬件支持并可实时开关。
- **防啸叫安全锁**：默认开启「仅允许蓝牙输出」，蓝牙音响意外断开时自动停止扩音，防止手机自带扬声器产生刺耳尖啸。

## 云端一键编译 APK（无需安装 Android Studio）

本项目已内置 **GitHub Actions** 自动编译工作流（`.github/workflows/build-apk.yml`），直接在浏览器上传代码即可生成可安装的 `.apk` 文件：

1. 在 GitHub 新建一个仓库（例如 `BlueHorn`），公开或私有均可。
2. 解压本项目的 `BlueHorn-Android.zip`，将文件夹内所有文件（注意包含隐藏目录 `.github/workflows/build-apk.yml`）上传并提交到 `main` 分支。
   - **网页端提示**：如果通过 GitHub 网页直接拖拽上传，请确保 `.github` 文件夹一并被拖入；或者使用 Git 命令行推送：
     ```bash
     cd BlueHorn
     git init
     git add .
     git commit -m "Initial commit: BlueHorn open-source Bluetooth megaphone"
     git branch -M main
     git remote add origin https://github.com/<你的用户名>/BlueHorn.git
     git push -u origin main
     ```
3. 打开仓库顶部的 **Actions** 标签页，等待 **Build Android APK** 工作流运行完成（约 2–3 分钟，出现绿色对勾 ✅）。
4. 点击该次运行记录，拉到页面底部的 **Artifacts** 区域，点击下载 **`BlueHorn-debug-apk`**。
5. 解压得到 `app-debug.apk`，传到 Android 手机（Android 8.0 及以上）安装即可。

## 本地编译（可选）

如果你本地已安装 Android Studio（Ladybug / 2024.2+）或 JDK 17 + Android SDK 35：

```bash
gradle assembleDebug
```

编译产物位于：`app/build/outputs/apk/debug/app-debug.apk`

## 防啸叫与低延迟使用建议

1. **保持距离与错开方向**：尽量让手机麦克风距离蓝牙音响 **2 米以上**，不要将手机话筒正对音响喇叭。
2. **优先使用「按住说话」**：在室内小空间使用时，按住说话模式可彻底杜绝持续回声累积。
3. **合理调节增益与门限**：先将蓝牙音响音量调至适中，再微调 App 内的「扩音增益」和「防底噪门限」。
4. **关于蓝牙延迟**：蓝牙 A2DP 媒体协议本身存在约 100–250ms 的编解码与无线传输延迟，这是蓝牙硬件协议固有特性；若需要近乎零延迟，可使用支持低延迟编解码的音响或有线 AUX 连接（关闭「仅允许蓝牙输出」即可支持有线输出）。

## 项目结构

```text
BlueHorn/
├── .github/workflows/build-apk.yml        # GitHub Actions 云端自动测试与打包 APK
├── app/src/main/
│   ├── AndroidManifest.xml                # 权限与麦克风前台服务声明
│   ├── java/io/github/bluehorn/
│   │   ├── MainActivity.kt                # 入口与动态权限管理
│   │   ├── audio/
│   │   │   ├── AudioConfig.kt             # 音频配置与状态数据模型
│   │   │   ├── AudioDspProcessor.kt       # 实时降噪门限、增益与软限幅 DSP
│   │   │   ├── BluetoothAudioRouter.kt    # 蓝牙 A2DP/BLE 设备热插拔监听与路由
│   │   │   └── MegaphoneEngine.kt         # AudioRecord -> AudioTrack 低延迟实时引擎
│   │   ├── service/
│   │   │   ├── MegaphoneRepository.kt     # UI 与后台服务共享状态仓库
│   │   │   └── MegaphoneService.kt        # 息屏保活前台服务与通知栏控制
│   │   └── ui/
│   │       ├── MegaphoneScreen.kt         # Jetpack Compose Material 3 界面
│   │       └── theme/Theme.kt             # 深色控制台主题配色
│   └── res/                               # 矢量自适应图标、字符串与主题
└── app/src/test/java/io/github/bluehorn/
    └── audio/AudioDspProcessorTest.kt     # DSP 核心逻辑单元测试
```

## 开源协议

本项目基于 [MIT License](LICENSE) 开源，欢迎自由使用、修改与二次分发。
