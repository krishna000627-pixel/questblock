package com.krishna.questblock.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

// ════════════════════════════════════════════════════════════════════════════════════════════
//  JELLY POP (v201): the bubbly design language of the whole app.
//  Big round corners, pill buttons, circle icon bubbles, glossy highlights, soft coloured glow.
//  Every Card / Button / OutlinedButton / OutlinedTextField / AlertDialog in the app resolves to the versions
//  below (a function in the same package wins over the material3 star import), so each page wears the look.
// ════════════════════════════════════════════════════════════════════════════════════════════

/** The one big soft corner every card, sheet and dialog uses. */
val BubbleShape: Shape = RoundedCornerShape(28.dp)
/** For small things inside a card (inputs, tiles). */
val BubbleShapeSmall: Shape = RoundedCornerShape(20.dp)
/** Buttons, chips, tabs, bars. */
val PillShape: Shape = RoundedCornerShape(50)

private fun bubbleOr(shape: Shape): Shape = if (shape == CircleShape) shape else BubbleShape

/** Very soft outline in the accent colour. */
@Composable
fun glowBorder(): BorderStroke = BorderStroke(1.dp, ColorPrimary.copy(alpha = if (LightMode) 0.16f else 0.14f))

/** A soft coloured glow under a bubble (skipped over a poster picture, where cards are see-through). */
@Composable
private fun Modifier.bubbleLift(shape: Shape): Modifier {
    if (PosterOn) return this
    val c = ColorPrimary
    return this.shadow(
        elevation = if (LightMode) 8.dp else 6.dp, shape = shape, clip = false,
        ambientColor = c.copy(alpha = if (LightMode) 0.18f else 0.30f),
        spotColor = c.copy(alpha = if (LightMode) 0.22f else 0.38f),
    )
}

/** A faint white shine over the top of a bubble, like light on jelly (dark looks only; on light it would vanish). */
private fun Modifier.bubbleGloss(shape: Shape): Modifier = this.clip(shape).drawWithContent {
    drawContent()
    if (!LightMode) {
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.07f), 0.55f to Color.Transparent,
                startY = 0f, endY = size.height,
            ),
        )
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = BubbleShape,
    colors: CardColors = CardDefaults.cardColors(containerColor = ColorCard),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = glowBorder(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = bubbleOr(shape)
    androidx.compose.material3.Card(
        modifier = modifier.bubbleLift(s).bubbleGloss(s), shape = s, colors = colors,
        elevation = elevation, border = border, content = content,
    )
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = BubbleShape,
    colors: CardColors = CardDefaults.cardColors(containerColor = ColorCard),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = glowBorder(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = bubbleOr(shape)
    androidx.compose.material3.Card(
        onClick = onClick, modifier = modifier.bubbleLift(s).bubbleGloss(s), enabled = enabled, shape = s,
        colors = colors, elevation = elevation, border = border, content = content,
    )
}

/** Pill button with a soft lift. The shape you pass is ignored: every button is a pill. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = PillShape,
    colors: ButtonColors = ButtonDefaults.buttonColors(containerColor = ColorPrimary, contentColor = ColorOnPrimary),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.Button(
        onClick = onClick, modifier = modifier, enabled = enabled, shape = PillShape, colors = colors,
        elevation = elevation, border = border, contentPadding = contentPadding, content = content,
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = PillShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = ColorPrimary),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(1.5.dp, ColorPrimary.copy(alpha = 0.55f)),
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 11.dp),
    content: @Composable RowScope.() -> Unit,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick, modifier = modifier, enabled = enabled, shape = PillShape, colors = colors,
        elevation = elevation, border = border, contentPadding = contentPadding, content = content,
    )
}

/** Soft rounded input. The shape you pass is ignored. */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    shape: Shape = BubbleShapeSmall,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, readOnly = readOnly,
        textStyle = textStyle, label = label, placeholder = placeholder, leadingIcon = leadingIcon,
        trailingIcon = trailingIcon, prefix = prefix, suffix = suffix, supportingText = supportingText,
        isError = isError, visualTransformation = visualTransformation, keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions, singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        shape = BubbleShapeSmall, colors = colors,
    )
}

/** Dialogs are one big bubble. */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(32.dp),
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier,
        dismissButton = dismissButton, icon = icon, title = title, text = text,
        shape = RoundedCornerShape(32.dp), containerColor = containerColor, iconContentColor = iconContentColor,
        titleContentColor = titleContentColor, textContentColor = textContentColor,
        tonalElevation = tonalElevation, properties = properties,
    )
}

/** Badge shape (level badge, avatar, welcome pages). */
val HexShape: Shape = CircleShape

/** The default profile picture (res/drawable-nodpi/avatar_default.jpg), filling whatever box it is put in (the box does the circle clipping). */
@Composable
fun AvatarFill() {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(com.krishna.questblock.R.drawable.avatar_default),
        contentDescription = null,
        // the picture is itself a circle with a pale rim and the face sits a little up and right: zoom in so the art fills our circle, and centre the face
        modifier = Modifier.fillMaxSize().graphicsLayer {
            scaleX = 1.2f; scaleY = 1.2f
            translationX = size.width * -0.03f; translationY = size.height * 0.06f
        },
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
    )
}

