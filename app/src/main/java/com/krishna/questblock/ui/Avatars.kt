package com.krishna.questblock.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** v247: line style profile avatars, same grid as SvgIcons (24 x 24, stroke 2, round caps). Picked on the Profile page (tap the badge), shown in the badge on Home and Profile instead of the level number. */
object Avatars {
    class Av(val id: String, val label: String, val paths: List<String>)

    val ALL: List<Av> = listOf(
        Av("ninja", "Ninja", listOf("M12 4a8 8 0 1 0 0 16a8 8 0 1 0 0-16z", "M4.5 9.5h15", "M4.5 14h15", "M9 11.8h.01", "M15 11.8h.01", "M19.5 9.5l2.5 2.3-2.5 2.2")),
        Av("samurai", "Samurai", listOf("M5 12a7 7 0 0 1 14 0", "M4 12h16", "M7 6L5 3", "M17 6l2-3", "M7 12v3a5 5 0 0 0 10 0v-3", "M10 14.5h.01", "M14 14.5h.01")),
        Av("fox", "Fox", listOf("M3 4l4.5 3.5", "M21 4l-4.5 3.5", "M5 8c0 6 3 12 7 12s7-6 7-12c-2-1.5-4.5-2-7-2s-5 .5-7 2z", "M9 12h.01", "M15 12h.01", "M12 16.5h.01")),
        Av("oni", "Oni", listOf("M12 5a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M7 6L6 2", "M17 6l1-4", "M8 10l3 1.5", "M16 10l-3 1.5", "M8.5 16c2 1.5 5 1.5 7 0", "M9.5 16.5v2", "M14.5 16.5v2")),
        Av("wizard", "Wizard", listOf("M12 2l5 11H7z", "M4 14h16", "M8 14.5a4 4 0 0 0 8 0", "M8 16c1 4 3 5 4 5s3-1 4-5", "M12 7h.01")),
        Av("knight", "Knight", listOf("M6 20V10a6 6 0 0 1 12 0v10z", "M6 11h12", "M12 11v5", "M12 4c3-2 6-1 6 2")),
        Av("dragon", "Dragon", listOf("M4 4l3 4", "M20 4l-3 4", "M4 14c0-5 4-7 8-7s8 2 8 7c0 4-3 7-8 7s-8-3-8-7z", "M9 12h.01", "M15 12h.01", "M10 17h.01", "M14 17h.01")),
        Av("robot", "Robot", listOf("M12 3v3", "M12 2h.01", "M5 7h14a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V8a1 1 0 0 1 1-1z", "M9 12h.01", "M15 12h.01", "M9 16h6", "M2 11v4", "M22 11v4")),
        Av("cat", "Cat", listOf("M5 10V3l4 3.5", "M19 10V3l-4 3.5", "M12 6c-4.5 0-7 2-7 6s2.5 7 7 7 7-3 7-7-2.5-6-7-6z", "M9 12h.01", "M15 12h.01", "M12 15h.01", "M2 13h3", "M22 13h-3")),
        Av("owl", "Owl", listOf("M5 4l3 2h8l3-2v12a7 7 0 0 1-14 0z", "M9 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M15 10a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M11 16h2l-1 2z")),
        Av("skull", "Skull", listOf("M5 11a7 7 0 0 1 14 0c0 2.5-1.5 4-3 5v3H8v-3c-1.5-1-3-2.5-3-5z", "M9 11.5h.01", "M15 11.5h.01", "M12 14l-1 1.5h2z", "M10 19v-2", "M14 19v-2")),
        Av("panda", "Panda", listOf("M7 4a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M17 4a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M12 6c-5 0-8 3-8 7s3 7 8 7 8-3 8-7-3-7-8-7z", "M9 12.5h.01", "M15 12.5h.01", "M11 16h2")),
    )

    fun get(id: String): Av? = ALL.firstOrNull { it.id == id }

    private const val KEY = "avatar_id"
    private var loaded = false

    /** The picked avatar id, empty = show the level number as before. */
    var current by mutableStateOf("")
        private set

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        current = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getString(KEY, "") ?: ""
    }

    fun set(ctx: Context, id: String) {
        loaded = true
        ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).edit().putString(KEY, id).apply()
        current = id
    }
}

/** One avatar, filling the space it is given (same drawing as SvgGlyph). */
@Composable
fun AvatarGlyph(id: String, tint: Color, modifier: Modifier = Modifier) {
    val av = Avatars.get(id) ?: return
    val paths = remember(id) { av.paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.fillMaxSize()) {
        val k = this.size.width / 24f
        withTransform({ scale(k, k, Offset.Zero) }) {
            paths.forEach { p -> drawPath(p, tint, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
        }
    }
}

/** Profile > tap the badge. First tile = the level number (no avatar). */
@Composable
fun AvatarPickerDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Avatars.load(ctx)
    val cur = Avatars.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Choose avatar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val tiles: List<String> = listOf("") + Avatars.ALL.map { it.id }
                tiles.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { id ->
                            val on = id == cur || (id.isEmpty() && Avatars.get(cur) == null)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(54.dp).clip(CircleShape)
                                        .border(if (on) 2.dp else 1.dp, if (on) ColorPrimary else ColorMuted.copy(alpha = 0.4f), CircleShape)
                                        .clickable { Avatars.set(ctx, id) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (id.isEmpty()) Text("LV", color = ColorMuted, fontSize = 14.sp)
                                    else Box(Modifier.size(34.dp)) { AvatarGlyph(id, ColorText) }
                                }
                                Text(if (id.isEmpty()) "Level" else Avatars.get(id)?.label.orEmpty(), color = ColorMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
    )
}
