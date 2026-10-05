// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: the glass tab bar's moving parts, kept out of composition so its animations never recompose a tab.
package skip.ui.liquidglass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The state of one glass tab bar: the selection, the pill sliding to it, the bar leaning into a drag, and how far the
 * bar is minimized.
 *
 * Everything that changes frame to frame is read only while laying out or drawing, so a pill slide or a minimize
 * animation relayouts and redraws the bar without recomposing its tabs.
 *
 * A picked tab is reported once the pill has landed on it: switching tabs composes the new tab's content, which can take
 * longer than a frame, and doing it while the pill moves would stall the slide. The pill starts on the tap's frame.
 *
 * @property minimizeProgress 0 the full bar, 1 minimized to the selected tab.
 */
@Stable
internal class LiquidGlassTabBarController(
    private val scope: CoroutineScope,
    tabCount: Int,
    initialIndex: Int,
    val minimizeProgress: () -> Float
) {
    /** The bar's tabs; updated on every composition. */
    var tabs: List<GlassTab> = emptyList()

    /** Called with a tab picked by tap or drag, including a re-tap of the selected one. */
    var onTabSelected: (Int) -> Unit = {}

    /** Called on a tap of the minimized bar. */
    var onExpand: (() -> Unit)? = null

    /** Whether tabs run left to right. */
    var isLtr = true

    /** The selected tab: taps, drag releases and the `TabView`'s own selection all set it. */
    var selectedIndex by mutableIntStateOf(initialIndex)
        private set

    /** The width the tabs share, in pixels, set by the bar's layout. */
    var contentWidth by mutableFloatStateOf(0f)

    /** The pill's position as a fractional tab index, with its press scale and velocity. */
    val pill = DampedDragAnimation(
        animationScope = scope,
        initialValue = initialIndex.toFloat(),
        valueRange = 0f..(tabCount - 1).coerceAtLeast(0).toFloat(),
        visibilityThreshold = 0.001f,
        initialScale = 1f,
        pressedScale = 1.4f,
        onDragStarted = { },
        onDragStopped = { endDrag() },
        onDrag = { _, dragAmount -> drag(dragAmount.x) }
    )

    /** Whether the bar is closer to minimized than full size. */
    val isMinimized by derivedStateOf { minimizeProgress() > 0.5f }

    private val lean = Animatable(0f)

    /** Reports a picked tab once the pill lands on it; see the class description. */
    private var pendingSelection: Job? = null

    // MARK: - Selection

    /** Selects the tab at [index] on a tap, or expands the bar when minimized. */
    fun tap(index: Int) {
        if (isMinimized) {
            onExpand?.invoke()
            return
        }
        pick(index)
    }

    /** Follows a selection the `TabView` made itself; ignored while a pick of this bar's is on its way. */
    fun follow(index: Int) {
        if (pendingSelection?.isActive == true) return
        if (index != selectedIndex) moveTo(index)
    }

    /** Moves the pill to [index] and reports it on landing; a re-tap of the selected tab is reported at once. */
    private fun pick(index: Int) {
        val isReselection = index == selectedIndex && pendingSelection?.isActive != true
        // Always settles the pill, which a drag may have left between tabs and stretched by its speed
        moveTo(index)
        if (isReselection) {
            onTabSelected(index)
            return
        }
        pendingSelection?.cancel()
        pendingSelection = scope.launch {
            withTimeoutOrNull(landingTimeoutMillis) {
                snapshotFlow { abs(pill.value - index) < landingDistance }.first { it }
            }
            onTabSelected(index)
        }
    }

    private fun moveTo(index: Int) {
        selectedIndex = index
        pill.animateToValue(index.toFloat())
    }

    private fun drag(deltaX: Float) {
        if (isMinimized) return
        val tabWidth = contentWidth / tabs.size.coerceAtLeast(1)
        if (tabWidth <= 0f) return
        val direction = if (isLtr) 1f else -1f
        pill.updateValue((pill.targetValue + deltaX / tabWidth * direction).coerceIn(0f, lastIndex.toFloat()))
        scope.launch { lean.snapTo(lean.value + deltaX) }
    }

    private fun endDrag() {
        if (isMinimized) {
            onExpand?.invoke()
            return
        }
        // The pill covers the selected tab, so a release in place is a re-tap
        pick(nearestEnabledIndex(pill.targetValue.roundToInt().coerceIn(0, lastIndex)))
        scope.launch { lean.animateTo(0f, spring(1f, 300f, 0.5f)) }
    }

    /** The nearest enabled tab to [index], where a drag released over a disabled one lands. */
    private fun nearestEnabledIndex(index: Int): Int {
        if (tabs.getOrNull(index)?.isEnabled != false) return index
        for (distance in 1 until tabs.size) {
            if (tabs.getOrNull(index - distance)?.isEnabled == true) return index - distance
            if (tabs.getOrNull(index + distance)?.isEnabled == true) return index + distance
        }
        return index
    }

    private val lastIndex: Int get() = (tabs.size - 1).coerceAtLeast(0)

    // MARK: - Geometry, read while laying out and drawing

    /** How far the bar leans toward a drag, in pixels: up to [maxLean], easing out. */
    fun leanOffset(maxLean: Float): Float {
        val fraction = if (contentWidth > 0f) (lean.value / contentWidth).coerceIn(-1f, 1f) else 0f
        return maxLean * (if (fraction >= 0f) 1f else -1f) * EaseOut.transform(abs(fraction))
    }

    /** The share of the width the tab at [index] takes: the selected one keeps its share as the others collapse. */
    fun weight(index: Int): Float = if (index == selectedIndex) 1f else (1f - minimizeProgress()).coerceAtLeast(0.0001f)

    /** The leading edge of the tab at [index] within [width], in pixels from the leading side. */
    fun tabStart(index: Int, width: Float): Float {
        var start = 0f
        for (i in 0 until index) start += weight(i)
        return start * width / totalWeight()
    }

    /** The width of the tab at [index] within [width]. */
    fun tabWidth(index: Int, width: Float): Float = weight(index) * width / totalWeight()

    /** The pill's leading edge within [width], between the two tabs it is passing. */
    fun pillStart(width: Float): Float = interpolate(width) { tabStart(it, width) }

    /** The pill's width within [width], between the two tabs it is passing. At full size every tab is as wide, so the
     *  slide isn't read and doesn't relayout the pill. */
    fun pillWidth(width: Float): Float =
        if (minimizeProgress() == 0f) width / tabs.size.coerceAtLeast(1) else interpolate(width) { tabWidth(it, width) }

    /** 1 for the tab under the pill, falling to 0 a tab away. */
    fun proximity(index: Int): Float = (1f - abs(pill.value - index)).coerceIn(0f, 1f)

    /** How visible the tab at [index] is: disabled tabs dim, and all but the selected fade as the bar minimizes. */
    fun collapseAlpha(index: Int): Float = when {
        tabs.getOrNull(index)?.isEnabled == false -> disabledAlpha
        index != selectedIndex -> 1f - minimizeProgress()
        else -> 1f
    }

    private fun totalWeight(): Float {
        var total = 0f
        for (i in tabs.indices) total += weight(i)
        return total.coerceAtLeast(0.0001f)
    }

    private inline fun interpolate(width: Float, measure: (Int) -> Float): Float {
        if (tabs.isEmpty() || width <= 0f) return 0f
        val position = pill.value.coerceIn(0f, lastIndex.toFloat())
        val lower = floor(position).toInt()
        val upper = (lower + 1).coerceAtMost(lastIndex)
        val fraction = position - lower
        return measure(lower) + (measure(upper) - measure(lower)) * fraction
    }

    companion object {
        /** A disabled tab's opacity. */
        const val disabledAlpha = 0.38f

        /** How close, in tabs, the pill counts as landed. */
        private const val landingDistance = 0.1f

        /** The longest a picked tab waits for the pill. */
        private const val landingTimeoutMillis = 250L
    }
}

/** A [LiquidGlassTabBarController] for a bar of [tabCount] tabs, starting on [selectedIndex]; made anew if the count changes. */
@Composable
internal fun rememberLiquidGlassTabBarController(tabCount: Int, selectedIndex: Int, minimizeProgress: () -> Float): LiquidGlassTabBarController {
    val scope = rememberCoroutineScope()
    return remember(scope, tabCount) { LiquidGlassTabBarController(scope, tabCount, selectedIndex, minimizeProgress) }
}

/**
 * Lays tabs side by side, each its [LiquidGlassTabBarController.tabWidth], mirrored right to left. Reads the
 * controller while measuring, so a minimize relayouts the tabs without recomposing them.
 */
@Composable
internal fun LiquidGlassTabRow(controller: LiquidGlassTabBarController, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints.fixed(controller.tabWidth(index, width).roundToInt(), height))
        }
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeRelative(controller.tabStart(index, width).roundToInt(), 0)
            }
        }
    }
}
