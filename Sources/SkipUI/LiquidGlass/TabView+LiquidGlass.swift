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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/// Remember the backdrop that records `TabView` content for the glass tab bar.
@Composable func rememberLiquidGlassTabBarBackdrop() -> LayerBackdrop {
    return rememberLayerBackdrop()
}

/// A modifier that records the tab content it is applied to into the glass tab bar backdrop.
func liquidGlassTabBarBackdropModifier(_ backdrop: LayerBackdrop) -> Modifier {
    return Modifier.layerBackdrop(backdrop)
}

/// Render the `TabView` bottom bar as a floating `LiquidGlassTabView` instead of the Material `NavigationBar`.
///
/// Uses the same icons, titles, and selection handling as the Material bar, including any `material3NavigationBar(_:)`
/// customization of `itemIcon`, `itemLabel`, and `onItemClick`. Tabs that are `nil` (from a `false` conditional) or hidden
/// are skipped, as in the Material bar.
///
/// - Parameters:
///   - backdrop: The recorded tab content that the bar refracts.
///   - tabs: The `TabView` tabs, indexed like its routes.
///   - selectedTabIndex: The index of the selected tab.
///   - options: The resolved navigation bar options.
@Composable func LiquidGlassTabViewNavigationBar(backdrop: Backdrop, tabs: kotlin.collections.List<Tab?>, selectedTabIndex: Int, options: Material3NavigationBarOptions) {
    let tabIndices = mutableListOf<Int>()
    for tabIndex in 0..<tabs.size {
        if tabs[tabIndex] == nil || tabs[tabIndex]?.isHidden == true {
            continue
        }
        tabIndices.add(tabIndex)
    }
    LiquidGlassTabView(backdrop: backdrop, tabIndices: tabIndices, selectedTabIndex: selectedTabIndex, icon: options.itemIcon, label: options.itemLabel, onTabSelected: options.onItemClick)
}
#endif
#endif
