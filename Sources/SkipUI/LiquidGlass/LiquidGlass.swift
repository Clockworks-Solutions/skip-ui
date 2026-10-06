//
//  LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 17/09/26.
//
//  Liquid Glass (Clockworks fork): the switch that picks how glass renders on Android, and the tier it resolves to.
//

#if !SKIP_BRIDGE
import Foundation
#if SKIP
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import skip.ui.liquidglass.__
#endif

/// Whether glass components render as Liquid Glass on Android: buttons, `glassEffect`, toolbars and the `TabView` bar.
/// No effect on Apple platforms, which render Liquid Glass natively.
///
/// ```swift
/// ContentView()
///     .environment(\.liquidGlass, .disabled)
/// ```
public enum LiquidGlass : Int, Hashable, Sendable {
    /// The default: full glass on high-end devices, glass without refraction on mid-range ones, Material on
    /// low-end ones. Decided once per launch, from the last launch's measurement, so the first frame is already right.
    case adaptive = 0 // For bridging

    /// Always Material; nothing is measured.
    case disabled = 1 // For bridging

    /// Always full glass; nothing is measured.
    case forced = 2 // For bridging

    /// Always glass: full on high-end devices, without refraction elsewhere.
    case forcedOptimized = 3 // For bridging
}

#if SKIP

/// The key for ``EnvironmentValues/liquidGlass``.
struct LiquidGlassKey: EnvironmentKey {
    static let defaultValue: LiquidGlass = .adaptive
}

// MARK: - EnvironmentValues: LiquidGlass
public extension EnvironmentValues {
    /// How glass in this subtree renders; ``LiquidGlass/adaptive`` by default.
    var liquidGlass: LiquidGlass {
        get { self[LiquidGlassKey.self] }
        set { self[LiquidGlassKey.self] = newValue }
    }
}

// MARK: - EnvironmentValues: Liquid Glass tier
extension EnvironmentValues {
    /// The tier glass renders with here. Only ``LiquidGlass/adaptive`` and ``LiquidGlass/forcedOptimized`` depend on
    /// the device class, which the first of them to render fixes for the session.
    @Composable func liquidGlassTier() -> LiquidGlassTier {
        let context = LocalContext.current
        switch liquidGlass {
        case .adaptive:
            LiquidGlassCapability.resolve(context)
            return LiquidGlassCapability.tier
        case .disabled:
            return LiquidGlassTier.NATIVE
        case .forced:
            return LiquidGlassTier.FULL
        case .forcedOptimized:
            LiquidGlassCapability.resolve(context)
            return LiquidGlassCapability.optimizedTier
        }
    }
}

// MARK: - EnvironmentValues: Liquid Glass bridging
extension EnvironmentValues {
    /// `liquidGlass`'s raw value, for SkipFuseUI's `@Environment`; the key and raw values must match SkipFuseUI's.
    @Composable func liquidGlassBridged() -> EnvironmentSupport {
        return EnvironmentSupport(builtinValue: liquidGlass.rawValue)
    }

    /// Sets `liquidGlass` from SkipFuseUI; an unknown raw value restores ``LiquidGlass/adaptive``. Always returns `true`.
    func setLiquidGlassBridged(_ value: EnvironmentSupport?) -> Bool {
        let rawValue = value?.builtinValue as? Int ?? LiquidGlass.adaptive.rawValue
        setliquidGlass(LiquidGlass(rawValue: rawValue) ?? LiquidGlass.adaptive)
        return true
    }
}
#endif
#endif
