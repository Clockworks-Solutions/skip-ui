// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: every fixed measurement the fork lays out with, in one place.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui.liquidglass

import androidx.compose.ui.unit.dp

// One file serves both layers: the Swift sources in `Sources/SkipUI/LiquidGlass` transpile into this module and read
// these values directly.

// MARK: - Toolbars

/**
 * The one spacing value a toolbar is built from: the bar's edge margin, the space after the back button, and the gap
 * between capsules. Every shape sits this far from its neighbour, so both bars read with one rhythm.
 */
internal val liquidGlassToolbarSpacing = 8.dp

/** The inset a glass button keeps between its own edge and its label. */
internal val liquidGlassButtonContentInset = 8.dp

/** The inset a capsule keeps around its items, matching a glass button's own inset so the two are spaced alike. */
internal val liquidGlassToolbarContentPadding = 8.dp

/**
 * The gap between two items on one capsule. Derived, not chosen: crossing a capsule boundary costs the capsule's
 * inset, the gap between shapes and the next button's inset, so both distances come out equal.
 */
internal val liquidGlassToolbarItemSpacing =
    liquidGlassToolbarContentPadding + liquidGlassToolbarSpacing + liquidGlassButtonContentInset

/** The height of a floating glass toolbar capsule at the bottom of the screen, and in the top bar. */
internal val liquidGlassToolbarHeight = 48.dp
internal val liquidGlassTopToolbarHeight = 40.dp

/** The gap a bottom toolbar keeps above the tab bar, or the bottom of the screen. */
internal val liquidGlassToolbarGap = 8.dp

// MARK: - Tab bar

/** The gap the bar keeps from the side of the screen, both at full size and once minimized. */
internal val liquidGlassTabBarSideMargin = 24.dp

/** How far above the bar the content starts blurring into the bottom edge of the screen. */
internal val liquidGlassTabBarEdgeFade = 32.dp

/** The width of the glass tab bar once it is minimized: room for one tab icon. */
internal val liquidGlassMinimizedTabBarWidth = 76.dp

/** The height of the glass tab bar at full size, and minimized. */
internal val liquidGlassTabBarHeight = 65.dp
internal val liquidGlassMinimizedTabBarHeight = 50.dp
