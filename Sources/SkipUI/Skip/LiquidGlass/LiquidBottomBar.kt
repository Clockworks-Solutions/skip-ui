// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: floating glass tab bar built on Kyant's backdrop library.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui.liquidglass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

import kotlin.math.abs
import kotlin.math.roundToInt

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The width of the glass tab bar once it is minimized: room for one tab icon. */
internal val liquidGlassMinimizedTabBarWidth = 76.dp

/** The height of the glass tab bar at full size, and minimized. */
internal val liquidGlassTabBarHeight = 65.dp
internal val liquidGlassMinimizedTabBarHeight = 50.dp

/**
 * A single tab in a [LiquidGlassTabBar].
 *
 * @property icon The tab icon, drawn in a fixed-size box.
 * @property title The tab label, drawn below the icon.
 * @property content The body shown when the tab is selected. Not used by [LiquidGlassTabView], where `TabView` renders the content.
 * @property isEnabled Whether the tab can be selected. A disabled tab is dimmed, ignores taps, and the selection pill
 *   snaps past it, matching the Material `NavigationBarItem` for a `.disabled` tab.
 */
internal class GlassTab(
    val icon: @Composable () -> Unit,
    val title: @Composable () -> Unit,
    val content: @Composable () -> Unit,
    val isEnabled: Boolean = true
)

/**
 * The Liquid Glass tab bar for SkipUI's `TabView`: a [LiquidGlassTabBar] floating over the tab content, in place of
 * the Material `NavigationBar`.
 *
 * The `TabView` owns the tab content, back stacks, and selection; this only renders the bar. It takes no height in the
 * tab view layout, so tab content extends to the bottom of the screen and scrolls beneath the glass. The bar draws
 * upward from the bottom edge, above the system navigation bar, and is sized to about 88dp per tab, capped to the
 * available width minus 24dp on each side.
 *
 * @param state The bar state shared with the `TabView`, holding the backdrop it refracts and its minimized state.
 * @param tabIndices The `TabView` indices of the visible tabs, in display order. Hidden and conditional tabs are omitted.
 * @param selectedTabIndex The `TabView` index of the selected tab.
 * @param icon Renders the icon for a `TabView` index.
 * @param label Renders the title for a `TabView` index, if tabs have titles.
 * @param isEnabled Whether the tab at a `TabView` index can be selected.
 * @param onTabSelected Called with the `TabView` index of the tab the user selects, on every tap, including a tap on
 *   the already-selected tab.
 * @param accentColor The selected tab color, from the `TabView` tint or the app accent color.
 *   `Color.Unspecified` falls back to the default blue.
 * @param backgroundColor A color drawn over the glass frost, from `.toolbarBackground(_:for: .tabBar)`.
 *   `Color.Unspecified` or a fully transparent color leaves the bar clear.
 * @param contentColor The unselected icon and label color. `Color.Unspecified` uses black or white for the theme.
 */
@Composable
internal fun LiquidGlassTabView(
    state: LiquidGlassTabBarState,
    tabIndices: kotlin.collections.List<Int>,
    selectedTabIndex: Int,
    icon: @Composable (Int) -> Unit,
    label: (@Composable (Int) -> Unit)?,
    isEnabled: (Int) -> Boolean,
    onTabSelected: (Int) -> Unit,
    accentColor: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified
) {
    if (tabIndices.isEmpty()) return

    // Map TabView indices to glass tabs; LiquidGlassTabBar works in display positions
    val tabs = tabIndices.map { tabIndex ->
        GlassTab(icon = { icon(tabIndex) }, title = { label?.invoke(tabIndex) }, content = {}, isEnabled = isEnabled(tabIndex))
    }
    val selectedPosition = tabIndices.indexOf(selectedTabIndex).coerceAtLeast(0)

    // 0 at full size, 1 minimized to a pill holding only the selected tab, as the iOS bar does when scrolled
    val minimizeProgress by animateFloatAsState(
        targetValue = if (state.isMinimized) 1f else 0f,
        animationSpec = spring(0.9f, 400f, 0.001f),
        label = "minimize"
    )

    // Zero-height slot so the TabView gives its content the full height; the bar overflows upward over the content
    Box(modifier = Modifier.fillMaxWidth().height(0.dp).zIndex(1f)) {
        // Measure available width, then size the bar to content (ideal 88dp per tab),
        // falling back to equal sharing when the screen is too narrow
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val idealPx = with(density) { (88.dp * tabs.size).toPx() }
            val maxPx = (constraints.maxWidth.toFloat() - with(density) { 48.dp.toPx() })
                .coerceAtLeast(0f)
            val fullBarWidth = with(density) { idealPx.coerceAtMost(maxPx).toDp() }
            // Minimized, the bar is just wide enough for the selected tab's icon
            val barWidth = fullBarWidth * (1f - minimizeProgress) + liquidGlassMinimizedTabBarWidth * minimizeProgress
            LiquidGlassTabBar(
                backdrop = state.backdrop,
                tabs = tabs,
                selectedIndex = selectedPosition,
                onTabSelected = { position -> onTabSelected(tabIndices[position]) },
                modifier = Modifier.width(barWidth),
                accentColor = accentColor,
                backgroundColor = backgroundColor,
                contentColor = contentColor,
                minimizeProgress = minimizeProgress,
                onExpand = { state.isMinimized = false }
            )
        }
    }
}

