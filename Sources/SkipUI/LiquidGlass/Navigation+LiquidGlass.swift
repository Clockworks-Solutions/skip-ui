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

/// The top bar back button, as glass in place of the Material icon button.
///
/// - Parameters:
///   - isProminent: `true` in place of `FilledIconButton`, `false` in place of `IconButton`.
///   - colors: Material 3 navigation icon button colors, which apply only to the Material buttons.
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

/// The backdrop a floating toolbar refracts, recorded from the `NavigationStack` content.
@Composable func rememberLiquidGlassNavBackdrop() -> LayerBackdrop {
    return rememberLayerBackdrop()
}

/// Records the navigation content into `backdrop`, or nothing when no floating toolbar samples it.
@Composable func liquidGlassNavContentModifier(_ backdrop: LayerBackdrop) -> Modifier {
    guard isLiquidGlassToolbarEnabled() else {
        return Modifier
    }
    return Modifier.layerBackdrop(backdrop)
}

// MARK: - Grouping

/// One capsule of a floating toolbar.
struct LiquidGlassToolbarItemGroup {
    /// The items on this capsule, in order.
    let items: kotlin.collections.List<Renderable>
    /// Whether a flexible `ToolbarSpacer` precedes it, which takes the width left over instead of a fixed gap.
    let isPrecededByFlexibleSpacer: Bool
    /// Whether the item brings its own surface, and so is drawn alone rather than on a capsule.
    let drawsOwnBackground: Bool
}

/// Whether a button style makes an item a capsule in its own right.
///
/// Only the prominent styles do: iOS 26 merges a plain `.glass` or `.bordered` item into the capsule its group
/// shares, and pulls out only the tinted ones.
func liquidGlassButtonStyleDrawsOwnBackground(_ style: Any) -> Bool {
    return style is GlassProminentButtonStyle || style is BorderedProminentButtonStyle
}

