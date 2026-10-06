// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: the glass button, and the backdrop and frost all glass shares.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui.liquidglass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

import com.kyant.backdrop.Backdrop

/** The recorded content behind glass, which glass blurs and refracts. `null` where nothing is recorded. */
internal val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * The frost on untinted glass in the system theme, shared by buttons, surfaces and the tab bar. Light enough for the
 * content's color to come through, as on iOS.
 */
@Composable
internal fun liquidGlassFrostColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF1C1C1E).copy(alpha = 0.3f) else Color(0xFFFAFAFA).copy(alpha = 0.4f)

/**
 * A capsule glass button with a touch glow and springy press, backing `.glass` and `.glassProminent`.
 *
 * @param isProminent Informational for callers configuring options; prominence is drawn through [tint].
 * @param tint Tints the glass, as `.glassProminent` does.
 * @param surfaceColor A fill in place of the frost.
 * @param content The label, in a centered row.
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
    Row(
        modifier = modifier
            .liquidGlassSurface(shape, tint, surfaceColor, isInteractive = enabled, style = style)
            .clickable(enabled = enabled, interactionSource = interactionSource ?: remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}
