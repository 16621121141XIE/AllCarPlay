// 会话编排层：串联 nearby、auth、dmsdp、hisight 四层，是唯一同时依赖这几层的 core 模块。
// 通用配置（Java 11 字节码、JUnit、安卓 API 23 签名检查）见根目录 build.gradle.kts。
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.animalsniffer)
}

dependencies {
    api(project(":subsystems:OpenHC:core:nearby"))
    api(project(":subsystems:OpenHC:core:auth"))
    api(project(":subsystems:OpenHC:core:dmsdp"))
    api(project(":subsystems:OpenHC:core:hisight"))
}
