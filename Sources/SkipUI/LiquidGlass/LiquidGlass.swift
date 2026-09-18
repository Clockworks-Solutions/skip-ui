//
//  LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 17/09/26.
//

#if !SKIP_BRIDGE
import Foundation
#if SKIP
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import skip.ui.liquidglass.__
#endif

/// Whether glass components render with Liquid Glass on Android.
///
/// Set it with `.environment(\.liquidGlass, …)`; a value set on a view applies to its subtree and can be overridden
/// further down.
///
///     ContentView()
///         .environment(\.liquidGlass, .disabled)
///
/// Affects `.glass` and `.glassProminent` buttons, the navigation title and back button, and the `TabView` bar.
public enum LiquidGlass : Int, Hashable, Sendable {
    /// Full glass on high-end devices, glass without lens effects on mid-range devices, and native Material
    /// rendering on low-end devices or when the level cannot be measured. This is the default.
    case adaptive = 0 // For bridging

    /// Never render Liquid Glass; use native Material rendering. Droid Dex is not started for this.
    case disabled = 1 // For bridging
}

#if SKIP

struct LiquidGlassKey: EnvironmentKey {
    static let defaultValue: LiquidGlass = .adaptive
}

// MARK: - EnvironmentValues: LiquidGlass
public extension EnvironmentValues {
    /// Whether glass components in this subtree render with Liquid Glass. The default is ``LiquidGlass/adaptive``.
    var liquidGlass: LiquidGlass {
        get { self[LiquidGlassKey.self] }
        set { self[LiquidGlassKey.self] = newValue }
    }
}

// MARK: - EnvironmentValues: Liquid Glass tier
extension EnvironmentValues {
    /// The tier to render glass with at this position in the view tree.
    ///
    /// ``LiquidGlass/disabled`` is always `NATIVE`. ``LiquidGlass/adaptive`` starts device detection on first use and
    /// returns the detected tier, which is `NATIVE` until detection completes.
    @Composable func liquidGlassTier() -> LiquidGlassTier {
        guard liquidGlass == LiquidGlass.adaptive else {
            return LiquidGlassTier.NATIVE
        }
        LiquidGlassCapability.resolve(LocalContext.current)
        return LiquidGlassCapability.tier
    }
}
#endif
#endif
