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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        // 「重选系统」按钮要浮在别的 App 上面，第一次用之前请用户开一下权限。
        if (Settings.canDrawOverlays(this)) {
            showOverlay()
        } else {
            requestOverlayPermission()
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

    /** 顶部居中的「重选系统」小胶囊：点一下回到选择页。 */
    private fun showOverlay() {
        if (overlay != null) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val pill = TextView(this).apply {
            text = "重选系统"
            setTextColor(Color.WHITE)
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(48, 20, 48, 20)
            background = GradientDrawable().apply {
                cornerRadius = 120f
                setColor(Color.parseColor("#CC1D242E"))
                setStroke(2, Color.parseColor("#44FFFFFF"))
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
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 40
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

/** 选择页：左上角名字，中间三张等宽卡片，整块都可点。 */
@Composable
private fun SelectionScreen(onPick: (Int) -> Unit) {
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(ComposeColor(0xFF0B0F14), ComposeColor(0xFF111A24))
                )
            )
            .systemBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "AllCarPlay",
                color = ComposeColor.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 40.dp, top = 26.dp),
            )
            Text(
                "选择要连接的系统",
                color = ComposeColor(0xFF8B96A5),
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 42.dp, top = 6.dp),
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                ) {
                    SystemCard(
                        label = "鸿蒙OS",
                        icon = painterResource(R.drawable.ic_hmcar),
                        accent = ComposeColor(0xFF2D7DF6),
                        modifier = Modifier.weight(1f),
                    ) { onPick(0) }
                    SystemCard(
                        label = "苹果IOS",
                        icon = painterResource(R.drawable.ic_apple),
                        accent = ComposeColor(0xFFE8EBF0),
                        modifier = Modifier.weight(1f),
                    ) { onPick(1) }
                    SystemCard(
                        label = "Android",
                        icon = painterResource(R.drawable.ic_android),
                        accent = ComposeColor(0xFF8BC34A),
                        modifier = Modifier.weight(1f),
                    ) { onPick(2) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SystemCard(
    label: String,
    icon: Painter,
    accent: ComposeColor,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .background(ComposeColor(0xFF161D27))
            .border(1.dp, ComposeColor(0x22FFFFFF), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(ComposeColor(0x10FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = icon,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(96.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            label,
            color = ComposeColor.White,
            fontSize = 26.sp,
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
