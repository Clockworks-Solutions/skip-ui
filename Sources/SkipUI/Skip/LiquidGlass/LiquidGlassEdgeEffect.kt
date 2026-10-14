// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: the blurred strip glass chrome floats on at a screen edge, as iOS 26's soft scroll edge effect.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

// Start low and never fully replace the sharp content, so rows beside the chrome stay readable
private val bottomEdgeMask = Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.85f))
// Solid behind the status bar and title, half gone at the bar's bottom edge, measured from iOS 26
private val topEdgeMask = Brush.verticalGradient(0f to Color.Black, 0.6f to Color.Black.copy(alpha = 0.9f), 0.8f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent)

/**
 * The strip glass chrome floats on, blurring the content toward the screen's [edge] as iOS does, so the chrome sits on
 * the content rather than being cut out of it. Shared by the tab bar and the navigation bar, so both edges match.
 *
 * @param backdrop The recorded content beneath the strip.
 * @param edge [Alignment.Top] or [Alignment.Bottom]: the edge the blur thickens toward.
 * @param height The strip's height, read while laying out.
 * @param blurRadius How far the content beneath blurs.
 * @param wash The background the blurred content fades toward, as iOS fades content beneath the navigation bar: by 90%
 *   in a light theme and 60% in a dark one, measured from iOS 26, so dark content stays visible.
 * @param alpha The strip's opacity, read while drawing; at 0 the strip leaves the composition.
 */
@Composable
internal fun LiquidGlassEdgeEffect(backdrop: Backdrop, edge: Alignment.Vertical, height: () -> Dp, modifier: Modifier = Modifier, blurRadius: Dp = 4.dp, wash: Color = Color.Unspecified, alpha: () -> Float = { 1f }) {
    val isHidden by remember(alpha) { derivedStateOf { alpha() <= 0f } }
    if (isHidden) return
    val mask = if (edge == Alignment.Top) topEdgeMask else bottomEdgeMask
    val washColor = if (wash.isSpecified) wash.copy(alpha = if (wash.luminance() < 0.5f) 0.6f else 0.9f) else wash
    Box(
        modifier = modifier
            .animatedHeight(height)
            .graphicsLayer {
                this.alpha = alpha()
                // Its own layer, so the gradient masks the blurred copy rather than the screen
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
            }
            .drawBackdrop(backdrop = backdrop, shape = { RectangleShape }, effects = { blur(blurRadius.toPx()) }, onDrawSurface = { if (washColor.isSpecified) drawRect(washColor) })
    )
}

/** A fixed height read while laying out, so an animated height relayouts without recomposing. */
internal fun Modifier.animatedHeight(height: () -> Dp): Modifier = layout { measurable, constraints ->
    val heightPx = height().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minHeight = heightPx, maxHeight = heightPx))
    layout(placeable.width, heightPx) { placeable.place(0, 0) }
}
