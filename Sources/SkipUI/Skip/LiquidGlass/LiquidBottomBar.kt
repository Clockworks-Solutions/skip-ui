// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: floating glass tab bar built on Kyant's backdrop library.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * A single tab in a [LiquidGlassTabBar].
 *
 * @property icon The tab icon, drawn in a fixed-size box.
 * @property title The tab label, drawn below the icon.
 * @property content The body shown when the tab is selected. Not used by [LiquidGlassTabView], where `TabView` renders the content.
 */
internal class GlassTab(
    val icon: @Composable () -> Unit,
    val title: @Composable () -> Unit,
    val content: @Composable () -> Unit
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
 * @param backdrop The recorded tab content that the bar refracts.
 * @param tabIndices The `TabView` indices of the visible tabs, in display order. Hidden and conditional tabs are omitted.
 * @param selectedTabIndex The `TabView` index of the selected tab.
 * @param icon Renders the icon for a `TabView` index.
 * @param label Renders the title for a `TabView` index, if tabs have titles.
 * @param onTabSelected Called with the `TabView` index of the tab the user selects.
 */
@Composable
internal fun LiquidGlassTabView(
    backdrop: Backdrop,
    tabIndices: kotlin.collections.List<Int>,
    selectedTabIndex: Int,
    icon: @Composable (Int) -> Unit,
    label: (@Composable (Int) -> Unit)?,
    onTabSelected: (Int) -> Unit
) {
    if (tabIndices.isEmpty()) return

    // Map TabView indices to glass tabs; LiquidGlassTabBar works in display positions
    val tabs = tabIndices.map { tabIndex ->
        GlassTab(icon = { icon(tabIndex) }, title = { label?.invoke(tabIndex) }, content = {})
    }
    val selectedPosition = tabIndices.indexOf(selectedTabIndex).coerceAtLeast(0)

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
            val barWidth = with(density) { idealPx.coerceAtMost(maxPx).toDp() }
            LiquidGlassTabBar(
                backdrop = backdrop,
                tabs = tabs,
                selectedIndex = selectedPosition,
                onTabSelected = { position -> onTabSelected(tabIndices[position]) },
                modifier = Modifier.width(barWidth)
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
 * @param tabs The tabs to show. Only [GlassTab.icon] and [GlassTab.title] are used.
 * @param selectedIndex The selected tab. Changes to this value move the pill.
 * @param onTabSelected Called with the new index when the selection changes by tap or drag.
 * @param modifier The modifier to apply to the bar, usually a width.
 */
@Composable
internal fun LiquidGlassTabBar(
    backdrop: Backdrop,
    tabs: kotlin.collections.List<GlassTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val isLightTheme = !isSystemInDarkTheme()

    // TODO: Take accentColor and containerColor from the environment tint and theme once finalized
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = 0.4f)
        else Color(0xFF121212).copy(alpha = 0.4f)

    val baseContentColor = if (isLightTheme) Color.Black else Color.White
    val isCompactLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val iconSize = if (isCompactLandscape) 20.dp else 28.dp
    val labelTextStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
    val labelTextStyleBold = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val minimumTouchTarget = 44.dp

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
                    val targetIndex = targetValue.roundToInt().coerceIn(0, tabs.size - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
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
        // Any currentIndex change (tap or drag-stop) animates the pill and notifies the caller
        LaunchedEffect(anim) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    anim.animateToValue(index.toFloat())
                    onTabSelected(index)
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
        Row(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(percent = 50) },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        // Grow the bar by up to 16dp in width while the pill is pressed
                        val progress = anim.pressProgress
                        val scale = 1f + (16f.dp.toPx() / size.width) * progress
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .fillMaxWidth()
                .height(65.dp)
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

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            currentIndex = index
                        },
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(
                        LocalContentColor provides baseContentColor.copy(alpha = iconAlpha)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(modifier = Modifier.size(iconSize)) { tab.icon() }
                            ProvideTextStyle(labelTextStyle) {
                                Box(
                                    modifier = Modifier
                                        .wrapContentHeight() // Hug the text height
                                        .padding(vertical = 2.dp) // Small breathing room instead of a fixed height
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
        // Hidden from accessibility so tabs aren't announced twice.
        Row(
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
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Box(modifier = Modifier.size(iconSize)) { tab.icon() }
                        ProvideTextStyle(labelTextStyleBold) {
                            Box(
                                modifier = Modifier
                                    .wrapContentHeight() // Hug the text height
                                    .padding(vertical = 2.dp) // Small breathing room instead of a fixed height
                            ) {
                                tab.title()
                            }
                        }
                    }
                }
            }
        }

        // ── Layer 3: sliding liquid pill + drag surface ─────────────────────
        // `anim.modifier` is what actually receives touch drags; without it
        // attached here, DampedDragAnimation never sees pointer input at all.
        Box(
            modifier = Modifier
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
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = anim.pressProgress)
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
                        // Resting: subtle fill so the pill is visible. Pressed: nearly clear so the lens shows through
                        val progress = anim.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(alpha = 0.1f)
                            else Color.White.copy(alpha = 0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabs.size)
        )
    }
}
