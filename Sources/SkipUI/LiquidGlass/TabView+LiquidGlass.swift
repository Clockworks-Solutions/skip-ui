//
//  TabView+LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 17/09/26.
//
//  Liquid Glass (Clockworks fork): the glass tab bar for `TabView`.
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
import com.kyant.backdrop.backdrops.layerBackdrop
import skip.ui.liquidglass.__

/// The state one `TabView` shares with its glass bar: the recorded content, the bar's insets and whether it is minimized.
@Composable func rememberLiquidGlassTabBarState() -> LiquidGlassTabBarState {
    return skip.ui.liquidglass.rememberLiquidGlassTabBarState()
}

/// Records the tab content for the bar and minimizes the bar on scroll, per `tabBarMinimizeBehavior(_:)`.
@Composable func liquidGlassTabBarContentModifier(_ state: LiquidGlassTabBarState) -> Modifier {
    guard isLiquidGlassTabBarEnabled() else {
        return Modifier
    }
    let minimizeConnection = rememberGlassTabBarMinimizeConnection(state: state, behavior: liquidGlassTabBarMinimizeBehavior())
    return Modifier.layerBackdrop(state.backdrop).nestedScroll(minimizeConnection)
}

/// The behavior set on this `TabView`; `.automatic` and `.never` never minimize, as on iOS.
@Composable func liquidGlassTabBarMinimizeBehavior() -> GlassTabBarMinimizeBehavior {
    switch EnvironmentValues.shared.tabBarMinimizeBehavior {
    case .onScrollDown: return GlassTabBarMinimizeBehavior.ON_SCROLL_DOWN
    case .onScrollUp: return GlassTabBarMinimizeBehavior.ON_SCROLL_UP
    default: return GlassTabBarMinimizeBehavior.NEVER
    }
}

/// The glass bar switches tabs instantly, as iOS does, rather than with Navigation 3's 700ms crossfade, which keeps both
/// tabs composed and drawn throughout. An app's `tabViewTransitions` still applies on top.
@Composable func liquidGlassTabViewTransitionDefaults() -> NavDisplayTransitionOptions {
    return isLiquidGlassTabBarEnabled() ? NavDisplayTransitionOptions(transition: .none, popTransition: .none) : NavDisplayTransitionOptions.tabViewDefaults
}

/// Renders a tab's content with the insets of the bar over it.
///
/// The glass bar takes no space, so it publishes how far it reaches; the Material bar publishes its height so a nested
/// bar clears it. An outer `safeAreaInset` is drawn over the bar, so content clears the higher of the two. The minimize
/// behavior is reset, as it applies only to the `TabView` it is set on.
@Composable func LiquidGlassTabContent(state: LiquidGlassTabBarState, materialBarHeightPx: Float, content: @Composable () -> Void) {
    let outerInset = LocalGlassOuterBottomInset.current
    let tabContent: @Composable () -> Void = {
        EnvironmentValues.shared.setValues {
            $0.settabBarMinimizeBehavior(TabBarMinimizeBehavior.automatic)
            return ComposeResult.ok
        } in: {
            content()
        }
    }
    guard isLiquidGlassTabBarEnabled() else {
        let materialInset = with(LocalDensity.current) { max(Float(0.0), materialBarHeightPx).toDp() }
        WithGlassTabBarInset(inset: materialInset, contentInset: max(0.dp, outerInset - materialInset), content: tabContent)
        return
    }
    WithGlassTabBarInset(inset: state.inset, contentInset: max(state.contentInset, outerInset), content: tabContent)
}

/// Renders a sheet or cover's content free of the presenting screen's glass state, as an iOS presentation is its own
/// window.
@Composable func LiquidGlassPresentationContent(content: @Composable () -> Void) {
    WithoutGlassChrome(content: content)
}

/// How far an enclosing glass bar reaches over this content; 0 outside one, and for the Material bar.
@Composable func liquidGlassTabBarInset() -> Dp {
    return LocalGlassTabBarInset.current
}

/// What a scrollable adds after its content to clear the glass chrome over it.
@Composable func liquidGlassScrollContentInset() -> Dp {
    return LocalGlassContentInset.current
}

/// Renders navigation content under a floating bottom toolbar, adding the toolbar to the scroll inset.
@Composable func LiquidGlassBottomBarContent(barHeightPx: Float, content: @Composable () -> Void) {
    guard isLiquidGlassBottomBarFloating(), barHeightPx > Float(0.0) else {
        content()
        return
    }
    let barHeight = with(LocalDensity.current) { barHeightPx.toDp() }
    WithGlassContentInset(inset: LocalGlassContentInset.current + barHeight, content: content)
}

/// How far a floating bottom toolbar lifts to clear the tab bar; 0 on the `NATIVE` tier, whose layout does it.
@Composable func liquidGlassFloatingBottomBarInset() -> Dp {
    return isLiquidGlassBottomBarFloating() ? liquidGlassTabBarInset() : 0.dp
}

/// Whether toolbars render as floating glass capsules rather than Material bars.
@Composable func isLiquidGlassToolbarEnabled() -> Bool {
    return EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE
}

/// Whether a bottom toolbar floats as glass capsules over the content rather than taking a Material bar's strip.
@Composable func isLiquidGlassBottomBarFloating() -> Bool {
    return isLiquidGlassToolbarEnabled()
}

/// Whether the `TabView` renders the glass bar rather than the Material `NavigationBar`.
@Composable func isLiquidGlassTabBarEnabled() -> Bool {
    return EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE
}

