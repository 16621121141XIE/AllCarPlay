package io.github.gcbond.openhc.connection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import io.github.gcbond.openhc.platform.media.DisplayRefreshRate
import java.io.File
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
        val found = runProbes(context, null) ?: return null
        cachedProbe = found
        cachedProbeAt = now
        return found
    }

    /**
     * 设置页「探测详情」用：把每条路试一遍，逐条给出结果（命中的写地址，没命中的写原因）。
     * 只给车主看，不改缓存。
     */
    fun diagnose(context: Context): List<String> {
        val log = mutableListOf<String>()
        val hit = runProbes(context, log)
        // 17 个属性键逐条列太啰嗦（车机屏幕小，滚半天看不到重点），合成一行。
        val property = log.filter { it.startsWith("属性 ") || it.startsWith("getprop ") }
        val rest = log.filterNot { it.startsWith("属性 ") || it.startsWith("getprop ") }
        val summary = if (property.isEmpty()) emptyList() else listOf(
            if (property.any { !it.endsWith("读不到") }) property.first { !it.endsWith("读不到") }
            else "系统属性（打了 ${property.size} 次，含 17 个常见键）：全读不到"
        )
        return summary + rest + (
            if (hit != null) "结论：读到了 ${hit.address}（来源：${hit.source}）"
            else "结论：这台车机一条路都没给地址，只能手填一次"
        )
    }

    fun systemBluetoothAddress(context: Context): String? =
        probe(context)?.address ?: syntheticBluetoothAddress(context)

    private fun runProbes(context: Context, log: MutableList<String>?): Probe? {
        fun note(step: String, raw: String?) {
            log?.add(if (raw == null) "$step：读不到" else "$step：$raw")
        }
        fun hit(context2: String, raw: String?, source: String): Probe? {
            note(context2, raw)
            return accept(raw, source)
        }

        // 1) 常见系统属性（很多车机把蓝牙地址写在属性里，普通应用可读）。
        for (key in PROPERTY_KEYS) {
            hit("属性 $key", readProperty(key), "系统属性 $key")?.let { return it }
        }
        // 2) 反射被系统拦了时，直接调 getprop 命令（普通应用可以执行，不受非公开接口限制）。
        for (key in PROPERTY_KEYS) {
            hit("getprop $key", readPropertyByShell(key), "系统属性 $key（getprop）")?.let { return it }
        }
        // 3) 不知道属性名时，整份 getprop 扫一遍，只看键名和蓝牙/地址有关的行。
        fromAllProperties(log)?.let { return it }
        // 4) 车机可能把地址放在 Settings.Secure（有系统权限的 ROM 才可读）。
        hit("Secure 设置", runCatching {
            Settings.Secure.getString(context.contentResolver, "bluetooth_address")
        }.getOrNull(), "系统设置")?.let { return it }
        // 5) 蓝牙适配器：安卓 6 起多数返回 02:00:00:00:00:00，个别车机 ROM 仍给真值。
        hit("BluetoothAdapter.address", runCatching {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter?.address
        }.getOrNull(), "蓝牙适配器")?.let { return it }
        // 6) 反射打到蓝牙服务内部：不少厂商 ROM 没做权限校验，这一下能拿到真值。
        hit("蓝牙服务反射", fromBluetoothServiceReflection(), "蓝牙服务（反射）")?.let { return it }
        // 7) hci0 的 sysfs 节点：有的车机 ROM SELinux 松，地址直接写在文件里。
        hit("sysfs hci0", fromSysfs(), "sysfs")?.let { return it }
        // 8) 系统自带的 hcitool / hciconfig，会打印控制器地址。
        hit("hcitool", fromHciTool(), "hcitool")?.let { return it }
        // 9) 车机的 bluedroid 配置（有 root 才读得到）。
        hit("bluedroid 配置", fromBluetoothConfigRaw(), "bluedroid 配置")?.let { return it }
        // 10) dumpsys：先按普通应用试，再试 root。
        hit("dumpsys", fromDumpsys(root = false), "dumpsys")?.let { return it }
        hit("dumpsys(root)", fromDumpsys(root = true), "dumpsys（root）")?.let { return it }
        return null
    }

    /** 只收真实硬件地址：格式对、不是占位值、第一字节的本地管理位为 0（软件编出来的地址都置了 1）。 */
    private fun accept(raw: String?, source: String): Probe? {
        val address = BluetoothAddressPolicy.normalize(raw) ?: return null
        if (BluetoothAddressPolicy.isLocallyAdministered(address)) return null
        return Probe(address, source)
    }

    /** 整份 getprop 里扫：格式是 `[键]: [值]`，只看键名带 bt / bluetooth / bdaddr / mac 的行。 */
    private fun fromAllProperties(log: MutableList<String>?): Probe? {
        val text = exec("/system/bin/getprop")
        if (text == null) {
            log?.add("全量 getprop：跑不起来")
            return null
        }
        var scanned = 0
        val pattern = Regex("""\[([^\]]+)\]:\s*\[([^\]]*)\]""")
        for (match in pattern.findAll(text)) {
            val key = match.groupValues[1]
            val lower = key.lowercase()
            if (!(lower.contains("bt") || lower.contains("bluetooth") || lower.contains("bdaddr") || lower.contains("mac"))) continue
            scanned++
            accept(match.groupValues[2], "系统属性 $key")?.let { return it }
        }
        log?.add("全量 getprop：扫了 $scanned 条跟蓝牙/地址有关的属性，没一个是真实地址")
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
    private fun fromBluetoothConfigRaw(): String? {
        for (file in CONFIG_FILES) {
            val text = runCatching { File(file).readText() }.getOrNull()
                ?: exec("su", "-c", "cat $file")
                ?: continue
            CONFIG_ADDRESS_PATTERN.find(text)?.let { return it.groupValues[1] }
        }
        return null
    }

    private fun fromDumpsys(root: Boolean): String? {
        val command = if (root) {
            arrayOf("su", "-c", "dumpsys bluetooth_manager 2>/dev/null | head -60")
        } else {
            arrayOf("sh", "-c", "dumpsys bluetooth_manager 2>/dev/null | head -40")
        }
        val text = exec(*command) ?: return null
        return ADDRESS_PATTERN.find(text)?.groupValues?.get(1)
    }

    /**
     * 反射钻到蓝牙服务里要地址：安卓 8 上 IBluetooth 有 `getAddress()`，很多厂商 ROM 没在这层做权限校验，
     * 普通应用也能拿到真值；系统真拦了会抛 SecurityException，这里吞掉当读不到。
     */
    @SuppressLint("PrivateApi")
    private fun fromBluetoothServiceReflection(): String? {
        val adapter = runCatching { BluetoothAdapter.getDefaultAdapter() }.getOrNull() ?: return null
        // 1) BluetoothAdapter 里的 mService（IBluetooth），直接调它的 getAddress。
        runCatching {
            val field = BluetoothAdapter::class.java.getDeclaredField("mService")
            field.isAccessible = true
            val service = field.get(adapter) ?: return@runCatching null
            val method = service.javaClass.getMethod("getAddress")
            (method.invoke(service) as? String)?.takeIf { it.isNotBlank() }
        }.getOrNull()?.let { return it }
        return null
    }

    /** hci0 的地址就在 sysfs 的节点里；有的车机 ROM 这些文件普通应用可读。 */
    private fun fromSysfs(): String? {
        val candidates = listOf(
            "/sys/class/bluetooth/hci0/address",
            "/sys/class/bluetooth/hci0/id",
            "/sys/devices/virtual/bluetooth/hci0/address",
        )
        for (path in candidates) {
            val text = runCatching { File(path).readText() }.getOrNull() ?: continue
            ADDRESS_PATTERN.find(text)?.let { return it.groupValues[1] }
            rawAddress(text)?.let { return it }
        }
        return null
    }

    /** hcitool / hciconfig 的输出里常有一行 BD Address。 */
    private fun fromHciTool(): String? {
        for (command in listOf(
            arrayOf("/system/bin/hcitool", "dev"),
            arrayOf("hcitool", "dev"),
            arrayOf("/system/xbin/hcitool", "dev"),
            arrayOf("/system/bin/hciconfig"),
            arrayOf("hciconfig", "-a"),
        )) {
            val text = exec(*command) ?: continue
            ADDRESS_PATTERN.find(text)?.let { return it.groupValues[1] }
            rawAddress(text)?.let { return it }
        }
        return null
    }

    /** 一整行/整段就是一个地址（sysfs、hcitool 常见格式）。 */
    private fun rawAddress(text: String): String? =
        Regex("""\b([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})\b""").find(text)?.groupValues?.get(1)

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
