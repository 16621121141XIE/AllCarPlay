package io.github.gcbond.openhc.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import io.github.gcbond.openhc.platform.media.DisplayRefreshRate
import java.security.MessageDigest

/** 从系统读车机的蓝牙地址属性和屏幕参数。 */
internal object DeviceInfo {

    /** 读蓝牙地址时要试的系统属性名，按常见程度排。 */
    private val PROPERTY_KEYS = listOf(
        BluetoothAddressPolicy.SYSTEM_PROPERTY_KEY,
        "ro.bt.bdaddr",
        "ro.boot.btmacaddr",
        "persist.vendor.bt.bdaddr",
        "vendor.bt.bdaddr",
        "persist.bluetooth.bdaddr",
        "ro.bt.mac",
        "ro.product.bt.mac",
    )

    private val ADDRESS_PATTERN = Regex("""(?:address|Address|bdaddr|BD_ADDR)\s*[:=]\s*([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})""")

    /**
     * 读系统属性 [BluetoothAddressPolicy.SYSTEM_PROPERTY_KEY]，读不到返回 null。
     * 系统属性的读取接口没有公开，只能反射。安卓 9 起非公开接口受限，这个接口属于允许调用、系统只记一行警告的那一类；
     * 将来的系统要是拦了，这里返回 null，退回手填地址。
     */
    @SuppressLint("PrivateApi")
    fun systemBluetoothAddress(context: Context): String? {
        // 1) 常见系统属性（很多车机把蓝牙地址写在属性里，普通应用可读）。
        for (key in PROPERTY_KEYS) {
            readProperty(key)?.let { raw ->
                BluetoothAddressPolicy.normalize(raw)?.let { return it }
            }
        }
        // 2) 少数系统把地址放在 Settings.Secure（部分车机、部分 ROM 可读）。
        runCatching {
            Settings.Secure.getString(context.contentResolver, "bluetooth_address")
        }.getOrNull()?.let { raw ->
            BluetoothAddressPolicy.normalize(raw)?.let { return it }
        }
        // 3) 蓝牙适配器：安卓 6 起多数返回 02:00:00:00:00:00，个别车机 ROM 仍给真值。
        runCatching {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.address
        }.getOrNull()?.let { raw ->
            BluetoothAddressPolicy.normalize(raw)?.let { return it }
        }
        // 4) 兜底：从 dumpsys 里抓（部分车机不限制应用调用 dumpsys bluetooth_manager）。
        fromDumpsys()?.let { return it }
        // 5) 全都拿不到时，按本机 ANDROID_ID 生成一个固定的软件地址（第一字节置本地管理位）。
        //    有线投屏和身份握手都能用；无线二维码需要真实地址，无线页会照旧提示。
        return syntheticBluetoothAddress(context)
    }

    /** 系统属性读取接口没有公开，只能反射；安卓 9 起非公开接口受限，这里属于允许调用的一类。 */
    private fun readProperty(key: String): String? = try {
        val get = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
    } catch (e: ReflectiveOperationException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    private fun fromDumpsys(): String? = try {
        val process = ProcessBuilder("sh", "-c", "dumpsys bluetooth_manager 2>/dev/null | head -40")
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        ADDRESS_PATTERN.findAll(text).map { it.groupValues[1] }
            .firstNotNullOfOrNull { BluetoothAddressPolicy.normalize(it) }
    } catch (e: java.io.IOException) {
        null
    } catch (e: RuntimeException) {
        null
    }

    /** 由 ANDROID_ID 哈希出固定的 6 字节地址，换设备才变，重装应用不变。 */
    private fun syntheticBluetoothAddress(context: Context): String {
        val id = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val digest = MessageDigest.getInstance("SHA-256").digest((id + "openhc").toByteArray())
        val bytes = ByteArray(6) { digest[it] }
        bytes[0] = (bytes[0].toInt() or 0x02).toByte()   // 本地管理位置 1：标明这是软件地址
        if (bytes[0].toInt() == 0x02 && bytes.copyOfRange(1, 6).all { it == 0.toByte() }) {
            bytes[5] = 0x01.toByte()                      // 避免正好撞上系统占位值 02:00:00:00:00:00
        }
        return BluetoothAddressPolicy.normalize(
            bytes.joinToString(":") { "%02X".format(it) }
        ) ?: "0A:00:00:00:00:01"
    }

    /** 默认屏幕（车机主屏）的实际像素、DPI 和按刷新率算出的帧率。 */
    fun screen(context: Context): ScreenSpec {
        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        val size = Point()
        if (display != null) {
            // getRealSize/getRealMetrics 在安卓 12 被标为过时（新接口要界面 Context），但仍然返回整块屏幕的实际像素，
            // 应用 Context 上也能用。
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
        }
        if (size.x <= 0 || size.y <= 0) {
            val fallback = context.resources.displayMetrics
            size.set(fallback.widthPixels, fallback.heightPixels)
            metrics.densityDpi = fallback.densityDpi
        }
        val dpi = if (metrics.densityDpi > 0) metrics.densityDpi else DisplayMetrics.DENSITY_DEFAULT
        return ScreenSpec(size.x, size.y, dpi, DisplayRefreshRate.declaredFps(context))
    }
}
