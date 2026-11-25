package io.github.gcbond.openhc.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gcbond.openhc.BuildConfig
import io.github.gcbond.openhc.IdentityLoader
import io.github.gcbond.openhc.R
import io.github.gcbond.openhc.connection.ConnectionUiState
import io.github.gcbond.openhc.connection.Notice
import io.github.gcbond.openhc.connection.Phase
import io.github.gcbond.openhc.connection.Step
import io.github.gcbond.openhc.settings.WirelessPreferences
import io.github.gcbond.openhc.wireless.WirelessUiState

/** 首页上和有线连接有关的操作。 */
internal class HomeActions(
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onRetryNow: () -> Unit,
    val onGrantVpn: () -> Unit,
    val onResetTrustStore: () -> Unit,
)

/** 两栏时卡片的最高高度：车机上别铺满整屏，文字和按钮之间留白太多。 */
private val TWO_CARD_MAX_HEIGHT = 360.dp

/** 内容宽不到这个值就排单栏（上无线、下有线，整页滚动）。 */
private val TWO_COLUMN_MIN = 560.dp

/** 两栏的宽度比例：左右等宽，中间留一条小空。 */
private const val WIRELESS_WEIGHT = 1f
private const val WIRED_WEIGHT = 1f
private val CARD_GAP = 20.dp

/**
 * 首页：左上应用名、右上「设置」；左栏无线（二维码、连接码），右栏有线（状态、提示、配对时的连接码）；
 * 左下角版本和当前模式。缺身份参数、或车机 WiFi 要车主自己打开时，顶上一张提示卡片。
 * 任何一边在投屏时，那一栏里有「回到投屏画面」。
 * 窄屏排成一列：无线在上、有线在下，整页滚动。
 */
