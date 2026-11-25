package io.github.gcbond.openhc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// 界面的配色、字号和几个公共尺寸。车机上常年开着，固定用深色，不跟系统深浅色走。

/**
 * 配色：深色、浅色两套，由 [OpenHcTheme] 按 [AppThemeState.mode] 应用。
 * 值是 Compose state，切换外观时所有用到的地方一起重画。
 */
internal object Palette {
    /** 当前是不是深色（供状态栏等外部地方参考）。 */
    var isDark by mutableStateOf(true)

    /** 页面底色。 */
    var Background by mutableStateOf(Color(0xFF111822))

    /** 卡片、分组框。 */
    var Card by mutableStateOf(Color(0x26FFFFFF))
    var Text by mutableStateOf(Color.White)

    /** 次要文字：说明、页脚。 */
    var Muted by mutableStateOf(Color(0xFFB0B6C0))

    /** 小标题（分组名、卡片名）。 */
    var Accent by mutableStateOf(Color(0xFF8FB8FF))

    /** 要用户注意的：缺东西、出错、需要重开才生效。 */
    var Warn by mutableStateOf(Color(0xFFFFB74D))

    /** 开关打开、选项选中。 */
    var On by mutableStateOf(Color(0xFF2D7DF6))
    var SwitchOff by mutableStateOf(Color(0xFF4A505A))

    /** 顶栏按钮底色、行内小按钮底色，以及压在它上面的白底/黑字。 */
    var Button by mutableStateOf(Color(0xFF59606B))
    var ButtonText by mutableStateOf(Color.White)
    var SegmentBackground by mutableStateOf(Color(0x1AFFFFFF))
    var Divider by mutableStateOf(Color(0x1FFFFFFF))

    /** 连接码方框。 */
    var PinBox by mutableStateOf(Color(0xFF565D65))
    var PinText by mutableStateOf(Color(0xFFEBF4FE))

    /** 主按钮底色（浅色深蓝、深色浅蓝）与压在上面的字色。 */
    var AccentFill by mutableStateOf(Color(0xFF245A9C))
    var OnAccentFill by mutableStateOf(Color.White)

    /** 卡片、按钮的 1dp 描边。 */
    var Outline by mutableStateOf(Color(0xFFA7B5C7))

    /** 胶囊主按钮：从左到右的蓝色渐变。 */
    var PrimaryStart by mutableStateOf(Color(0xFF1590FA))
    var PrimaryEnd by mutableStateOf(Color(0xFF00ACEB))

    /** 胶囊次按钮。 */
    var Secondary by mutableStateOf(Color(0xFF365173))

    /** 弹出框、菜单的底色（比页面略亮）。 */
    var Raised by mutableStateOf(Color(0xFF1E2632))

    private data class Scheme(
        val background: Color,
        val card: Color,
        val text: Color,
        val muted: Color,
        val accent: Color,
        val warn: Color,
        val switchOff: Color,
        val button: Color,
        val buttonText: Color,
        val segmentBackground: Color,
        val divider: Color,
        val pinBox: Color,
        val pinText: Color,
        val secondary: Color,
        val raised: Color,
        val accentFill: Color,
        val onAccentFill: Color,
        val outline: Color,
    )

    private val dark = Scheme(
        background = Color(0xFF0C111B),
        card = Color(0xFF151E2C),
        text = Color(0xFFF1F5FC),
        muted = Color(0xFFA8B6CA),
        accent = Color(0xFFA6C8FF),
        warn = Color(0xFFFFB74D),
        switchOff = Color(0xFF2A384B),
        button = Color(0xFF1F2B3D),
        buttonText = Color(0xFFF1F5FC),
        segmentBackground = Color(0xFF1F2B3D),
        divider = Color(0xFF2A384B),
        pinBox = Color(0xFF243247),
        pinText = Color(0xFFF1F5FC),
        secondary = Color(0xFF1F2B3D),
        raised = Color(0xFF1A2434),
        accentFill = Color(0xFFA6C8FF),
        onAccentFill = Color(0xFF0C111B),
        outline = Color(0xFF2A384B),
    )