/** A round bubble with an emoji: the icon of a quest, item or setting row. A little glint sits on its top-left, like a soap bubble. */
@Composable
fun IconTile(emoji: String, color: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(Brush.radialGradient(listOf(color.copy(alpha = 0.60f), color.copy(alpha = 0.24f))))
            .border(1.dp, color.copy(alpha = 0.55f), CircleShape)
            .drawWithContent {
                drawContent()
                drawCircle(Color.White.copy(alpha = 0.38f), radius = this.size.minDimension * 0.09f,
                    center = Offset(this.size.width * 0.30f, this.size.height * 0.25f))
            },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size.value * 0.46f).sp) }
}

/** Row of pill tabs: the picked one is a filled lime bubble with a soft glow. */
@Composable
fun PillTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .then(if (sel) Modifier.shadow(6.dp, PillShape, clip = false, ambientColor = ColorPrimary.copy(0.4f), spotColor = ColorPrimary.copy(0.5f)) else Modifier)
                    .clip(PillShape)
                    .background(if (sel) ColorPrimary else ColorSurfaceHigh)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, maxLines = 1, style = MaterialTheme.typography.labelLarge,
                    color = if (sel) ColorOnPrimary else ColorText,
                    fontWeight = if (sel) FontWeight.Black else FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Small stat bubble: emoji in a round bubble, big value, small label. */
@Composable
fun StatTile(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier, shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = ColorSurface),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(ColorPrimary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 17.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = ColorText, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = ColorMuted, maxLines = 1)
        }
    }
}

/** Fat jelly progress bar with a shine line. [fraction] 0..1. */
@Composable
fun GlowBar(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val h = height.coerceAtLeast(12.dp)
    Box(modifier.fillMaxWidth().height(h).clip(CircleShape).background(ColorSurfaceHigh)) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(ColorPrimary, ColorAccent)))
                .drawWithContent {
                    drawContent()
                    val hh = this.size.height
                    drawRoundRect(
                        Color.White.copy(alpha = 0.32f),
                        topLeft = Offset(hh * 0.45f, hh * 0.17f),
                        size = Size((this.size.width - hh * 0.9f).coerceAtLeast(0f), hh * 0.22f),
                        cornerRadius = CornerRadius(hh * 0.11f, hh * 0.11f),
                    )
                },
        )
    }
}

/** Screen title with a round icon bubble in front. */
@Composable
fun TitleWithIcon(emoji: String, title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(emoji, ColorPrimary, 40.dp)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = ColorText)
    }
}

/** Top bar of the sub-pages: a round back bubble and a big bold title. [actions] sit on the right. */
@Composable
fun BubbleTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(ColorSurfaceHigh).clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ColorText) }
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black,
            color = ColorText, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        actions()
    }
}

/** Small tinted pill with a short label: category, coins, due time. */
@Composable
fun BubbleChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(PillShape).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold, maxLines = 1) }
}

/** Section title with an optional small pill on the right. */
@Composable
fun BubbleSection(title: String, modifier: Modifier = Modifier, trailing: String? = null, trailingColor: Color = ColorPrimary) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = ColorText)
        if (trailing != null) BubbleChip(trailing, trailingColor)
    }
}

/** A ring that fills clockwise from the top; [content] sits in the middle. */
@Composable
fun BubbleRing(fraction: Float, size: Dp, stroke: Dp, color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val track = ColorSurfaceHigh
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val d = this.size.minDimension - w
            val tl = Offset(w / 2f, w / 2f)
            drawArc(track, 0f, 360f, false, topLeft = tl, size = Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft = tl, size = Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** The big round "done" button of a quest row. */
@Composable
fun CheckBubble(onClick: () -> Unit, modifier: Modifier = Modifier, done: Boolean = false, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (done) ColorPrimary else ColorPrimary.copy(alpha = 0.14f))
            .border(2.dp, ColorPrimary, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Text("✓", color = if (done) ColorOnPrimary else ColorPrimary, fontWeight = FontWeight.Black, fontSize = (size.value * 0.45f).sp) }
}

/** Soft light blobs drawn over the page background: lime top-right, mint left, lilac bottom (not over a poster picture). */
fun Modifier.bubbleBackdrop(): Modifier = this.drawWithContent {
    drawContent()
    if (!PosterOn) {
        val a = if (LightMode) 0.10f else 0.12f
        drawCircle(Brush.radialGradient(listOf(ColorPrimary.copy(alpha = a), Color.Transparent), center = Offset(size.width * 0.95f, size.height * 0.04f), radius = size.width * 0.75f),
            radius = size.width * 0.75f, center = Offset(size.width * 0.95f, size.height * 0.04f))
        drawCircle(Brush.radialGradient(listOf(ColorAccent.copy(alpha = a * 0.7f), Color.Transparent), center = Offset(size.width * 0.02f, size.height * 0.45f), radius = size.width * 0.6f),
            radius = size.width * 0.6f, center = Offset(size.width * 0.02f, size.height * 0.45f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFFB69CFF).copy(alpha = a * 0.6f), Color.Transparent), center = Offset(size.width * 0.85f, size.height * 0.92f), radius = size.width * 0.65f),
            radius = size.width * 0.65f, center = Offset(size.width * 0.85f, size.height * 0.92f))
    }
}
