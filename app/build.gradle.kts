plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.xielinyan.allcarplay"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.xielinyan.allcarplay"
        minSdk = 26
        targetSdk = 36
        versionCode = (project.findProperty("allcarplayVersionCode") as String).toInt()
        versionName = project.findProperty("allcarplayVersionName") as String
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }


    packaging {
        jniLibs {
            // CarPlay 和 MiAuto 都带一个同名的热点修复库（liblocal_hotspot_radio.so），功能一致，留一份。
            pickFirsts += "lib/**/liblocal_hotspot_radio.so"
        }
        resources {
            // HmCar 的 BouncyCastle 和 jspecify 都带了这份 OSGi 清单，重名；安卓用不到。
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    // HmCar（OpenHC）并进同一个安装包：同一个进程、同一个 APK，不跳出去装第二个 App。
    implementation(project(":subsystems:OpenHC:app"))
    // CarPlay（本地 DiPlay 源码）以源码模块并进同一个安装包。
    implementation(project(":subsystems:CarPlay:common"))
    // 安卓投屏（MiAuto / ICCOA CarLink 车机端源码）也并进同一个安装包。
    implementation(project(":subsystems:MiAuto:app"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
}
