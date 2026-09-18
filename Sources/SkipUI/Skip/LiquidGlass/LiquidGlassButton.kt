// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: glass button and surface built on Kyant's backdrop library.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
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
 * A capsule button with a Liquid Glass look: blurred, lens-refracted backdrop, specular highlight,
 * and a squash-and-stretch press animation with a touch glow.
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
 * @param surfaceColor An overlay color drawn on the glass. When unspecified and there is no [tint], a light
 *   or dark frost is used based on the system theme.
 * @param contentPadding Padding between the glass edge and the content.
 * @param interactionSource An optional source for observing press state. One is created if `null`.
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
    content: @Composable RowScope.() -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    val animationScope = rememberCoroutineScope()
    val highlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f),
        label = "GlassButtonPress"
    )

    val isLight = !isSystemInDarkTheme()

    val resolvedSurfaceColor = when {
        surfaceColor.isSpecified -> surfaceColor
        !tint.isSpecified -> if (isLight) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.1f)
        else -> Color.Unspecified
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
                    lens(12f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = true)
                },
                layerBlock = {
                    // Squash and stretch: widen and flatten while pressed
                    val s = 1f + 0.06f * press
                    scaleX = s
                    scaleY = 1f / s
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(alpha = 0.30f) },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        // Hue blend colors the backdrop, then a translucent fill sets the prominent tint
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (resolvedSurfaceColor.isSpecified) {
                        drawRect(resolvedSurfaceColor)
                    }
                }
            )
    } else {
        // No shared backdrop: fall back to a local one so the button still renders as glass
        val localBackdrop = rememberLayerBackdrop { drawContent() }
        val fillColor = if (isLight) Color.White else Color.Black
        val fillAlpha = if (isLight) 0.2f else 0.1f

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
                    lens(12f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(alpha = 0.30f) },
                onDrawSurface = { drawRect(fillColor.copy(alpha = fillAlpha)) }
            )
    }

    Row(
        modifier = modifier
            .then(glassModifier)
            .then(highlight.modifier)
            .then(highlight.gestureModifier)
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
 * @param content The surface content, laid out in a centered row.
 */
@Composable
internal fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(percent = 50),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit
) {
    val backdrop = LocalGlassBackdrop.current
    val isLight = !isSystemInDarkTheme()
    val surfaceColor = if (isLight) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f)

    val glassModifier = if (backdrop != null) {
        // Shared backdrop: refract the real content behind the surface
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(4f.dp.toPx())
                lens(12f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = true)
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(alpha = 0.30f) },
            onDrawSurface = { drawRect(surfaceColor) }
        )
    } else {
        // No shared backdrop: fall back to a local one so the surface still renders as glass
        val localBackdrop = rememberLayerBackdrop { drawContent() }
        val fillColor = if (isLight) Color.White else Color.Black
        val fillAlpha = if (isLight) 0.2f else 0.1f

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
                    lens(12f.dp.toPx(), 24f.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Default },
                shadow = { Shadow(alpha = 0.30f) },
                onDrawSurface = { drawRect(fillColor.copy(alpha = fillAlpha)) }
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
