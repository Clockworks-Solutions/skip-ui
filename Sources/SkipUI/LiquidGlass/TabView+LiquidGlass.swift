//
//  TabView+LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 17/09/26.
//
//  Liquid Glass (Clockworks fork): glass tab bar for `TabView`.
//

#if !SKIP_BRIDGE
import Foundation
#if SKIP
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import skip.ui.liquidglass.__

/// Remember the state one `TabView` shares with its glass tab bar: the backdrop that records the tab content, and
/// whether the bar is minimized.
@Composable func rememberLiquidGlassTabBarState() -> LiquidGlassTabBarState {
    return skip.ui.liquidglass.rememberLiquidGlassTabBarState()
}

/// Records the tab content into the bar's backdrop and watches it scroll, so the bar can minimize when
/// `tabBarMinimizeBehavior(_:)` asks. Nothing on the `NATIVE` tier.
@Composable func liquidGlassTabBarContentModifier(_ state: LiquidGlassTabBarState) -> Modifier {
    guard isLiquidGlassTabBarEnabled() else {
        return Modifier
    }
    let minimizeConnection = rememberGlassTabBarMinimizeConnection(state: state, behavior: liquidGlassTabBarMinimizeBehavior())
    return Modifier.layerBackdrop(state.backdrop).nestedScroll(minimizeConnection)
}

/// How the bar reacts to scrolling. `.automatic` never minimizes, matching iOS, where minimizing is opt-in.
@Composable func liquidGlassTabBarMinimizeBehavior() -> GlassTabBarMinimizeBehavior {
    let behavior = EnvironmentValues.shared.tabBarMinimizeBehavior
    if behavior == TabBarMinimizeBehavior.onScrollDown {
        return GlassTabBarMinimizeBehavior.ON_SCROLL_DOWN
    } else if behavior == TabBarMinimizeBehavior.onScrollUp {
        return GlassTabBarMinimizeBehavior.ON_SCROLL_UP
    } else {
        return GlassTabBarMinimizeBehavior.NEVER
    }
}

/// Render `TabView` content, publishing how far the bar reaches over it.
///
/// The glass bar takes no space in the layout, so content draws under it and refracts through it; anything that must
/// not be covered reads `liquidGlassTabBarInset()` and moves up. The Material bar publishes its height too, since a
/// nested `TabView` would otherwise land its bar on the outer one.
@Composable func LiquidGlassTabContent(state: LiquidGlassTabBarState, materialBarHeightPx: Float, content: @Composable () -> Void) {
    guard isLiquidGlassTabBarEnabled() else {
        // The Material bar takes its own space, so nothing has to move for it — except a nested `TabView`'s bar, which
        // Compose otherwise drops straight on top of this one. It publishes only the inset, and no content inset
        let materialInset = with(LocalDensity.current) { max(Float(0.0), materialBarHeightPx).toDp() }
        WithGlassTabBarInset(inset: materialInset, contentInset: 0.dp, content: content)
        return
    }
    WithGlassTabBarInset(inset: state.inset, contentInset: state.contentInset, content: content)
}

/// How far an enclosing `TabView`'s glass bar reaches over this content, including the system navigation bar and any
/// outer bar when nested. Zero outside a `TabView`, and zero for the Material bar, which takes its own space.
@Composable func liquidGlassTabBarInset() -> Dp {
    return LocalGlassTabBarInset.current
}

/// The space a scrollable adds after its content so the end clears the chrome floating over it.
///
/// Nothing moves: content still draws to the bottom of the screen and refracts through the glass, the scrollable is
/// just that much longer — how an iOS list clears a floating tab bar.
@Composable func liquidGlassScrollContentInset() -> Dp {
    return LocalGlassContentInset.current
}

/// Render navigation content under a floating bottom toolbar, adding the toolbar's height to the scroll inset so
/// nothing is stranded beneath it.
///
/// - Parameters:
///   - barHeightPx: The measured height of the bottom toolbar, or 0 when there is none.
@Composable func LiquidGlassBottomBarContent(barHeightPx: Float, content: @Composable () -> Void) {
    guard isLiquidGlassBottomBarFloating(), barHeightPx > Float(0.0) else {
        content()
        return
    }
    let barHeight = with(LocalDensity.current) { barHeightPx.toDp() }
    WithGlassContentInset(inset: LocalGlassContentInset.current + barHeight, content: content)
}

/// How far a floating bottom toolbar lifts to clear the tab bar below it.
///
/// Zero on the `NATIVE` tier, where the layout already places the Material toolbar above the Material tab bar and
/// adding the published inset would apply it twice.
@Composable func liquidGlassFloatingBottomBarInset() -> Dp {
    guard isLiquidGlassBottomBarFloating() else {
        return 0.dp
    }
    return liquidGlassTabBarInset()
}

