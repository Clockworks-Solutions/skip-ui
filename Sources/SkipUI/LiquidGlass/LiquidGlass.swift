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

    /// Full glass on every device, whatever its class. Droid Dex is not started for this.
    case forced = 2 // For bridging

    /// Glass on every device, full on the top two device classes and without lens effects on the rest, including a
    /// device whose class cannot be measured.
    case forcedOptimized = 3 // For bridging
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
    /// Device detection starts on first use and only for the modes that need it; ``LiquidGlass/disabled`` and
    /// ``LiquidGlass/forced`` answer without measuring.
    @Composable func liquidGlassTier() -> LiquidGlassTier {
        let context = LocalContext.current
        switch liquidGlass {
        case .disabled:
            return LiquidGlassTier.NATIVE
        case .forced:
            return LiquidGlassTier.FULL
        case .forcedOptimized:
            LiquidGlassCapability.resolve(context)
            return LiquidGlassCapability.optimizedTier
        default:
            LiquidGlassCapability.resolve(context)
            return LiquidGlassCapability.tier
        }
    }
}
#endif
#endif
