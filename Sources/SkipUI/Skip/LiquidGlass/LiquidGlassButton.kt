// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: glass button built on Kyant's backdrop library.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui.liquidglass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

import com.kyant.backdrop.Backdrop

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
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .liquidGlassSurface(shape = shape, tint = tint, surfaceColor = surfaceColor, isInteractive = enabled, style = style)
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
