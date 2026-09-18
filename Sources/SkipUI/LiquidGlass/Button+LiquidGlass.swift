//
//  Button+LiquidGlass.swift
//  skip-ui
//
//  Created by Dhruv Chhatbar on 17/09/26.
//
//  Liquid Glass (Clockworks fork): `.glass` and `.glassProminent` button styles.
//

#if !SKIP_BRIDGE
import Foundation
#if SKIP
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import skip.ui.liquidglass.__

extension Button {
    /// Render a Liquid Glass button for the `.glass` and `.glassProminent` styles.
    ///
    /// Draws the label on a `LiquidGlassButton` capsule, which samples `LocalGlassBackdrop` when a presentation provides one.
    ///
    /// Label color, in order of precedence:
    /// - `onError` for destructive buttons
    /// - The environment `foregroundStyle`
    /// - White for prominent buttons, to read over the tinted glass
    /// - The environment `tint`
    /// - `Color.primary`
    ///
    /// Prominent buttons tint the glass with the Material `error` color when destructive, otherwise the environment `tint`,
    /// otherwise the Material `primary` color. Use `material3GlassButton(_:)` to customize the resolved options.
    ///
    /// - Parameters:
    ///   - isProminent: Whether to render the tinted `.glassProminent` variant.
    ///   - action: The tap action, or nil for a button that does nothing when tapped.
    ///
    /// When the Liquid Glass tier is `NATIVE`, renders the Material `.bordered` or `.borderedProminent` style instead.
    @Composable static func RenderGlassButton(
        label: View,
        context: ComposeContext,
        role: ButtonRole? = nil,
        isEnabled: Bool = EnvironmentValues.shared.isEnabled,
        isProminent: Bool,
        action: (() -> Void)? = nil
    ) {
        guard EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE else {
            EnvironmentValues.shared.setValues {
                $0.set_buttonStyle(isProminent ? ButtonStyle.borderedProminent : ButtonStyle.bordered)
                return ComposeResult.ok
            } in: {
                RenderButton(label: label, context: context, role: role, isEnabled: isEnabled, action: action ?? {})
            }
            return
        }
        let isHitTestingEnabled = EnvironmentValues.shared._isHitTestingEnabled

        var foregroundStyle: ShapeStyle
        if role == .destructive {
            foregroundStyle = Color(colorImpl: { MaterialTheme.colorScheme.onError })
        } else if let envForegroundStyle = EnvironmentValues.shared._foregroundStyle {
            foregroundStyle = envForegroundStyle
        } else if isProminent {
            // Prominent: text/icon is always white over the tinted surface
            foregroundStyle = Color.white
        } else if let tint = EnvironmentValues.shared._tint {
            foregroundStyle = tint
        } else {
            foregroundStyle = Color.primary
        }

        if !isEnabled {
            foregroundStyle = AnyShapeStyle(foregroundStyle, opacity: Double(ContentAlpha.disabled))
        }

        var options = Material3GlassButtonOptions(
            onClick: action ?? {},
            modifier: context.modifier,
            enabled: isEnabled && isHitTestingEnabled,
            isProminent: isProminent,
            shape: RoundedCornerShape(percent: 50)
        )
        if isProminent {
            if role == .destructive {
                options.tint = MaterialTheme.colorScheme.error
            } else if let envTint = EnvironmentValues.shared._tint {
                options.tint = envTint.colorImpl()
            } else {
                options.tint = MaterialTheme.colorScheme.primary
            }
        }
        if let updateOptions = EnvironmentValues.shared._material3GlassButton {
            options = updateOptions(options)
        }
        let contentContext = context.content()

        EnvironmentValues.shared.setValues {
            $0.set_foregroundStyle(foregroundStyle)
            return ComposeResult.ok
        } in: {
            LiquidGlassButton(
                modifier: options.modifier,
                enabled: options.enabled,
                isProminent: options.isProminent,
                onClick: options.onClick,
                shape: options.shape,
                tint: options.tint,
                surfaceColor: options.surfaceColor,
                contentPadding: options.contentPadding,
                interactionSource: options.interactionSource
            ) {
                label.Compose(context: contentContext)
            }
        }
    }
}

extension View {
    /// Compose glass button customization for `.glass` and `.glassProminent` button styles.
    ///
    /// The closure receives the options resolved from the button's role, tint, and enabled state, and returns the options
    /// to render with.
    public func material3GlassButton(_ options: @Composable (Material3GlassButtonOptions) -> Material3GlassButtonOptions) -> View {
        return environment(\._material3GlassButton, options, affectsEvaluate: false)
    }
}

extension EnvironmentValues {
    /// Liquid Glass: customizes `.glass` and `.glassProminent` buttons. Set with `material3GlassButton(_:)`.
    var _material3GlassButton: (@Composable (Material3GlassButtonOptions) -> Material3GlassButtonOptions)? {
        get { builtinValue(key: "_material3GlassButton", defaultValue: { nil }) as! (@Composable (Material3GlassButtonOptions) -> Material3GlassButtonOptions)? }
        set { setBuiltinValue(key: "_material3GlassButton", value: newValue, defaultValue: { nil }) }
    }
}

/// Options for rendering a `.glass` or `.glassProminent` button with `LiquidGlassButton`.
///
/// - Seealso: ``View/material3GlassButton(_:)``
public struct Material3GlassButtonOptions {
    /// The tap action.
    public var onClick: () -> Void
    /// The modifier applied to the glass capsule.
    public var modifier: Modifier = Modifier
    /// Whether the button responds to taps.
    public var enabled: Bool = true
    /// Whether this is the tinted `.glassProminent` variant.
    public var isProminent: Bool = false
    /// The glass shape. Defaults to a capsule.
    public var shape: androidx.compose.ui.graphics.Shape
    /// The glass tint. Unspecified for `.glass`; the accent, `primary`, or `error` color for `.glassProminent`.
    public var tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
    /// An overlay color drawn on the glass. When unspecified and there is no tint, a light or dark frost is used.
    public var surfaceColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
    /// Padding between the glass edge and the label.
    public var contentPadding: PaddingValues = PaddingValues(horizontal: 8.dp, vertical: 8.dp)
    /// An optional source for observing press state.
    public var interactionSource: MutableInteractionSource? = nil

    public func copy(
        onClick: () -> Void = self.onClick,
        modifier: Modifier = self.modifier,
        enabled: Bool = self.enabled,
        isProminent: Bool = self.isProminent,
        shape: androidx.compose.ui.graphics.Shape = self.shape,
        tint: androidx.compose.ui.graphics.Color = self.tint,
        surfaceColor: androidx.compose.ui.graphics.Color = self.surfaceColor,
        contentPadding: PaddingValues = self.contentPadding,
        interactionSource: MutableInteractionSource? = self.interactionSource
    ) -> Material3GlassButtonOptions {
        return Material3GlassButtonOptions(onClick: onClick, modifier: modifier, enabled: enabled, isProminent: isProminent, shape: shape, tint: tint, surfaceColor: surfaceColor, contentPadding: contentPadding, interactionSource: interactionSource)
    }
}
#endif
#endif
