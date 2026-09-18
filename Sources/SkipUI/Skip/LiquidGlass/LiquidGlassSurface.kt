// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: the glass surface shared by glass buttons, the navigation title pill, and `glassEffect`.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui.liquidglass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp

import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/**
 * Draws Liquid Glass behind the content: a blurred, lens-refracted backdrop with a frost or tint, a specular highlight,
 * and a shadow.
 *
 * Samples [LocalGlassBackdrop] when a presentation provides one, so the glass shows the content behind it. Otherwise
 * falls back to a local backdrop, which still looks like glass.
 *
 * When [isInteractive], the glass reacts to touch like iOS interactive glass: a glow follows the touch, and the glass
 * and its content grow with a spring, by up to 12dp in width and at most 15%. Touches are observed without being
 * consumed, so clickable content and enclosing gestures still work.
 *
 * @param shape The glass shape. Defaults to a capsule.
 * @param tint When specified, tints the glass with this color, as in `.glassProminent` and `Glass.tint(_:)`.
 * @param surfaceColor An overlay color drawn on the glass. When unspecified and there is no [tint], the
 *   [liquidGlassFrostColor] for the system theme is used.
 * @param isInteractive Whether the glass reacts to touch.
 * @param style The tier's glass settings. Defaults to [LiquidGlassStyle.current].
 */
@Composable
internal fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(percent = 50),
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    isInteractive: Boolean = false,
    style: LiquidGlassStyle = LiquidGlassStyle.current
): Modifier {
    val backdrop = LocalGlassBackdrop.current ?: rememberLayerBackdrop { drawContent() }
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val frost = when {
        surfaceColor.isSpecified -> surfaceColor
        !tint.isSpecified -> liquidGlassFrostColor(!isSystemInDarkTheme())
        else -> Color.Unspecified
    }

    val interactionModifier = if (isInteractive) highlight.modifier.then(highlight.gestureModifier) else Modifier
    val pressModifier = if (isInteractive) {
        Modifier.graphicsLayer {
            val growth = (12f.dp.toPx() / size.width.coerceAtLeast(1f)).coerceAtMost(0.15f)
            val scale = 1f + growth * highlight.pressProgress
            scaleX = scale
            scaleY = scale
        }
    } else {
        Modifier
    }

    return this
        .then(pressModifier)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(4f.dp.toPx())
                if (style.lens) lens(12f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = style.chromaticAberration)
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(alpha = 0.30f) },
            onDrawSurface = {
                if (tint.isSpecified) {
                    // Hue blend colors the backdrop, then a translucent fill sets the tint
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.75f))
                }
                if (frost.isSpecified) {
                    drawRect(frost)
                }
            }
        )
        .then(interactionModifier)
}