/// Renders the `TabView`'s glass bar from the same options as the Material bar, so `material3NavigationBar(_:)` applies.
///
/// Hidden and `nil` tabs are skipped. Only an app-set color background is drawn; glass raises no scroll background.
@Composable func LiquidGlassTabViewBar(state: LiquidGlassTabBarState, tabs: kotlin.collections.List<Tab?>, selectedTabIndex: Int, options: Material3NavigationBarOptions, tabBarPreferences: ToolbarBarPreferences) {
    let tabIndices = mutableListOf<Int>()
    for tabIndex in 0..<tabs.size {
        if let tab = tabs[tabIndex], !tab.isHidden {
            tabIndices.add(tabIndex)
        }
    }
    // Labels and icons take their color from SkipUI's environment, so the bar's per-tab color is forwarded to them
    let itemLabel: (@Composable (Int) -> Void)? = options.itemLabel == nil ? nil : { tabIndex in
        WithLiquidGlassContentColor { options.itemLabel?(tabIndex) }
    }
    let itemIcon: @Composable (Int) -> Void = { tabIndex in
        WithLiquidGlassContentColor { options.itemIcon(tabIndex) }
    }
    let isTabEnabled: (Int) -> Bool = { tabIndex in
        return options.itemEnabled(tabIndex) && tabs[tabIndex]?.isDisabled != true
    }
    let backgroundColor = tabBarPreferences.backgroundVisibility == Visibility.hidden ? nil : tabBarPreferences.background?.asColor(opacity: 1.0, animationContext: nil)
    LiquidGlassTabView(
        state: state,
        tabIndices: tabIndices,
        selectedTabIndex: selectedTabIndex,
        icon: itemIcon,
        label: itemLabel,
        isEnabled: isTabEnabled,
        onTabSelected: options.onItemClick,
        accentColor: (EnvironmentValues.shared._tint ?? Color.accentColor).asComposeColor(),
        backgroundColor: backgroundColor ?? androidx.compose.ui.graphics.Color.Unspecified,
        contentColor: options.contentColor
    )
}

/// Renders `content` with SkipUI's foreground style set to Compose's current content color.
@Composable private func WithLiquidGlassContentColor(_ content: @Composable () -> Void) {
    let color = LocalContentColor.current
    EnvironmentValues.shared.setValues {
        $0.set_foregroundStyle(Color(colorImpl: { color }))
        return ComposeResult.ok
    } in: {
        content()
    }
}

// MARK: - Tab icon

/// Renders a tab's icon held inside its slot, with an iOS-style badge, for the glass and Material bars alike.
///
/// SF Symbols are enlarged by half, as their Material stand-ins draw small; other images draw at their own size.
///
/// - Parameters:
///   - label: The tab's stripped label.
///   - slotSize: The icon's square, from the font size, or `nil` for the incoming constraints.
@Composable func RenderLiquidGlassTabIcon(_ label: Any, slotSize: Dp?, badge: Text?, context: ComposeContext, icon: @Composable () -> Void) {
    let renderBadge: (@Composable () -> Void)? = badge == nil ? nil : { badge!.foregroundStyle(Color.white).Render(context: context) }
    let symbolScale = isSystemSymbolTabIcon(label, context: context) ? Float(1.5) : Float(1.0)
    LiquidGlassTabIcon(modifier: context.modifier, slotSize: slotSize, symbolScale: symbolScale, badge: renderBadge, icon: icon)
}

/// Whether a tab label's icon is an SF Symbol.
@Composable func isSystemSymbolTabIcon(_ label: Any, context: ComposeContext) -> Bool {
    let icon = (label as? Label)?.image.Evaluate(context: context, options: 0).firstOrNull()?.strip() ?? label
    guard let image = icon as? Image else {
        return false
    }
    switch image.image {
    case .system: return true
    default: return false
    }
}

// MARK: - EnvironmentValues: tab bar minimize behavior

/// The key for `EnvironmentValues.tabBarMinimizeBehavior`.
struct TabBarMinimizeBehaviorKey: EnvironmentKey {
    static let defaultValue: TabBarMinimizeBehavior = .automatic
}

extension EnvironmentValues {
    /// How the glass bar of this `TabView` reacts to scrolling, from `tabBarMinimizeBehavior(_:)`.
    var tabBarMinimizeBehavior: TabBarMinimizeBehavior {
        get { self[TabBarMinimizeBehaviorKey.self] }
        set { self[TabBarMinimizeBehaviorKey.self] = newValue }
    }
}
#endif

// MARK: - Bridging

extension View {
    /// Skip Fuse entry point for `tabBarMinimizeBehavior(_:)`.
    ///
    /// - Parameter bridgedBehavior: The raw value: 1 `automatic`, 2 `onScrollDown`, 3 `onScrollUp`, 4 `never`.
    // SKIP @bridge
    public func tabBarMinimizeBehavior(bridgedBehavior: Int) -> any View {
        return tabBarMinimizeBehavior(TabBarMinimizeBehavior(rawValue: bridgedBehavior))
    }
}

extension TabContent {
    /// Skip Fuse entry point for `badge(_:)` on a `Tab`; SkipFuseUI resolves every overload to a `Text`.
    ///
    /// Named `tabBadge` because Kotlin overloads ignore argument labels, so `badge(bridgedLabel:)` would clash.
    // SKIP @bridge
    public func tabBadge(bridgedLabel: Text?) -> any TabContent {
        var tabContent = self
        tabContent.badge = bridgedLabel
        return tabContent
    }
}
#endif
