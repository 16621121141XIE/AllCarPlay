# CarPlay 子系统（0.2.22，源码构建）

源码：`/Users/xielinyan/dev/diplay-src` 的 `mobile` 模块（DiPlay 二次开发，applicationId `xielinyan.carplay.com`）。
本目录放的是从源码构建出的安装包。

相对上游 DiPlay 的改动：
- `common/.../values-zh-rCN/strings.xml`：`home_public_preview` / `about_public_preview_prefix` 的「公开预览」改为「当前版本」。
- `mobile/build.gradle.kts`：debug 构建去掉 `versionNameSuffix = "-hud-test"`，版本显示 `0.2.22`。
- `common/.../DiPlayActivity.kt` 的 `standardHeader()`：首页在「车机主页」按钮左边新增「重选系统」按钮，复用 `headerButton()`（同高、同圆角、同图标 tint），点击跳 `com.xielinyan.allcarplay/.MainActivity`。
