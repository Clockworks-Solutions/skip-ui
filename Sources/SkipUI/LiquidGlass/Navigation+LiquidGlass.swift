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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import skip.ui.liquidglass.__

// MARK: - Back button

/// The back button as a glass circle with a chevron, as on iOS 26; the Material `FilledIconButton` (prominent) or
/// `IconButton` on the `NATIVE` tier.
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
        modifier: Modifier.padding(start = liquidGlassToolbarSpacing).size(liquidGlassTopToolbarHeight),
        enabled: true,
        isProminent: isProminent,
        onClick: onClick,
        contentPadding: PaddingValues(0.dp)
    ) {
        // iOS 26's chevron rather than Material's arrow, mirrored for right-to-left
        let mirror = LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl ? Float(-1.0) : Float(1.0)
        Icon(imageVector: Icons.Rounded.ArrowBackIosNew, contentDescription: "Back", modifier: Modifier.size(20.dp).scale(scaleX: mirror, scaleY: Float(1.0)), tint: (EnvironmentValues.shared._tint ?? Color.primary).colorImpl())
    }
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

// MARK: - Top bar

/// Whether the top bar is glass, as on iOS 26: transparent, with the content scrolling beneath it onto a blurred edge.
///
/// An app-set background, `.visible` background visibility, or the legacy column layout keep the Material bar.
@Composable func isLiquidGlassTopBarEnabled(_ preferences: ToolbarBarPreferences?) -> Bool {
    guard isLiquidGlassToolbarEnabled(), EnvironmentValues.shared._layoutImplementationVersion >= 2 else {
        return false
    }
    // `isSystemBackground` is left out: a grouped `List` sets it to match its background, which runs beneath the glass bar
    return preferences?.background == nil && preferences?.backgroundVisibility != Visibility.visible
}

/// The iOS 26 navigation bar in place of Material's: a centered title, and a large one that collapses into it.
// SKIP INSERT: @OptIn(ExperimentalMaterial3Api::class)
@Composable func LiquidGlassNavigationBar(title: @Composable () -> Void, navigationIcon: @Composable () -> Void, actions: @Composable () -> Void, isLarge: Bool, scrollBehavior: TopAppBarScrollBehavior, windowInsets: WindowInsets, titleColor: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    LiquidGlassTopAppBar(title: title, navigationIcon: navigationIcon, actions: actions, isLarge: isLarge, scrollBehavior: scrollBehavior, windowInsets: windowInsets, titleColor: titleColor, modifier: modifier)
}

/// The space before the title: none on the glass bar, which centers it; upstream's left-aligned bar keeps it clear of the
/// glass back button.
@Composable func liquidGlassTitleLeadingPadding() -> Dp {
    return LocalGlassTitleLeadingPadding.current
}

/// Whether content has scrolled beneath the bar. Observed as a derived state, so the bar recomposes when that changes
/// rather than on every scrolled pixel.
// SKIP INSERT: @OptIn(ExperimentalMaterial3Api::class)
@Composable func isTopBarOverlapped(_ scrollBehavior: TopAppBarScrollBehavior) -> Bool {
    let isOverlapped = remember(scrollBehavior) { derivedStateOf { scrollBehavior.state.overlappedFraction > Float(0.0) } }
    return isOverlapped.value
}

/// The top bar's background: none on the glass bar, whose edge effect replaces it.
@Composable func liquidGlassTopBarBackgroundColor(_ preferences: ToolbarBarPreferences?) -> androidx.compose.ui.graphics.Color {
    return isLiquidGlassTopBarEnabled(preferences) ? androidx.compose.ui.graphics.Color.Transparent : Color.systemBarBackground.colorImpl()
}

/// Renders the top bar, whose glass back button and capsules refract the navigation content beneath a glass bar.
@Composable func LiquidGlassTopBar(isGlass: Bool, backdrop: Backdrop, content: @Composable () -> Void) {
    guard isGlass else {
        content()
        return
    }
    WithGlassBackdrop(backdrop: backdrop, content: content)
}

/// The blurred edge a glass top bar floats on, washed toward the background as on iOS: lighter in a light theme, darker
/// in a dark one. None when the bar's background is hidden, so content stays sharp beneath it.
// SKIP INSERT: @OptIn(ExperimentalMaterial3Api::class)
@Composable func LiquidGlassTopBarEdgeEffect(isGlass: Bool, backdrop: Backdrop, height: Dp, isLarge: Bool, scrollBehavior: TopAppBarScrollBehavior, preferences: ToolbarBarPreferences?) {
    guard isGlass, height > 0.dp, preferences?.backgroundVisibility != Visibility.hidden else {
        return
    }
    // The background the bar would paint: its `toolbarColorScheme`'s, a grouped list's, or the screen's
    let background = preferences?.colorScheme?.asMaterialTheme().background ?? (preferences?.isSystemBackground == true ? Color.systemBarBackground.colorImpl() : Color.background.colorImpl())
    LiquidGlassTopBarEdge(backdrop: backdrop, height: height, isLarge: isLarge, scrollBehavior: scrollBehavior, wash: background, modifier: Modifier.zIndex(Float(1.0)).fillMaxWidth())
}

/// The content's safe area under a glass top bar. Its top follows the content's padding, also while a large title
/// collapses, so a scrollable running beneath the bar measures against where the content actually starts.
@Composable func liquidGlassNavContentSafeArea(_ safeArea: SafeArea?, isGlass: Bool, topPadding: Dp) -> SafeArea? {
    guard isGlass, let safeArea else {
        return safeArea
    }
    let safe = safeArea.safeBoundsPx
    // Rounded as the padding is laid out, so the content's top matches exactly
    let top = safeArea.presentationBoundsPx.top + with(LocalDensity.current) { Float(topPadding.roundToPx()) }
    return SafeArea(presentation: safeArea.presentationBoundsPx, safe: Rect(left: safe.left, top: top, right: safe.right, bottom: safe.bottom), absoluteSystemBars: safeArea.absoluteSystemBarEdges)
}

/// Renders navigation content: a scrollable at its top runs beneath a glass top bar, and scrollables clear a floating
/// bottom toolbar.
@Composable func LiquidGlassNavContent(isGlassTopBar: Bool, bottomBarHeightPx: Float, content: @Composable () -> Void) {
    WithGlassTopBarUnderlay(isUnderlaid: isGlassTopBar) {
        LiquidGlassBottomBarContent(barHeightPx: bottomBarHeightPx, content: content)
    }
}

/// The edges a scrollable runs beneath, clearing the chrome there with a header: the top under a glass top bar, for a
/// vertical scrollable.
@Composable func liquidGlassScrollUnderEdges(axes: Axis.Set = .vertical) -> Edge.Set {
    return axes.contains(.vertical) && LocalGlassTopBarUnderlay.current ? .top : []
}

/// The header a scrollable adds to clear the chrome it runs beneath by `expansion`.
@Composable func liquidGlassScrollHeaderHeight(_ expansion: IntRect) -> Dp {
    return with(LocalDensity.current) { expansion.top.toDp() }
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
