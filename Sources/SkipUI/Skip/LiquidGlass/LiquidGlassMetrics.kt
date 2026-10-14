// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: every fixed measurement, shared by the Kotlin layer and the Swift sources transpiled into it.
package skip.ui.liquidglass

import androidx.compose.ui.unit.dp

// MARK: - Toolbars

/** The toolbar's one spacing: edge margin, gap after the back button, and gap between capsules. */
internal val liquidGlassToolbarSpacing = 8.dp

/** A glass button's inset around its label. */
internal val liquidGlassButtonContentInset = 8.dp

/** A capsule's inset around its items, matching a button's so the two space alike. */
internal val liquidGlassToolbarContentPadding = 8.dp

/** The gap between items on one capsule, derived so it equals the distance across a capsule boundary. */
internal val liquidGlassToolbarItemSpacing = liquidGlassToolbarContentPadding + liquidGlassToolbarSpacing + liquidGlassButtonContentInset

/** A capsule's height in the bottom bar. */
internal val liquidGlassToolbarHeight = 48.dp

/** A capsule's height in the top bar, and the back button's size, as on iOS 26. */
internal val liquidGlassTopToolbarHeight = 44.dp

// MARK: - Navigation bar

/** The bar's height with an inline title: Material's, from which SkipUI pads the content. */
internal val liquidGlassTopBarHeight = 64.dp

/** The bar's height with its large title expanded: Material's, from which SkipUI pads the content. */
internal val liquidGlassLargeTopBarHeight = 112.dp

/** How far below the navigation bar the content finishes fading in, as on iOS 26. */
internal val liquidGlassTopEdgeFade = 24.dp

/** The space kept above and below an icon in a capsule; a taller icon is scaled to fit. */
internal val liquidGlassToolbarIconInset = 8.dp

/** The gap a bottom toolbar keeps above the tab bar or screen edge. */
internal val liquidGlassToolbarGap = 8.dp

// MARK: - Tab bar

/** The bar's margin from the screen's sides, full size and minimized. */
internal val liquidGlassTabBarSideMargin = 24.dp

/** How far past the tab bar or navigation bar the content starts blurring into the screen's edge. */
internal val liquidGlassEdgeFade = 32.dp

/** The minimized bar's width: one tab icon. */
internal val liquidGlassMinimizedTabBarWidth = 76.dp

/** The bar's height at full size. */
internal val liquidGlassTabBarHeight = 65.dp

/** The bar's height minimized. */
internal val liquidGlassMinimizedTabBarHeight = 50.dp

// MARK: - Tab badge (measured from iOS 26)

/** A badge's height and minimum width, so one digit sits in a circle. */
internal val liquidGlassBadgeHeight = 20.dp

/** The inset either side of a badge's text. */
internal val liquidGlassBadgeHorizontalPadding = 5.dp

/** How far a badge's leading edge sits inside the icon. */
internal val liquidGlassBadgeOverlap = 5.dp

/** How far a badge's top sits above the icon. */
internal val liquidGlassBadgeRise = 6.dp
