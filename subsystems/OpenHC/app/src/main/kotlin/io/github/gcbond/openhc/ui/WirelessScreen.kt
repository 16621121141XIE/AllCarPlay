package io.github.gcbond.openhc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.gcbond.openhc.R
import io.github.gcbond.openhc.wireless.WirelessNotice
import io.github.gcbond.openhc.wireless.WirelessPhase
import io.github.gcbond.openhc.wireless.WirelessUiState

/** 首页左栏「无线连接」上的操作。 */
internal class WirelessActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onShowProjection: () -> Unit,
    /** 去本应用的设置页（填车机蓝牙地址、关「只用数据线连接」）。 */
    val onOpenSettings: () -> Unit,
    /** 去系统里本应用的设置页（权限被永久拒绝时）。 */
    val onOpenAppSettings: () -> Unit,
    val onEnableBluetooth: () -> Unit,
    val onResetTrustStore: () -> Unit,
)

/**
 * 首页左栏「无线连接」：顶上一行「无线连接 · 状态」，等扫码时中间是二维码和下面一排连接码，
 * 其余时候是一段居中的说明、要注意的事和操作按钮。
 *
 * [wirelessOff]：设置里开着「只用数据线连接」、也没有正在进行的无线连接，整张卡片写明无线已关闭。
 * [fillHeight]：两栏时卡片撑满高度，二维码按剩下的空间算大小；单栏（整页滚动）时按宽度算。
 */
@Composable
internal fun WirelessCard(
    state: WirelessUiState,
    identityLoaded: Boolean,
    wirelessOff: Boolean,
    actions: WirelessActions,
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val compact = LocalCompact.current
    val qr = state.qrCode.takeIf { state.phase == WirelessPhase.WAITING_FOR_SCAN }
    CardBox(modifier) {
        CardSectionHeader(stringResource(R.string.home_wireless_header))
        if (qr != null) {
            ScanContent(state, qr, actions, fillHeight)
        } else {
            StatusContent(state, identityLoaded, wirelessOff, actions, fillHeight)
        }
    }
}

/** 等扫码：二维码和六位连接码（横屏并排、竖屏上下），底下居中的「停止」。 */
@Composable
private fun ColumnScope.ScanContent(state: WirelessUiState, qr: String, actions: WirelessActions, fillHeight: Boolean) {
    val compact = LocalCompact.current
    if (state.addressMismatch) {
        Text(
            stringResource(R.string.wireless_address_synthetic),
            style = UiType.hint,
            color = Palette.Warn,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    BoxWithConstraints(
        (if (fillHeight) Modifier.weight(1f) else Modifier).fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val pin = state.pinCode
        val bounded = constraints.hasBoundedHeight
        val width = maxWidth.value
        val height = if (bounded) maxHeight.value else Float.POSITIVE_INFINITY
        // 横屏、卡片宽比高大：二维码在左、六位连接码在右（上下排会被二维码挤掉连接码）。
        val sideBySide = bounded && pin != null && width > height
        if (sideBySide) {
            val margin = QrPinLayout.QR_MARGIN * 2f
            // 连接码每行三位：方框按卡片高度和可用宽度取，二维码再让出连接码的宽度。
            val rows = (pin.length + 2) / 3
            val perRow = minOf(3, pin.length)
            val box = minOf((height - margin) / (rows + 0.6f), width * 0.30f / perRow * 1.0f)
                .coerceIn(18f, 88f)
            val gridWidth = perRow * box * 1.16f
            val gap = (box * 0.08f)
            val qrSide = minOf(height - margin, width - gridWidth - gap * 2 - 24f, QrPinLayout.MAX_QR).coerceAtLeast(1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                QrCodeImage(
                    qr,
                    Modifier
                        .size(qrSide.dp)
                        .background(Color.White)
                        .padding((qrSide * QR_EXTRA_QUIET).dp),
                )
                Spacer(Modifier.width(20.dp))
                PinGrid(
                    pin,
                    box.dp,
                    gap.dp,
                    description = stringResource(R.string.home_pin_description, pin),
                )
            }
        } else {
            val size = QrPinLayout.plan(width, height, pin?.length ?: 0, compact)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(QrPinLayout.QR_MARGIN.dp))
                // 码图自带 2 个模块的白边，卡片是深色的：外面再补约 2 个模块的白边，静区凑够 4 个模块，好扫。
                QrCodeImage(
                    qr,
                    Modifier
                        .size(size.qr.dp)
                        .background(Color.White)
                        .padding((size.qr * QR_EXTRA_QUIET).dp),
                )
                if (pin != null && size.pinBox > 0f) {
                    Spacer(Modifier.height(size.pinTop.dp))
                    PinBoxes(pin, size.pinBox.dp, size.pinGap.dp, description = stringResource(R.string.home_pin_description, pin))
                }
                Spacer(Modifier.height(QrPinLayout.QR_MARGIN.dp))
            }
        }
    }
    state.notice?.let {
        Text(wirelessNoticeText(it), style = UiType.hint, color = Palette.Warn, modifier = Modifier.padding(top = 4.dp))
    }
    // 底部只留「停止」：位置和「开始连接」一样居中，无线方式、广播情况这些说明都去掉了。
    Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        PillButton(
            stringResource(R.string.action_stop),
            actions.onStop,
            primary = false,
            enabled = state.phase != WirelessPhase.STOPPING,
        )
    }
}