@Composable
internal fun HomeScreen(
    wired: ConnectionUiState,
    wireless: WirelessUiState,
    identity: IdentityLoader.Result,
    actions: HomeActions,
    wirelessActions: WirelessActions,
    onShowProjection: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { WirelessPreferences(context) }
    val identityLoaded = identity is IdentityLoader.Result.Loaded
    // 设置是在设置页里改的，回到首页时首页重新组合，这里每次读到的都是新值。
    val wirelessOff = prefs.wiredOnly && !wireless.active
    val wiredState = wired.withoutIdentityNotice()
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (Palette.isDark) {
                        listOf(Color(0xFF0C111B), Color(0xFF101A2B))
                    } else {
                        listOf(Color(0xFFF8FBFF), Color(0xFFE3EBF8))
                    },
                ),
            )
            .systemBarsPadding(),
    ) {
        val compact = maxHeight < Dimens.CompactHeight
        val side = if (maxWidth >= Dimens.WIDE_PAGE_MIN) Dimens.PageSide else Dimens.PageSideNarrow
        val twoColumn = maxWidth - side * 2 >= TWO_COLUMN_MIN
        CompositionLocalProvider(LocalCompact provides compact) {
            if (twoColumn) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = side),
                ) {
                    HomeTopBar(onOpenSettings, actions, wirelessActions)
                    IdentityHomeNotice(identity, Modifier.padding(top = 8.dp))
                    WifiRestoreCard(Modifier.padding(top = 8.dp))
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = TWO_CARD_MAX_HEIGHT),
                    ) {
                        WirelessCard(
                            wireless,
                            identityLoaded,
                            wirelessOff,
                            wirelessActions,
                            fillHeight = true,
                            modifier = Modifier
                                .weight(WIRELESS_WEIGHT)
                                .fillMaxHeight(),
                        )
                        Spacer(Modifier.width(CARD_GAP))
                        WiredCard(
                            wiredState,
                            identityLoaded,
                            actions,
                            onShowProjection,
                            Modifier
                                .weight(WIRED_WEIGHT)
                                .fillMaxHeight(),
                            fillHeight = true,
                        )
                    }
                    }
                    HomeFooter(prefs)
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = side),
                ) {
                    HomeTopBar(onOpenSettings, actions, wirelessActions)
                    IdentityHomeNotice(identity, Modifier.padding(top = 8.dp))
                    WifiRestoreCard(Modifier.padding(top = 8.dp))
                    WirelessCard(
                        wireless,
                        identityLoaded,
                        wirelessOff,
                        wirelessActions,
                        fillHeight = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                    WiredCard(
                        wiredState,
                        identityLoaded,
                        actions,
                        onShowProjection,
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        fillHeight = false,
                    )
                    HomeFooter(prefs, Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

/** 顶栏：左边应用名，右上「重选系统」「设置」（设置页的「返回」在同一个位置）。 */
@Composable
private fun HomeTopBar(
    onOpenSettings: () -> Unit,
    actions: HomeActions,
    wirelessActions: WirelessActions,
) {
    val compact = LocalCompact.current
    val context = LocalContext.current
    val reselect = @Composable { modifier: Modifier ->
        // 回到 AllCarPlay 的系统选择页，两个按钮同一排、同一套样式。
        TopBarButton(
            stringResource(R.string.action_reselect_system),
            {
                // 回选择页前先把连接断开：不这么办，蓝牙通道、音频和解码还挂在后台，
                // 换到另一个系统会同时占着车机的蓝牙音频。
                actions.onStop()
                wirelessActions.onStop()
                openSystemPicker(context)
            },
            modifier,
        )
    }
    val settings = @Composable { modifier: Modifier ->
        TopBarButton(stringResource(R.string.action_settings), onOpenSettings, modifier)
    }
    // 标题和两个按钮同一排：竖屏时按钮收一档 padding/字号，标题不会被挤成省略号，
    // 也不再单独占一行把下面的卡片顶矮。
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .padding(top = if (compact) 14.dp else 24.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 标题左边挂华为菊花标（HmCar 就是连华为手机）
            Image(
                painter = painterResource(R.drawable.ic_huawei_petal),
                contentDescription = null,
                modifier = Modifier
                    .height(if (compact) 30.dp else 40.dp)
                    .width(if (compact) 30.dp else 40.dp),
            )
            Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
            Text(
                stringResource(R.string.home_app_title),
                style = UiType.title(compact)
                    .copy(fontSize = if (compact) 24.sp else 40.sp)
                    .copy(
                        brush = Brush.horizontalGradient(
                            if (Palette.isDark) {
                                listOf(Color(0xFFD9ECFF), Color(0xFF8FC0FF), Color(0xFF3E7CFF))
                            } else {
                                listOf(Color(0xFF2E7BFF), Color(0xFF093F9E))
                            },
                        ),
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        reselect(Modifier.padding(top = if (compact) 8.dp else 12.dp, end = if (compact) 6.dp else 10.dp))
        settings(Modifier.padding(top = if (compact) 8.dp else 12.dp, end = if (compact) 12.dp else 16.dp))
    }
}

/** 打开 AllCarPlay 的系统选择页；没装 AllCarPlay 时提示一下，不崩。 */
private fun openSystemPicker(context: android.content.Context) {
    val intent = Intent().apply {
        setClassName(context.packageName, "com.xielinyan.allcarplay.MainActivity")
        // 同一个安装包里的选择页：回到它、把它顶到前面（用 NEW_TASK 在同一个任务里只会把旧界面留在栈里）。
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(context, R.string.reselect_need_allcarplay, Toast.LENGTH_SHORT).show()
    }
}

/** 卡片头：左边一条短色块 + 加粗标题，下面一条细线（和 CarPlay 页的分节标题同一路数）。 */
@Composable
private fun CardHeader(text: String) = CardSectionHeader(text)

/** 右栏「有线连接」：小标题、状态、提示，配对时一排连接码，上次结束的原因，操作按钮。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WiredCard(
    state: ConnectionUiState,
    identityLoaded: Boolean,
    actions: HomeActions,
    onShowProjection: () -> Unit,
    modifier: Modifier,
    fillHeight: Boolean,
) {
    val compact = LocalCompact.current
    // 要用户动手处理的状态（关 USB 调试、拔线重插、手机迟迟不响应）用提醒色。
    val warning = state.phase == Phase.USB_DEBUGGING_ON || state.phase == Phase.REPLUG_REQUIRED ||
        state.step == Step.PHONE_SWITCH_SLOW
    CardBox(modifier) {
        CardHeader(stringResource(R.string.home_wired_title))
        if (fillHeight) {
            // 和无线卡一样：说明在可滚动区里，按钮钉在卡片底部，两张卡的按钮就落在同一排。
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        WiredBody(state, compact, warning)
                    }
                }
                WiredButtons(
                    state,
                    identityLoaded,
                    actions,
                    onShowProjection,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                )
            }
        } else {
            WiredBody(state, compact, warning)
            WiredButtons(state, identityLoaded, actions, onShowProjection, Modifier.padding(top = 14.dp))
        }
    }
}

/** 有线卡里的说明部分：状态、提示、配对时的连接码、上次结束的原因。 */
@Composable
private fun WiredBody(
    state: ConnectionUiState,
    compact: Boolean,
    warning: Boolean,
) {
    if (state.phase != Phase.IDLE) {
        Text(
            phaseTitle(state.phase),
            style = UiType.status,
            color = if (warning) Palette.Warn else Palette.Text,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        phaseHint(state),
        style = if (state.phase == Phase.IDLE) UiType.message(compact) else UiType.hint,
        color = if (warning) Palette.Warn else if (state.phase == Phase.IDLE) Palette.Text else Palette.Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (state.phase == Phase.IDLE) 0.dp else 6.dp),
    )
    val pin = state.pinCode
    if (state.phase == Phase.PAIRING && pin != null) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
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
    state.notice?.let {
        Text(noticeText(it), style = UiType.hint, color = Palette.Warn, modifier = Modifier.padding(top = 6.dp))
    }
}

/** 有线卡底部的操作按钮（和无线卡同一排、同样居中）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WiredButtons(
    state: ConnectionUiState,
    identityLoaded: Boolean,
    actions: HomeActions,
    onShowProjection: () -> Unit,
    modifier: Modifier,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.phase == Phase.IDLE) {
            when (state.notice) {
                Notice.VpnConsentRequired ->
                    PillButton(stringResource(R.string.action_grant_vpn), actions.onGrantVpn)
                is Notice.TrustStoreCorrupt ->
                    PillButton(stringResource(R.string.action_reset_trust), actions.onResetTrustStore)
                else ->
                    PillButton(stringResource(R.string.action_start), actions.onStart, enabled = identityLoaded)
            }
        } else {
            if (state.phase == Phase.PROJECTING) {
                PillButton(stringResource(R.string.action_show_projection), onShowProjection)
            }
            if (state.phase == Phase.REPLUG_REQUIRED) {
                PillButton(stringResource(R.string.action_retry_now), actions.onRetryNow, primary = false)
            }
            PillButton(
                stringResource(R.string.action_stop),
                actions.onStop,
                primary = false,
                enabled = state.phase != Phase.STOPPING,
            )
        }
    }
}

/**
 * 左下角：版本、当前模式，以及车型编号。
 * 底边这一行的居中位置空着，留给以后的主按钮（如「退出」）。
 */
@Composable
private fun HomeFooter(
    prefs: WirelessPreferences,
    modifier: Modifier = Modifier,
) {
    val lines = mutableListOf(
        stringResource(R.string.home_version, BuildConfig.VERSION_NAME),
        stringResource(R.string.home_mode, modeSummary(prefs)),
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            lines.joinToString("  ·  "),
            style = UiType.footer(LocalCompact.current),
            color = Palette.Muted,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 当前模式一句话：「无线 + 有线」或「只用数据线」，后面接开着的自动连接选项。首页页脚和设置页底部共用。 */
@Composable
internal fun modeSummary(prefs: WirelessPreferences): String {
    if (prefs.wiredOnly) return stringResource(R.string.home_mode_wired_only)
    val parts = mutableListOf(stringResource(R.string.home_mode_both))
    if (prefs.connectOnBluetooth) parts += stringResource(R.string.home_mode_connect_on_bt)
    return parts.joinToString(" · ")
}
