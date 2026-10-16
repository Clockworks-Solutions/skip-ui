// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: the navigation bar as iOS 26 draws it, transparent over the content scrolling beneath it.
package skip.ui.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.kyant.backdrop.Backdrop

import kotlin.math.roundToInt

// Measured from iOS 26
private val inlineTitleStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
private val largeTitleStyle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold)

/** How far the large title scrolls before the bar shows only its inline title. */
private val largeTitleRange = liquidGlassLargeTopBarHeight - liquidGlassTopBarHeight

/** The space before a title, which upstream's left-aligned Material bar needs beside the glass back button. */
internal val LocalGlassTitleLeadingPadding = compositionLocalOf { 8.dp }

/**
 * The glass navigation bar: an inline title centered between the back button and the trailing capsules, and with
 * [isLarge] a large title beneath that scrolls away with the content into it, as on iOS 26. Draws no background: the
 * blurred edge behind it and the content beneath show through.
 *
 * Takes a pinned [scrollBehavior], so scrolling never collapses the bar ahead of the content. The large title follows
 * how far the content has scrolled, read only while laying out and drawing, and only the inline row is measured and
 * tapped as the bar: the content's padding stays fixed and nothing recomposes as it scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiquidGlassTopAppBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    isLarge: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    windowInsets: WindowInsets,
    titleColor: Color,
    modifier: Modifier = Modifier
) {
    val rangePx = with(LocalDensity.current) { largeTitleRange.toPx() }
    // 0 expanded, 1 scrolled away; read only while laying out and drawing
    val collapsed: () -> Float = { if (isLarge) scrollBehavior.largeTitleCollapse(rangePx) else 1f }
    CompositionLocalProvider(LocalContentColor provides titleColor, LocalGlassTitleLeadingPadding provides 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Measured and tapped as the bar, so its height never changes while scrolling
            Box(modifier = modifier.fillMaxWidth().windowInsetsPadding(windowInsets)) {
                TopBarRow(navigationIcon = navigationIcon, actions = actions) {
                    // The inline title appears once the large title has scrolled under the bar
                    Box(modifier = Modifier.graphicsLayer { alpha = ((collapsed() - 0.6f) / 0.4f).coerceIn(0f, 1f) }) {
                        ProvideTextStyle(inlineTitleStyle, title)
                    }
                }
            }
            if (isLarge) {
                Box(
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            // Shrinks from the top, keeping the title on the content's edge as it scrolls away
                            val height = (rangePx * (1f - collapsed())).roundToInt()
                            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                            layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
                        }
                        .clipToBounds()
                        .graphicsLayer { alpha = 1f - collapsed() }
                        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                ) {
                    ProvideTextStyle(largeTitleStyle, title)
                }
            }
        }
    }
}

/** The bar's row: the leading and trailing items at its edges and the title centered on the bar, clear of both. */
@Composable
private fun TopBarRow(navigationIcon: @Composable () -> Unit, actions: @Composable () -> Unit, title: @Composable () -> Unit) {
    Layout(
        content = {
            Row(modifier = Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) { navigationIcon() }
            Row(modifier = Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
            Box { title() }
        },
        modifier = Modifier.fillMaxWidth().height(liquidGlassTopBarHeight)
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leading = measurables[0].measure(loose)
        val trailing = measurables[1].measure(loose)
        val side = maxOf(leading.width, trailing.width)
        val title = measurables[2].measure(loose.copy(maxWidth = (constraints.maxWidth - side * 2).coerceAtLeast(0)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val height = constraints.maxHeight
            leading.placeRelative(0, (height - leading.height) / 2)
            trailing.placeRelative(constraints.maxWidth - trailing.width, (height - trailing.height) / 2)
            title.placeRelative((constraints.maxWidth - title.width) / 2, (height - title.height) / 2)
        }
    }
}

/**
 * The blurred edge the glass bar floats on, from the top of the screen to [height] below it, less however far the large
 * title has scrolled away. Appears only once content scrolls beneath the bar, as on iOS, and leaves the composition at
 * rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiquidGlassTopBarEdge(backdrop: Backdrop, height: Dp, isLarge: Boolean, scrollBehavior: TopAppBarScrollBehavior, wash: Color, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val rangePx = with(density) { largeTitleRange.toPx() }
    val fadePx = with(density) { liquidGlassTopEdgeFade.toPx() }
    val state = scrollBehavior.state
    LiquidGlassEdgeEffect(
        backdrop = backdrop,
        edge = Alignment.Top,
        height = { height - largeTitleRange * (if (isLarge) scrollBehavior.largeTitleCollapse(rangePx) else 0f) + liquidGlassTopEdgeFade },
        modifier = modifier,
        blurRadius = 8.dp,
        wash = wash,
        alpha = remember(state, fadePx) { { (-state.contentOffset / fadePx).coerceIn(0f, 1f) } }
    )
}

/** How far a large title has scrolled away, 0 to 1. It follows the content, as on iOS, rather than collapsing first. */
@OptIn(ExperimentalMaterial3Api::class)
private fun TopAppBarScrollBehavior.largeTitleCollapse(rangePx: Float): Float = (-state.contentOffset / rangePx).coerceIn(0f, 1f)
