package com.andrerinas.openheadunit.main

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.aap.AapService
import com.andrerinas.openheadunit.connection.IcocaBridge
import com.andrerinas.openheadunit.connection.IcocaLinkState
import com.andrerinas.openheadunit.icoca.IcocaProjectionActivity
import kotlinx.coroutines.launch

/**
 * The car's standby screen for MiAuto.
 *
 * Unlike DiAuto, whose standby screen drives an Android Auto connection, this screen is a status
 * board for the ICCOA CarLink (Xiaomi CarWith) link. The link advertises itself continuously, so
 * there is nothing to start: the screen simply mirrors the [IcocaBridge] phase and surfaces the
 * pairing code the moment the SDK issues one.
 *
 * That is why there are no "start pairing" / "pair with Bluetooth" buttons — the phone initiates
 * everything, exactly like HiCar and Honor CarLink. The only control that can appear is a
 * contextual "Open projection" button, and only while a phone is already connected, so that
 * backing out of the projection screen is not a dead end.
 */
class HomeFragment : Fragment() {
    private val app get() = App.provide(requireContext())

    private var lastTapAt = 0L

    /**
     * 按下即触发：不依赖 click 的 UP 判定，因此不会被手指微小位移判成滑动。
     * 400ms 内重复按下会被忽略，避免连点叠出多个 Activity。
     */
    private fun instantTap(target: View?, action: () -> Unit) {
        target ?: return
        target.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (now - lastTapAt >= 400L) {
                        lastTapAt = now
                        runCatching { action() }
                    }
                    true
                }
                else -> true
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        // 车机屏幕上 click 判定要等 ACTION_UP + 触摸容差，手指稍一滑就判成滑动，于是"点很多下才触发"。
        // 这里统一按 ACTION_DOWN 直接触发，和「重选系统」一样，按下即响应，不做任何动画。
        instantTap(view.findViewById(R.id.settings_button)) {
            runCatching {
                startActivity(Intent(requireContext(), SettingsActivity::class.java))
                requireActivity().overridePendingTransition(0, 0)
            }
        }
        // 车辆主屏幕：回系统桌面，不销毁连接。
        instantTap(view.findViewById(R.id.exit_button)) {
            val home = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { startActivity(home) }.onFailure {
                requireActivity().moveTaskToBack(true)
            }
        }
        // 「重选系统」：回 AllCarPlay 的系统选择页。
        instantTap(view.findViewById(R.id.reselect_button)) {
            val intent = Intent().setClassName(
                requireContext().packageName,
                "com.xielinyan.allcarplay.MainActivity",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            runCatching { startActivity(intent) }
            requireActivity().overridePendingTransition(0, 0)
        }
        instantTap(view.findViewById(R.id.wifi_button)) { openProjection() }
        view.findViewById<View>(R.id.refresh_pin_button)?.let { v ->
            instantTap(v) {
                val bridge = app.icocaBridge
                if (bridge == null) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "缺少 ICCOA CarLink SDK（icar-sdk.dex）",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    bridge.refreshPairing()
                    android.widget.Toast.makeText(
                        requireContext(),
                        "已重新广播配对码",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.icocaBridge?.state?.collect { render(it) }
            }
        }

        // Keep the AA service alive so the process (and with it the BLE advertisement) is not
        // reaped while the car sits on the standby screen.
        if (app.settings.autoStartOnBoot || app.settings.autoStartOnScreenOn) {
            ContextCompat.startForegroundService(requireContext(), Intent(requireContext(), AapService::class.java))
        }
    }

    /**
     * Paints one link state onto the standby screen.
     *
     * The pairing code is driven purely by the presence of a code, not by the phase: the SDK hands
     * one out a moment after the phone starts pairing and withdraws it once the session is up, so
     * keying the visibility off the value itself keeps the card from flickering through phases.
     */
    private fun render(state: IcocaBridge.State) {
        val view = view ?: return

        val status = when (state.phase) {
            IcocaLinkState.Phase.Idle,
            IcocaLinkState.Phase.Advertising -> getString(R.string.da_ready)

            IcocaLinkState.Phase.Pairing -> getString(R.string.da_connecting)
            IcocaLinkState.Phase.Connected -> getString(R.string.da_connected)

            // Say what actually failed. The SDK's error code is the only handle a user can report
            // back, and "waiting for your phone" over a failed attempt was actively misleading.
            IcocaLinkState.Phase.Error -> getString(R.string.da_link_failed, state.errorCode ?: 0)

            // A head unit whose ABI has no protocol libraries will never connect. Naming the CPU
            // here is the difference between "the app is broken" and "this box is 32-bit".
            IcocaLinkState.Phase.Unsupported ->
                getString(R.string.da_unsupported, Build.SUPPORTED_ABIS.joinToString("/"))
        }
        view.findViewById<TextView>(R.id.connection_status)?.text = status

        // Null-tolerant lookups: the layout has an orientation-specific variant, and a missing view
        // must never take the standby screen down while it is the only thing the driver can see.
        val code = state.pinCode
        val codeBlock = view.findViewById<View>(R.id.pairing_code_block)
        if (codeBlock != null) {
            if (code.isNullOrBlank()) {
                codeBlock.visibility = View.GONE
            } else {
                view.findViewById<TextView>(R.id.pairing_code)?.text = code
                codeBlock.visibility = View.VISIBLE
            }
        }

        val connected = state.phase == IcocaLinkState.Phase.Connected
        view.findViewById<View>(R.id.wifi_button)?.visibility =
            if (connected) View.VISIBLE else View.GONE

        // Refresh is pointless once a session is up, so it is the mirror image of "Open projection".
        // It is equally pointless on a box that cannot run the link at all.
        val unsupported = state.phase == IcocaLinkState.Phase.Unsupported
        view.findViewById<View>(R.id.refresh_pin_button)?.visibility =
            if (connected || unsupported) View.GONE else View.VISIBLE
    }

    private fun openProjection() {
        // Mark as opened so MainActivity's auto-open observer doesn't immediately re-launch the
        // projection the moment this fragment's host resumes.
        MainActivity.icocaProjectionOpened = true
        startActivity(IcocaProjectionActivity.intent(requireContext()))
    }

    companion object {
        // Retained for existing automation entry points; self projection has no standby control.
        var forceSelfModeLaunch = false
        fun resetAutoStart() { forceSelfModeLaunch = false }
    }
}
