# AllCarPlay

一个 App 里装三套车机互联方案，装到车机/手机上后先出现系统选择页，进对应子系统就能用：

| 卡片 | 子系统 | 说明 |
| --- | --- | --- |
| HUAWEI | HmCar（OpenHC） | 华为 HiCar 车机端接收 |
| iPhone | CarPlay（DiPlay / xcertplay） | 苹果 CarPlay 车机端接收，无线 / USB |
| Android | AndroidCar（MiAuto） | ICCOA CarLink 车机端接收，小米 CarWith / OPPO Car+ / vivo 等手机直连 |

包名 `com.xielinyan.allcarplay`，三套子系统已按源码合并进同一工程，唯一 LAUNCHER 是宿主的 `com.xielinyan.allcarplay.MainActivity`。

## 下载

APK 在 Releases 页面，选最新一版下载即可：
<https://github.com/16621121141XIE/allcarplay/releases/latest>

## 工程结构

```
app/                        宿主：系统选择页 + 图标 + 打包（唯一 Launcher）
subsystems/OpenHC/          HmCar / OpenHC 源码（application -> library）
subsystems/CarPlay/         CarPlay / DiPlay 源码（shared + common）
subsystems/MiAuto/          AndroidCar / MiAuto 源码（app + contract）
tools/stage-apk.sh          构建产物注入 ICCOA SDK dex、zipalign、重签、校验
app/libs/icar-sdk.dex       ICCOA CarLink SDK（AndroidCar 投屏协议栈，7MB dex）
```

## 本地构建

需要 JDK 17、Android SDK（compileSdk 36、build-tools 35.0.0）和 Gradle 8.13。

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
gradle :app:assembleDebug --no-daemon
./tools/stage-apk.sh          # 注入 SDK dex + 签名，产物在仓库同级的 AllCarPlay-<版本>.apk
```

版本号在 `gradle.properties` 的 `allcarplayVersionName` / `allcarplayVersionCode`。

### 本地私有资产（未随仓库提供）

- `app/src/main/assets/offline-mfi/`：CarPlay 离线 MFi 证书与私钥，只在车机上插了认证适配器或使用本地离线身份时才需要，按你自己的证书放进 `identity.pk8`、`certificate.p7b`。
- `local.properties`：SDK 路径，按本机环境自行生成。

这两个都不会进公开仓库，缺失时 CarPlay 那条线会提示认证加载失败，HmCar / AndroidCar 不受影响。

## 说明

- HmCar 与 CarPlay 的界面语言跟随系统，AndroidCar 跟随车机日夜模式。
- 三个子系统页面上都自带「重选系统 / 车机主页」按钮，宿主不再挂悬浮窗。
- AndroidCar 依赖的 ICCOA SDK 只提供 arm64-v8a 原生库，32 位车机不支持投屏协议栈。
