# CarPlay 子系统（0.2.22）

车机端 CarPlay 接收程序，包名 `xielinyan.carplay.com`，入口 `com.shilapi.xcertplay.DiPlayActivity`。
自 0.2.22 起在 AllCarPlay 里作为「苹果IOS」卡片的目标。

- 版本信息行文字：`公开预览` → `当前版本`（resources.arsc 同长度原位替换）
- 版本号：`0.2.22-hud-test` → `0.2.22`（AndroidManifest versionName，尾部补空格保持字串池长度不变）
- 其余代码与原包一致；重签用 `~/.android/debug.keystore`，与旧版签名相同，可覆盖安装。

发布文件：`CarPlay-0.2.22.apk`。
