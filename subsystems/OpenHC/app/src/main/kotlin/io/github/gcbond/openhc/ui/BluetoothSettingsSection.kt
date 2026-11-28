package io.github.gcbond.openhc.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.gcbond.openhc.R
import io.github.gcbond.openhc.connection.BluetoothAddressPolicy
import io.github.gcbond.openhc.connection.BluetoothSource
import io.github.gcbond.openhc.settings.AppSettings
import io.github.gcbond.openhc.wireless.WirelessPermissions

/** 扫描列出来的一台设备：屏幕上显示名字，点它就把地址填进配置。 */
private data class NearbyHit(val name: String, val address: String)

/**
 * 「连接方式」组里的车机蓝牙地址：一行「车机蓝牙地址 [当前地址 ›]」，说明里写来源；点它在下面展开。
 *
 * 展开后有两条路：手动填写，或者点「扫描车机蓝牙」——安卓应用读不到自己设备的蓝牙地址（系统固定返回
 * 02:00:00:00:00:00），但**读对端设备**是允许的，所以用这台设备的蓝牙去扫附近，扫到车机那条点一下，
 * 地址就填进来并保存。
 */
@Composable
internal fun BluetoothAddressSection(settings: AppSettings, systemBluetoothAddress: String?) {
    val context = LocalContext.current
    var savedManual by remember { mutableStateOf(settings.manualBluetoothAddress) }
    var manualText by remember { mutableStateOf(savedManual ?: "") }
    var scanning by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf(listOf<NearbyHit>()) }
    var message by remember { mutableStateOf<String?>(null) }
    val current = BluetoothAddressPolicy.choose(systemBluetoothAddress, savedManual)
    var editing by rememberSaveable { mutableStateOf(current.source == BluetoothSource.UNAVAILABLE) }

    val adapter = remember {
        runCatching {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        }.getOrNull()
    }

    fun beginScan() {
        val a = adapter
        if (a == null) {
            message = context.getString(R.string.settings_bt_scan_no_adapter)
            return
        }
        if (!a.isEnabled) {
            message = context.getString(R.string.settings_bt_scan_blue_off)
            return
        }
        hits = emptyList()
        message = null
        runCatching { a.cancelDiscovery() }
        val started = runCatching { a.startDiscovery() }.getOrDefault(false)
        if (!started) {
            scanning = false
            message = context.getString(R.string.settings_bt_scan_failed)
        } else {
            scanning = true
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) beginScan() else message = context.getString(R.string.settings_bt_scan_permission)
    }

    fun fill(hit: NearbyHit) {
        settings.manualBluetoothAddress = hit.address
        savedManual = hit.address
        manualText = hit.address
        message = context.getString(R.string.settings_bt_scan_filled, hit.address)
        scanning = false
        runCatching { adapter?.cancelDiscovery() }
    }

    // 扫描期间挂广播接收器，结束时收掉；离开设置页（或再点一次停止）时也会自动注销。
    DisposableEffect(scanning) {
        if (!scanning) return@DisposableEffect onDispose {}
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        @Suppress("DEPRECATION")
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                            ?: return
                        val address = runCatching { device.address }.getOrNull() ?: return
                        val name = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.settings_bt_unnamed)
                        hits = (hits + NearbyHit(name, address)).distinctBy { it.address }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        scanning = false
                        if (hits.isEmpty()) message = context.getString(R.string.settings_bt_scan_empty)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { adapter?.cancelDiscovery() }
        }
    }

    val address = current.address
    ValueRow(
        title = stringResource(R.string.settings_bt),
        value = address ?: stringResource(R.string.settings_bt_value_missing),
        description = when (current.source) {
            BluetoothSource.SYSTEM_PROPERTY -> stringResource(R.string.settings_bt_current_property, address ?: "")
            BluetoothSource.MANUAL -> stringResource(R.string.settings_bt_current_manual, address ?: "")
            BluetoothSource.UNAVAILABLE -> stringResource(R.string.settings_bt_current_unavailable)
        } + "\n" + stringResource(R.string.settings_bt_hint),
        valueColor = if (address == null) Palette.Warn else Palette.Text,
        monospace = address != null,
    ) { editing = !editing }
    if (!editing) return

    GroupRow(spacing = 8.dp) {
        val normalized = BluetoothAddressPolicy.normalize(manualText)
        val invalid = manualText.isNotBlank() && normalized == null
        OutlinedTextField(
            value = manualText,
            onValueChange = { manualText = it },
            label = { Text(stringResource(R.string.settings_bt_manual_label)) },
            isError = invalid,
            supportingText = if (invalid) {
                { Text(stringResource(R.string.settings_bt_invalid)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(
                if (scanning) stringResource(R.string.settings_bt_scanning) else stringResource(R.string.settings_bt_scan),
                onClick = {
                    if (scanning) {
                        scanning = false
                        runCatching { adapter?.cancelDiscovery() }
                        return@ActionButton
                    }
                    if (adapter == null) {
                        message = context.getString(R.string.settings_bt_scan_no_adapter)
                        return@ActionButton
                    }
                    if (!adapter.isEnabled) {
                        message = context.getString(R.string.settings_bt_scan_blue_off)
                        return@ActionButton
                    }
                    val missing = scanPermissions().filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) {
                        beginScan()
                    } else {
                        runCatching { permissions.launch(missing.toTypedArray()) }
                    }
                },
                kind = ActionKind.PRIMARY,
            )
            ActionButton(
                stringResource(R.string.action_save),
                onClick = {
                    settings.manualBluetoothAddress = normalized
                    savedManual = normalized
                    manualText = normalized ?: ""
                    message = null
                },
                enabled = normalized != null && normalized != savedManual,
            )
            ActionButton(
                stringResource(R.string.action_clear),
                onClick = {
                    settings.manualBluetoothAddress = null
                    savedManual = null
                    manualText = ""
                    message = null
                },
                enabled = savedManual != null,
            )
        }
        Text(
            stringResource(R.string.settings_bt_scan_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Muted,
        )
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Warn)
        }
        if (hits.isNotEmpty()) {
            Text(
                stringResource(R.string.settings_bt_scan_list),
                style = MaterialTheme.typography.labelLarge,
                color = Palette.Text,
            )
            hits.forEach { hit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { fill(hit) }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(hit.name, style = MaterialTheme.typography.bodyMedium, color = Palette.Text)
                    Text(
                        hit.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.Accent,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

/** 扫描要的运行时权限：安卓 12 起是「附近的设备」里的搜索权限（读名字还要连接权限），更早是精确位置。 */
private fun scanPermissions(): List<String> = if (Build.VERSION.SDK_INT >= 31) {
    listOf("android.permission.BLUETOOTH_SCAN", WirelessPermissions.BLUETOOTH_CONNECT)
} else {
    listOf(WirelessPermissions.FINE_LOCATION)
}
