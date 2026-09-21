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

/// A modifier for the tab content: it records the content into the glass tab bar backdrop, and watches it scroll so the
/// bar can minimize when `tabBarMinimizeBehavior(_:)` asks it to. No modifier when the Liquid Glass tier is `NATIVE`
/// and the Material bar is shown.
@Composable func liquidGlassTabBarContentModifier(_ state: LiquidGlassTabBarState) -> Modifier {
    guard isLiquidGlassTabBarEnabled() else {
        return Modifier
    }
    let minimizeConnection = rememberGlassTabBarMinimizeConnection(state: state, behavior: liquidGlassTabBarMinimizeBehavior())
    return Modifier.layerBackdrop(state.backdrop).nestedScroll(minimizeConnection)
}

/// The glass tab bar's reaction to scrolling, from the `tabBarMinimizeBehavior(_:)` environment value.
///
/// `.automatic` never minimizes, as it does on iOS today, where minimizing is opt-in.
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

/// Whether the `TabView` bottom bar renders as the glass tab bar, rather than the Material `NavigationBar` for the
/// `NATIVE` Liquid Glass tier.
@Composable func isLiquidGlassTabBarEnabled() -> Bool {
    return EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE
}

/// Render the `TabView` bottom bar as a floating `LiquidGlassTabView` instead of the Material `NavigationBar`.
///
/// Uses the same icons, titles, and selection handling as the Material bar, including any `material3NavigationBar(_:)`
/// customization of `itemIcon`, `itemLabel`, `itemEnabled`, and `onItemClick`. Tabs that are `nil` (from a `false`
/// conditional) or hidden are skipped, as in the Material bar.
///
/// Colors follow the Material bar too: the selected tab takes the `tint` environment value, falling back to the app's
/// accent color, a tab bar background set with `toolbarBackground(_:for: .tabBar)` is drawn over the glass frost, and
/// unselected tabs take the bar's content color.
///
/// Unlike the Material bar, the glass bar does not raise a system background when content scrolls under it: glass is
/// meant to show the content moving beneath, so only a background the app asks for is drawn. A gradient or other
/// non-color background is ignored for now.
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
