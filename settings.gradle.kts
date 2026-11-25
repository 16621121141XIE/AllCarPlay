pluginManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "allcarplay"
include(":app")

// 子系统：HmCar（OpenHC）以库模块并进同一个安装包，不再单独装 App。
include(":subsystems:OpenHC:core:common")
include(":subsystems:OpenHC:core:nearby")
include(":subsystems:OpenHC:core:auth")
include(":subsystems:OpenHC:core:dmsdp")
include(":subsystems:OpenHC:core:hisight")
include(":subsystems:OpenHC:core:session")
include(":subsystems:OpenHC:platform")
include(":subsystems:OpenHC:app")

// 子系统：CarPlay（本地 DiPlay 源码，common + shared 直接编进同一个安装包）。
include(":subsystems:CarPlay:shared")
include(":subsystems:CarPlay:common")

// 子系统：安卓投屏（MiAuto / ICCOA CarLink 车机端源码）。
include(":subsystems:MiAuto:app")
include(":subsystems:MiAuto:contract")

