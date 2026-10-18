package com.krishna.questblock.ui

import android.content.Context

/** v217: a launcher theme also decides the layout. Own themes and Material You keep the classic grid. */
internal fun qhStyleOf(t: QhTheme): String = when (t.id) {
    "niagara" -> "niagara"   // black, big text list at the bottom, no dock, text drawer with an A to Z scrubber
    "smart" -> "smart"       // category tabs in the drawer and four category shortcuts above the dock
    "lawn" -> "lawn"         // date glance on top, floating dock, search pill at the bottom
    else -> "classic"
}

internal val QH_CATS = listOf("All", "Social", "Media", "Games", "Tools", "Other")

private val qhCatCache = HashMap<String, String>()

/** Sorts an app into one of the Smart tabs by the same app kinds the glyph icons use. */
internal fun qhCategoryOf(ctx: Context, pkg: String, label: String): String = qhCatCache.getOrPut(pkg) {
    when (com.krishna.questblock.data.QhIcons.kindOf(ctx, pkg, label)) {
        "call", "chat", "mail" -> "Social"
        "music", "video", "camera" -> "Media"
        "game" -> "Games"
        "tool", "web", "map", "shop" -> "Tools"
        else -> "Other"
    }
}
