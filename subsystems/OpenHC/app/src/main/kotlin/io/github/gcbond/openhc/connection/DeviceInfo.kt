package io.github.gcbond.openhc.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.SystemClock
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
        "ro.btmac",
        "ro.bluetooth.address",
        "ro.vendor.bt.bdaddr",
        "persist.sys.btaddr",
        "ro.boot.bt_mac",
        "ro.rockchip.bt.bdaddr",
        "ro.mediatek.bt.bdaddr",
        "hw.bt.mac",
        "sys.bt.mac",
    )

    /** 车机把蓝牙地址写在这些配置文件里（安卓 7～9 的 bluedroid 配置）；读它要多有 root 权限。 */
    private val CONFIG_FILES = listOf(
        "/data/misc/bluetooth/bt_config.conf",
        "/data/misc/bluedroid/bt_config.conf",
        "/data/misc/bluetooth/bt_config.xml",
        "/data/misc/bluedroid/bt_config.xml",
    )

    private val CONFIG_ADDRESS_PATTERN = Regex(
        """"?(?:BtAddr|Address|bd_addr|bdaddr|BD_ADDR)"?\s*[=:]\s*"?(\w{2}[:-]\w{2}[:-]\w{2}[:-]\w{2}[:-]\w{2}[:-]\w{2})""",
    )

    private const val CACHE_MILLIS = 10 * 60 * 1000L

    private val ADDRESS_PATTERN = Regex("""(?:address|Address|bdaddr|BD_ADDR)\s*[:=]\s*([0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2}:[0-9A-Fa-f]{2})""")

    /**
     * 读系统属性 [BluetoothAddressPolicy.SYSTEM_PROPERTY_KEY]，读不到返回 null。
     * 系统属性的读取接口没有公开，只能反射。安卓 9 起非公开接口受限，这个接口属于允许调用、系统只记一行警告的那一类；
     * 将来的系统要是拦了，这里返回 null，退回手填地址。
     */
    /** 探到的车机蓝牙地址和它的出处（[source] 给设置页显示用）。 */
    data class Probe(val address: String, val source: String)

    @Volatile private var cachedProbe: Probe? = null
    @Volatile private var cachedProbeAt = 0L

    /**
     * 主动向车机要一次真实蓝牙地址。安卓 6 起普通应用拿不到本机蓝牙地址，但很多车机把它放在系统属性、
     * bluedroid 配置或 dumpsys 里，这里把能试的都试一遍；[fresh] 为 true 时忽略缓存重新查（设置页的「获取蓝牙地址」）。
     * 只认真实硬件地址（第一字节的本地管理位为 0），软件编出来的地址不收。
     */
    fun probe(context: Context, fresh: Boolean = false): Probe? {
        val now = SystemClock.elapsedRealtime()
        if (!fresh) cachedProbe?.let { if (now - cachedProbeAt < CACHE_MILLIS) return it }
        val found = runProbes(context) ?: return null
        cachedProbe = found
        cachedProbeAt = now
        return found
    }

    fun systemBluetoothAddress(context: Context): String? =
        probe(context)?.address ?: syntheticBluetoothAddress(context)

    private fun runProbes(context: Context): Probe? {
        // 1) 常见系统属性（很多车机把蓝牙地址写在属性里，普通应用可读）。
        for (key in PROPERTY_KEYS) {
            accept(readProperty(key), "系统属性 $key")?.let { return it }
        }
        // 2) 反射被系统拦了时，直接调 getprop 命令（普通应用可以执行，不受非公开接口限制）。
        for (key in PROPERTY_KEYS) {
            accept(readPropertyByShell(key), "系统属性 $key（getprop）")?.let { return it }
        }
        // 3) 不知道属性名时，整份 getprop 扫一遍，只看键名和蓝牙/地址有关的行。
        fromAllProperties()?.let { return it }
        // 4) 车机可能把地址放在 Settings.Secure（有系统权限的 ROM 才可读）。
        accept(runCatching {
            Settings.Secure.getString(context.contentResolver, "bluetooth_address")
        }.getOrNull(), "系统设置")?.let { return it }
        // 5) 蓝牙适配器：安卓 6 起多数返回 02:00:00:00:00:00，个别车机 ROM 仍给真值。
        accept(runCatching {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.address
        }.getOrNull(), "蓝牙适配器")?.let { return it }
        // 6) 车机的 bluedroid 配置（有 root 才读得到）。
        fromBluetoothConfig()?.let { return it }
        // 7) dumpsys：先按普通应用试，再试 root。
        fromDumpsys(root = false)?.let { return it }
        fromDumpsys(root = true)?.let { return it }
        return null
    }

    /** 只收真实硬件地址：格式对、不是占位值、第一字节的本地管理位为 0（软件编出来的地址都置了 1）。 */
    private fun accept(raw: String?, source: String): Probe? {
        val address = BluetoothAddressPolicy.normalize(raw) ?: return null
        if (BluetoothAddressPolicy.isLocallyAdministered(address)) return null
        return Probe(address, source)
    }

    /** 整份 getprop 里扫：格式是 `[键]: [值]`，只看键名带 bt / bluetooth / bdaddr / mac 的行。 */
    private fun fromAllProperties(): Probe? {
        val text = exec("/system/bin/getprop") ?: return null
        val pattern = Regex("""\[([^\]]+)\]:\s*\[([^\]]*)\]""")
        for (match in pattern.findAll(text)) {
            val key = match.groupValues[1]
            val lower = key.lowercase()
            if (!(lower.contains("bt") || lower.contains("bluetooth") || lower.contains("bdaddr") || lower.contains("mac"))) continue
            accept(match.groupValues[2], "系统属性 $key")?.let { return it }
        }
        return null
    }

    private fun exec(vararg command: String): String? = try {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        text
    } catch (e: java.io.IOException) {
        null
    } catch (e: RuntimeException) {
        null
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

    /** 直接执行 `getprop <key>`：反射被非公开接口限制拦住时的退路。 */
    private fun readPropertyByShell(key: String): String? =
        exec("/system/bin/getprop", key)?.trim()?.takeIf { it.isNotBlank() }

    /** 从 bluedroid 配置文件里读车机地址：先按普通应用直读，不行再用 su。 */
    private fun fromBluetoothConfig(): Probe? {
        for (file in CONFIG_FILES) {
            val text = runCatching { java.io.File(file).readText() }.getOrNull()
                ?: exec("su", "-c", "cat $file")
                ?: continue
            CONFIG_ADDRESS_PATTERN.findAll(text).forEach { match ->
                accept(match.groupValues[1], "bluedroid 配置")?.let { return it }
            }
        }
        return null
    }

    private fun fromDumpsys(root: Boolean): Probe? {
        val command = if (root) {
            arrayOf("su", "-c", "dumpsys bluetooth_manager 2>/dev/null | head -60")
        } else {
            arrayOf("sh", "-c", "dumpsys bluetooth_manager 2>/dev/null | head -40")
        }
        val text = exec(*command) ?: return null
        ADDRESS_PATTERN.findAll(text).forEach { match ->
            accept(match.groupValues[1], if (root) "dumpsys（root）" else "dumpsys")?.let { return it }
        }
        return null
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
