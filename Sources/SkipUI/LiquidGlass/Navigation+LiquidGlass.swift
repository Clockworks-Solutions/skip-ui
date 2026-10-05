//
//  Navigation+LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 17/09/26.
//
//  Liquid Glass (Clockworks fork): glass back button and floating glass toolbars for `NavigationStack`.
//

#if !SKIP_BRIDGE
import Foundation
#if SKIP
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import skip.ui.liquidglass.__

// MARK: - Back button

/// The back button as glass; the Material `FilledIconButton` (prominent) or `IconButton` on the `NATIVE` tier.
@Composable func LiquidGlassNavigationBackButton(isProminent: Bool, colors: IconButtonColors?, onClick: () -> Void, icon: @Composable () -> Void) {
    guard isLiquidGlassToolbarEnabled() else {
        if isProminent {
            FilledIconButton(onClick: onClick, colors: colors ?? IconButtonDefaults.filledIconButtonColors()) { icon() }
        } else {
            IconButton(onClick: onClick, colors: colors ?? IconButtonDefaults.iconButtonColors()) { icon() }
        }
        return
    }
    LiquidGlassButton(
        modifier: Modifier.padding(start = liquidGlassToolbarSpacing),
        enabled: true,
        isProminent: isProminent,
        onClick: onClick,
        contentPadding: PaddingValues(horizontal: liquidGlassButtonContentInset, vertical: 8.dp)
    ) { icon() }
}

// MARK: - Backdrop

/// The `NavigationStack` content recording that floating toolbars refract.
@Composable func rememberLiquidGlassNavBackdrop() -> LayerBackdrop {
    return rememberLayerBackdrop()
}

/// Records the navigation content into `backdrop`; nothing on the `NATIVE` tier.
@Composable func liquidGlassNavContentModifier(_ backdrop: LayerBackdrop) -> Modifier {
    guard isLiquidGlassToolbarEnabled() else {
        return Modifier
    }
    return Modifier.layerBackdrop(backdrop)
}

// MARK: - Grouping

/// What a group of toolbar items sits on.
enum LiquidGlassToolbarSurface {
    /// A glass capsule shared by the group: the default for every system placement, whatever the button style.
    case capsule
    /// The item's own prominent glass, drawn by its button style.
    case own
    /// Nothing: `.principal` content, which iOS 26 never puts on glass, buttons included.
    case plain
}

/// One group of a glass toolbar: the items between two spacers, or one item that sits apart.
struct LiquidGlassToolbarItemGroup {
    let items: kotlin.collections.List<Renderable>
    /// Whether a flexible `ToolbarSpacer` precedes it, taking the leftover width.
    let isPrecededByFlexibleSpacer: Bool
    let surface: LiquidGlassToolbarSurface
}

/// Whether a button style makes an item its own capsule: only the prominent ones do on iOS 26.
func liquidGlassButtonStyleDrawsOwnBackground(_ style: Any) -> Bool {
    return style is GlassProminentButtonStyle || style is BorderedProminentButtonStyle
}

/// The surface a toolbar item sits on alone, or `nil` when it shares the group's capsule.
@Composable func liquidGlassToolbarItemSurface(_ item: Renderable, context: ComposeContext) -> LiquidGlassToolbarSurface? {
    if (item.strip() as? ToolbarItem)?.placement == ToolbarItemPlacement.principal {
        return .plain
    }
    return liquidGlassToolbarItemDrawsOwnBackground(item, context: context) ? .own : nil
}

/// Whether a toolbar item draws its own surface; only detectable from its button style.
@Composable func liquidGlassToolbarItemDrawsOwnBackground(_ item: Renderable, context: ComposeContext) -> Bool {
    // A `ToolbarItem`'s style is on the button inside it
    var renderables = listOf(item)
    if let toolbarItem = item.strip() as? ToolbarItem {
        renderables = toolbarItem.content.Evaluate(context: context, options: 0)
    }
    for renderable in renderables {
        let match = renderable.forEachModifier { modifier in
            if let styleModifier = modifier as? ButtonStyleModifier, liquidGlassButtonStyleDrawsOwnBackground(styleModifier.style) {
                return true
            }
            return nil
        }
        if match == true {
            return true
        }
    }
    return false
}

