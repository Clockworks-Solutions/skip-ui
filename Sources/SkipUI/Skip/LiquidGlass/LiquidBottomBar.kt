// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: the floating glass tab bar, built on Kyant's backdrop library.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui.liquidglass

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
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

import kotlin.math.roundToInt

import kotlinx.coroutines.flow.collectLatest

/**
 * One tab of a [LiquidGlassTabBar].
 *
 * @property isEnabled A disabled tab is dimmed, ignores taps, and the pill snaps past it.
 */
@Immutable
internal class GlassTab(val icon: @Composable () -> Unit, val title: @Composable () -> Unit, val isEnabled: Boolean = true)

private val capsuleShape = RoundedCornerShape(percent = 50)
private val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
private val minimizeAnimation = spring(0.9f, 400f, 0.001f)

/** The inset between the bar's edge and its tabs, which the pill keeps too. */
private val tabInset = 4.dp

/** How much shorter than the bar the pill and the accent copy are. */
private val pillInset = 9.dp

/** The bar's height at [minimizeProgress]. */
private fun barHeight(minimizeProgress: Float): Dp = lerp(liquidGlassTabBarHeight, liquidGlassMinimizedTabBarHeight, minimizeProgress)

/**
 * The glass bar for SkipUI's `TabView`, floating over the tab content in place of the Material `NavigationBar`.
 *
 * Takes no height, so content scrolls beneath it. About 88dp per tab, within 24dp side margins; it sits above the
 * system bar, or an outer bar when nested, and publishes its footprint through [LiquidGlassTabBarState]. Minimizing
 * animates the bar's size while laying out, so the tabs aren't recomposed on each frame.
 *
 * @param tabIndices The `TabView` indices of the visible tabs, in order.
 * @param onTabSelected Called on every tap, including on the selected tab.
 * @param accentColor The selected tab's color; unspecified falls back to the system blue.
 * @param backgroundColor A `.toolbarBackground(_:for: .tabBar)` color drawn over the frost.
 * @param contentColor The unselected tab color; unspecified follows the glass.
 */
