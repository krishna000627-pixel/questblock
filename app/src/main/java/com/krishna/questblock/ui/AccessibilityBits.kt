package com.krishna.questblock.ui

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** v281: makes small tap targets at least 48 dp (the Android accessibility minimum) without changing how the icon inside looks. */
fun Modifier.minTouchTarget(size: Dp = 48.dp): Modifier =
    if (com.krishna.questblock.data.Flags.get("a11y_bits")) this.defaultMinSize(minWidth = size, minHeight = size) else this.size(28.dp)

/** v282: a spoken label for an icon, or null when the Accessibility extras flag is off. */
fun a11yText(label: String): String? = if (com.krishna.questblock.data.Flags.get("a11y_bits")) label else null

/** v281: one spoken label (and optional role) for a whole group, so TalkBack reads it as one item. */
fun Modifier.a11yLabel(label: String, role: Role? = null): Modifier = if (!com.krishna.questblock.data.Flags.get("a11y_bits")) this else this.semantics(mergeDescendants = true) {
    contentDescription = label
    if (role != null) this.role = role
}
