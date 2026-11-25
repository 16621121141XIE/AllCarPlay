package io.github.gcbond.openhc

import android.app.Application
import io.github.gcbond.openhc.connection.ConnectionController
import io.github.gcbond.openhc.connection.Phase
import io.github.gcbond.openhc.diagnostics.LogRecorder
import io.github.gcbond.openhc.startup.BootStart
import io.github.gcbond.openhc.startup.ProjectionSurfacer
import io.github.gcbond.openhc.wireless.LePairingNames
import io.github.gcbond.openhc.wireless.WirelessController

open class OpenHcApp : Application() {

    /** 有线投屏的总调度，进程内唯一。 */
    val connection: ConnectionController by lazy { ConnectionController(this) }

    private val wirelessLazy = lazy {
        WirelessController(
            this,
            wiredActive = { connection.state.value.active },
            // 有线在准备或等插线、车机上又没插着手机：只是在等插线，认识的手机蓝牙连上时让给无线。
            wiredWaitingForPhone = {
                connection.state.value.phase in WIRED_WAITING_PHASES && !BootStart.phonePlugged(this)
            },
            stopWiredWaiting = { connection.stop() },
            resumeWiredWaiting = { connection.start() },
        ).also { controller ->
            controller.addHostListener { surfacer.onStateChanged() }
        }
    }

    /** 无线投屏的总调度，进程内唯一；和有线互斥（有线在进行时不开始）。 */
    val wireless: WirelessController by wirelessLazy

    /** 无线调度已经建了才返回它：只想看一眼状态时不为此去建。 */
    internal val wirelessIfCreated: WirelessController? get() = if (wirelessLazy.isInitialized()) wireless else null

    /** 本应用有没有界面在前台。 */
    internal val uiVisibility = UiVisibility()

    /** 开机静默连接：手机连上时把界面带到前台（静默关着时什么都不做）。 */
    private val surfacer by lazy { ProjectionSurfacer(this) }

    /** HmCar 那套有线/无线调度只在 HmCar 界面打开时才起，别让 MiAuto / CarPlay 也跟着常驻跑。 */
    private var hostStackReady = false

    /**
     * 惰性初始化：USB/蓝牙调度、蓝牙名恢复、投屏 surfaced 监听。
     * 由 HmCar 的 MainActivity 在 onStart 之前调用，重复调用无副作用。
     */
    fun ensureHostStack() {
        if (hostStackReady) return
        hostStackReady = true
        connection.addHostListener {
            wireless.onWiredChanged()
            surfacer.onStateChanged()
        }
        LePairingNames.restoreIfNeeded(this, "打开投屏界面")
    }

    override fun onCreate() {
        super.onCreate()
        // 一启动就开始记自己的日志（「设置 → 诊断 → 导出诊断日志」用），越早越能把启动过程记全。
        LogRecorder.start(this)
        registerActivityLifecycleCallbacks(uiVisibility)
        // 重型调度见 ensureHostStack()：等 HmCar 界面真的打开再起。
    }

    private companion object {
        /** 有线还没接上手机的阶段：准备中、等插线。 */
        val WIRED_WAITING_PHASES = setOf(Phase.PREPARING, Phase.WAITING_FOR_PHONE)
    }
}