@Composable
internal fun LiquidGlassTabView(
    state: LiquidGlassTabBarState,
    tabIndices: List<Int>,
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
    val tabs = tabIndices.map { GlassTab(icon = { icon(it) }, title = { label?.invoke(it) }, isEnabled = isEnabled(it)) }
    // 0 full size, 1 minimized to the selected tab; read only while laying out and drawing
    val minimize = remember { Animatable(if (state.isMinimized) 1f else 0f) }
    // Observed here rather than read in composition, so minimizing doesn't recompose the bar
    LaunchedEffect(state, minimize) {
        snapshotFlow { state.isMinimized }.collectLatest { minimize.animateTo(if (it) 1f else 0f, minimizeAnimation) }
    }
    val minimizeProgress: () -> Float = remember(minimize) { { minimize.value } }

    // A zero-height slot: the bar overflows upward over the content
    Box(modifier = Modifier.fillMaxWidth().height(0.dp).zIndex(1f)) {
        val density = LocalDensity.current
        val systemBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // Above the system bar, or the outer bar's footprint when nested
        val bottomInset = LocalGlassTabBarInset.current.takeIf { it > 0.dp } ?: systemBottomInset

        LiquidGlassTabBarEdgeEffect(
            backdrop = state.backdrop,
            height = { bottomInset + 2.dp + barHeight(minimizeProgress()) + liquidGlassTabBarEdgeFade },
            minimizeProgress = minimizeProgress,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Bottom, unbounded = true)
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                .padding(bottom = bottomInset + 2.dp),
            contentAlignment = Alignment.Center
        ) {
            val availableWidth = maxWidth
            val fullWidth = minOf(88.dp * tabs.size, (availableWidth - liquidGlassTabBarSideMargin * 2f).coerceAtLeast(0.dp))
            LiquidGlassTabBar(
                backdrop = state.backdrop,
                tabs = tabs,
                selectedIndex = tabIndices.indexOf(selectedTabIndex).coerceAtLeast(0),
                onTabSelected = { onTabSelected(tabIndices[it]) },
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val progress = minimizeProgress()
                        val width = lerp(fullWidth, liquidGlassMinimizedTabBarWidth, progress)
                        val placeable = measurable.measure(Constraints.fixedWidth(width.roundToPx()).copy(maxHeight = constraints.maxHeight))
                        layout(placeable.width, placeable.height) {
                            // Minimizing tucks the bar into the leading edge, out of the content's way
                            val offset = (liquidGlassTabBarSideMargin - (availableWidth - width) / 2f) * progress
                            placeable.placeRelative(offset.roundToPx(), 0)
                        }
                    }
                    .onGloballyPositioned {
                        // Only at full size, so what reads the inset doesn't move through the minimize animation
                        if (minimizeProgress() == 0f) {
                            state.inset = bottomInset + 2.dp + with(density) { it.size.height.toDp() }
                            // Scrollables clear the system bar through the safe area already
                            state.contentInset = (state.inset - systemBottomInset).coerceAtLeast(0.dp)
                        }
                    },
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
 * The strip the bar floats on, blurring the content toward the screen's bottom edge as iOS does, so the bar sits on the
 * content rather than being cut out of it. Fades out as the bar minimizes.
 */
@Composable
private fun LiquidGlassTabBarEdgeEffect(backdrop: Backdrop, height: () -> Dp, minimizeProgress: () -> Float, modifier: Modifier = Modifier) {
    val isHidden by remember { derivedStateOf { minimizeProgress() >= 1f } }
    if (isHidden) return
    // Starts low and never fully replaces the sharp content, so rows beside the bar stay readable
    val mask = remember { Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Black.copy(alpha = 0.25f), 1f to Color.Black.copy(alpha = 0.85f)) }
    Box(
        modifier = modifier
            .animatedHeight(height)
            .graphicsLayer {
                alpha = 1f - minimizeProgress()
                // Its own layer, so the gradient masks the blurred copy rather than the screen
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
            }
            .drawBackdrop(backdrop = backdrop, shape = { RectangleShape }, effects = { blur(4f.dp.toPx()) })
    )
}

/**
 * A capsule glass tab bar with a draggable selection pill.
 *
 * Tap a tab, or drag the pill and release to snap. Pressed, the pill grows, stretches with its velocity and magnifies
 * the accent-colored icon beneath it; the bar leans toward the drag. Drawn in four layers:
 * 1. The glass bar with neutral icons and labels.
 * 2. With [LiquidGlassStyle.accentLayer], a hidden accent-tinted copy of the icons for the pill to reveal; without it,
 *    layer 1 tints the tab under the pill and feeds the pill.
 * 3. The pill, which dims the selected tab at rest, as on iOS. Minimizing, it becomes the minimized bar, so the
 *    selected tab stays selected throughout.
 * 4. The badges, above the pill, as on iOS.
 *
 * The moving parts live in a [LiquidGlassTabBarController] and are read only while laying out and drawing, so sliding
 * the pill or minimizing redraws the bar without recomposing its tabs. The glass follows the system theme.
 *
 * @param backdrop The recorded content behind the bar.
 * @param onTabSelected Called with the picked position, by tap or drag, including a re-tap of the selected tab.
 * @param accentColor The selected tab's color; unspecified falls back to the system blue.
 * @param backgroundColor A color over the frost; opaque makes the bar opaque.
 * @param contentColor The unselected tab color; unspecified follows the glass.
 * @param minimizeProgress 0 the full bar, 1 minimized to the selected tab; read while laying out and drawing.
 * @param onExpand Called on a tap of the minimized bar, which restores it on iOS.
 */
@Composable
internal fun LiquidGlassTabBar(
    backdrop: Backdrop,
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: LiquidGlassStyle = LiquidGlassStyle.current,
    accentColor: Color = Color.Unspecified,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    minimizeProgress: () -> Float = { 0f },
    onExpand: (() -> Unit)? = null
) {
    if (tabs.isEmpty()) return
    val controller = rememberLiquidGlassTabBarController(tabs.size, selectedIndex, minimizeProgress)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    SideEffect {
        controller.tabs = tabs
        controller.onTabSelected = onTabSelected
        controller.onExpand = onExpand
        controller.isLtr = isLtr
        controller.follow(selectedIndex)
    }

    // The system theme's glass
    val isLight = !isSystemInDarkTheme()
    val accent = if (accentColor.isSpecified) accentColor else if (isLight) Color(0xFF0088FF) else Color(0xFF0091FF)
    val frost = liquidGlassFrostColor()
    val baseContent = if (contentColor.isSpecified && contentColor.alpha > 0f) contentColor else if (isLight) Color.Black else Color.White
    val drawBarSurface: DrawScope.() -> Unit = {
        drawRect(frost)
        if (backgroundColor.isSpecified && backgroundColor.alpha > 0f) drawRect(backgroundColor)
    }

    val iconSize = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 22.dp else 30.dp
    val textStyle = LocalTextStyle.current
    // SkipUI draws a tab icon at 1.5× the font size, so the icon is sized through the text style
    val cellStyle = remember(iconSize, textStyle) { GlassTabCellStyle(iconSize, textStyle.copy(fontSize = (iconSize.value / 1.5f).sp)) }

    val tabsBackdrop = rememberLayerBackdrop()
    val rimHighlight = rememberLiquidGlassHighlight()
    val animationScope = rememberCoroutineScope()
    val maxLean = with(LocalDensity.current) { 4.dp.toPx() }
    val insetPx = with(LocalDensity.current) { tabInset.toPx() }
    val layers = GlassTabBarLayers(controller, tabs, cellStyle, backdrop, style, rimHighlight, maxLean)

    // The press glow, anchored to the pill rather than the finger
    val glow = remember(controller) {
        InteractiveHighlight(animationScope = animationScope, position = { size, _ ->
            val center = insetPx + controller.pillStart(controller.contentWidth) + controller.pillWidth(controller.contentWidth) / 2f
            Offset((if (controller.isLtr) center else size.width - center) + controller.leanOffset(maxLean), size.height / 2f)
        })
    }

    Box(
        modifier = modifier.layout { measurable, constraints ->
            val height = barHeight(minimizeProgress()).roundToPx()
            val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
            // Plain, not observed: drags and the glow read it, and they are re-run anyway
            controller.contentWidth = (placeable.width - tabInset.roundToPx() * 2).toFloat()
            layout(placeable.width, height) { placeable.place(0, 0) }
        },
        contentAlignment = Alignment.CenterStart
    ) {
        layers.Bar(tabsBackdrop, drawBarSurface, glow, baseContent, accent)
        if (style.accentLayer) layers.AccentCopy(tabsBackdrop, drawBarSurface, glow, accent)
        layers.Pill(tabsBackdrop, glow, isLight)
        layers.Badges()
    }
}

/** The per-bar sizes every tab cell shares. */
@Immutable
private data class GlassTabCellStyle(val iconSize: Dp, val iconTextStyle: TextStyle)

/** The bar's four layers, sharing one controller and appearance. Each reads the controller only while drawing. */
private class GlassTabBarLayers(
    private val controller: LiquidGlassTabBarController,
    private val tabs: List<GlassTab>,
    private val cellStyle: GlassTabCellStyle,
    private val backdrop: Backdrop,
    private val style: LiquidGlassStyle,
    private val rimHighlight: (Float) -> Highlight,
    private val maxLean: Float
) {
    private val pill get() = controller.pill

    /** Layer 1: the glass bar and its tabs, which take the taps. */
    @Composable
    fun Bar(tabsBackdrop: LayerBackdrop, drawSurface: DrawScope.() -> Unit, glow: InteractiveHighlight, contentColor: Color, accent: Color) {
        LiquidGlassTabRow(
            controller,
            modifier = Modifier
                .then(if (style.accentLayer) Modifier else Modifier.layerBackdrop(tabsBackdrop))
                .graphicsLayer { translationX = controller.leanOffset(maxLean) }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsuleShape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        if (style.lens) lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = { rimHighlight(1f) },
                    layerBlock = {
                        // Grows up to 16dp wider while the pill is pressed
                        val scale = 1f + 16.dp.toPx() / size.width * pill.pressProgress
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = drawSurface
                )
                .then(glow.modifier)
                .fillMaxSize()
                .padding(horizontal = tabInset, vertical = 5.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                tabs.forEachIndexed { index, tab ->
                    val appearance = if (style.accentLayer) {
                        // The icon under the pill gives way to the accent copy the pill reveals
                        Modifier.graphicsLayer { alpha = controller.collapseAlpha(index) * (1f - controller.proximity(index)) }
                    } else {
                        Modifier.accentTint(accent, amount = { controller.proximity(index) }, alpha = { controller.collapseAlpha(index) })
                    }
                    val tap = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, enabled = tab.isEnabled) {
                        controller.tap(index)
                    }
                    GlassTabCell(tab, GlassTabIconPart.ICON, cellStyle, controller.minimizeProgress, appearance.then(tap))
                }
            }
        }
    }

    /** Layer 2: the hidden accent copy of the tabs, recorded for the pill to reveal; hidden from accessibility. */
    @Composable
    fun AccentCopy(tabsBackdrop: LayerBackdrop, drawSurface: DrawScope.() -> Unit, glow: InteractiveHighlight, accent: Color) {
        LiquidGlassTabRow(
            controller,
            modifier = Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = controller.leanOffset(maxLean) }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { capsuleShape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        val press = pill.pressProgress
                        if (press > 0f) lens(24.dp.toPx() * press, 24.dp.toPx() * press)
                    },
                    highlight = { pill.pressProgress.takeIf { it > 0f }?.let(rimHighlight) },
                    onDrawSurface = drawSurface
                )
                .then(glow.modifier)
                .fillMaxWidth()
                .animatedHeight { barHeight(controller.minimizeProgress()) - pillInset }
                .padding(horizontal = tabInset)
        ) {
            CompositionLocalProvider(LocalContentColor provides accent) {
                tabs.forEachIndexed { index, tab ->
                    val appearance = if (tab.isEnabled) Modifier else Modifier.alpha(LiquidGlassTabBarController.disabledAlpha)
                    GlassTabCell(tab, GlassTabIconPart.ICON, cellStyle, controller.minimizeProgress, appearance)
                }
            }
        }
    }

    /**
     * Layer 3: the pill, which takes the drags. It covers the tab it is passing and, minimizing, grows into the minimized
     * bar, so the selected tab never loses its pill.
     */
    @Composable
    fun Pill(tabsBackdrop: LayerBackdrop, glow: InteractiveHighlight, isLight: Boolean) {
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val progress = controller.minimizeProgress()
                    val width = controller.pillWidth((constraints.maxWidth - tabInset.roundToPx() * 2).toFloat()).roundToInt().coerceAtLeast(0)
                    val height = (barHeight(progress) - pillInset).roundToPx().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    // Positioned while drawing, so a slide neither recomposes nor relayouts
                    val inset = tabInset.toPx()
                    val start = inset + controller.pillStart(controller.contentWidth)
                    val x = if (controller.isLtr) start else controller.contentWidth + inset * 2f - start - size.width
                    translationX = x + controller.leanOffset(maxLean)
                }
                .then(glow.gestureModifier)
                .then(pill.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { capsuleShape },
                    // Lens, rim and shadows only while pressed; at rest none is drawn at all
                    effects = {
                        val press = pill.pressProgress
                        if (style.lens && press > 0f) lens(10.dp.toPx() * press, 14.dp.toPx() * press, chromaticAberration = style.chromaticAberration)
                    },
                    highlight = { pill.pressProgress.takeIf { it > 0f }?.let(rimHighlight) },
                    shadow = { pill.pressProgress.takeIf { it > 0f }?.let { Shadow(alpha = it) } },
                    innerShadow = { pill.pressProgress.takeIf { it > 0f }?.let { InnerShadow(radius = 8.dp * it, alpha = it) } },
                    layerBlock = {
                        // Stretches along its travel and thins, up to 20%; not once it is the minimized bar
                        val expanded = 1f - controller.minimizeProgress()
                        val velocity = pill.velocity / 10f
                        val stretchX = pill.scaleX / (1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f))
                        val stretchY = pill.scaleY * (1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f))
                        scaleX = 1f + (stretchX - 1f) * expanded
                        scaleY = 1f + (stretchY - 1f) * expanded
                    },
                    onDrawSurface = {
                        // Dims the selected tab at rest, as iOS does; clears when pressed and as the bar minimizes
                        val dim = if (isLight) Color.Black.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.1f)
                        drawRect(dim, alpha = (1f - pill.pressProgress) * (1f - controller.minimizeProgress()))
                    }
                )
        )
    }

    /** Layer 4: badges over the pill, each laid out on an invisible copy of its tab. Takes no touches. */
    @Composable
    fun Badges() {
        LiquidGlassTabRow(
            controller,
            modifier = Modifier
                .clearAndSetSemantics {}
                .graphicsLayer { translationX = controller.leanOffset(maxLean) }
                .fillMaxSize()
                .padding(horizontal = tabInset, vertical = 5.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                val appearance = Modifier.graphicsLayer { alpha = controller.collapseAlpha(index) }
                GlassTabCell(tab, GlassTabIconPart.BADGE, cellStyle, controller.minimizeProgress, appearance, showsLabel = false)
            }
        }
    }
}

