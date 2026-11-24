package com.xielinyan.allcarplay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * AllCarPlay 的总入口：一张选择页，三张卡片（HmCar 鸿蒙 / CarPlay 苹果 / 安卓投屏）。
 * 点卡片就打开对应那个 App；打开后本页挂一个小小的「重选系统」悬浮按钮，点一下回到这一页。
 */
class MainActivity : ComponentActivity() {
    private var overlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SelectionScreen(onPick = ::openSystem)
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到选择页就把悬浮按钮收掉，免得盖在选择页上。
        hideOverlay()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    /** 三个目标 App：包名 + 入口 Activity（unknown 时按启动 Intent 找）。 */
    private data class Target(
        val packageName: String,
        val activityName: String?,
    )

    private fun openSystem(index: Int) {
        val target = when (index) {
            0 -> Target("io.github.gcbond.openhc", "io.github.gcbond.openhc.MainActivity")
            1 -> Target("xielinyan.carplay.com", "com.shilapi.xcertplay.DiPlayActivity")
            else -> Target("com.shilapi.xcertplay", null)
        }
        val intent = if (target.activityName != null) {
            Intent().setClassName(target.packageName, target.activityName)
        } else {
            packageManager.getLaunchIntentForPackage(target.packageName)
        }
        if (intent == null) {
            Toast.makeText(this, "没有安装这个 App：${target.packageName}", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // HmCar 与 CarPlay 都自带「重选系统」按钮，不再需要浮层；只有安卓投屏还没有自带按钮。
        val needOverlay = index == 2
        if (needOverlay) {
            if (Settings.canDrawOverlays(this)) {
                showOverlay(carPlayStyle = true)
            } else {
                requestOverlayPermission()
            }
        }
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "打不开：${it.message}", Toast.LENGTH_SHORT).show() }
    }

