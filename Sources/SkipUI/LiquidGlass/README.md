# Liquid Glass — Swift layer

Clockworks fork additions that bring iOS 26 Liquid Glass to SkipUI on Android. This folder holds the Swift half: the
public API, the SkipUI-side glue, and the entry points that upstream SkipUI calls into. The Compose implementation lives
in `Sources/SkipUI/Skip/LiquidGlass`, in the `skip.ui.liquidglass` Kotlin package, and is documented in
[its own README](../Skip/LiquidGlass/README.md) along with the Android dependencies and the device tiers.

Forked from [skiptools/skip-ui](https://github.com/skiptools/skip-ui), which the `upstream` git remote points at.

## Files

### `LiquidGlass.swift`

The public switch. `LiquidGlass` is an `Int`-backed enum with `adaptive` (the default) and `disabled`, set with
`.environment(\.liquidGlass, …)` and read anywhere in the subtree.

`EnvironmentValues.liquidGlassTier()` turns that into the tier glass actually renders with: `disabled` is always
`NATIVE`; `adaptive` starts Droid Dex on first use and returns the measured tier, which is `NATIVE` until the
measurement lands. Every other file in the fork gates on this, so one environment value switches the whole feature off
and falls back to stock Material rendering.

### `Button+LiquidGlass.swift`

Rendering for the `.glass` and `.glassProminent` button styles. Resolves the label color and the prominent tint from
role, `foregroundStyle`, and `tint`, then draws through `LiquidGlassButton`. On the `NATIVE` tier it re-renders the
button as `.bordered` / `.borderedProminent` instead.

Also defines `material3GlassButton(_:)` and its environment value, matching SkipUI's other `material3*` customization
hooks, so an app can adjust the resolved glass options.

### `Glass+LiquidGlass.swift`

`liquidGlassEffectModifier(_:in:)` backs `glassEffect()`. It applies the same `liquidGlassSurface` modifier the glass
buttons use, so arbitrary views and buttons come out identical. On the `NATIVE` tier it fills the shape with
`surfaceContainerHigh`, or with the glass tint.

### `TabView+LiquidGlass.swift`

Everything the floating glass tab bar needs from the `TabView` side.

- `rememberLiquidGlassTabBarState()` — one state object per `TabView`: the backdrop, the bar's published insets, and
  its minimized flag.
- `isLiquidGlassTabBarEnabled()` — whether this `TabView` shows the glass bar or the Material `NavigationBar`.
- `liquidGlassTabBarContentModifier(_:)` — applied to the tab content: records it into the backdrop the bar refracts,
  and attaches the nested-scroll connection that drives minimizing.
- `LiquidGlassTabViewBar(…)` — builds the bar from the same `Material3NavigationBarOptions` the Material bar uses, so
  icons, labels, enablement, badges, tint, and `toolbarBackground` all behave the same. Tab labels are `Text`, which
  reads the SkipUI environment rather than Compose's `LocalContentColor`, so the per-tab color is forwarded into
  `foregroundStyle` here.
- `LiquidGlassTabContent(state:materialBarHeightPx:)` — wraps tab content and publishes two insets. The glass bar
  publishes its full footprint plus the part scrollables must add themselves; the Material bar publishes only its
  height, which is what keeps a nested `TabView`'s bar off the outer one.
- `liquidGlassTabBarInset()` / `liquidGlassScrollContentInset()` — the two readers. The first moves chrome (a nested
  tab bar, a bottom toolbar) above the bar. The second only lengthens a scrollable so its end clears the chrome;
  nothing moves, so content keeps drawing under the glass and refracting through it.
- `LiquidGlassBottomBarContent(barHeightPx:)` / `isLiquidGlassBottomBarFloating()` — used by `Navigation.swift` to
  float a bottom toolbar over the content and add its height to the scroll inset, with or without a `TabView`.
- `liquidGlassFloatingBottomBarInset()` — how far a floating bottom toolbar lifts to clear the glass tab bar. Zero on
  the `NATIVE` tier, where the Material layout already places the toolbar above the Material tab bar and adding the
  published inset there would inset it twice.
- `tabBarMinimizeBehavior` environment value and `liquidGlassTabBarMinimizeBehavior()` — back
  `tabBarMinimizeBehavior(_:)`, mapping `.onScrollDown` / `.onScrollUp` to the Kotlin enum; `.automatic` and `.never`
  do not minimize.

### `Navigation+LiquidGlass.swift`

- `LiquidGlassNavigationBackButton(…)` — the top bar back button as a glass capsule, falling back to
  `FilledIconButton` / `IconButton` on the `NATIVE` tier.
- `rememberLiquidGlassNavBackdrop()` and `liquidGlassNavContentModifier(_:)` — record the navigation content that a
  floating toolbar refracts. The recording covers the content only, never the toolbar that samples it.
- `LiquidGlassBottomToolbar(…)` — bottom toolbar items as floating glass capsules over the content. Inside a `TabView`
  they sit above the tab bar; on their own they clear the system navigation bar themselves.
- `LiquidGlassTopToolbarItems(…)` — the same capsules for top bar items, at the shorter top bar height.
- `liquidGlassToolbarGroups(_:)` / `LiquidGlassToolbarItemGroup` — splits toolbar items into one group per capsule,
  divided at every `ToolbarSpacer`. The sizing decides only the distance: a flexible spacer takes the width left over
  and pushes the capsules to opposite ends, a fixed one leaves `liquidGlassToolbarCapsuleGap`, so a prominent action
  can sit just off the end of the pair before it. The top bar gets the fixed gap either way, since it places its
  leading and trailing items itself.
- `liquidGlassToolbarItemDrawsOwnBackground(_:context:)` — an item with a `.glassProminent` or `.borderedProminent`
  button style is a capsule in its own right, so it is split into a group of its own and rendered with no glass behind
  it and no capsule padding around it. The non-prominent `.glass` and `.bordered` styles stay in the group's capsule,
  matching iOS 26, where two `.glass` items side by side come out as one capsule rather than two; such a button skips
  its own surface by reading `isOnLiquidGlassToolbarCapsule()`. A `ToolbarItem` holds its content as an unevaluated
  `ComposeBuilder`, so the check evaluates that content first — the style is on the button inside the item, never on
  the item itself. Only a button style is detectable: `.background(_:)` and `glassEffect()` both compose into the
  generic `RenderModifier`.
- A plain `Spacer` between bottom bar items is dropped. `ToolbarItems.Evaluate` inserts one whenever there are two or
  more items and no spacer of any kind, to spread them across a Material bar; inside a glass capsule its weight
  stretched the capsule across the screen and pushed the items past the capsule's own edge, which clipped the last one
  out of sight. Grouping comes from `ToolbarSpacer` alone.
- A capsule is never narrower than it is tall and centers what it holds, so a capsule with a single icon comes out
  round rather than oval and the icon sits in the middle of it. Top bar items are also rendered without the
  12dp-per-item padding the bar adds for a Material layout, which would otherwise stretch that capsule sideways.
- A prominent item is held to the bar's height by `liquidGlassToolbarItemSize(_:)`, with a minimum width to match, so
  it lines up with the capsules beside it rather than sizing itself from its label — which left it shorter than they
  were, or taller with a large label. A label too big for that height is clipped, as it would be on a capsule.
- All toolbar spacing comes from `liquidGlassToolbarSpacing`, in `Skip/LiquidGlass/LiquidGlassMetrics.kt`: the margin
  each bar keeps at its edge, the space after the back button, and the gap between capsules are one value, and the
  spacing between two items sharing a capsule is derived from it, so top and bottom bars read with the same rhythm.
- Every top bar capsule is preceded by that spacing, the first one included, and one follows the last: what sits
  beside a capsule is the back button, the title, or the edge of the bar, and a capsule drawn hard against any of them
  reads as one overlapping shape.
- Bottom bar alignment follows iOS 26: with no flexible `ToolbarSpacer` anywhere, the capsules are centered in the bar
  rather than left against the leading edge; a flexible spacer switches the row to leading alignment and takes the
  width left over.

All three fall back to plain rendering on the `NATIVE` tier, and take their effects from the tier's
`LiquidGlassStyle`, so `FULL` gets lens refraction and chromatic aberration where `REDUCED` does not.

## Upstream touch points

Upstream SkipUI files are edited only in single lines carrying a `// Liquid Glass:` marker that names the fork file to
look in, so a sync shows conflicts exactly where the fork differs.

| Upstream file | What the marked lines do |
| --- | --- |
| `Controls/Button.swift` | Routes `.glass` / `.glassProminent` to the fork renderer; makes the two styles available. |
| `Graphics/Glass.swift` | Routes `glassEffect()` to the fork modifier. |
| `Containers/TabView.swift` | Creates the bar state, swaps the Material bar for the glass bar, records tab content and watches it scroll, publishes insets to tab content, pads a nested Material bar, and makes `tabBarMinimizeBehavior(_:)` work. |
| `Containers/Navigation.swift` | Glass back button, the nav-content backdrop, lifting the bottom toolbar above the tab bar, the floating glass toolbar, dropping the content inset when the toolbar floats, and the title's leading padding. |
| `Containers/ZStack.swift` | Records each child so glass in a later child refracts the ones behind it. |
| `Containers/List.swift`, `Containers/ScrollView.swift` | Add the scroll content inset so the end of the content clears floating chrome. |

## Extension file layout

Fixed measurements are not declared here at all: they live in `Skip/LiquidGlass/LiquidGlassMetrics.kt`, which the
Swift layer reads directly since both compile into the same module.

All fork logic sits in `*+LiquidGlass.swift` files next to the upstream type they extend, rather than inline in the
upstream sources. Upstream files then change by one marked line per hook, which keeps a merge from the upstream repo to
a small, readable set of conflicts, and keeps the whole feature reviewable and removable in one place. The same rule
applies to the Compose code, which lives in its own Kotlin package rather than in SkipUI's transpiled output.
