// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: adapted from the catalog components in Kyant's AndroidLiquidGlass project.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A draggable value that settles with spring physics, used to move the selection pill of [LiquidGlassTabBar].
 *
 * The value (for example a fractional tab index) follows drags through [updateValue] and settles on a
 * target through [animateToValue]. While pressed, the element grows to [pressedScale] and [pressProgress]
 * animates to 1, which glass effects use to fade in their lens, highlight, and shadow. [velocity] tracks
 * how fast the value is moving so the pill can stretch in the direction of travel.
 *
 * Attach [modifier] to the element that should receive drags.
 *
 * @param animationScope The scope that runs all animations, typically from `rememberCoroutineScope()`.
 * @param initialValue The starting value.
 * @param valueRange The range that the value is clamped to.
 * @param visibilityThreshold The distance at which the value animation is considered settled.
 * @param initialScale The scale when not pressed.
 * @param pressedScale The scale while pressed.
 * @param onDragStarted Called with the touch position when a drag begins.
 * @param onDragStopped Called when a drag ends or is cancelled; typically snaps to the nearest value.
 * @param onDrag Called with the element size and drag delta for each movement; typically calls [updateValue].
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {

    private val valueAnimationSpec =
        spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec =
        spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec =
        spring(1f, 1000f, 0.001f)
    // Slightly different damping on each axis gives the press a wobbly, liquid feel
    private val scaleXAnimationSpec =
        spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec =
        spring(0.7f, 250f, 0.001f)

    private val valueAnimation =
        Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation =
        Animatable(0f, 5f)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()

    private val velocityTracker = VelocityTracker()

    /** The current animated value. */
    val value: Float get() = valueAnimation.value
    /** The current value as a fraction of [valueRange], from 0 to 1. */
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    /** The value that the animation is settling toward. */
    val targetValue: Float get() = valueAnimation.targetValue
    /** How pressed the element is, from 0 (released) to 1 (fully pressed). */
    val pressProgress: Float get() = pressProgressAnimation.value
    /** The current horizontal scale. */
    val scaleX: Float get() = scaleXAnimation.value
    /** The current vertical scale. */
    val scaleY: Float get() = scaleYAnimation.value
    /** The current velocity, in [valueRange] units per second. */
    val velocity: Float get() = velocityAnimation.value

    /** Pointer input modifier that routes drags on the attached element to the drag callbacks. */
    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    /** Animates into the pressed state. */
    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    /** Animates out of the pressed state once the value is close to settling. */
    fun release() {
        animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                // Stay pressed until the value is within 2.5% of its target so the pill doesn't shrink mid-flight
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    /** Moves the value toward [value], clamped to [valueRange], while tracking velocity. Use during a drag. */
    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() } }
        }
    }

    /** Animates to [value], clamped to [valueRange], with a press and release. Use for taps and programmatic selection. */
    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            System.currentTimeMillis(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }

    private suspend fun awaitFrame() {
        withFrameNanos { }
    }
}

private fun Float.coerceIn(range: ClosedRange<Float>): Float =
    coerceIn(range.start, range.endInclusive)
