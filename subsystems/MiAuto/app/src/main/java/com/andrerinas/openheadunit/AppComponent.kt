package com.andrerinas.openheadunit

import android.app.NotificationManager
import android.content.Context
import android.net.wifi.WifiManager
import com.andrerinas.openheadunit.connection.CommManager
import com.andrerinas.openheadunit.connection.IcocaBridge
import com.andrerinas.openheadunit.connection.carkey.CarKeysManager
import com.andrerinas.openheadunit.decoder.AudioDecoder
import com.andrerinas.openheadunit.decoder.DeviceMemoryProfile
import com.andrerinas.openheadunit.decoder.VideoDecoder
import com.andrerinas.openheadunit.utils.SUExecutor
import com.andrerinas.openheadunit.utils.Settings

class AppComponent(private val app: App) {

    val settings = Settings(app)
    val videoDecoder = VideoDecoder(settings, DeviceMemoryProfile.readWithOverride(app, settings.debugForceMemoryProfile))
    val audioDecoder = AudioDecoder()

    val notificationManager: NotificationManager
        get() = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val wifiManager: WifiManager
        get() = app.getSystemService(Context.WIFI_SERVICE) as WifiManager

    val commManager = CommManager(app, settings, audioDecoder, videoDecoder)

    /**
     * MiAuto's ICCOA CarLink (Xiaomi CarWith) link. Process-scoped: the SDK must be initialised
     * once per process, so this bridge outlives any single activity and is the single owner of
     * the [com.miauto.app.IccoaManager] instance.
     */
    /**
     * ICCOA SDK 的适配层。SDK 的字节码是单独注入 APK 的（libs/icar-sdk.dex），源码工程里
     * 只有签名存根；在没有 dex 的构建里构造它必然抛 NoClassDefFoundError，所以这里容错为 null，
     * 由界面显示「缺少 ICCOA CarLink SDK」，而不是让整个应用起不来。
     */
    val icocaBridge: IcocaBridge? by lazy {
        runCatching { IcocaBridge(app) }
            .onFailure { android.util.Log.w("MiAuto", "ICCOA SDK unavailable", it) }
            .getOrNull()
    }

    val suExecutor = SUExecutor()

    val carKeysManager = CarKeysManager()
}
