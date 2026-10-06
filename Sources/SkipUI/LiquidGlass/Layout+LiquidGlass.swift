//
//  Layout+LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 01/10/26.
//
//  Liquid Glass (Clockworks fork): glass refracts the view layered behind it by `overlay`, `background` and
//  `safeAreaInset`, as it does a `ZStack`'s children.
//

#if !SKIP_BRIDGE
import Foundation
#if SKIP
import androidx.compose.runtime.Composable
import skip.ui.liquidglass.__

/// A lazily recorded layer of the view behind another, for glass in front; `nil` on the `NATIVE` tier.
@Composable func rememberLiquidGlassLayer() -> LiquidGlassLayer? {
    return skip.ui.liquidglass.rememberLiquidGlassLayer()
}

/// Renders one of two layered views: the one behind records into `layer`; the one in front refracts it.
@Composable func LiquidGlassLayeredContent(_ layer: LiquidGlassLayer?, isBehind: Bool, content: @Composable () -> Void) {
    if isBehind {
        LiquidGlassBehind(layer, content: content)
    } else {
        LiquidGlassInFront(layer, content: content)
    }
}

/// Wraps `background(_ style:)`'s render action so glass in the view refracts a solid background color. Other styles
/// and the `NATIVE` tier are unchanged.
func liquidGlassColorBackgroundAction(_ style: any ShapeStyle, render: (@Composable (Renderable, ComposeContext) -> Void)?) -> (@Composable (Renderable, ComposeContext) -> Void)? {
    guard let render else {
        return nil
    }
    return { content, context in
        guard EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE, let color = style.asColor(opacity: 1.0, animationContext: context) else {
            render(content, context)
            return
        }
        LiquidGlassColorBehind(color) {
            render(content, context)
        }
    }
}
#endif
#endif