/**
 * A capsule-shaped Liquid Glass tab bar with a draggable selection pill.
 *
 * Tap a tab to select it, or drag the pill across tabs; it snaps to the nearest tab on release. While
 * pressed, the pill grows, stretches in the direction of travel, and shows a lens effect that magnifies
 * the accent-colored icon beneath it. The whole bar nudges slightly in the drag direction.
 *
 * The bar is built from three stacked layers:
 * 1. The visible glass bar with neutral icons and labels.
 * 2. A hidden, accent-tinted copy of the icons, recorded into a separate backdrop.
 * 3. The pill, which refracts both backdrops so the selected icon shows in the accent color.
 *
 * [LiquidGlassTabView] uses this for SkipUI's `TabView`, or use this directly to place the bar over custom content.
 *
 * @param backdrop The recorded content behind the bar, typically from `rememberLayerBackdrop` applied to the
 *   screen content with `layerBackdrop`.
 * @param tabs The tabs to show. Only [GlassTab.icon], [GlassTab.title], and [GlassTab.isEnabled] are used.
 * @param selectedIndex The selected tab. Changes to this value move the pill.
 * @param onTabSelected Called with the index the user picked, by tap or by drag. A tap reports the tab even when it is
 *   already selected, so a re-tap reaches the app, as with the Material `NavigationBar`.
 * @param modifier The modifier to apply to the bar, usually a width.
 * @param style The tier's glass settings. Defaults to [LiquidGlassStyle.current]. Without [LiquidGlassStyle.accentLayer],
 *   layer 2 is skipped: layer 1 tints the tab under the pill with the accent color and feeds the pill's backdrop instead.
 * @param accentColor The selected tab color. `Color.Unspecified` falls back to the default blue, for callers
 *   outside `TabView`, which always passes the tint or the app accent color.
 * @param backgroundColor A color drawn over the glass frost, leaving the bar clear when it is `Color.Unspecified` or
 *   fully transparent. An opaque color makes the bar opaque, as it does for the Material bar.
 * @param contentColor The unselected icon and label color. `Color.Unspecified` uses black or white for the theme.
 * @param minimizeProgress 0 draws the full bar, 1 the minimized pill: labels gone, unselected tabs collapsed away, and
 *   only the selected icon left. Values between animate that.
 * @param onExpand Called when the user taps the minimized bar, which restores it on iOS.
 */
