package com.krishna.questblock.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The real app whose logo a lock key stands for: "short:ig" is Instagram; YouTube (fun and study rows) and Shorts use the installed YouTube or a mod of it. Anything else is itself. */
fun iconPackageFor(ctx: Context, key: String): String {
    val yt = listOf("com.google.android.youtube", "app.revanced.android.youtube", "com.vanced.android.youtube")
    return when {
        key == "short:ig" -> "com.instagram.android"
        key == "short:yt" || key.startsWith("yt:") || key.startsWith("ytstudy:") ->
            yt.firstOrNull { runCatching { ctx.packageManager.getApplicationInfo(it, 0); true }.getOrDefault(false) } ?: yt[0]
        else -> key
    }
}

/** App icon from the installed package (first letter if it can't be loaded). */
@Composable
fun AppIcon(pkg: String, label: String, size: Dp = 38.dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        runCatching {
            val d = ctx.packageManager.getApplicationIcon(iconPackageFor(ctx, pkg))
            val b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 96, 96)
            d.draw(Canvas(b))
            b.asImageBitmap()
        }.getOrNull()
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
        } else {
            Text(label.firstOrNull()?.uppercase() ?: "?", color = ColorText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
