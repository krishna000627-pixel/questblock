package com.krishna.questblock.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** v276: Flags > Theme app icon > Choose. Pick the home screen icon yourself, or let it follow the theme. Same layout as the Fonts page. */
@Composable
fun AppIconPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }
    var msg by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf(AppName.iconPick(ctx)) }
    val flagOn = com.krishna.questblock.data.Flags.get("themeicon")
    fun choose(id: String?) {
        if (TermuxMode.strictNow(ctx)) msg = "refused: a strict Focus session is running"
        else { AppName.setIconPick(ctx, id); pick = id; msg = "The home screen icon changes when you leave the app" }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = ColorText) }
                Column {
                    Text("Flags", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
                    Text("App icon", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ColorText)
                }
            }
        }
        item {
            Text(
                "Tap an icon to use it on the home screen. Follow the theme changes the icon with the equipped theme. " +
                    "The Termux theme always shows the Terminal icon.",
                style = MaterialTheme.typography.bodySmall, color = ColorMuted,
            )
        }
        if (msg.isNotEmpty()) item { Text(msg, style = MaterialTheme.typography.bodySmall, color = if (msg.startsWith("refused")) ColorDanger else ColorMuted) }
        item {
            val follow = AppName.followChoice()
            IconRow(
                res = follow.res, name = "Follow the theme",
                note = if (flagOn) "Now: ${follow.label}. Ninja, System, Paper and Gothic have their own colours"
                else "Theme app icon is off in Flags, so this shows the gold icon",
                selected = pick == null,
            ) { choose(null) }
        }
        AppName.CHOICES.forEach { c ->
            item(key = c.id) { IconRow(c.res, c.label, c.note, pick == c.id) { choose(c.id) } }
        }
    }
}

/** The real launcher icon, drawn the way the home screen draws it. */
@Composable
private fun IconRow(res: Int, name: String, note: String, selected: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val bmp = remember(res) {
        runCatching {
            val d = ctx.getDrawable(res)!!
            val b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 192, 192)
            d.draw(Canvas(b))
            b.asImageBitmap()
        }.getOrNull()
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorCard), shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(2.dp, ColorPrimary) else null,
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (bmp != null) Image(bmp, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ColorText)
                Text(note, style = MaterialTheme.typography.bodySmall, color = ColorMuted)
            }
            if (selected) Icon(Icons.Default.Check, "Selected", tint = ColorPrimary)
        }
    }
}
