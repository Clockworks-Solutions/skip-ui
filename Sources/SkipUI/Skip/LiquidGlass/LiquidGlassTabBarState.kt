// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: the state one `TabView` shares with its floating glass tab bar.
package skip.ui.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

import kotlin.math.abs

/**
 * What one `TabView` and its glass tab bar share.
 *
 * The bar is drawn in the tab bar slot while the tab content it refracts is recorded somewhere else entirely, and
 * scrolling that content minimizes the bar, so the pieces meet here. One instance per `TabView` composition.
 *
 * @property backdrop The recorded tab content that the bar refracts.
 */
internal class LiquidGlassTabBarState(val backdrop: LayerBackdrop) {
    /**
     * Whether the bar is minimized to its compact pill.
     *
     * Set by scrolling when `tabBarMinimizeBehavior(_:)` asks for it, and cleared when the user taps the minimized bar.
     */
    var isMinimized by mutableStateOf(false)
}

/** Remembers the [LiquidGlassTabBarState] for one `TabView` composition. */
@Composable
internal fun rememberLiquidGlassTabBarState(): LiquidGlassTabBarState {
    val backdrop = rememberLayerBackdrop()
    return remember(backdrop) { LiquidGlassTabBarState(backdrop) }
}

/**
 * How the glass tab bar reacts to the tab content scrolling, from SwiftUI's `tabBarMinimizeBehavior(_:)`.
 */
internal enum class GlassTabBarMinimizeBehavior {
    /** The bar keeps its full size. Also the behavior for `.automatic`, which does not minimize on iOS either. */
    NEVER,
    /** The bar minimizes while the user scrolls down the content, and returns while they scroll back up. */
    ON_SCROLL_DOWN,
    /** The reverse: the bar minimizes while the user scrolls up. */
    ON_SCROLL_UP
}

/**
 * Watches the tab content scroll and minimizes or restores the bar.
 *
 * Reads the scroll before the content does, and consumes nothing, so lists and scroll views behave exactly as they do
 * without it. A small run of scrolling in one direction is needed before the bar reacts, so a shaky finger or the
 * bounce at the end of a list does not flip it back and forth.
 *
 * @param state The bar state to drive.
 * @param behavior What the app asked for with `tabBarMinimizeBehavior(_:)`.
 */
@Composable
internal fun rememberGlassTabBarMinimizeConnection(
    state: LiquidGlassTabBarState,
    behavior: GlassTabBarMinimizeBehavior
): NestedScrollConnection {
    val thresholdPx = with(LocalDensity.current) { 12.dp.toPx() }
    return remember(state, behavior, thresholdPx) {
        object : NestedScrollConnection {
            private var travel = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (behavior == GlassTabBarMinimizeBehavior.NEVER || available.y == 0f) {
                    return Offset.Zero
                }
                // Start over whenever the finger turns around, so only a deliberate run in one direction counts
                if (travel != 0f && (travel < 0f) != (available.y < 0f)) {
                    travel = 0f
                }
                travel += available.y
                if (abs(travel) >= thresholdPx) {
                    // Content moves up, a negative delta, as the user scrolls down through it
                    val isScrollingDown = travel < 0f
                    state.isMinimized = if (behavior == GlassTabBarMinimizeBehavior.ON_SCROLL_DOWN) isScrollingDown else !isScrollingDown
                    travel = 0f
                }
                return Offset.Zero
            }
        }
    }
}
