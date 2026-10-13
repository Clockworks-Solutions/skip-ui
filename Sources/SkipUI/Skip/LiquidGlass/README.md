# Liquid Glass — Compose layer

The Android side of the fork's Liquid Glass, in the `skip.ui.liquidglass` package: plain Compose, compiled as-is and
called from the Swift in `Sources/SkipUI/LiquidGlass`. Upstream SkipUI files change only by single marked lines.

Dependencies, declared in `Sources/SkipUI/Skip/skip.yml`:

- **Kyant's AndroidLiquidGlass** (`io.github.kyant0:backdrop:1.0.6`) — layer recording, `drawBackdrop`, and the
  `vibrancy`, `blur` and `lens` effects. The lens needs Android 13.
- **Blinkit's Droid Dex** (`com.eternal.kits:droid-dex:4.0.0`, GPL-2.0) — measures CPU and memory.

## Rendering tiers

| Droid Dex level | Tier | Glass |
| --- | --- | --- |
| `EXCELLENT`, `HIGH` | `FULL` | Every effect: blur, lens with chromatic aberration, tab bar accent layer |
| `AVERAGE` | `REDUCED` | Blur and chrome (frost, rim, shadow, pill), no lens |
| `LOW`, `UNKNOWN` | `NATIVE` | None: upstream Material rendering |

Glass follows the system light or dark theme.

The tier is fixed for the session at its first use, so glass never switches while the app is open. It comes from the
level stored in the `skip.ui.liquidglass` preferences by an earlier launch or, on the very first launch, from a quick
estimate from memory and cores; both are synchronous, so the first frame already shows the right bar. Droid Dex
measures in the background every launch and stores its result for the next.

`liquidGlass` picks how the level is used: `adaptive` as above; `disabled` always `NATIVE`; `forced` always `FULL`;
`forcedOptimized` `FULL` on the top two levels and `REDUCED` elsewhere.

To test a tier, write the level the next launch reads (`HIGH`, `AVERAGE` or `LOW`):

```
adb shell run-as <application id> sh -c 'echo "<map><string name=\"performanceLevel\">HIGH</string></map>" > shared_prefs/skip.ui.liquidglass.xml'
```

## Files

| File | Contents |
| --- | --- |
| `LiquidGlassCapability.kt` | Tiers, `LiquidGlassStyle`, and resolving the device class. |
| `LiquidGlassMetrics.kt` | Every fixed size, shared with the Swift layer. One spacing builds a toolbar's rhythm. |
| `LiquidGlassSurface.kt` | `liquidGlassSurface`, the glass behind buttons, capsules and `glassEffect`; the toolbar capsule. |
| `LiquidGlassButton.kt` | The glass button, the shared frost color, and `LocalGlassBackdrop`. |
| `LiquidGlassLight.kt` | The rim highlight, lit from an angle that follows the device's tilt. |
| `LiquidGlassLayer.kt` | Lazily recorded layers for glass in `overlay`, `background` and `safeAreaInset`. |
| `LiquidGlassZStack.kt` | The same for a `ZStack`'s children. |
| `LiquidBottomBar.kt` | The floating tab bar: glass bar, accent layer, dimming pill and badges, in four layers. |
| `LiquidGlassTabBarController.kt` | The bar's moving parts (selection, pill, lean, minimize geometry) and the tab row layout, read only while laying out and drawing so animations never recompose a tab. |
| `LiquidGlassTabBarState.kt` | State a `TabView` shares with its bar, minimize-on-scroll, and the inset locals. |
| `LiquidGlassTabIcon.kt` | Icons held inside their slot, and iOS-style badges. |
| `DampedDragAnimation.kt`, `DragGestureInspector.kt`, `InteractiveHighlight.kt` | The pill's drag spring and the touch glow, adapted from Kyant's catalog. |

## Backdrops

Glass refracts `LocalGlassBackdrop`: a recording of what lies behind it. It can only sample content beside it, never
content containing it, or the layer would draw itself and crash the renderer. So wherever views are layered — the tab
bar and toolbars over navigation content, `ZStack` children, `overlay`, `background`, `safeAreaInset` — the view behind
is recorded for the one in front, but only once glass in front requests it. A solid `background(Color)` is handed over
as a painted backdrop, with nothing to record.

The backdrop library doesn't redraw when a draw lambda changes, so animated values such as the frost are read while
drawing.

## Insets

The glass bar takes no layout space, so content runs beneath it. Two values carry its footprint outward:

- **`LocalGlassTabBarInset`** moves things: a nested bar and a bottom toolbar pad themselves above the bar.
- **`LocalGlassContentInset`** moves nothing: scrollables add it after their content so the end clears the chrome.

`safeAreaInset(edge: .bottom)` follows iOS: inside a tab it sits just above the bar; on a `TabView` the bar stays at the
bottom with the inset drawn over it. A sheet or cover starts with every glass local reset (`WithoutGlassChrome`), as an
iOS presentation is its own window.

## Tab bar performance

Animation frames never recompose the bar: the pill's position, the minimize progress and the lean are read only in
layout and draw lambdas. A picked tab is reported to the `TabView` once the pill lands (at most 250ms), because
composing the new tab's content can take longer than a frame and would otherwise stall the slide. At rest the pill draws
no lens, rim or shadow. Measure on a release build: a debuggable APK runs Compose in the interpreter, several times
slower.
