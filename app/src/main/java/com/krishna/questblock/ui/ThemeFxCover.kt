package com.krishna.questblock.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

/**
 * v269: the short cover shown when you leave a theme's Try it page. Before, the list under it was drawn once in the previewed theme
 * and only then in your real one (a flash). Now the real theme is put back in the same step that closes the Try it page
 * ([restoreRealTheme]), and this plain page, drawn with the background of the theme you really use, covers the switch for a moment.
 * It follows the real theme, so it is different for every theme without any per-theme code.
 */
@Composable
fun ThemeSwitchCover(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(COVER_MS); onDone() }
    Box(Modifier.fillMaxSize().background(ColorBg))
}

private const val COVER_MS = 140L

/** Puts the saved theme back at once (in memory only; the preview never saved anything). Call it in the same click that closes Try it. */
internal fun restoreRealTheme(ctx: Context) {
    val saved = ctx.getSharedPreferences("questblock", Context.MODE_PRIVATE).getString("shop_theme", null) ?: "default"
    if (ShopTheme != saved) ShopTheme = saved
}