/// Whether a toolbar item brings its own surface.
///
/// Only a button style is detectable: `.background(_:)` and `glassEffect()` both compose into the generic
/// `RenderModifier`, and a filled shape baked into an image asset is invisible to any inspection.
@Composable func liquidGlassToolbarItemDrawsOwnBackground(_ item: Renderable, context: ComposeContext) -> Bool {
    // A `ToolbarItem` holds its content unevaluated, so the style is on the button inside it, not on the item
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

/// Split toolbar items into one group per capsule.
///
/// iOS 26 breaks a toolbar at every `ToolbarSpacer`, whatever its sizing; the sizing decides only how far apart the
/// capsules end up. An item that brings its own surface breaks the run the same way. A spacer before the first item,
/// or two in a row, goes in front of the capsule that follows and counts as flexible if either was.
@Composable func liquidGlassToolbarGroups(_ items: kotlin.collections.List<Renderable>, context: ComposeContext) -> kotlin.collections.List<LiquidGlassToolbarItemGroup> {
    let groups = mutableListOf<LiquidGlassToolbarItemGroup>()
    var group = mutableListOf<Renderable>()
    var isPrecededByFlexibleSpacer = false
    for item in items {
        // `ToolbarItems.Evaluate` inserts a plain `Spacer` to spread items across a Material bar. Its weight would
        // stretch a capsule across the screen, and grouping comes from `ToolbarSpacer` anyway
        if item.strip() is Spacer {
            continue
        }
        if let spacer = item.strip() as? ToolbarSpacer {
            let isFlexible = spacer.sizing != SpacerSizing.fixed
            if group.isEmpty() {
                isPrecededByFlexibleSpacer = isPrecededByFlexibleSpacer || isFlexible
            } else {
                groups.add(LiquidGlassToolbarItemGroup(items: group, isPrecededByFlexibleSpacer: isPrecededByFlexibleSpacer, drawsOwnBackground: false))
                group = mutableListOf<Renderable>()
                isPrecededByFlexibleSpacer = isFlexible
            }
        } else if liquidGlassToolbarItemDrawsOwnBackground(item, context: context) {
            if !group.isEmpty() {
                groups.add(LiquidGlassToolbarItemGroup(items: group, isPrecededByFlexibleSpacer: isPrecededByFlexibleSpacer, drawsOwnBackground: false))
                group = mutableListOf<Renderable>()
                isPrecededByFlexibleSpacer = false
            }
            let styledItem = mutableListOf<Renderable>()
            styledItem.add(item)
            groups.add(LiquidGlassToolbarItemGroup(items: styledItem, isPrecededByFlexibleSpacer: isPrecededByFlexibleSpacer, drawsOwnBackground: true))
            isPrecededByFlexibleSpacer = false
        } else {
            group.add(item)
        }
    }
    if !group.isEmpty() {
        groups.add(LiquidGlassToolbarItemGroup(items: group, isPrecededByFlexibleSpacer: isPrecededByFlexibleSpacer, drawsOwnBackground: false))
    }
    return groups
}

// MARK: - Rendering

/// Render one group: a prominent item alone at bar height, or the items on a shared glass capsule.
@Composable func RenderLiquidGlassToolbarGroup(group: LiquidGlassToolbarItemGroup, height: Dp, context: ComposeContext) {
    guard !group.drawsOwnBackground else {
        let sizedContext = context.content(modifier: Modifier.liquidGlassToolbarItemSize(height))
        for renderable in group.items {
            renderable.Render(context: sizedContext)
        }
        return
    }
    LiquidGlassToolbarGroup(height: height) {
        for renderable in group.items {
            renderable.Render(context: context)
        }
    }
}

/// Render top bar items as glass capsules, or plainly on the `NATIVE` tier.
@Composable func LiquidGlassTopToolbarItems(items: kotlin.collections.List<Renderable>, context: ComposeContext) {
    guard isLiquidGlassToolbarEnabled(), !items.isEmpty() else {
        for renderable in items {
            renderable.Render(context: context)
        }
        return
    }
    let groups = liquidGlassToolbarGroups(items, context: context)
    // Dropping the bar's own 12dp-per-item padding, which the capsule's inset replaces
    let itemContext = context.content()
    for groupIndex in 0..<groups.size {
        // The bar places its leading and trailing slots itself, so a flexible spacer has no width to take and both
        // sizings come out as the same gap. The first capsule is spaced too: what precedes it is the back button or
        // the title, and a capsule drawn hard against either reads as one shape
        androidx.compose.foundation.layout.Spacer(modifier: Modifier.width(liquidGlassToolbarSpacing))
        RenderLiquidGlassToolbarGroup(group: groups[groupIndex], height: liquidGlassTopToolbarHeight, context: itemContext)
    }
    // And one after the last, so the trailing group keeps the same margin from the edge of the bar
    androidx.compose.foundation.layout.Spacer(modifier: Modifier.width(liquidGlassToolbarSpacing))
}

/// Render bottom toolbar items as glass capsules floating over the content, above the glass tab bar.
///
/// - Parameters:
///   - items: The bottom toolbar items, in order, including any spacers.
///   - backdrop: The recorded navigation content the glass refracts.
///   - modifier: The bar modifier, which lifts the toolbar above the tab bar and measures it.
@Composable func LiquidGlassBottomToolbar(items: kotlin.collections.List<Renderable>, backdrop: Backdrop, modifier: Modifier, context: ComposeContext) {
    let groups = liquidGlassToolbarGroups(items, context: context)
    guard !groups.isEmpty() else {
        return
    }
    // Inside a `TabView` the bar modifier already lifts the toolbar clear of the system navigation bar
    let systemBarModifier = liquidGlassFloatingBottomBarInset() > 0.dp ? Modifier : Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    let itemContext = context.content()
    // Without a flexible spacer there is nothing to spread the capsules apart, and iOS 26 centers them
    var hasFlexibleSpacer = false
    for groupIndex in 0..<groups.size {
        if groups[groupIndex].isPrecededByFlexibleSpacer {
            hasFlexibleSpacer = true
        }
    }
    WithGlassBackdrop(backdrop: backdrop) {
        Row(
            modifier: modifier
                .then(systemBarModifier)
                .fillMaxWidth()
                .padding(start: liquidGlassToolbarSpacing, end: liquidGlassToolbarSpacing, bottom: liquidGlassToolbarGap),
            horizontalArrangement: hasFlexibleSpacer ? Arrangement.Start : Arrangement.Center,
            verticalAlignment: Alignment.CenterVertically
        ) {
            // Spacers are placed here rather than by the arrangement, since one bar can hold both kinds
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