/** 二维码外面补的白边占边长的比例（约 2 个模块）。 */
private const val QR_EXTRA_QUIET = 0.04f

/** 没有二维码时：居中一段说明，下面是连接码（手机连上蓝牙、还没认证完时还留着）、要注意的事和操作按钮。 */
@Composable
private fun ColumnScope.StatusContent(
    state: WirelessUiState,
    identityLoaded: Boolean,
    wirelessOff: Boolean,
    actions: WirelessActions,
    fillHeight: Boolean,
) {
    if (fillHeight) {
        // 说明文字在可滚动区里，按钮单独放在底部：矮屏（手机横屏、小尺寸车机）文字再多，按钮也不会被卡片裁掉。
        Column(Modifier.weight(1f).fillMaxWidth()) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    StatusBody(state, identityLoaded, wirelessOff, actions, includeButtons = false)
                }
            }
            WirelessButtons(
                state,
                identityLoaded,
                wirelessOff,
                actions,
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
            )
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusBody(state, identityLoaded, wirelessOff, actions)
        }
    }
}

@Composable
private fun StatusBody(
    state: WirelessUiState,
    identityLoaded: Boolean,
    wirelessOff: Boolean,
    actions: WirelessActions,
    includeButtons: Boolean = true,
) {
    val compact = LocalCompact.current
    val message = if (wirelessOff) stringResource(R.string.home_wireless_off_note) else wirelessHint(state)
    Text(
        message,
        style = UiType.message(compact),
        color = Palette.Text,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
    state.pinCode?.let { pin ->
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            val (box, gap) = QrPinLayout.pinRow(
                maxWidth.value,
                pin.length,
                if (compact) QrPinLayout.PIN_BOX_COMPACT else QrPinLayout.PIN_BOX,
            )
            PinBoxes(
                pin,
                box.dp,
                gap.dp,
                Modifier.fillMaxWidth(),
                description = stringResource(R.string.home_pin_description, pin),
            )
        }
    }
    // 「只用数据线连接」开着时，卡片本身已经写明了，不再重复这条提示。
    state.notice?.takeUnless { wirelessOff && it == WirelessNotice.WiredOnly }?.let {
        CenteredLine(wirelessNoticeText(it), Palette.Warn)
    }
    if (includeButtons) {
        // 撑满卡片宽度：不这么做，FlowRow 只按内容宽度排，按钮会贴在卡片左边，和「开始连接」不在一条中线上。
        WirelessButtons(state, identityLoaded, wirelessOff, actions, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

@Composable
private fun CenteredLine(text: String, color: Color) {
    Text(text, style = UiType.hint, color = color, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WirelessButtons(
    state: WirelessUiState,
    identityLoaded: Boolean,
    wirelessOff: Boolean,
    actions: WirelessActions,
    modifier: Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.phase != WirelessPhase.IDLE) {
            if (state.phase == WirelessPhase.PROJECTING) {
                PillButton(stringResource(R.string.action_show_projection), actions.onShowProjection)
            }
            PillButton(
                stringResource(R.string.action_stop),
                actions.onStop,
                primary = false,
                enabled = state.phase != WirelessPhase.STOPPING,
            )
            return@FlowRow
        }
        if (wirelessOff) {
            PillButton(stringResource(R.string.wireless_action_open_settings), actions.onOpenSettings)
            return@FlowRow
        }
        val start = stringResource(R.string.wireless_action_start)
        when (val notice = state.notice) {
            is WirelessNotice.PermissionsDenied -> if (notice.permanently) {
                PillButton(stringResource(R.string.wireless_action_app_settings), actions.onOpenAppSettings)
                PillButton(start, actions.onStart)
            } else {
                PillButton(stringResource(R.string.wireless_action_grant), actions.onStart)
            }
            WirelessNotice.BluetoothOff -> {
                PillButton(stringResource(R.string.wireless_action_enable_bt), actions.onEnableBluetooth)
                PillButton(start, actions.onStart)
            }
            WirelessNotice.BluetoothAddressMissing -> PillButton(start, actions.onStart, enabled = identityLoaded)
            WirelessNotice.WiredOnly ->
                PillButton(stringResource(R.string.wireless_action_open_settings), actions.onOpenSettings)
            is WirelessNotice.TrustStoreCorrupt ->
                PillButton(stringResource(R.string.action_reset_trust), actions.onResetTrustStore)
            else -> PillButton(start, actions.onStart, enabled = identityLoaded)
        }
    }
}
