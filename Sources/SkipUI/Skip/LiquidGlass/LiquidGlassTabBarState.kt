// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: the state a `TabView` shares with its glass tab bar, and the insets glass chrome publishes.
package skip.ui.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

import kotlin.math.abs

/**
 * What one `TabView` and its glass bar share.
 *
 * @property backdrop The recorded tab content the bar refracts.
 */
internal class LiquidGlassTabBarState(val backdrop: LayerBackdrop) {
    /** The bar's whole footprint from the bottom of the content, system bar included; 0 until measured. */
    var inset by mutableStateOf(0.dp)

    /** [inset] without the system bar: what a scrollable adds after its content to clear the bar. */
    var contentInset by mutableStateOf(0.dp)

    /** Whether the bar is minimized to its pill. */
    var isMinimized by mutableStateOf(false)
}

/** A [LiquidGlassTabBarState] for one `TabView`, with its own content recording. */
@Composable
internal fun rememberLiquidGlassTabBarState(): LiquidGlassTabBarState {
    val backdrop = rememberLayerBackdrop()
    return remember(backdrop) { LiquidGlassTabBarState(backdrop) }
}

/** `tabBarMinimizeBehavior(_:)` for the glass bar; `.automatic` never minimizes, as on iOS. */
internal enum class GlassTabBarMinimizeBehavior { NEVER, ON_SCROLL_DOWN, ON_SCROLL_UP }

/**
 * Minimizes and restores the bar as the tab content scrolls, following iOS 26:
 * - Scrolling in the behavior's direction minimizes it; scrolling back restores it.
 * - Reaching the top restores it, as does dragging past the bottom. A fling that only lands on the bottom doesn't.
 * - Switching to a behavior that never minimizes restores it.
 *
 * Direction comes from what the content actually scrolled, after 12dp of travel, so edge bounces and a shaky finger
 * don't flip the bar. Consumes nothing.
 */
@Composable
internal fun rememberGlassTabBarMinimizeConnection(state: LiquidGlassTabBarState, behavior: GlassTabBarMinimizeBehavior): NestedScrollConnection {
    val thresholdPx = with(LocalDensity.current) { 12.dp.toPx() }
    if (behavior == GlassTabBarMinimizeBehavior.NEVER && state.isMinimized) {
        SideEffect { state.isMinimized = false }
    }
    return remember(state, behavior, thresholdPx) {
        object : NestedScrollConnection {
            private var travel = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (behavior == GlassTabBarMinimizeBehavior.NEVER) return Offset.Zero
                if (consumed.y != 0f) {
                    // A turnaround starts the run over
                    if ((travel < 0f) != (consumed.y < 0f)) travel = 0f
                    travel += consumed.y
                    if (abs(travel) >= thresholdPx) {
                        // Content moves up (negative) as the user scrolls down
                        val isScrollingDown = travel < 0f
                        state.isMinimized = isScrollingDown == (behavior == GlassTabBarMinimizeBehavior.ON_SCROLL_DOWN)
                        travel = 0f
                    }
                }
                // Leftover scroll means an edge: downward is the top; upward is the bottom, counted only while dragging
                if (available.y > 0f || (available.y < 0f && source == NestedScrollSource.UserInput)) {
                    state.isMinimized = false
                    travel = 0f
                }
                return Offset.Zero
            }
        }
    }
}

/** How far the glass bar reaches over the content; what must not be covered (a nested bar, a toolbar) pads by it. */
internal val LocalGlassTabBarInset = compositionLocalOf { 0.dp }

/** What a scrollable adds after its content to clear the glass chrome over it. Moves nothing. */
internal val LocalGlassContentInset = compositionLocalOf { 0.dp }

/**
 * The height of a `safeAreaInset(edge: .bottom)` on a `TabView`. iOS draws it over the bar instead of lifting the bar,
 * and the tabs' content clears the higher of the two.
 */
internal val LocalGlassOuterBottomInset = compositionLocalOf { 0.dp }

/** Provides [LocalGlassOuterBottomInset] to a `TabView` with a bottom `safeAreaInset`. */
@Composable
internal fun WithGlassOuterBottomInset(inset: Dp, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalGlassOuterBottomInset provides inset, content = content)

/** Provides a `TabView`'s insets to its tabs, clearing the outer inset, which isn't theirs. */
@Composable
internal fun WithGlassTabBarInset(inset: Dp, contentInset: Dp, content: @Composable () -> Unit) =
    CompositionLocalProvider(
        LocalGlassTabBarInset provides inset,
        LocalGlassContentInset provides contentInset,
        LocalGlassOuterBottomInset provides 0.dp,
        content = content
    )

/** Provides [LocalGlassContentInset], replacing the one from further out. */
@Composable
internal fun WithGlassContentInset(inset: Dp, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalGlassContentInset provides inset, content = content)

/**
 * Starts a sheet or cover with none of the presenting screen's glass state. iOS presents in a fresh window; without
 * this, Compose would hand on the outer bar's insets and backdrop, and a presented `TabView` would float its bar as if
 * nested.
 */
@Composable
internal fun WithoutGlassChrome(content: @Composable () -> Unit) =
    CompositionLocalProvider(
        LocalGlassTabBarInset provides 0.dp,
        LocalGlassContentInset provides 0.dp,
        LocalGlassOuterBottomInset provides 0.dp,
        LocalGlassBackdrop provides null,
        LocalGlassBackdropRequest provides null,
        LocalGlassToolbarCapsule provides false,
        LocalGlassToolbarItemMaxHeight provides Dp.Unspecified,
        LocalGlassTabIconPart provides GlassTabIconPart.ALL,
        content = content
    )
