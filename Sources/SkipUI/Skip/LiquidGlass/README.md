# Liquid Glass — Compose layer

The Android implementation behind the fork's Liquid Glass support, in the `skip.ui.liquidglass` Kotlin package. These
files are plain Compose, compiled as-is rather than transpiled, and are called from the Swift side in
`Sources/SkipUI/LiquidGlass`.

Built on two dependencies, both declared in `Sources/SkipUI/Skip/skip.yml`:

- **Kyant's AndroidLiquidGlass** — `io.github.kyant0:backdrop:1.0.6`,
  [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass). Layer recording (`LayerBackdrop`,
  `layerBackdrop`), `drawBackdrop`, and the `vibrancy` / `blur` / `lens` effects.
- **Blinkit's Droid Dex** — `com.eternal.kits:droid-dex:4.0.0`,
  [grofers/droid-dex](https://github.com/grofers/droid-dex), GPL-2.0. Measures device CPU and memory capability.

## Rendering tiers

`LiquidGlassCapability.kt` resolves one tier per process from Droid Dex's CPU and memory levels: `EXCELLENT`/`HIGH` →
`FULL`, `AVERAGE` → `REDUCED`, `LOW`/`UNKNOWN` → `NATIVE`. Measurement is asynchronous and runs on flows, with no
timers.

The measured level is stored in a `skip.ui.liquidglass` preferences file, so only the first launch of an app starts on
Material and switches once the measurement lands; later launches read the stored level and open straight into the right
tier. The measurement still runs on those launches, but only to update what is stored — the session keeps the tier it
opened with, and a new level takes effect on the next launch, so glass never switches mid-session and a measurement
taken while the device was busy corrects itself on the launch after.

Clearing that file, or the app's data, makes the next launch measure from scratch.

## Testing a tier

The tier follows what the device measures, so testing one means running on hardware that reaches it. These emulator
configurations resolve as listed:

| Level → tier | Device profile | RAM | Cores | Heap |
| --- | --- | --- | --- | --- |
| `AVERAGE` → `REDUCED` | Pixel 7 | 4096 MB | 4 | 256 MB |
| `HIGH` → `FULL` | Pixel 9 | 6144 MB | 6 | 512 MB |

To force a tier on a device that measures into a different one, edit the mapping in `LiquidGlassCapability.kt`. Line 59
is the one to change:

```kotlin
PerformanceLevel.LOW, PerformanceLevel.UNKNOWN -> NATIVE  // NATIVE → FULL or REDUCED to test glass on a slow device
```

The preferences file stores the measured *level*, not the tier, so an edit to this mapping takes effect on the next
launch without clearing anything. Clear the file only to force a fresh measurement:

```
adb shell run-as <application id> rm /data/data/<application id>/shared_prefs/skip.ui.liquidglass.xml
```

The `liquidGlass` environment value picks how that level is used: `adaptive` maps it as above, `disabled` is always
`NATIVE`, `forced` is always `FULL` without measuring at all, and `forcedOptimized` gives the top two classes `FULL`
and every other class — including an unmeasured one — `REDUCED`, so glass renders everywhere but the expensive lens
effects are kept to devices that can afford them.

`LiquidGlassStyle` carries what differs between tiers — lens refraction, chromatic aberration, and whether the tab bar
draws its separate accent layer. Components read `LiquidGlassStyle.current` instead of hard-coding effects. `NATIVE`
never reaches this code: the Swift side falls back to Material rendering before calling in.

## Measurements

**`LiquidGlassMetrics.kt`** holds every fixed measurement the fork lays out with — toolbar and tab bar alike — so a
size is changed in one place rather than hunted through the layout code. One file serves both layers: the Swift
sources in `Sources/SkipUI/LiquidGlass` are transpiled into this same module and read these values directly, so
nothing has to be kept in step by hand.

A toolbar is built from a single value, `liquidGlassToolbarSpacing`. It is the margin a bar keeps at its edge, the
space between the back button and the capsule after it, and the gap between any two capsules — so the run from the
edge of the screen through the back button and on across the capsules is evenly spaced, and the top and bottom bars
share one rhythm. `liquidGlassToolbarItemSpacing`, the gap between two items sharing a capsule, is derived from it
rather than chosen: crossing a capsule boundary costs the capsule's inset, that spacing, and the next button's own
inset, and two icons on one capsule are set the same distance apart.

## Components

### Surfaces and buttons

- **`LiquidGlassSurface.kt`** — `Modifier.liquidGlassSurface(…)`, the one glass surface used by buttons, `glassEffect`,
  and the toolbar. Blur, lens refraction, frost or tint, specular highlight, shadow. It samples `LocalGlassBackdrop`
  when something provides a recording, otherwise a local recording of its own content. Also holds the floating toolbar
  capsule (`LiquidGlassToolbarGroup`), which is never narrower than it is tall and centers what it holds so a capsule
  with one icon comes out round, along with `WithGlassBackdrop` and `LocalGlassToolbarCapsule`.
- **`LiquidGlassButton.kt`** — the glass button capsule, the frost color used across the fork, and
  `LocalGlassBackdrop`, the composition local that tells glass which recording to sample.

### Tab bar

- **`LiquidBottomBar.kt`** — the floating glass tab bar. `LiquidGlassTabView` places it: it occupies a zero-height slot
  so tab content keeps its full height and scrolls under the glass, draws upward from the bottom edge, sits above the
  system navigation bar or an outer bar's footprint, and reports its own footprint back through the shared state.
  `LiquidGlassTabBar` draws the bar itself in three layers — the visible glass bar with neutral icons, a hidden
  accent-tinted copy recorded into its own layer, and the sliding pill that refracts both so the selected icon reads in
  the tint. It also handles selection by tap or drag, disabled tabs, the tint and toolbar background, and the minimized
  state. `LiquidGlassTabBarEdgeEffect` blurs the content out toward the bottom edge below the bar.
- **`LiquidGlassTabBarState.kt`** — what one `TabView` and its bar share: the backdrop, `inset` (the bar's whole
  footprint, read by chrome that must sit above it), `contentInset` (the part a scrollable adds after its content), and
  `isMinimized`. Also the nested-scroll connection that drives minimizing, and the two composition locals
  (`LocalGlassTabBarInset`, `LocalGlassContentInset`) with their providers.

### Containers

- **`LiquidGlassZStack.kt`** — records each `ZStack` child except the frontmost and hands each child the combined
  layers behind it, so a glass button over a list refracts the list. A child never samples its own layer; that would
  make the layer draw itself and crash the renderer.

### Interaction

- **`DampedDragAnimation.kt`** — the spring-settled value behind the tab bar's selection pill: drag tracking, press
  progress, per-axis scale, and velocity for the stretch.
- **`DragGestureInspector.kt`** — drag detection without touch slop, observing pointer events without consuming them,
  so glass reacts on touch-down while enclosing `clickable` modifiers still work.
- **`InteractiveHighlight.kt`** — the glow that follows a touch across a glass surface.

The last three are adapted from the catalog components in
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).

## Insets

The glass bar takes no space in the layout, which is what leaves live content under the glass to refract. Two separate
values carry that fact outward, and mixing them up is what makes content clip or a bar land on another:

- **`LocalGlassTabBarInset`** moves things. A nested `TabView`'s bar and a bottom toolbar pad themselves by it and end
  up above the bar. It accumulates through nesting, so a bar inside a bar stacks correctly at any depth.
- **`LocalGlassContentInset`** moves nothing. `List` adds it to its footer spacer and `ScrollView` as bottom padding,
  so the end of the content can be scrolled clear of the chrome while still drawing beneath it. It excludes the system
  navigation bar, which scrollables already handle through the safe area. A floating bottom toolbar adds its own height
  to it for the content below it.

The Material tab bar publishes the first value too, with no content inset, which is what keeps a nested Material bar
off the outer one.

## Package layout

This code lives in its own `skip.ui.liquidglass` package rather than inside SkipUI's transpiled Swift, for two
reasons. Compose APIs like composition locals, `Modifier.layout`, nested scroll connections, and the backdrop library's
draw calls are written directly instead of through the transpiler. And the fork stays contained: upstream SkipUI files
change only by single marked lines that call into the Swift layer, which calls into this package.