    private val light = Scheme(
        background = Color(0xFFE9EEF6),
        card = Color(0xFFF8FAFD),
        text = Color(0xFF1C1C1E),
        muted = Color(0xFF5A6474),
        accent = Color(0xFF245A9C),
        warn = Color(0xFFA85900),
        switchOff = Color(0xFFC3CAD5),
        button = Color(0xFFDCE9F1),
        buttonText = Color(0xFF1C1C1E),
        segmentBackground = Color(0xFFDCE9F1),
        divider = Color(0xFFA7B5C7),
        pinBox = Color(0xFFDCE9F1),
        pinText = Color(0xFF1C1C1E),
        secondary = Color(0xFFDCE9F1),
        raised = Color(0xFFFFFFFF),
        accentFill = Color(0xFF245A9C),
        onAccentFill = Color.White,
        outline = Color(0xFFA7B5C7),
    )

    /** 切到深色 / 浅色。已经有值在用时也安全，Compose 会合并。 */
    fun apply(dark: Boolean) {
        val s = if (dark) this.dark else light
        isDark = dark
        Background = s.background
        Card = s.card
        Text = s.text
        Muted = s.muted
        Accent = s.accent
        Warn = s.warn
        SwitchOff = s.switchOff
        Button = s.button
        ButtonText = s.buttonText
        SegmentBackground = s.segmentBackground
        Divider = s.divider
        PinBox = s.pinBox
        PinText = s.pinText
        Secondary = s.secondary
        Raised = s.raised
        AccentFill = s.accentFill
        OnAccentFill = s.onAccentFill
        Outline = s.outline
    }
}

/** 当前外观：0 跟随车机（系统白天/黑夜）、1 浅色、2 深色。设置页一改，整个界面立刻重画。 */
internal object AppThemeState {
    var mode by mutableStateOf(0)
}

/** 公共尺寸。 */
internal object Dimens {
    /** 页面左右留白；窄屏（宽不到 [WIDE_PAGE_MIN]）收成 [PageSideNarrow]。 */
    val PageSide = 48.dp
    val PageSideNarrow = 16.dp
    val WIDE_PAGE_MIN = 600.dp

    /** 低于这个高度算矮屏：字号、间距收一档。 */
    val CompactHeight = 560.dp
    val CardRadius = 20.dp
    val CardPadding = 18.dp
}

/** 当前是不是矮屏（见 [Dimens.CompactHeight]），由各页面的根布局按实际高度提供。 */
internal val LocalCompact = compositionLocalOf { false }

/** 各处文字样式。只定字号、粗细，颜色由调用处给；字距一律为 0（中文加字距反而难看）。 */
internal object UiType {
    /**
     * 和 CarPlay 页面同一套字形：系统 sans（Roboto / 车机自带的 sans-serif），
     * CarPlay 那边是用 Typeface.create("sans-serif" / "sans-serif-medium")，
     * 所以这里也走 FontFamily.SansSerif，不再用打包进来的 HarmonyOS Sans。
     */
    private val family = FontFamily.SansSerif
    private val base = TextStyle(letterSpacing = 0.sp, fontFamily = family)

    fun title(compact: Boolean) = base.copy(fontSize = if (compact) 26.sp else 40.sp, fontWeight = FontWeight.SemiBold)
    val topButton = base.copy(fontSize = 18.sp)
    val pill = base.copy(fontSize = 18.sp)
    fun action(compact: Boolean) = base.copy(fontSize = if (compact) 14.sp else 15.sp)

