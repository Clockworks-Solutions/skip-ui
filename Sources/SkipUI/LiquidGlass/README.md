# Liquid Glass — Swift layer

The Swift half of the Clockworks fork's iOS 26 Liquid Glass for SkipUI on Android: the public API, and the functions
upstream SkipUI calls into. The Compose half, the dependencies and the rendering tiers are in
[`Skip/LiquidGlass`](../Skip/LiquidGlass/README.md).

Forked from [skiptools/skip-ui](https://github.com/skiptools/skip-ui), which the `upstream` git remote points at.

## Files

| File | Contents |
| --- | --- |
| `LiquidGlass.swift` | `LiquidGlass` (`adaptive`, `disabled`, `forced`, `forcedOptimized`), `EnvironmentValues.liquidGlass`, and `liquidGlassTier()`, which every other file gates on. |
| `Button+LiquidGlass.swift` | `.glass` and `.glassProminent`: label color and tint, the toolbar-capsule case, `.bordered` fallbacks, and `material3GlassButton(_:)`. |
| `Glass+LiquidGlass.swift` | `glassEffect(_:in:isEnabled:)`, mapping SwiftUI shapes to the corner-based shapes the lens needs. |
| `Layout+LiquidGlass.swift` | Glass refracting the view behind it in `overlay`, `background` and `background(Color)`. |
| `SafeArea+LiquidGlass.swift` | `safeAreaInset(edge: .bottom)` above the tab bar inside a tab; over it on a `TabView`. |
| `TabView+LiquidGlass.swift` | The glass bar: state, minimize on scroll, the insets tabs receive, icons, badges, and presentations starting clean. |
| `Navigation+LiquidGlass.swift` | The glass back button and floating toolbars: capsule grouping, placements and image fitting. |

## Toolbars

Items follow iOS 26's automatic glass:

- Every system placement sits on glass whatever the button style, `.plain` included. Neighbouring items share a
  capsule; a `ToolbarSpacer` splits capsules, and a flexible one pushes them apart.
- `.glassProminent` and `.borderedProminent` items are capsules of their own.
- `.principal` content is never on glass, buttons included.
- `.confirmationAction` sits last on the trailing side.
- Images taller than the capsule are scaled to fit; the bar never grows.
- Buttons outside a toolbar are glass only when styled `.glass` or `.glassProminent`.

## Skip Fuse bridging

SkipFuseUI declares its own `LiquidGlass`, `Glass` and `TabBarMinimizeBehavior`, which cross as plain values through
`// SKIP @bridge` entry points:

| Fuse API | SkipUI entry point |
| --- | --- |
| `.environment(\.liquidGlass, …)`, `@Environment(\.liquidGlass)` | builtin key `"liquidGlass"`: `liquidGlassBridged()`, `setLiquidGlassBridged(_:)` |
| `tabBarMinimizeBehavior(_:)` | `View.tabBarMinimizeBehavior(bridgedBehavior:)` |
| `Tab.badge(_:)` | `TabContent.tabBadge(bridgedLabel:)` |
| `glassEffect(_:in:isEnabled:)` | `View.glassEffect(bridgedTint:isInteractive:in:isEnabled:)` |
| `.buttonStyle(.glass)`, `.glassProminent` | `View.buttonStyle(bridgedStyle:)`, identifiers 5 and 6 |

## Upstream touch points

Upstream files change only by short lines marked `// Liquid Glass:` (or `// Clockworks fork:` for fixes outside glass)
that name the fork file to read, so an upstream merge conflicts only where the fork differs.

| Upstream file | Hook |
| --- | --- |
| `Controls/Button.swift` | Routes `.glass` and `.glassProminent` to the fork. |
| `Graphics/Glass.swift` | Routes `glassEffect` to the fork. |
| `Environment/EnvironmentValues.swift` | Bridges `liquidGlass`. |
| `Containers/TabView.swift` | Bar state, the glass bar, icon fitting and badges, minimize behaviors. |
| `Containers/Navigation.swift` | Glass back button, toolbar capsules, the toolbar backdrop and floating bottom bar. |
| `Commands/Toolbar.swift` | `.confirmationAction` last on the trailing side. |
| `Containers/ZStack.swift`, `Compose/ComposeLayouts.swift`, `View/AdditionalViewModifiers.swift` | Record the view behind glass. |
| `Layout/SafeArea.swift` | Bottom inset placement and recording. |
| `Layout/Presentation.swift` | Sheets and covers start without the presenter's glass state. |
| `Containers/List.swift`, `Containers/ScrollView.swift` | Content clears the floating chrome. |
| `System/Assets.swift` | `Image(_:bundle: .module)` falls back to the module's real resources. |
