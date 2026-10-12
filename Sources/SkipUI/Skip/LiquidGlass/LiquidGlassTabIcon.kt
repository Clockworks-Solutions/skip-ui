// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: tab and toolbar icons at their own size, as iOS draws them, and iOS 26 tab badges.
package skip.ui.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

import kotlin.math.min
import kotlin.math.roundToInt

// MARK: - Bar items

/** Measures all children with no size proposed, as a tab bar draws an icon at its own size, and returns the largest. */
private fun measureAll(measurables: List<Measurable>): Triple<List<Placeable>, Int, Int> {
    val placeables = measurables.map { it.measure(Constraints()) }
    return Triple(placeables, placeables.maxOfOrNull { it.width } ?: 0, placeables.maxOfOrNull { it.height } ?: 0)
}

/**
 * Lays a toolbar item out as iOS does: proposed [maxHeight] and any width, so a resizable image fits that height while
 * an image of fixed size or a frame keeps its own. The item never grows the bar; anything larger overflows, centered.
 */
@Composable
internal fun LiquidGlassBarItemContent(modifier: Modifier = Modifier, maxHeight: Dp = Dp.Unspecified, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val limitHeight = if (maxHeight.isSpecified) min(constraints.maxHeight, maxHeight.roundToPx()) else constraints.maxHeight
        val measureHeight = if (maxHeight.isSpecified) Constraints.Infinity else limitHeight
        val (placeables, measuredWidth, measuredHeight) = measureAll(measurables, Constraints(maxWidth = constraints.maxWidth, maxHeight = measureHeight))
        val scale = fitScale(measuredWidth, measuredHeight, constraints.maxWidth, limitHeight)
        val width = (measuredWidth * scale).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (measuredHeight * scale).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            // Compose centers an oversized placeable on its box, and the layer scales about its center
            placeables.forEach {
                it.placeWithLayer((width - it.width) / 2, (height - it.height) / 2) {
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
    }
}

/** The tallest a glass button's label may be, set by a toolbar for its prominent items; unspecified elsewhere. */
internal val LocalGlassToolbarItemMaxHeight = compositionLocalOf { Dp.Unspecified }

/** [LocalGlassToolbarItemMaxHeight] here; read by SkipUI's glass button. */
@Composable
internal fun liquidGlassToolbarItemMaxHeight(): Dp = LocalGlassToolbarItemMaxHeight.current

/** Provides [LocalGlassToolbarItemMaxHeight] to a toolbar's prominent items. */
@Composable
internal fun WithGlassToolbarItemMaxHeight(maxHeight: Dp, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalGlassToolbarItemMaxHeight provides maxHeight, content = content)

// MARK: - Tab icon

/** The parts of a tab icon to draw: the glass bar draws icons under its pill and badges over it, as iOS does. */
internal enum class GlassTabIconPart { ALL, ICON, BADGE }

/** The parts of the tab icons here to draw; [GlassTabIconPart.ALL] outside the glass bar. */
internal val LocalGlassTabIconPart = compositionLocalOf { GlassTabIconPart.ALL }

/** Provides [LocalGlassTabIconPart] to one layer of the glass bar. */
@Composable
internal fun WithGlassTabIconPart(part: GlassTabIconPart, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalGlassTabIconPart provides part, content = content)

/**
 * A tab icon at its own size, centered on its slot as iOS draws it, with an optional badge at its top trailing corner.
 * An icon larger than the slot overflows it rather than growing the bar.
 *
 * @param slotSize The icon's square, or `null` for the incoming constraints.
 * @param symbolScale 1.5 for SF Symbols, whose Material stand-ins draw small; 1 for images, drawn at their own size.
 * @param badge The badge label, if any.
 */
@Composable
internal fun LiquidGlassTabIcon(
    modifier: Modifier = Modifier,
    slotSize: Dp?,
    symbolScale: Float,
    badge: (@Composable () -> Unit)?,
    icon: @Composable () -> Unit
) {
    val part = LocalGlassTabIconPart.current
    val showsBadge = badge != null && part != GlassTabIconPart.ICON
    val iconAlpha = if (part == GlassTabIconPart.BADGE) 0f else 1f
    Layout(
        modifier = modifier,
        content = {
            // Unwrapped, so a required size stays visible to the measurement
            icon()
            if (showsBadge) LiquidGlassBadge(content = badge!!)
        }
    ) { measurables, constraints ->
        val slotPx = slotSize?.roundToPx()
        val maxWidth = slotPx?.let { min(it, constraints.maxWidth) } ?: constraints.maxWidth
        val maxHeight = slotPx?.let { min(it, constraints.maxHeight) } ?: constraints.maxHeight
        // Unbounded in height, so a framed icon in a wrapper still measures at its frame
        val (icons, iconWidth, iconHeight) = measureAll(if (showsBadge) measurables.dropLast(1) else measurables, Constraints(maxWidth = maxWidth))
        val scale = symbolScale * fitScale((iconWidth * symbolScale).roundToInt(), (iconHeight * symbolScale).roundToInt(), maxWidth, maxHeight)
        val scale = symbolScale
        val width = (slotPx ?: (iconWidth * scale).roundToInt()).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (slotPx ?: (iconHeight * scale).roundToInt()).coerceIn(constraints.minHeight, constraints.maxHeight)
        val badgePlaceable = if (showsBadge) measurables.last().measure(Constraints()) else null

        layout(width, height) {
            icons.forEach {
                it.placeWithLayer((width - it.width) / 2, (height - it.height) / 2) {
                    scaleX = scale
                    scaleY = scale
                    alpha = iconAlpha
                }
            }
            badgePlaceable?.let { badge ->
                // Against the drawn icon, not the slot, so a small icon keeps its badge close
                val halfWidth = iconWidth * scale / 2f
                val overlap = liquidGlassBadgeOverlap.toPx()
                val x = if (layoutDirection == LayoutDirection.Rtl) width / 2f - halfWidth + overlap - badge.width else width / 2f + halfWidth - overlap
                val y = height / 2f - iconHeight * scale / 2f - liquidGlassBadgeRise.toPx()
                badge.place(x.roundToInt(), y.roundToInt())
            }
        }
    }
}

// MARK: - Badge

/** A tab badge as on iOS 26: a system-red capsule 20dp tall, round for one digit, with white 13sp text. */
@Composable
internal fun LiquidGlassBadge(content: @Composable () -> Unit) {
    val color = if (isSystemInDarkTheme()) Color(0xFFFF4245) else Color(0xFFFF383C)
    Box(
        modifier = Modifier
            .heightIn(min = liquidGlassBadgeHeight)
            .widthIn(min = liquidGlassBadgeHeight)
            .background(color, RoundedCornerShape(percent = 50))
            .padding(horizontal = liquidGlassBadgeHorizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            ProvideTextStyle(TextStyle(color = Color.White, fontSize = 13.sp, lineHeight = 16.sp), content)
        }
    }
}
