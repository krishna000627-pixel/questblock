package com.krishna.questblock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krishna.questblock.R

/** v235: achievement, badge and rank pictures are SVG icons (names from SvgIcons.ICONS) in a thin ring, like the Shop cards. */
object Art {
    private val BADGES = mapOf(
        "beginner" to "sprout", "iron_will" to "shield", "deep_diver" to "target", "scholar" to "books",
        "machine" to "gear", "triple" to "trident", "ghost" to "ghost", "unbroken" to "gem",
        "streak7" to "flame", "streak30" to "volcano", "hundred" to "rosette", "focus10" to "hourglass",
        "coder" to "laptop", "first_quest" to "swords", "quest10" to "rocket", "quest50" to "medal",
        "streak3" to "flame", "streak100" to "crown", "focus_hour" to "stopwatch", "focus50" to "meditate",
        "bookworm" to "book", "fullstack" to "moon", "clean_slate" to "sparkles", "double_focus" to "repeat",
        "balanced" to "scale",
    )
    private val ACH = mapOf(
        "first_quest" to "feet", "streak_3" to "flame", "streak_7" to "swords", "streak_30" to "trophy",
        "quests_10" to "map", "quests_50" to "hero", "quests_100" to "crown", "coins_100" to "coin",
        "level_5" to "star", "level_10" to "sparkles", "focus_1" to "target", "timer_quest_1" to "stopwatch",
    )
    private val RANK_ICONS = listOf("sprout", "bolt", "shield", "medal", "crown", "galaxy")
    private val RANK_NAMES = listOf("NOVICE", "INITIATE", "DISCIPLINED", "ELITE", "MASTER", "ASCENDANT")

    fun badge(id: String): String? = BADGES[id]
    fun ach(id: String): String? = ACH[id]
    /** v233: shop items (power-ups, frames, badges) have no drawn art, their emoji is drawn as an SVG icon like the themes. */
    @Suppress("UNUSED_PARAMETER")
    fun shop(id: String): Int? = null
    fun rank(name: String): String? = RANK_NAMES.indexOf(name.uppercase()).takeIf { it >= 0 }?.let { RANK_ICONS[it] }
}

/** An SVG icon in a thin ring (accent colour; grey and faded when not active). No icon name: the emoji as text. */
@Composable
fun SvgArtIcon(name: String?, emoji: String, size: Dp, modifier: Modifier = Modifier, active: Boolean = true) {
    if (name == null) { Text(emoji, fontSize = (size.value * 0.62f).sp); return }
    val tint = if (active) ColorPrimary else ColorMuted.copy(alpha = 0.55f)
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (active) ColorPrimary.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.5.dp, if (active) ColorPrimary else ColorDivider, CircleShape),
        contentAlignment = Alignment.Center,
    ) { SvgGlyph(name, tint, Modifier.size(size * 0.54f)) }
}

/** Big muted icon for an empty page. */
@Composable
fun EmptyArt(name: String, size: Dp = 120.dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) { SvgGlyph(name, ColorMuted.copy(alpha = 0.7f), Modifier.size(size * 0.6f)) }
}

/** The art if there is one, else the emoji. */
@Composable
fun ArtIcon(res: Int?, emoji: String, size: Dp, modifier: Modifier = Modifier) {
    if (res != null) Image(painterResource(res), contentDescription = null, modifier = modifier.size(size))
    else Text(emoji, fontSize = (size.value * 0.62f).sp)
}

/** A badge on the Badges page: its SVG icon in a ring (grey and faded while locked), or the emoji ring when it has no icon. */
@Composable
fun BadgeArt(id: String, emoji: String, unlocked: Boolean, size: Dp = 56.dp) {
    val name = Art.badge(id)
    if (name != null) SvgArtIcon(name, emoji, size, active = unlocked)
    else Box(
        Modifier.size(size).clip(CircleShape)
            .border(1.5.dp, if (unlocked) ColorPrimary else ColorDivider, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(if (unlocked) emoji else "\uD83D\uDD12", fontSize = 24.sp) }
}
