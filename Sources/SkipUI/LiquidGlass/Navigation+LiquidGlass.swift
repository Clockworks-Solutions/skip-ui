//
//  Navigation+LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 17/09/26.
//
//  Liquid Glass (Clockworks fork): glass title pill and back button for the `NavigationStack` top bar.
//

#if !SKIP_BRIDGE
import Foundation
#if SKIP
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/// Whether the navigation title should be drawn in a Liquid Glass pill.
///
/// Matches iOS: the pill appears only when there is a back button and the title is in the inline position, either
/// because the title display mode is inline or because the large title has been scrolled more than halfway away.
///
/// - Parameters:
///   - hasBackButton: Whether the top bar shows a back button.
///   - isInlineTitleDisplayMode: Whether the top bar uses the inline title display mode.
///   - collapsedFraction: How collapsed the large top bar is, from 0 (expanded) to 1 (collapsed).
func shouldShowLiquidGlassNavigationTitle(hasBackButton: Bool, isInlineTitleDisplayMode: Bool, collapsedFraction: Float) -> Bool {
    return hasBackButton && (isInlineTitleDisplayMode || collapsedFraction > Float(0.5))
}

/// Render the navigation title in a `LiquidGlassSurface` pill.
///
/// With a title menu, the pill shows the title and a chevron and toggles the menu when tapped. Without one, the title is
/// drawn at 14sp semibold.
///
/// - Parameters:
///   - title: The navigation title.
///   - titleMenu: The toolbar title menu, if any.
///   - interactionSource: The top bar's interaction source, shared so taps don't show an indication.
@Composable func LiquidGlassNavigationTitle(title: Text, titleMenu: ToolbarTitleMenu?, interactionSource: MutableInteractionSource, context: ComposeContext) {
    if let titleMenu {
        LiquidGlassSurface(
            modifier: Modifier.clickable(interactionSource: interactionSource, indication: nil, onClick: { titleMenu.toggleMenu() }),
            contentPadding: PaddingValues(horizontal: 16.dp, vertical: 6.dp)
        ) {
            androidx.compose.material3.Text(title.localizedTextString(), maxLines: 1, overflow: TextOverflow.Ellipsis)
            Image(systemName: "chevron.down").accessibilityHidden(true).Compose(context: context)
        }
        titleMenu.Render(context: context)
    } else {
        LiquidGlassSurface(
            contentPadding: PaddingValues(horizontal: 16.dp, vertical: 6.dp)
        ) {
            androidx.compose.material3.Text(
                text = title.localizedTextString(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Normal, // or FontStyle.Italic
                fontFamily = FontFamily.Default // or a custom font family
            )
        }
    }
}

/// Render the top bar back button as a `LiquidGlassButton`.
///
/// Replaces the Material `FilledIconButton` / `IconButton`. The Material 3 navigation icon button colors are not applied.
///
/// - Parameters:
///   - isProminent: `true` in place of `FilledIconButton`, `false` in place of `IconButton`.
///   - onClick: The back action.
///   - icon: The back arrow icon.
@Composable func LiquidGlassNavigationBackButton(isProminent: Bool, onClick: () -> Void, icon: @Composable () -> Void) {
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
