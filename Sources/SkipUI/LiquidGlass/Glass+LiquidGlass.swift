//
//  Glass+LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 18/09/26.
//

#if !SKIP_BRIDGE
import Foundation
#if SKIP
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import skip.ui.liquidglass.__

/// The Compose modifier that draws `glass` behind a view in `shape`, for `glassEffect(_:in:isEnabled:)`.
///
/// Renders Liquid Glass through the same `liquidGlass` modifier as `.glass` buttons, so the two look identical. When the
/// Liquid Glass tier is `NATIVE`, fills `shape` with the Material `surfaceContainerHigh` color, or with the glass tint,
/// instead.
///
/// - Parameters:
///   - glass: The glass configuration: its tint, and whether it reacts to touch.
///   - shape: The shape of the glass.
@Composable func liquidGlassEffectModifier(_ glass: Glass, in shape: any Shape) -> Modifier {
    let composeShape = shape.asComposeShape(density: LocalDensity.current)
    let tint = glass.tintColor?.colorImpl()
    guard EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE else {
        return Modifier.background(tint ?? MaterialTheme.colorScheme.surfaceContainerHigh, composeShape)
    }
    return Modifier.liquidGlassSurface(shape: composeShape, tint: tint ?? androidx.compose.ui.graphics.Color.Unspecified, isInteractive: glass.isInteractive)
}
#endif
#endif
