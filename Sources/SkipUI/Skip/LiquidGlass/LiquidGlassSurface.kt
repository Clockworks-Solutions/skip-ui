// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: the glass surface shared by glass buttons, toolbar capsules and `glassEffect`.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui.liquidglass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

/**
 * Draws Liquid Glass behind the content: the blurred, refracted [LocalGlassBackdrop], a frost or tint, a rim lit by
 * the device's tilt, and a shadow. The frost follows the system theme.
 *
 * @param shape The glass shape; the lens needs a corner-based one.
 * @param tint Tints the glass, as `.glassProminent` and `Glass.tint(_:)` do.
 * @param surfaceColor A fill in place of the frost.
 * @param isInteractive Whether the glass glows and grows under a touch, as iOS interactive glass does. Touches are
 *   observed, not consumed.
 * @param style The tier's settings.
 */
@Composable
internal fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(percent = 50),
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    isInteractive: Boolean = false,
    style: LiquidGlassStyle = LiquidGlassStyle.current
): Modifier {
    val sampledBackdrop = LocalGlassBackdrop.current
    RequestGlassBackdrop()
    val frost = liquidGlassFrostColor()
    val rimHighlight = rememberLiquidGlassHighlight()
    val animationScope = rememberCoroutineScope()
    val press = remember(animationScope) { InteractiveHighlight(animationScope) }
    // The lens throws for any shape that isn't corner-based
    val hasLens = style.lens && shape is CornerBasedShape

    val pressModifier = if (!isInteractive) Modifier else Modifier.graphicsLayer {
        // Grows by up to 12dp, at most 15%
        val scale = 1f + (12f.dp.toPx() / size.width.coerceAtLeast(1f)).coerceAtMost(0.15f) * press.pressProgress
        scaleX = scale
        scaleY = scale
    }
    return this
        .then(pressModifier)
        .drawBackdrop(
            backdrop = sampledBackdrop ?: emptyBackdrop(),
            shape = { shape },
            effects = {
                vibrancy()
                blur(4f.dp.toPx())
                if (hasLens) lens(12f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = style.chromaticAberration)
            },
            highlight = { rimHighlight(1f) },
            shadow = { Shadow(alpha = 0.3f) },
            onDrawSurface = {
                if (tint.isSpecified) {
                    // Hue blend colors the backdrop, then a translucent fill sets the tint
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.75f))
                }
                val fill = if (surfaceColor.isSpecified) surfaceColor else if (!tint.isSpecified) frost else Color.Unspecified
                if (fill.isSpecified) drawRect(fill)
            }
        )
        .then(if (isInteractive) press.modifier.then(press.gestureModifier) else Modifier)
}

/** Whether this content is on a toolbar capsule, where a plain glass button draws its label alone. */
internal val LocalGlassToolbarCapsule = compositionLocalOf { false }

/** Whether this content is on a toolbar capsule; read by SkipUI's glass button. */
@Composable
fun isOnLiquidGlassToolbarCapsule(): Boolean = LocalGlassToolbarCapsule.current

/** Makes glass inside sample [backdrop], replacing any backdrop from further out. */
@Composable
internal fun WithGlassBackdrop(backdrop: Backdrop?, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalGlassBackdropRequest provides null, content = content)

/** Sizes a toolbar item that draws its own surface to the bar's height; never narrower than tall. */
internal fun Modifier.liquidGlassToolbarItemSize(height: Dp): Modifier = height(height).widthIn(min = height)

/** One capsule of a floating toolbar: never narrower than tall, so a single icon comes out round. */
@Composable
internal fun LiquidGlassToolbarGroup(
    height: Dp = liquidGlassToolbarHeight,
    contentPadding: Dp = liquidGlassToolbarContentPadding,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .height(height)
            .widthIn(min = height)
            .liquidGlassSurface()
            .padding(horizontal = contentPadding),
        horizontalArrangement = Arrangement.spacedBy(liquidGlassToolbarItemSpacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalGlassToolbarCapsule provides true, content = content)
    }
}
