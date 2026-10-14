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
| `Navigation+LiquidGlass.swift` | The glass back button, the glass top bar and its blurred edge, and floating toolbars: capsule grouping, placements and image fitting. |

## Toolbars

Items follow iOS 26's automatic glass:

- Every system placement sits on glass whatever the button style, `.plain` included. Neighbouring items share a
  capsule; a `ToolbarSpacer` splits capsules, and a flexible one pushes them apart.
- `.glassProminent` and `.borderedProminent` items are capsules of their own.
- `.principal` content is never on glass, buttons included.
- `.confirmationAction` sits last on the trailing side.
- Images taller than the capsule are scaled to fit; the bar never grows.
- Buttons outside a toolbar are glass only when styled `.glass` or `.glassProminent`.

## Top bar

The navigation bar is iOS 26's in place of Material's: transparent, with the inline title centered in 17sp semibold, a
34sp bold large title that collapses into it, and a 44dp glass back button with a chevron. A `List`, `Form` or vertical
`ScrollView` at the top of the content runs up beneath it and clears it with a header; any other content stays below
the bar. The bar floats on a blurred edge that washes the content toward the background: 90% in a light theme and 60% in
a dark one, solid behind the status bar and title and gone 24dp below the bar, all measured from iOS 26. Its back
button and capsules refract the content beneath.

| Bar preferences | Top bar |
| --- | --- |
| None, or `.toolbarBackgroundVisibility(.automatic)` | Glass, on the blurred edge. |
| `.toolbarBackgroundVisibility(.hidden)` | Glass with no edge: content stays sharp beneath the bar. |
| `.toolbarBackground(_:)` color or style, `.toolbarBackgroundVisibility(.visible)` | The Material bar, colored as upstream. |
| `.toolbar(.hidden)` | No bar and no edge. |

A large title scrolls away with the content, as on iOS, rather than collapsing ahead of it as Material's bar does: the
glass bar takes a pinned scroll behavior, the content's padding stays fixed, and the title and edge follow the scroll
offset while laying out and drawing. Scrolling never recomposes the bar or the content, and the edge leaves the
composition at rest. The legacy column layout (`_layoutImplementationVersion` below 2) keeps the Material bar.

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
| `Containers/Navigation.swift` | Glass back button, toolbar capsules, the glass top bar and its edge, the toolbar backdrop and floating bottom bar. |
| `Commands/Toolbar.swift` | `.confirmationAction` last on the trailing side. |
| `Containers/ZStack.swift`, `Compose/ComposeLayouts.swift`, `View/AdditionalViewModifiers.swift` | Record the view behind glass. |
| `Components/Image.swift` | A tab's asset image fits the bar's icon size, or its own frame; an asset image's modifiers apply once, not twice. |
| `Layout/SafeArea.swift` | Bottom inset placement and recording. |
| `Layout/Presentation.swift` | Sheets and covers start without the presenter's glass state. |
| `Containers/List.swift`, `Containers/ScrollView.swift` | Content runs beneath the glass top bar and clears the floating chrome; the refresh indicator starts below the bar. |
| `System/Assets.swift` | `Image(_:bundle: .module)` falls back to the module's real resources. |