@Composable
internal fun LiquidGlassTabBar(
    backdrop: Backdrop,
    tabs: kotlin.collections.List<GlassTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: LiquidGlassStyle = LiquidGlassStyle.current,
    accentColor: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    minimizeProgress: Float = 0f,
    onExpand: (() -> Unit)? = null
) {
    if (tabs.isEmpty()) return

    val isMinimized = minimizeProgress > 0.5f

    val isLightTheme = !isSystemInDarkTheme()

    val resolvedAccentColor = if (accentColor.isSpecified) accentColor else if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val frostColor = liquidGlassFrostColor(isLightTheme)
    // An app background color is drawn over the frost; the default one is fully transparent and leaves the glass clear
    val hasBackgroundColor = backgroundColor.isSpecified && backgroundColor.alpha > 0f
    val drawBarSurface: DrawScope.() -> Unit = {
        drawRect(frostColor)
        if (hasBackgroundColor) {
            drawRect(backgroundColor)
        }
    }

    val baseContentColor = if (contentColor.isSpecified && contentColor.alpha > 0f) contentColor else if (isLightTheme) Color.Black else Color.White
    val isCompactLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val iconSize = if (isCompactLandscape) 22.dp else 30.dp
    // SkipUI draws a tab icon at 1.5x the current font size, so a larger text style grows the icon itself. Scaling the
    // whole icon instead would scale its badge with it, and blur the glyph
    val iconTextStyle = LocalTextStyle.current.copy(fontSize = (iconSize.value / 1.5f).sp)
    val labelTextStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
    val labelTextStyleBold = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val minimumTouchTarget = 44.dp * (1f - minimizeProgress)
    val barHeight = liquidGlassTabBarHeight * (1f - minimizeProgress) + liquidGlassMinimizedTabBarHeight * minimizeProgress
    val disabledContentAlpha = 0.38f // Material's disabled content alpha, applied over the bar's already-faded tabs

    val tabsBackdrop = rememberLayerBackdrop()
    val animationScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabs.size
        }

        // Rubber-band nudge: the whole bar leans slightly in the drag direction,
        // decaying via EaseOut, then springs back to 0 once the drag ends
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).coerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * (if (fraction >= 0f) 1f else -1f) * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

        // Local mirror of selectedIndex: driven by taps, drag-stop, and external
        // selectedIndex changes alike, so the pill animation has one source of truth
        var currentIndex by remember { mutableIntStateOf(selectedIndex) }

        // The drag animation below is remembered across recompositions, so it reads the callback and the tabs through
        // these holders rather than capturing the first composition's values
        val latestOnTabSelected = rememberUpdatedState(onTabSelected)
        val latestTabs = rememberUpdatedState(tabs)

        // A disabled tab can't take the selection, so a drag released over one snaps to the closest tab that can
        fun nearestEnabledIndex(index: Int): Int {
            val currentTabs = latestTabs.value
            if (currentTabs.getOrNull(index)?.isEnabled != false) {
                return index
            }
            for (distance in 1 until currentTabs.size) {
                if (currentTabs.getOrNull(index - distance)?.isEnabled == true) {
                    return index - distance
                }
                if (currentTabs.getOrNull(index + distance)?.isEnabled == true) {
                    return index + distance
                }
            }
            return index
        }

        // Drives the pill position as a fractional tab index.
        // pressProgress / scaleX / scaleY animate on press for the squish effect
        val anim = remember {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.toFloat(),
                valueRange = 0f..(tabs.size - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.4f,
                onDragStarted = { _ -> },
                onDragStopped = {
                    val targetIndex = nearestEnabledIndex(targetValue.roundToInt().coerceIn(0, tabs.size - 1))
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    // The pill covers the selected tab and takes its touches, so this also reports a tap on the
                    // selected tab, which is how a re-tap reaches the app
                    latestOnTabSelected.value(targetIndex)
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    val direction = if (isLtr) 1f else -1f
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * direction)
                            .coerceIn(0f, (tabs.size - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }

        // Sync externally-driven selection changes (e.g. back-stack navigation)
        LaunchedEffect(selectedIndex) {
            if (selectedIndex != currentIndex) currentIndex = selectedIndex
        }
        // Any currentIndex change (tap, drag-stop, or external selection) animates the pill. The tap and drag-stop
        // handlers report the selection themselves, so a re-tap of the selected tab still reaches the caller
        LaunchedEffect(anim) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    anim.animateToValue(index.toFloat())
                }
        }

        // Shared glow, anchored to the pill's own position rather than the finger,
        // fading in on press and out on release
        val interactiveHighlight = remember {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (anim.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (anim.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // ── Layer 1: visible glass bar + tab icons ──────────────────────────
        // Without the accent layer, this layer feeds `tabsBackdrop` instead, so the pill still shows the
        // (accent-tinted) icon beneath it.
        Row(
            modifier = Modifier
                .then(if (style.accentLayer) Modifier else Modifier.layerBackdrop(tabsBackdrop))
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(percent = 50) },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        if (style.lens) lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        // Grow the bar by up to 16dp in width while the pill is pressed
                        val progress = anim.pressProgress
                        val scale = 1f + (16f.dp.toPx() / size.width) * progress
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = drawBarSurface
                )
                .then(interactiveHighlight.modifier)
                .fillMaxWidth()
                .height(barHeight)
                .padding(5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                // Proximity: 1.0 at the selected tab, fading to 0 one tab away
                val proximity = (1f - abs(anim.value - index.toFloat())).coerceIn(0f, 1f)
                val isSelected = index == anim.value.roundToInt()

                // Neutral icons fade out as the pill passes over them, letting the accent copy show through
                val iconAlpha by animateFloatAsState(
                    targetValue = (1f - proximity) * 0.5f,
                    animationSpec = tween(durationMillis = 200),
                    label = "Alpha_$index"
                )

                // Minimizing collapses every tab but the selected one, leaving its icon alone in the pill
                val isMinimizingAway = index != currentIndex
                val tabWeight = if (isMinimizingAway) (1f - minimizeProgress).coerceAtLeast(0.0001f) else 1f
                val tabAlpha = when {
                    !tab.isEnabled -> disabledContentAlpha
                    isMinimizingAway -> 1f - minimizeProgress
                    else -> 1f
                }

                Box(
                    modifier = Modifier
                        .weight(tabWeight)
                        .fillMaxHeight()
                        .sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget)
                        // Fade the whole tab rather than only its content color, so an icon that draws its own colors dims too
                        .alpha(tabAlpha)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = tab.isEnabled
                        ) {
                            if (isMinimized && onExpand != null) {
                                // A tap on the minimized bar restores it, rather than changing tab
                                onExpand()
                            } else {
                                currentIndex = index
                                // Report every tap, including one on the selected tab, as the Material bar does
                                latestOnTabSelected.value(index)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val tabContentColor = if (style.accentLayer) baseContentColor.copy(alpha = iconAlpha)
                        else lerp(baseContentColor.copy(alpha = 0.5f), resolvedAccentColor, proximity)
                    // Without the accent layer, this layer also draws the selected tab, so it takes the bolder label
                    val tabLabelTextStyle = if (!style.accentLayer && isSelected) labelTextStyleBold else labelTextStyle
                    CompositionLocalProvider(
                        LocalContentColor provides tabContentColor
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                                ProvideTextStyle(iconTextStyle) { tab.icon() }
                            }
                            ProvideTextStyle(tabLabelTextStyle) {
                                Box(
                                    modifier = Modifier
                                        .wrapContentHeight()
                                        .minimizedLabel(minimizeProgress)
                                ) {
                                    tab.title()
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Layer 2: hidden accent-tinted copy ──────────────────────────────
        // Feeds `tabsBackdrop`. The pill below reveals a lens-distorted slice of
        // this layer through a combined backdrop, which is why the icon under
        // the glass pill reads as accent-colored while the rest stay neutral.
        // Hidden from accessibility so tabs aren't announced twice. Skipped without the accent layer.
        if (style.accentLayer) Row(
            modifier = Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(percent = 50) },
                    effects = {
                        val progress = anim.pressProgress
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(
                            24f.dp.toPx() * progress,
                            24f.dp.toPx() * progress
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = anim.pressProgress)
                    },
                    onDrawSurface = drawBarSurface
                )
                .then(interactiveHighlight.modifier)
                .fillMaxWidth()
                .height(barHeight - 9.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val isMinimizingAway = index != currentIndex
                Box(
                    modifier = Modifier
                        .weight(if (isMinimizingAway) (1f - minimizeProgress).coerceAtLeast(0.0001f) else 1f)
                        .fillMaxHeight()
                        .sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget)
                        .alpha(if (tab.isEnabled) 1f else disabledContentAlpha),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides resolvedAccentColor) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                                ProvideTextStyle(iconTextStyle) { tab.icon() }
                            }
                            ProvideTextStyle(labelTextStyleBold) {
                                Box(
                                    modifier = Modifier
                                        .wrapContentHeight()
                                        .minimizedLabel(minimizeProgress)
                                ) {
                                    tab.title()
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Layer 3: sliding liquid pill + drag surface ─────────────────────
        // `anim.modifier` is what actually receives touch drags; without it
        // attached here, DampedDragAnimation never sees pointer input at all.
        // Minimized, the bar itself is the pill, so this layer fades out and stops taking touches.
        if (!isMinimized) Box(
            modifier = Modifier
                .alpha(1f - minimizeProgress)
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) anim.value * tabWidth + panelOffset
                        else size.width - (anim.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(anim.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { RoundedCornerShape(percent = 50) },
                    effects = {
                        // Lens, highlight, and shadows only appear while pressed
                        val progress = anim.pressProgress
                        if (style.lens) lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = style.chromaticAberration
                        )
                    },
                    highlight = {
                        // A faint rim at rest gives the pill a defined capsule edge, as on iOS, and it brightens on press
                        Highlight.Default.copy(alpha = 0.4f + 0.6f * anim.pressProgress)
                    },
                    shadow = {
                        Shadow(alpha = anim.pressProgress)
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 8f.dp * anim.pressProgress,
                            alpha = anim.pressProgress
                        )
                    },
                    layerBlock = {
                        scaleX = anim.scaleX
                        scaleY = anim.scaleY

                        // Stretch along the direction of travel and thin out vertically, capped at ±20%
                        val velocity = anim.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        // Resting: a light fill, so the pill reads as a bright capsule over the content rather than a
                        // shadow. Pressed: nearly clear so the lens shows through
                        val progress = anim.pressProgress
                        drawRect(
                            if (isLightTheme) Color.White.copy(alpha = 0.24f)
                            else Color.White.copy(alpha = 0.16f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(barHeight - 9.dp)
                .fillMaxWidth(1f / tabs.size)
        )
    }
}

/**
 * Collapses a tab label as the bar minimizes: it fades out and gives up its height, so the icon settles into the
 * middle of the shrinking bar instead of the label leaving a gap behind it.
 */
private fun Modifier.minimizedLabel(progress: Float): Modifier {
    if (progress <= 0f) {
        return this
    }
    return this
        .alpha(1f - progress)
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, (placeable.height * (1f - progress)).roundToInt()) {
                placeable.place(0, 0)
            }
        }
}
