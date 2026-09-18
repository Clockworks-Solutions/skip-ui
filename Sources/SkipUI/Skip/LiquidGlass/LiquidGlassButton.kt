// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: glass button and surface built on Kyant's backdrop library.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui.liquidglass

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.graphics.CompositingStrategy

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * The backdrop that glass components blur and refract.
 *
 * A backdrop is a recorded layer of the content behind glass elements. When a presentation root provides
 * one, every glass component inside it samples the real screen content. When it is `null`, components fall
 * back to a local backdrop of their own drawing, which still looks like glass but does not show the
 * content behind it.
 */
internal val LocalGlassBackdrop: ProvidableCompositionLocal<Backdrop?> = compositionLocalOf { null }

/**
 * The frost drawn on untinted glass: the tab bar's container color, so buttons, surfaces, and the tab bar share one look.
 *
 * @param isLight Whether the system theme is light.
 */
internal fun liquidGlassFrostColor(isLight: Boolean): Color =
    if (isLight) Color(0xFFFAFAFA).copy(alpha = 0.7f) else Color(0xFF121212).copy(alpha = 0.4f)

/**
 * A capsule button with a Liquid Glass look: blurred, lens-refracted backdrop, specular highlight,
 * and a springy press animation with a touch glow.
 *
 * Backs the `.glass` and `.glassProminent` button styles.
 *
 * @param modifier The modifier to apply to the button.
 * @param enabled Whether the button responds to taps.
 * @param isProminent Whether this is the prominent variant. Prominence is expressed through [tint]; this
 *   flag is informational for callers that configure options.
 * @param onClick Called when the button is tapped.
 * @param shape The glass shape. Defaults to a capsule.
 * @param tint When specified, tints the glass with this color, as in `.glassProminent`.
 * @param surfaceColor An overlay color drawn on the glass. When unspecified and there is no [tint], the
 *   [liquidGlassFrostColor] for the system theme is used.
 * @param contentPadding Padding between the glass edge and the content.
 * @param interactionSource An optional source for observing press state. One is created if `null`.
 * @param style The tier's glass settings. Defaults to [LiquidGlassStyle.current].
 * @param content The button label, laid out in a centered row.
 */
@Composable
internal fun LiquidGlassButton(
    modifier: Modifier,
    enabled: Boolean,
    isProminent: Boolean,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(percent = 50),
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    interactionSource: MutableInteractionSource? = null,
    style: LiquidGlassStyle = LiquidGlassStyle.current,
    content: @Composable RowScope.() -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    val isLight = !isSystemInDarkTheme()

    val resolvedSurfaceColor = when {
        surfaceColor.isSpecified -> surfaceColor
        !tint.isSpecified -> liquidGlassFrostColor(isLight)
        else -> Color.Unspecified
    }

    // Draws the prominent tint and the frost on the glass. Shared by both backdrop paths, so `.glassProminent` stays tinted
    // with the local fallback backdrop too.
    val drawSurface: DrawScope.() -> Unit = {
        if (tint.isSpecified) {
            // Hue blend colors the backdrop, then a translucent fill sets the prominent tint
            drawRect(tint, blendMode = BlendMode.Hue)
            drawRect(tint.copy(alpha = 0.75f))
        }
        if (resolvedSurfaceColor.isSpecified) {
            drawRect(resolvedSurfaceColor)
        }
    }

    val glassModifier = if (backdrop != null) {
        // Shared backdrop: refract the real content behind the button
        Modifier
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
                onDrawSurface = drawSurface
            )
    } else {
        // No shared backdrop: fall back to a local one so the button still renders as glass
        val localBackdrop = rememberLayerBackdrop { drawContent() }

        Modifier
            .shadow(
                elevation = 0.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.28f),
                ambientColor = Color.Black.copy(alpha = 0.10f)
            )
            .drawBackdrop(
                backdrop = localBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    if (style.lens) lens(12f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = style.chromaticAberration)
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(alpha = 0.30f) },
                onDrawSurface = drawSurface
            )
    }

    Row(
        modifier = modifier
            // Grow the glass and label together while pressed, by up to 12dp in width and at most 15%, springing
            // back on release
            .graphicsLayer {
                val growth = (12f.dp.toPx() / size.width.coerceAtLeast(1f)).coerceAtMost(0.15f)
                val scale = 1f + growth * highlight.pressProgress
                scaleX = scale
                scaleY = scale
            }
            .then(glassModifier)
            .then(highlight.modifier)
            .then(if (enabled) highlight.gestureModifier else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = resolvedInteractionSource,
                indication = null,
                onClick = onClick
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * A non-interactive Liquid Glass container, such as the title pill in a navigation bar.
 *
 * Uses the same glass as an untinted [LiquidGlassButton] at rest (frost, lens, highlight, and shadow), so a surface next
 * to a glass button looks identical. It has no press behavior.
 *
 * @param modifier The modifier to apply to the surface.
 * @param shape The glass shape. Defaults to a capsule.
 * @param contentPadding Padding between the glass edge and the content.
 * @param style The tier's glass settings. Defaults to [LiquidGlassStyle.current].
 * @param content The surface content, laid out in a centered row.
 */
@Composable
internal fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = 50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    style: LiquidGlassStyle = LiquidGlassStyle.current,
    content: @Composable RowScope.() -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    val isLight = !isSystemInDarkTheme()
    val surfaceColor = liquidGlassFrostColor(isLight)

    val glassModifier = if (backdrop != null) {
        // Shared backdrop: refract the real content behind the surface
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(4f.dp.toPx())
                if (style.lens) lens(12f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = style.chromaticAberration)
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(alpha = 0.30f) },
            onDrawSurface = { drawRect(surfaceColor) }
        )
    } else {
        // No shared backdrop: fall back to a local one so the surface still renders as glass
        val localBackdrop = rememberLayerBackdrop { drawContent() }

        Modifier
            .shadow(
                elevation = 0.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.28f),
                ambientColor = Color.Black.copy(alpha = 0.10f)
            )
            .drawBackdrop(
                backdrop = localBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(4f.dp.toPx())
                    if (style.lens) lens(12f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = style.chromaticAberration)
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(alpha = 0.30f) },
                onDrawSurface = { drawRect(surfaceColor) }
            )
    }

    Row(
        modifier = modifier
            .then(glassModifier)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
