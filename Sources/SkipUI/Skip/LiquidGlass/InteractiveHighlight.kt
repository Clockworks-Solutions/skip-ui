// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: adapted from the catalog components in Kyant's AndroidLiquidGlass project.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A soft white glow that follows the touch on a glass element, like the press highlight of iOS Liquid Glass.
 *
 * Apply [modifier] to the element that draws the glow and [gestureModifier] to the element that receives
 * touches. They can be different elements: [LiquidGlassTabBar] draws the glow on the bar and tracks touches
 * on the pill.
 *
 * @param animationScope The scope that runs the press and position animations.
 * @param position Maps the element size and the tracked touch position to the glow center. Defaults to the
 *   touch position; override it to anchor the glow elsewhere, such as the center of a selection pill.
 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset }
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    /** How pressed the element is, from 0 (released) to 1 (fully pressed). */
    val pressProgress: Float get() = pressProgressAnimation.value
    /** The distance the touch has moved from where the press started. */
    val offset: Offset get() = positionAnimation.value - startPosition

    /** Draws the glow behind the element's content while pressed. */
    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                val center = position(size, positionAnimation.value)
                val radius = (size.minDimension * 1.5f).coerceAtLeast(1f)
                // Additive blending brightens the glass underneath instead of covering it
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f * progress),
                            Color.White.copy(alpha = 0f)
                        ),
                        center = center,
                        radius = radius
                    ),
                    blendMode = BlendMode.Plus
                )
            }

            drawContent()
        }

    /** Tracks touches on the attached element: fades the glow in on press, follows the drag, and springs back on release. */
    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                }
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}
