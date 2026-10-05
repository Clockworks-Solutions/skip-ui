//
//  Glass+LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 18/09/26.
//
//  Liquid Glass (Clockworks fork): `glassEffect(_:in:isEnabled:)`.
//

#if !SKIP_BRIDGE
import Foundation
#if SKIP
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import skip.ui.liquidglass.__

/// Draws `glass` behind a view in `shape`, as `.glass` buttons do; on the `NATIVE` tier, fills `shape` with the tint or
/// Material `surfaceContainerHigh`.
@Composable func liquidGlassEffectModifier(_ glass: Glass, in shape: any Shape) -> Modifier {
    let composeShape = liquidGlassCornerBasedShape(shape) ?? shape.asComposeShape(density: LocalDensity.current)
    let tint = glass.tintColor?.colorImpl()
    guard EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE else {
        return Modifier.background(tint ?? MaterialTheme.colorScheme.surfaceContainerHigh, composeShape)
    }
    return Modifier.liquidGlassSurface(shape: composeShape, tint: tint ?? androidx.compose.ui.graphics.Color.Unspecified, isInteractive: glass.isInteractive)
}

/// `shape` as a Compose `CornerBasedShape`, which the lens needs; `nil` for other shapes, which get no lens.
///
/// A `Circle` in a non-square frame becomes a capsule, and corners are always circular, using the corner width.
func liquidGlassCornerBasedShape(_ shape: any Shape) -> CornerBasedShape? {
    if shape is Capsule || shape is Circle {
        return RoundedCornerShape(percent: 50)
    }
    if shape is Rectangle {
        return RoundedCornerShape(0.dp)
    }
    if let roundedRectangle = shape as? RoundedRectangle {
        return RoundedCornerShape(Double(roundedRectangle.cornerSize.width).dp)
    }
    if let unevenRoundedRectangle = shape as? UnevenRoundedRectangle {
        let radii = unevenRoundedRectangle.cornerRadii
        return RoundedCornerShape(topStart: Double(radii.topLeading).dp, topEnd: Double(radii.topTrailing).dp, bottomEnd: Double(radii.bottomTrailing).dp, bottomStart: Double(radii.bottomLeading).dp)
    }
    return nil
}
#endif

// MARK: - View: glass effect bridging

extension View {
    /// Skip Fuse entry point for `glassEffect(_:in:isEnabled:)`; SkipFuseUI's `Glass` crosses the bridge as its parts.
    // SKIP @bridge
    public func glassEffect(bridgedTint: Color?, isInteractive: Bool, in shape: any Shape, isEnabled: Bool) -> any View {
        return glassEffect(Glass(tintColor: bridgedTint, isInteractive: isInteractive), in: shape, isEnabled: isEnabled)
    }
}
#endif
