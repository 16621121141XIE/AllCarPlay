plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

android {
    compileSdk = 36
    ndkVersion = "28.2.13676358"
    namespace = "com.andrerinas.openheadunit"

    buildFeatures {
        buildConfig = true
        aidl = true // Shizuku
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            // 原来按 github 渠道放了一份 VpnControl 等源码，合并后直接并进 main。
            java.srcDir("src/github/java")
        }
    }

    defaultConfig {
        minSdk = 26
        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true
        buildConfigField("boolean", "ICCOA_ONLY", "true")
        buildConfigField("String", "APPLICATION_ID", "\"com.xielinyan.allcarplay\"")
        buildConfigField("String", "VERSION_NAME", "\"" + (rootProject.findProperty("allcarplayVersionName") ?: "0.2.4") + "\"")
        buildConfigField("int", "VERSION_CODE", (rootProject.findProperty("allcarplayVersionCode") ?: "24").toString())
        buildConfigField("String", "AVAILABLE_LOCALES", "\"\"")
        externalNativeBuild { cmake { cppFlags("") } }
    }

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/*.kotlin_module"
        }
    }

    lint {
        abortOnError = false
        disable += "PackagedPrivateKey"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // ICCOA CarLink SDK：编译期用纯签名存根；运行期需要 icar-sdk.dex 由整包注入。
    compileOnly(files("stubs/icar-sdk-stubs.jar"))

    implementation("androidx.room:room-runtime:2.4.3")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
    implementation("com.google.protobuf:protobuf-java:3.25.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.startup:startup-runtime:1.1.1")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    kapt("androidx.lifecycle:lifecycle-compiler:2.6.2")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.3.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.3.5")
    implementation("com.linkedin.dexmaker:dexmaker:2.28.3")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    kapt("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation(project(":subsystems:MiAuto:contract"))
    // 合并后共用一个 Application：MiAuto 的 App 继承 HmCar 的 OpenHcApp。
    implementation(project(":subsystems:OpenHC:app"))
}
