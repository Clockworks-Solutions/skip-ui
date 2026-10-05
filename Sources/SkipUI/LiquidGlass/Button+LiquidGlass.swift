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
    /// Renders a `.glass` or `.glassProminent` button; `.bordered` or `.borderedProminent` on the `NATIVE` tier.
    ///
    /// Label color, first match: destructive (`onError` on prominent, else `error`); the environment `foregroundStyle`;
    /// white on prominent; the `tint`; else black or white to suit the glass. Prominent glass is tinted `error` when
    /// destructive, else the `tint`, else `primary`. `material3GlassButton(_:)` customizes the result.
    ///
    /// - Parameter action: The tap action; `nil` does nothing.
    @Composable static func RenderGlassButton(
        label: View,
        context: ComposeContext,
        role: ButtonRole? = nil,
        isEnabled: Bool = EnvironmentValues.shared.isEnabled,
        isProminent: Bool,
        action: (() -> Void)? = nil
    ) {
        guard EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE else {
            let nativeStyle: Any = isProminent ? BorderedProminentButtonStyle() : BorderedButtonStyle()
            let nativeStackedStyle = StackedButtonStyle(style: nativeStyle, parent: EnvironmentValues.shared._buttonStyle?.parent, source: ButtonStyleModifier(style: nativeStyle))
            EnvironmentValues.shared.setValues {
                $0.set_buttonStyle(nativeStackedStyle)
                return ComposeResult.ok
            } in: {
                RenderButton(label: label, context: context, role: role, isEnabled: isEnabled, action: action ?? {})
            }
            return
        }
        let isHitTestingEnabled = EnvironmentValues.shared._isHitTestingEnabled

        var foregroundStyle: ShapeStyle
        if role == .destructive {
            foregroundStyle = isProminent ? Color(colorImpl: { MaterialTheme.colorScheme.onError }) : Color(colorImpl: { MaterialTheme.colorScheme.error })
        } else if let envForegroundStyle = EnvironmentValues.shared._foregroundStyle {
            foregroundStyle = envForegroundStyle
        } else if isProminent {
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

        // On a toolbar capsule a plain glass item draws its label alone, as iOS 26 merges it into the capsule. The plain
        // style keeps the render from routing back here
        if !isProminent, isOnLiquidGlassToolbarCapsule() {
            let plainStyle: Any = PlainButtonStyle()
            let plainStackedStyle = StackedButtonStyle(style: plainStyle, parent: EnvironmentValues.shared._buttonStyle?.parent, source: ButtonStyleModifier(style: plainStyle))
            EnvironmentValues.shared.setValues {
                $0.set_buttonStyle(plainStackedStyle)
                $0.set_foregroundStyle(foregroundStyle)
                return ComposeResult.ok
            } in: {
                RenderButton(label: label, context: context, role: role, isEnabled: isEnabled, action: action ?? {})
            }
            return
        }

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
                // An oversized label is scaled to fit the capsule, or the toolbar it is on
                LiquidGlassFitContent(maxHeight: liquidGlassToolbarItemMaxHeight()) {
                    label.Compose(context: contentContext)
                }
            }
        }
    }
}

extension View {
    /// Customizes `.glass` and `.glassProminent` buttons: receives the resolved options and returns those to render.
    public func material3GlassButton(_ options: @Composable (Material3GlassButtonOptions) -> Material3GlassButtonOptions) -> View {
        return environment(\._material3GlassButton, options, affectsEvaluate: false)
    }
}

extension EnvironmentValues {
    /// Set with `material3GlassButton(_:)`.
    var _material3GlassButton: (@Composable (Material3GlassButtonOptions) -> Material3GlassButtonOptions)? {
        get { builtinValue(key: "_material3GlassButton", defaultValue: { nil }) as! (@Composable (Material3GlassButtonOptions) -> Material3GlassButtonOptions)? }
        set { setBuiltinValue(key: "_material3GlassButton", value: newValue, defaultValue: { nil }) }
    }
}

/// The options a `.glass` or `.glassProminent` button renders with; see ``View/material3GlassButton(_:)``.
public struct Material3GlassButtonOptions {
    /// The button's action.
    public var onClick: () -> Void
    /// Applied to the glass capsule.
    public var modifier: Modifier = Modifier
    /// Whether the button responds to touches; `false` when disabled or hit testing is off.
    public var enabled: Bool = true
    /// Whether this is `.glassProminent`.
    public var isProminent: Bool = false
    /// The glass shape; a capsule unless customized. Only a corner-based shape refracts.
    public var shape: androidx.compose.ui.graphics.Shape
    /// Unspecified for `.glass`; the tint, `primary` or `error` for `.glassProminent`.
    public var tint: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
    /// A fill in place of the frost.
    public var surfaceColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
    /// The inset around the label.
    public var contentPadding: PaddingValues = PaddingValues(horizontal: liquidGlassButtonContentInset, vertical: 8.dp)
    /// Observes the button's press and focus interactions; one is made if `nil`.
    public var interactionSource: MutableInteractionSource? = nil

    /// A copy with the given options replaced, for Kotlin callers of ``View/material3GlassButton(_:)``.
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
