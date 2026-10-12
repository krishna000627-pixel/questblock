package com.krishna.questblock.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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

/** Drawn vector art (res/drawable/art_*.xml). Anything without art keeps its emoji. */
object Art {
    private val BADGES = mapOf(
        "beginner" to R.drawable.art_badge_beginner,
        "iron_will" to R.drawable.art_badge_iron_will,
        "deep_diver" to R.drawable.art_badge_deep_diver,
        "scholar" to R.drawable.art_badge_scholar,
        "machine" to R.drawable.art_badge_machine,
        "triple" to R.drawable.art_badge_triple,
        "ghost" to R.drawable.art_badge_ghost,
        "unbroken" to R.drawable.art_badge_unbroken,
        "streak7" to R.drawable.art_badge_streak7,
        "streak30" to R.drawable.art_badge_streak30,
        "hundred" to R.drawable.art_badge_hundred,
        "focus10" to R.drawable.art_badge_focus10,
        "coder" to R.drawable.art_badge_coder,
        "first_quest" to R.drawable.art_badge_first_quest,
        "quest10" to R.drawable.art_badge_quest10,
        "quest50" to R.drawable.art_badge_quest50,
        "streak3" to R.drawable.art_badge_streak3,
        "streak100" to R.drawable.art_badge_streak100,
        "focus_hour" to R.drawable.art_badge_focus_hour,
        "focus50" to R.drawable.art_badge_focus50,
        "bookworm" to R.drawable.art_badge_bookworm,
        "fullstack" to R.drawable.art_badge_fullstack,
        "clean_slate" to R.drawable.art_badge_clean_slate,
        "double_focus" to R.drawable.art_badge_double_focus,
        "balanced" to R.drawable.art_badge_balanced,
    )
    private val ACH = mapOf(
        "first_quest" to R.drawable.art_ach_first_quest,
        "streak_3" to R.drawable.art_ach_streak_3,
        "streak_7" to R.drawable.art_ach_streak_7,
        "streak_30" to R.drawable.art_ach_streak_30,
        "quests_10" to R.drawable.art_ach_quests_10,
        "quests_50" to R.drawable.art_ach_quests_50,
        "quests_100" to R.drawable.art_ach_quests_100,
        "coins_100" to R.drawable.art_ach_coins_100,
        "level_5" to R.drawable.art_ach_level_5,
        "level_10" to R.drawable.art_ach_level_10,
        "focus_1" to R.drawable.art_ach_focus_1,
        "timer_quest_1" to R.drawable.art_ach_timer_quest_1,
    )
    private val SHOP = mapOf(
        "xp_boost_1h" to R.drawable.art_shop_xp_boost_1h,
        "xp_boost_3h" to R.drawable.art_shop_xp_boost_3h,
        "coin_mult_1h" to R.drawable.art_shop_coin_mult_1h,
        "coin_mult_3h" to R.drawable.art_shop_coin_mult_3h,
        "streak_shield" to R.drawable.art_shop_streak_shield,
        "streak_repair" to R.drawable.art_shop_streak_repair,
        "streak_freeze" to R.drawable.art_shop_streak_freeze,
        "frame_fire" to R.drawable.art_shop_frame_fire,
        "frame_ice" to R.drawable.art_shop_frame_ice,
        "frame_gold" to R.drawable.art_shop_frame_gold,
        "frame_galaxy" to R.drawable.art_shop_frame_galaxy,
        "badge_star" to R.drawable.art_shop_badge_star,
        "badge_crown" to R.drawable.art_shop_badge_crown,
        "badge_sword" to R.drawable.art_shop_badge_sword,
        "badge_dragon" to R.drawable.art_shop_badge_dragon,
        "badge_beginner" to R.drawable.art_shop_badge_beginner,
    )
    private val RANKS = listOf(R.drawable.art_rank_0, R.drawable.art_rank_1, R.drawable.art_rank_2, R.drawable.art_rank_3, R.drawable.art_rank_4, R.drawable.art_rank_5)
    private val RANK_NAMES = listOf("NOVICE", "INITIATE", "DISCIPLINED", "ELITE", "MASTER", "ASCENDANT")

    fun badge(id: String): Int? = BADGES[id]
    fun ach(id: String): Int? = ACH[id]
    fun shop(id: String): Int? = SHOP[id]
    fun rank(name: String): Int? = RANK_NAMES.indexOf(name.uppercase()).takeIf { it >= 0 }?.let { RANKS[it] }
}

/** The art if there is one, else the emoji. */
@Composable
fun ArtIcon(res: Int?, emoji: String, size: Dp, modifier: Modifier = Modifier) {
    if (res != null) Image(painterResource(res), contentDescription = null, modifier = modifier.size(size))
    else Text(emoji, fontSize = (size.value * 0.62f).sp)
}

/** A badge on the Badges page: its drawn hexagon (grey and faded while locked), or the old hexagon with the emoji. */
@Composable
fun BadgeArt(id: String, emoji: String, unlocked: Boolean, size: Dp = 56.dp) {
    val res = Art.badge(id)
    if (res != null) {
        Image(
            painterResource(res), contentDescription = null,
            modifier = Modifier.size(size),
            alpha = if (unlocked) 1f else 0.38f,
            colorFilter = if (unlocked) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
        )
    } else {
        Box(
            Modifier.size(size).clip(HexShape)
                .background(if (unlocked) Brush.verticalGradient(listOf(ColorPrimary.copy(0.55f), ColorPrimary.copy(0.12f))) else Brush.verticalGradient(listOf(ColorSurfaceHigh, ColorSurfaceHigh)))
                .border(2.dp, if (unlocked) ColorPrimary else ColorDivider, HexShape),
            contentAlignment = Alignment.Center,
        ) { Text(if (unlocked) emoji else "\uD83D\uDD12", fontSize = 24.sp) }
    }
}
