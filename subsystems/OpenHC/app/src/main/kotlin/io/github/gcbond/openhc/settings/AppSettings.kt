package io.github.gcbond.openhc.settings

import android.content.Context
import androidx.core.content.edit
import io.github.gcbond.openhc.BuildConfig
import io.github.gcbond.openhc.connection.DisplayPlan
import kotlin.math.roundToInt

/**
 * 应用设置（SharedPreferences）。改动在下一次连接时生效。任意线程可读写。
 */
class AppSettings(context: Context) {
    /** 构造参数只在初始化里能直接读，自动缩放要随时按当前屏幕算，所以存一份。 */
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /**
     * 没设过「界面大小」时的默认：按这块屏自动算，换到别的车机也不用再手调。
     *
     * 手机投屏按「声明的像素 + DPI」排版，实际逻辑宽度 = 像素 × 160 ÷ DPI。宽度落在 1200～1400dp 时
     * 车机上看着最合适：太小（比如 2340×1080@440 = 851dp）手机按手机尺寸铺满整块屏，字和图标大得离谱；
     * 太大（比如 2560@160 = 2560dp）又会小得看不清。所以按目标 1280dp 反推出需要的缩放比例，
     * 按 5% 取整（不必只落在设置页那几档，差一档字就会大/小一截），再限制在 50%～100%；
     * 本来就超宽的屏保持 100%。用户没在设置页手动选过时一直按这块屏自动算，换车机、换分辨率也跟着变。
     */
    private val autoUiScale: Int
        get() {
            val metrics = appContext.resources.displayMetrics
            val dpi = if (metrics.densityDpi > 0) metrics.densityDpi.toFloat() else 160f
            val logicalWidth = metrics.widthPixels * 160f / dpi
            val needed = (logicalWidth * 100f / TARGET_LOGICAL_WIDTH).roundToInt()
            return (needed / 5 * 5).coerceIn(MIN_AUTO_UI_SCALE, 100)
        }

    /** 自动算出来的最小缩放：再小字就看不清了。 */
    private val MIN_AUTO_UI_SCALE = 50

    /** 自动「界面大小」的目标逻辑宽度（dp）：车机上看着最舒服的一档。 */
    private val TARGET_LOGICAL_WIDTH = 1280f

    /** 声明给手机的帧率；0 表示按屏幕刷新率。 */
    var fps: Int
        get() = prefs.getInt(KEY_FPS, 60).coerceAtLeast(0)
        set(value) = prefs.edit { putInt(KEY_FPS, value.coerceAtLeast(0)) }

    /** 「界面大小」：声明给手机的 DPI 按这个百分比缩小，只认设置页列出的档位；没手动选过就按屏幕自动算。下次连接生效。 */
    var uiScalePercent: Int
        get() {
            // 手动选过的档位优先；没选过（或存的值不认识了）用自动值，换车机不用再调。
            if (prefs.getBoolean(KEY_UI_SCALE_MANUAL, false)) {
                prefs.getInt(KEY_UI_SCALE, 0).takeIf { it in DisplayPlan.UI_SCALE_CHOICES }?.let { return it }
            }
            return autoUiScale
        }
        set(value) = prefs.edit {
            putInt(KEY_UI_SCALE, value)
            putBoolean(KEY_UI_SCALE_MANUAL, true)
        }

    /** 用户手填的车机蓝牙地址（原样保存，用的时候再校验）；没填为 null。 */
    var manualBluetoothAddress: String?
        // 构建时给了车机真实地址（BuildConfig）就当默认值，用户不用手填；设置页照样能改。
        get() = prefs.getString(KEY_MANUAL_BT, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.DEFAULT_BT_ADDRESS.takeIf { it.isNotBlank() }
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_MANUAL_BT) else putString(KEY_MANUAL_BT, value.trim())
        }

    /**
     * 应用被「设备接入」拉起过：用户在系统框里同意过用本应用打开手机（勾了「默认」之后系统会自动授权）。
     * 这之后（「插线时不再弹窗」开着时）有线会话先等一会儿看系统会不会自动授权，再自己弹授权框，免掉一个多余的框。
     */
    var usbAttachAccepted: Boolean
        get() = prefs.getBoolean(KEY_USB_ATTACH_ACCEPTED, false)
        set(value) = prefs.edit { putBoolean(KEY_USB_ATTACH_ACCEPTED, value) }

    /**
     * 「全屏显示」：开（默认）时投屏藏起状态栏和底部导航栏、铺满整屏；关时保留底部导航栏，画面让出它的位置，
     * 声明给手机的尺寸也随之变小。下次连接生效。
     */
    var fullScreen: Boolean
        get() = prefs.getBoolean(KEY_FULL_SCREEN, true)
        set(value) = prefs.edit { putBoolean(KEY_FULL_SCREEN, value) }

    /** 外观：0 跟随车机（系统白天/黑夜自动切）、1 浅色、2 深色。 */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, 0).coerceIn(0, 2)
        set(value) = prefs.edit { putInt(KEY_THEME_MODE, value.coerceIn(0, 2)) }

    /** 安卓 13 起的通知权限问过一次了（拒绝了也不再追问）。 */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION_ASKED, value) }

    private companion object {
        const val FILE = "openhc-settings"
        const val KEY_FPS = "fps"
        const val KEY_UI_SCALE = "ui_scale_percent"
        const val KEY_UI_SCALE_MANUAL = "ui_scale_manual"
        const val KEY_MANUAL_BT = "manual_bluetooth_address"
        const val KEY_USB_ATTACH_ACCEPTED = "usb_attach_accepted"
        const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
        const val KEY_FULL_SCREEN = "full_screen"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
