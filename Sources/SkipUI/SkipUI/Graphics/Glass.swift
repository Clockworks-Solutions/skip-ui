// Copyright 2025–2026 Skip
// SPDX-License-Identifier: MPL-2.0
#if !SKIP_BRIDGE
#if SKIP
import androidx.compose.runtime.Composable
import skip.model.StateTracking
#elseif canImport(CoreGraphics)
import struct CoreGraphics.CGFloat
#endif

public struct Glass : Equatable, @unchecked Sendable { // Immutable; `Color` is not `Sendable`
    /// The color the glass is tinted with, if any.
    let tintColor: Color?
    /// Whether the glass reacts to touch.
    let isInteractive: Bool

    init(tintColor: Color? = nil, isInteractive: Bool = false) {
        self.tintColor = tintColor
        self.isInteractive = isInteractive
    }

    public static var regular: Glass {
        return Glass()
    }

    public func tint(_ color: Color?) -> Glass {
		return .init(tintColor: color, isInteractive: isInteractive)
    }

    public func interactive(_ isEnabled: Bool = true) -> Glass {
		return .init(tintColor: tintColor, isInteractive: isEnabled)
    }
}

public struct GlassEffectContainer<Content> : View, Sendable where Content : View {
    @available(*, unavailable)
    public init(spacing: CGFloat? = nil, @ViewBuilder content: () -> Content) {
    }

    public var body: some View {
        EmptyView()
    }
}

public struct GlassEffectTransition : Sendable {
    @available(*, unavailable)
    public static var matchedGeometry: GlassEffectTransition {
        return GlassEffectTransition()
    }

    @available(*, unavailable)
    public static func matchedGeometry(properties: MatchedGeometryProperties = .frame, anchor: UnitPoint = .center) -> GlassEffectTransition {
        return GlassEffectTransition()
    }

    public static var identity: GlassEffectTransition {
        return GlassEffectTransition()
    }
}

extension View {
    public func glassEffect(_ glass: Glass = .regular, in shape: some Shape = .capsule, isEnabled: Bool = true) -> some View {
        #if SKIP
        guard isEnabled else {
            return self
        }
        // Liquid Glass: see Glass+LiquidGlass.swift
        return ModifiedContent(content: self, modifier: RenderModifier {
            return $0.modifier.then(liquidGlassEffectModifier(glass, in: shape))
        })
        #else
        return self
        #endif
    }

    public func glassEffectTransition(_ transition: GlassEffectTransition, isEnabled: Bool = true) -> some View {
        return self
    }

    @available(*, unavailable)
    public func glassEffectUnion(id: (any Hashable)?, namespace: Namespace.ID) -> some View {
        return self
    }

    @available(*, unavailable)
    public func glassEffectID(_ id: (any Hashable)?, in namespace: Namespace.ID) -> some View {
        return self
    }
}

#endif
