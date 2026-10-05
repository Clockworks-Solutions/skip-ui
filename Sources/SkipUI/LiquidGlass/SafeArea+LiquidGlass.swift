//
//  SafeArea+LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 01/10/26.
//
//  Liquid Glass (Clockworks fork): `safeAreaInset(edge: .bottom)` laid out around the floating glass tab bar as iOS 26
//  lays it out around its tab bar.
//

#if !SKIP_BRIDGE
import Foundation
#if SKIP
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import skip.ui.liquidglass.__

/// How far a bottom inset view lifts: above the glass chrome over this content, as iOS sets it above the tab bar. Not
/// on a `TabView`, where iOS draws it over the bar, nor on other edges.
@Composable func liquidGlassSafeAreaInsetLift(_ renderable: Renderable, edge: Edge) -> Dp {
    guard edge == .bottom, !(renderable.strip() is TabView) else {
        return 0.dp
    }
    return liquidGlassScrollContentInset()
}

/// Renders the view a `safeAreaInset` is attached to, inset as on iOS.
///
/// A `TabView` isn't padded, which would lift its bar; its tabs clear the inset instead. Content under a lifted inset
/// clears both the inset and the chrome, once.
///
/// - Parameters:
///   - insets: The insets SkipUI would apply.
///   - lift: From `liquidGlassSafeAreaInsetLift(_:edge:)`.
@Composable func RenderLiquidGlassSafeAreaInsetContent(_ renderable: Renderable, edge: Edge, insets: EdgeInsets, lift: Dp, render: @Composable (EdgeInsets) -> Void) {
    guard edge == .bottom else {
        render(insets)
        return
    }
    if renderable.strip() is TabView {
        WithGlassOuterBottomInset(inset: insets.bottom.dp) {
            render(EdgeInsets())
        }
        return
    }
    guard lift > 0.dp else {
        render(insets)
        return
    }
    WithGlassContentInset(inset: 0.dp) {
        render(EdgeInsets(bottom: insets.bottom + Double(lift.value)))
    }
}
#endif
#endif