/// Whether toolbars render as glass: floating capsules over the content, rather than a Material bar.
///
/// True wherever glass renders, with or without a `TabView`. Inside one the bottom toolbar sits above the tab bar, on
/// its own it sits above the system navigation bar, and either way the content keeps running underneath it.
@Composable func isLiquidGlassToolbarEnabled() -> Bool {
    return EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE
}

/// - Seealso: `isLiquidGlassToolbarEnabled()`
@Composable func isLiquidGlassBottomBarFloating() -> Bool {
    return isLiquidGlassToolbarEnabled()
}

/// Whether the `TabView` bottom bar renders as the glass tab bar, rather than the Material `NavigationBar` for the
/// `NATIVE` Liquid Glass tier.
@Composable func isLiquidGlassTabBarEnabled() -> Bool {
    return EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE
}

/// Render the `TabView` bottom bar as a floating glass bar instead of the Material `NavigationBar`.
///
/// Icons, titles, selection, enablement and colors all come from the same `Material3NavigationBarOptions` the Material
/// bar uses, so `material3NavigationBar(_:)` customization applies here too, and `nil` or hidden tabs are skipped.
///
/// Unlike the Material bar it raises no system background when content scrolls under it — glass is meant to show the
/// content moving beneath — so only an app-specified background is drawn. Non-color backgrounds are ignored for now.
///
/// - Parameters:
///   - state: The bar state shared with the `TabView`.
///   - tabs: The `TabView` tabs, indexed like its routes.
///   - selectedTabIndex: The index of the selected tab.
///   - options: The resolved navigation bar options.
///   - tabBarPreferences: The reduced tab bar preferences, for an app-specified background.
@Composable func LiquidGlassTabViewBar(state: LiquidGlassTabBarState, tabs: kotlin.collections.List<Tab?>, selectedTabIndex: Int, options: Material3NavigationBarOptions, tabBarPreferences: ToolbarBarPreferences) {
    let tabIndices = mutableListOf<Int>()
    for tabIndex in 0..<tabs.size {
        if tabs[tabIndex] == nil || tabs[tabIndex]?.isHidden == true {
            continue
        }
        tabIndices.add(tabIndex)
    }
    // A disabled tab is dimmed and unselectable, as `NavigationBarItem(enabled:)` makes it in the Material bar
    let isTabEnabled: (Int) -> Bool = { tabIndex in
        return options.itemEnabled(tabIndex) && tabs[tabIndex]?.isDisabled != true
    }
    // The selected tab takes the `tint`, or the app's accent color when no tint is set, as elsewhere in SkipUI
    let accentColor = (EnvironmentValues.shared._tint ?? Color.accentColor).asComposeColor()
    // A tab label is `Text`, which takes its color from the SkipUI environment rather than Compose's `LocalContentColor`.
    // Forwarding the bar's per-tab content color means the selected label takes the tint, as it does on iOS
    let itemLabel: (@Composable (Int) -> Void)? = options.itemLabel == nil ? nil : { tabIndex in
        let labelColor = LocalContentColor.current
        EnvironmentValues.shared.setValues {
            $0.set_foregroundStyle(Color(colorImpl: { labelColor }))
            return ComposeResult.ok
        } in: {
            options.itemLabel?(tabIndex)
        }
    }
    // Only a background the app asked for, so scrolling content does not raise an opaque system background over the glass
    let backgroundColor = tabBarPreferences.backgroundVisibility == Visibility.hidden ? nil : tabBarPreferences.background?.asColor(opacity: 1.0, animationContext: nil)
    LiquidGlassTabView(state: state, tabIndices: tabIndices, selectedTabIndex: selectedTabIndex, icon: options.itemIcon, label: itemLabel, isEnabled: isTabEnabled, onTabSelected: options.onItemClick, accentColor: accentColor, backgroundColor: backgroundColor ?? androidx.compose.ui.graphics.Color.Unspecified, contentColor: options.contentColor)
}

// MARK: - EnvironmentValues: tab bar minimize behavior

struct TabBarMinimizeBehaviorKey: EnvironmentKey {
    static let defaultValue: TabBarMinimizeBehavior = .automatic
}

extension EnvironmentValues {
    /// How the `TabView` bar in this subtree reacts to scrolling, from `tabBarMinimizeBehavior(_:)`.
    ///
    /// Only the glass tab bar acts on it; the Material `NavigationBar` keeps its size.
    var tabBarMinimizeBehavior: TabBarMinimizeBehavior {
        get { self[TabBarMinimizeBehaviorKey.self] }
        set { self[TabBarMinimizeBehaviorKey.self] = newValue }
    }
}
#endif
#endif
