package io.github.gcbond.openhc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import io.github.gcbond.openhc.R
import io.github.gcbond.openhc.connection.BluetoothAddressPolicy
import io.github.gcbond.openhc.connection.DeviceInfo
import io.github.gcbond.openhc.connection.BluetoothSource
import io.github.gcbond.openhc.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「连接方式」组里的车机蓝牙地址：一行「车机蓝牙地址 [当前地址 ›]」，说明里写来源；点它在下面展开手动填写（保存、清除）。
 * 没取到地址时一进来就是展开的。
 */
@Composable
internal fun BluetoothAddressSection(settings: AppSettings, systemBluetoothAddress: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var savedManual by remember { mutableStateOf(settings.manualBluetoothAddress) }
    var manualText by remember { mutableStateOf(savedManual ?: "") }
    var probing by remember { mutableStateOf(false) }
    var probeMessage by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<List<String>?>(null) }
    val current = BluetoothAddressPolicy.choose(systemBluetoothAddress, savedManual)
    var editing by rememberSaveable { mutableStateOf(current.source == BluetoothSource.UNAVAILABLE) }

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
            // 自动向车机要真实地址：查到了直接存成车机地址，车主不用自己抄。
            ActionButton(
                if (probing) stringResource(R.string.settings_bt_probe_running) else stringResource(R.string.settings_bt_probe),
                onClick = {
                    if (probing) return@ActionButton
                    probing = true
                    probeMessage = null
                    scope.launch {
                        val found = withContext(Dispatchers.IO) { DeviceInfo.probe(context, fresh = true) }
                        probing = false
                        if (found == null) {
                            probeMessage = context.getString(R.string.settings_bt_probe_failed)
                        } else {
                            settings.manualBluetoothAddress = found.address
                            savedManual = found.address
                            manualText = found.address
                            probeMessage = context.getString(R.string.settings_bt_probe_ok, found.address, found.source)
                        }
                    }
                },
                kind = ActionKind.NORMAL,
                enabled = !probing,
            )
            ActionButton(
                stringResource(R.string.settings_bt_diagnose),
                onClick = {
                    if (probing) return@ActionButton
                    probing = true
                    probeMessage = null
                    scope.launch {
                        details = withContext(Dispatchers.IO) { DeviceInfo.diagnose(context) }
                        probing = false
                    }
                },
                enabled = !probing,
            )
            ActionButton(
                stringResource(R.string.action_save),
                onClick = {
                    settings.manualBluetoothAddress = normalized
                    savedManual = normalized
                    manualText = normalized ?: ""
                },
                kind = ActionKind.PRIMARY,
                enabled = normalized != null && normalized != savedManual,
            )
            ActionButton(
                stringResource(R.string.action_clear),
                onClick = {
                    settings.manualBluetoothAddress = null
                    savedManual = null
                    manualText = ""
                    probeMessage = null
                },
                enabled = savedManual != null,
            )
        }
        probeMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (it == context.getString(R.string.settings_bt_probe_failed)) Palette.Warn else Palette.Text) }
        details?.let { lines ->
            Text(stringResource(R.string.settings_bt_diagnose_title), style = MaterialTheme.typography.labelLarge, color = Palette.Text)
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
            }
        }
    }
}