    // 首页卡片
    fun cardHeader(compact: Boolean) = base.copy(fontSize = if (compact) 12.sp else 14.sp)
    val cardHeading = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.em)

    /** 首页两张大卡的标题：比正文明显大一档。 */
    fun cardTitle(compact: Boolean) = base.copy(fontSize = if (compact) 18.sp else 22.sp, fontWeight = FontWeight.Bold)
    val status = base.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val hint = base.copy(fontSize = 13.sp, lineHeight = 1.45.em)
    fun message(compact: Boolean) = base.copy(fontSize = if (compact) 17.sp else 20.sp, lineHeight = 1.5.em)
    fun footer(compact: Boolean) = base.copy(fontSize = if (compact) 11.sp else 12.sp, lineHeight = 1.4.em)

    // 设置页
    fun groupTitle(compact: Boolean) =
        base.copy(fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.05.em)
    fun rowTitle(compact: Boolean) = base.copy(fontSize = if (compact) 15.sp else 17.sp)
    fun rowDesc(compact: Boolean) = base.copy(fontSize = if (compact) 12.sp else 13.sp, lineHeight = 1.45.em)
    fun rowNote(compact: Boolean) =
        base.copy(fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.Bold, lineHeight = 1.4.em)
    fun option(compact: Boolean) = base.copy(fontSize = if (compact) 14.sp else 15.sp)
    fun value(compact: Boolean) = base.copy(fontSize = if (compact) 14.sp else 15.sp, fontFamily = FontFamily.Monospace)
    fun chevron(compact: Boolean) = base.copy(fontSize = if (compact) 18.sp else 20.sp)
}

/**
 * 全应用的主题：深色 / 浅色 / 跟随车机,由 [AppThemeState] 决定。还在用的 Material 组件（输入框、弹框）从这里取色：
 * 主色是开关蓝，「错误」用提醒用的琥珀色，和自己画的控件一致。
 */
@Composable
internal fun OpenHcTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (AppThemeState.mode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    if (Palette.isDark != dark) Palette.apply(dark)
    val colors = if (dark) darkColorScheme(
        primary = Palette.On,
        onPrimary = Color.White,
        primaryContainer = Palette.On,
        onPrimaryContainer = Color.White,
        secondary = Palette.Accent,
        onSecondary = Palette.Background,
        tertiary = Palette.Accent,
        background = Palette.Background,
        onBackground = Palette.Text,
        surface = Palette.Background,
        onSurface = Palette.Text,
        surfaceVariant = Palette.Raised,
        onSurfaceVariant = Palette.Muted,
        surfaceContainerLowest = Palette.Background,
        surfaceContainerLow = Palette.Raised,
        surfaceContainer = Palette.Raised,
        surfaceContainerHigh = Palette.Raised,
        surfaceContainerHighest = Palette.Raised,
        outline = Palette.Button,
        outlineVariant = Palette.SwitchOff,
        error = Palette.Warn,
        onError = Palette.Background,
        errorContainer = Palette.Warn,
        onErrorContainer = Palette.Background,
    ) else lightColorScheme(
        primary = Palette.On,
        onPrimary = Color.White,
        primaryContainer = Palette.On,
        onPrimaryContainer = Color.White,
        secondary = Palette.Accent,
        onSecondary = Palette.Background,
        tertiary = Palette.Accent,
        background = Palette.Background,
        onBackground = Palette.Text,
        surface = Palette.Background,
        onSurface = Palette.Text,
        surfaceVariant = Palette.Raised,
        onSurfaceVariant = Palette.Muted,
        surfaceContainerLowest = Palette.Background,
        surfaceContainerLow = Palette.Raised,
        surfaceContainer = Palette.Raised,
        surfaceContainerHigh = Palette.Raised,
        surfaceContainerHighest = Palette.Raised,
        outline = Palette.Button,
        outlineVariant = Palette.SwitchOff,
        error = Palette.Warn,
        onError = Color.White,
        errorContainer = Palette.Warn,
        onErrorContainer = Palette.Background,
    )
    // 中文不加字距；其余沿用默认字号。
    val d = Typography()
    val typography = Typography(
        bodyLarge = d.bodyLarge.copy(letterSpacing = 0.sp),
        bodyMedium = d.bodyMedium.copy(letterSpacing = 0.sp),
        bodySmall = d.bodySmall.copy(letterSpacing = 0.sp),
        labelLarge = d.labelLarge.copy(letterSpacing = 0.sp),
        labelMedium = d.labelMedium.copy(letterSpacing = 0.sp),
        labelSmall = d.labelSmall.copy(letterSpacing = 0.sp),
        titleMedium = d.titleMedium.copy(letterSpacing = 0.sp),
        titleSmall = d.titleSmall.copy(letterSpacing = 0.sp),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