/** One tab: its icon over its label, which collapses as the bar minimizes. */
@Composable
private fun GlassTabCell(
    tab: GlassTab,
    iconPart: GlassTabIconPart,
    style: GlassTabCellStyle,
    minimizeProgress: () -> Float,
    modifier: Modifier = Modifier,
    showsLabel: Boolean = true
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Box(modifier = Modifier.size(style.iconSize), contentAlignment = Alignment.Center) {
                ProvideTextStyle(style.iconTextStyle) { WithGlassTabIconPart(iconPart) { tab.icon() } }
            }
            ProvideTextStyle(labelStyle) {
                Box(modifier = Modifier.wrapContentHeight().collapsingLabel(minimizeProgress).alpha(if (showsLabel) 1f else 0f)) {
                    tab.title()
                }
            }
        }
    }
}

/** Fades a label out and gives up its height as the bar minimizes, so the icon settles into the middle. */
private fun Modifier.collapsingLabel(progress: () -> Float): Modifier = this
    .graphicsLayer { alpha = 1f - progress() }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height * (1f - progress())).roundToInt()) { placeable.place(0, 0) }
    }

/** Tints content toward [color] by [amount] and fades it to [alpha], both read while drawing. */
private fun Modifier.accentTint(color: Color, amount: () -> Float, alpha: () -> Float): Modifier = this
    .graphicsLayer {
        this.alpha = alpha()
        // Its own layer, so the tint covers only the tab's pixels
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        val tint = amount()
        if (tint > 0f) drawRect(color, alpha = tint, blendMode = BlendMode.SrcAtop)
    }

/** A fixed height read while laying out, so an animated height relayouts without recomposing. */
private fun Modifier.animatedHeight(height: () -> Dp): Modifier = layout { measurable, constraints ->
    val heightPx = height().roundToPx().coerceAtLeast(0)
    val placeable = measurable.measure(constraints.copy(minHeight = heightPx, maxHeight = heightPx))
    layout(placeable.width, heightPx) { placeable.place(0, 0) }
}

/** The [Dp] [fraction] of the way from [start] to [end]. */
private fun lerp(start: Dp, end: Dp, fraction: Float): Dp = start + (end - start) * fraction
