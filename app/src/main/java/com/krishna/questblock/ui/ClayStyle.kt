package com.krishna.questblock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * v230: the Claymorphism shop theme (theme_clay). Soft pastel colours (Theme.kt) plus puffy shapes: with the theme on,
 * Card (in Glass.kt) and Button (which shadow the material3 ones in the ui package, like Text does) get a big radius, a soft coloured
 * drop shadow and a light rim on the top left. Every other time they call straight through to material3.
 */
val ClayOn: Boolean get() = ShopTheme == "theme_clay" && !PosterOn && !TermuxOn

internal val CLAY_CARD = RoundedCornerShape(28.dp)
private val CLAY_BUTTON = RoundedCornerShape(22.dp)
private val ClayShadow = Color(0x887C6CA8)

internal fun Modifier.clay(shape: Shape, lift: Int): Modifier = this
    .shadow(lift.dp, shape, ambientColor = ClayShadow, spotColor = ClayShadow)
    .border(2.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0f), Color(0x227C6CA8))), shape)

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    if (ClayOn) androidx.compose.material3.Button(onClick, modifier.clay(CLAY_BUTTON, 6), enabled, CLAY_BUTTON, colors, ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp), null, contentPadding, interactionSource, content)
    else if (FxOn) androidx.compose.material3.Button(onClick, modifier.fxButtonFx(), enabled, fxButtonShape(), fxButtonColors(colors),
        ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp), fxButtonBorder(), contentPadding, interactionSource, content)
    else if (Fx2X.on) androidx.compose.material3.Button(onClick, modifier, enabled, fx2ButtonShape(), fx2ButtonColors(colors),
        ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp), fx2ButtonBorder(), contentPadding, interactionSource, content)
    else androidx.compose.material3.Button(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
}
