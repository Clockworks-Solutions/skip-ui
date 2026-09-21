//
//  Navigation+LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 17/09/26.
//
//  Liquid Glass (Clockworks fork): glass back button for the `NavigationStack` top bar.
//

#if !SKIP_BRIDGE
import Foundation
#if SKIP
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import skip.ui.liquidglass.__

/// Render the top bar back button as a `LiquidGlassButton`.
///
/// Replaces the Material `FilledIconButton` / `IconButton`, which are rendered instead when the Liquid Glass tier is
/// `NATIVE`. The Material 3 navigation icon button colors only apply to the Material buttons.
///
/// - Parameters:
///   - isProminent: `true` in place of `FilledIconButton`, `false` in place of `IconButton`.
///   - colors: The Material 3 navigation icon button colors, if customized.
///   - onClick: The back action.
///   - icon: The back arrow icon.
@Composable func LiquidGlassNavigationBackButton(isProminent: Bool, colors: IconButtonColors?, onClick: () -> Void, icon: @Composable () -> Void) {
    guard EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE else {
        if isProminent {
            FilledIconButton(onClick: onClick, colors: colors ?? IconButtonDefaults.filledIconButtonColors()) { icon() }
        } else {
            IconButton(onClick: onClick, colors: colors ?? IconButtonDefaults.iconButtonColors()) { icon() }
        }
        return
    }
    LiquidGlassButton(
        modifier: Modifier.padding(start = 8.dp),
        enabled: true,
        isProminent: isProminent,
        onClick: onClick,
        contentPadding: PaddingValues(horizontal: 8.dp, vertical: 8.dp)
    ) { icon() }
}
#endif
#endif
