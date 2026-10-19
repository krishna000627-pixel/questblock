package com.krishna.questblock.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krishna.questblock.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Copies the picked picture into the app (max about 2000 px, turned upright, JPEG). Nothing is uploaded. */
internal fun savePoster(ctx: Context, uri: Uri): Boolean = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (maxOf(opts.outWidth, opts.outHeight) / sample > 2000) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return@runCatching false
    val orient = ctx.contentResolver.openInputStream(uri)?.use {
        runCatching { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
    } ?: 1
    val deg = when (orient) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
    val out = if (deg != 0f) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true) else bmp
    File(ctx.filesDir, "poster_user.jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    true
}.getOrDefault(false)

/** Hub -> Experiments: pick any picture from the gallery as the background of the whole app and the lock pages. */
@Composable
fun PosterPicker(prefs: Prefs) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasFile by remember { mutableStateOf(File(ctx.filesDir, "poster_user.jpg").exists()) }
    var on by remember { mutableStateOf(prefs.posterOn) }
    var tint by remember { mutableStateOf(prefs.posterTint) }
    var dim by remember { mutableFloatStateOf(prefs.posterDim.toFloat()) }
    var msg by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { savePoster(ctx, uri) }
            if (ok) {
                hasFile = true; on = true; prefs.posterOn = true; PosterOn = true; PosterStamp++
                msg = "Picture set"
            } else msg = "Could not read that picture"
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("\uD83D\uDDBC\uFE0F Poster background", color = ColorText, fontWeight = FontWeight.SemiBold)
        Text("Pick a picture from your gallery. It is copied into the app (nothing is uploaded) and drawn dimmed behind every screen and the lock pages. Uses the dark look while it is on.",
            style = MaterialTheme.typography.bodySmall, color = ColorMuted)
        Button(
            onClick = { pick.launch("image/*") },
            colors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (hasFile) "Change picture" else "Choose picture") }
        if (hasFile) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Show the poster", color = ColorText)
                Switch(checked = on, onCheckedChange = {
                    on = it; prefs.posterOn = it; PosterOn = it; PosterStamp++
                })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Tint with the accent colour", color = ColorText)
                Switch(checked = tint, onCheckedChange = { tint = it; prefs.posterTint = it; PosterStamp++ })
            }
            Text("Darkness ${dim.toInt()}%", style = MaterialTheme.typography.labelMedium, color = ColorMuted)
            Slider(
                value = dim, onValueChange = { dim = it },
                onValueChangeFinished = { prefs.posterDim = dim.toInt(); PosterStamp++ },
                valueRange = 20f..90f,
                colors = SliderDefaults.colors(thumbColor = ColorPrimary, activeTrackColor = ColorPrimary),
            )
        }
        msg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ColorMuted) }
    }
}