/// Splits toolbar items into capsules, breaking at every `ToolbarSpacer` and around items that sit apart (prominent
/// buttons and `.principal` content), as iOS 26 does. Consecutive spacers merge, flexible if either is.
@Composable func liquidGlassToolbarGroups(_ items: kotlin.collections.List<Renderable>, context: ComposeContext) -> kotlin.collections.List<LiquidGlassToolbarItemGroup> {
    let groups = mutableListOf<LiquidGlassToolbarItemGroup>()
    var group = mutableListOf<Renderable>()
    var isPrecededByFlexibleSpacer = false
    for item in items {
        // The plain `Spacer` SkipUI inserts for the Material bar would stretch a capsule
        if item.strip() is Spacer {
            continue
        }
        if let spacer = item.strip() as? ToolbarSpacer {
            let isFlexible = spacer.sizing != SpacerSizing.fixed
            if group.isEmpty() {
                isPrecededByFlexibleSpacer = isPrecededByFlexibleSpacer || isFlexible
            } else {
                groups.add(LiquidGlassToolbarItemGroup(items: group, isPrecededByFlexibleSpacer: isPrecededByFlexibleSpacer, surface: .capsule))
                group = mutableListOf<Renderable>()
                isPrecededByFlexibleSpacer = isFlexible
            }
        } else if let surface = liquidGlassToolbarItemSurface(item, context: context) {
            if !group.isEmpty() {
                groups.add(LiquidGlassToolbarItemGroup(items: group, isPrecededByFlexibleSpacer: isPrecededByFlexibleSpacer, surface: .capsule))
                group = mutableListOf<Renderable>()
                isPrecededByFlexibleSpacer = false
            }
            groups.add(LiquidGlassToolbarItemGroup(items: listOf(item), isPrecededByFlexibleSpacer: isPrecededByFlexibleSpacer, surface: surface))
            isPrecededByFlexibleSpacer = false
        } else {
            group.add(item)
        }
    }
    if !group.isEmpty() {
        groups.add(LiquidGlassToolbarItemGroup(items: group, isPrecededByFlexibleSpacer: isPrecededByFlexibleSpacer, surface: .capsule))
    }
    return groups
}

// MARK: - Rendering

/// Renders one group on its surface. Anything taller than the room inside is scaled to fit, as iOS keeps a toolbar at
/// its height.
@Composable func RenderLiquidGlassToolbarGroup(group: LiquidGlassToolbarItemGroup, height: Dp, context: ComposeContext) {
    let maxItemHeight = height - liquidGlassToolbarIconInset - liquidGlassToolbarIconInset
    switch group.surface {
    case .capsule:
        LiquidGlassToolbarGroup(height: height) {
            for renderable in group.items {
                LiquidGlassFitContent(maxHeight: maxItemHeight) {
                    renderable.Render(context: context)
                }
            }
        }
    case .own:
        // A glass button, which fits its own label
        let sizedContext = context.content(modifier: Modifier.liquidGlassToolbarItemSize(height))
        WithGlassToolbarItemMaxHeight(maxHeight: maxItemHeight) {
            for renderable in group.items {
                renderable.Render(context: sizedContext)
            }
        }
    case .plain:
        for renderable in group.items {
            LiquidGlassFitContent(maxHeight: height) {
                renderable.Render(context: context)
            }
        }
    }
}

/// Renders top bar items as glass capsules, spaced evenly from the back button or title and the bar's edge; plainly on
/// the `NATIVE` tier.
@Composable func LiquidGlassTopToolbarItems(items: kotlin.collections.List<Renderable>, context: ComposeContext) {
    guard isLiquidGlassToolbarEnabled(), !items.isEmpty() else {
        for renderable in items {
            renderable.Render(context: context)
        }
        return
    }
    let groups = liquidGlassToolbarGroups(items, context: context)
    // Without the bar's per-item padding, which the capsule's inset replaces
    let itemContext = context.content()
    for groupIndex in 0..<groups.size {
        androidx.compose.foundation.layout.Spacer(modifier: Modifier.width(liquidGlassToolbarSpacing))
        RenderLiquidGlassToolbarGroup(group: groups[groupIndex], height: liquidGlassTopToolbarHeight, context: itemContext)
    }
    androidx.compose.foundation.layout.Spacer(modifier: Modifier.width(liquidGlassToolbarSpacing))
}

/// Renders bottom toolbar items as glass capsules floating above the tab bar, centered unless a flexible spacer
/// spreads them, as on iOS 26.
///
/// - Parameters:
///   - backdrop: The navigation content recording the capsules refract.
///   - modifier: The bar modifier, which lifts and measures the toolbar.
@Composable func LiquidGlassBottomToolbar(items: kotlin.collections.List<Renderable>, backdrop: Backdrop, modifier: Modifier, context: ComposeContext) {
    let groups = liquidGlassToolbarGroups(items, context: context)
    guard !groups.isEmpty() else {
        return
    }
    // Inside a `TabView` the bar modifier already clears the system bar
    let systemBarModifier = liquidGlassFloatingBottomBarInset() > 0.dp ? Modifier : Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    let itemContext = context.content()
    let hasFlexibleSpacer = groups.any { $0.isPrecededByFlexibleSpacer }
    WithGlassBackdrop(backdrop: backdrop) {
        Row(
            modifier: modifier
                .then(systemBarModifier)
                .fillMaxWidth()
                .padding(start: liquidGlassToolbarSpacing, end: liquidGlassToolbarSpacing, bottom: liquidGlassToolbarGap),
            horizontalArrangement: hasFlexibleSpacer ? Arrangement.Start : Arrangement.Center,
            verticalAlignment: Alignment.CenterVertically
        ) {
            for groupIndex in 0..<groups.size {
                let group = groups[groupIndex]
                if group.isPrecededByFlexibleSpacer {
                    androidx.compose.foundation.layout.Spacer(modifier: Modifier.weight(Float(1.0)))
                } else if groupIndex > 0 {
                    androidx.compose.foundation.layout.Spacer(modifier: Modifier.width(liquidGlassToolbarSpacing))
                }
                RenderLiquidGlassToolbarGroup(group: group, height: liquidGlassToolbarHeight, context: itemContext)
            }
        }
    }
}

#endif
#endif