    /** 申请「显示在其他应用上层」：开一次以后就一直有「重选系统」按钮。 */
    private fun requestOverlayPermission() {
        Toast.makeText(this, "开启「显示在其他应用上层」后，连接页上会出现「重选系统」按钮", Toast.LENGTH_LONG).show()
        runCatching {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** 「重选系统」：CarPlay 首页右上角，和旁边「车机主页」同一套浅色圆角按钮样式。 */
    private fun showOverlay(carPlayStyle: Boolean) {
        if (overlay != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val pill = TextView(this).apply {
            text = "重选系统"
            setTextColor(Color.parseColor("#1B2430"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding((18 * density).toInt(), (12 * density).toInt(), (18 * density).toInt(), (12 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(Color.parseColor("#E9EEF7"))
                setStroke((1 * density).toInt(), Color.parseColor("#D3DBE8"))
            }
            setOnClickListener {
                hideOverlay()
                startActivity(
                    Intent(this@MainActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            }
        }
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            // 车机主页按钮在右上角，这个标签贴在它左边，垂直对齐。
            x = (190 * density).toInt()
            y = (24 * density).toInt()
        }
        runCatching { wm.addView(pill, params) }.onSuccess { overlay = pill }
    }

    private fun hideOverlay() {
        val view = overlay ?: return
        overlay = null
        runCatching {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
        }
    }
}

/** 选择页：左上角「Car」渐变字标，三张系统卡片；横屏三张并排，竖屏上下排；深浅色跟随系统。 */
@Composable
private fun SelectionScreen(onPick: (Int) -> Unit) {
    val dark = isSystemInDarkTheme()
    val pageBg = if (dark) {
        listOf(ComposeColor(0xFF0B0F14), ComposeColor(0xFF111A24))
    } else {
        listOf(ComposeColor(0xFFF5F8FD), ComposeColor(0xFFE7EEFA))
    }
    val cardBg = if (dark) ComposeColor(0xFF161D27) else ComposeColor.White
    val cardBorder = if (dark) ComposeColor(0x22FFFFFF) else ComposeColor(0x1A0B1B2E)
    val textColor = if (dark) ComposeColor.White else ComposeColor(0xFF14202E)
    val gradient = if (dark) {
        listOf(ComposeColor(0xFFFFFFFF), ComposeColor(0xFFCFE4FF), ComposeColor(0xFF3F82FF))
    } else {
        listOf(ComposeColor(0xFF9FCBFF), ComposeColor(0xFF3D82F5), ComposeColor(0xFF0F3FA6))
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(pageBg))
            .systemBarsPadding(),
    ) {
        val wide = maxWidth >= 640.dp
        Column(Modifier.fillMaxSize()) {
            Text(
                "Car",
                style = TextStyle(
                    fontSize = if (wide) 50.sp else 38.sp,
                    fontWeight = FontWeight.Bold,
                    brush = Brush.verticalGradient(gradient),
                ),
                modifier = Modifier.padding(
                    start = if (wide) 36.dp else 22.dp,
                    top = if (wide) 18.dp else 12.dp,
                ),
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (wide) 36.dp else 18.dp,
                        vertical = if (wide) 14.dp else 10.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (wide) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .fillMaxHeight(0.92f),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        SystemCard(
                            label = "鸿蒙OS",
                            icon = painterResource(R.drawable.ic_hmcar),
                            accent = ComposeColor(0xFF2D7DF6),
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textColor = textColor,
                            modifier = Modifier.weight(1f),
                        ) { onPick(0) }
                        SystemCard(
                            label = "苹果IOS",
                            icon = painterResource(R.drawable.ic_apple),
                            iconTint = textColor,
                            accent = ComposeColor(0xFF8A94A6),
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textColor = textColor,
                            modifier = Modifier.weight(1f),
                        ) { onPick(1) }
                        SystemCard(
                            label = "Android",
                            icon = painterResource(R.drawable.ic_android),
                            accent = ComposeColor(0xFF8BC34A),
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textColor = textColor,
                            iconSize = 120.dp,
                            modifier = Modifier.weight(1f),
                        ) { onPick(2) }
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SystemCardRow(
                            label = "鸿蒙OS",
                            icon = painterResource(R.drawable.ic_hmcar),
                            accent = ComposeColor(0xFF2D7DF6),
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textColor = textColor,
                            modifier = Modifier.fillMaxWidth().height(112.dp),
                        ) { onPick(0) }
                        SystemCardRow(
                            label = "苹果IOS",
                            icon = painterResource(R.drawable.ic_apple),
                            iconTint = textColor,
                            accent = ComposeColor(0xFF8A94A6),
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textColor = textColor,
                            modifier = Modifier.fillMaxWidth().height(112.dp),
                        ) { onPick(1) }
                        SystemCardRow(
                            label = "Android",
                            icon = painterResource(R.drawable.ic_android),
                            accent = ComposeColor(0xFF8BC34A),
                            cardBg = cardBg,
                            cardBorder = cardBorder,
                            textColor = textColor,
                            modifier = Modifier.fillMaxWidth().height(112.dp),
                        ) { onPick(2) }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SystemCard(
    label: String,
    icon: Painter,
    accent: ComposeColor,
    cardBg: ComposeColor,
    cardBorder: ComposeColor,
    textColor: ComposeColor,
    iconSize: Dp? = null,
    iconTint: ComposeColor? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 图标固定尺寸，卡片变矮时文字不会被裁掉。
        Box(
            Modifier.size(iconSize ?: 104.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = icon,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                colorFilter = iconTint?.let { ColorFilter.tint(it) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            label,
            color = textColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(36.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent.copy(alpha = 0.75f))
        )
    }
}

/** 竖屏用的横排卡片：左边图标，右边系统名，整卡可点。 */
@Composable
private fun SystemCardRow(
    label: String,
    icon: Painter,
    accent: ComposeColor,
    cardBg: ComposeColor,
    cardBorder: ComposeColor,
    textColor: ComposeColor,
    iconTint: ComposeColor? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = icon,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                colorFilter = iconTint?.let { ColorFilter.tint(it) },
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = textColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(30.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.75f))
            )
        }
    }
}
