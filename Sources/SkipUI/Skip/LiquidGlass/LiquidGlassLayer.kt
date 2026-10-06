// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: records the view layered behind glass, for the glass to refract.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui.liquidglass

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

import skip.ui.EnvironmentValues

/**
 * A recording of the view behind another, for the glass in front to refract.
 *
 * Glass can only sample content beside it, never content containing it, so `overlay`, `background` and
 * `safeAreaInset` record the view behind, as `ZStack` does. Nothing is recorded or allocated until glass asks through
 * [LocalGlassBackdropRequest]; until then this backdrop draws nothing.
 */
internal class LiquidGlassLayer : Backdrop {
    /** Whether glass samples this layer, and so whether it records. */
    var isRequested by mutableStateOf(false)

    /** The recording, created by [LiquidGlassBehind] once requested. */
    var recording: LayerBackdrop? by mutableStateOf(null)

    override val isCoordinatesDependent = true

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        recording?.run { drawBackdrop(density, coordinates, layerBlock) }
    }
}

/** Asks every lazily recorded layer behind this point to record. `null` where there is none. */
internal val LocalGlassBackdropRequest = compositionLocalOf<(() -> Unit)?> { null }

/** Requests recording of the layers behind a glass surface. */
@Composable
internal fun RequestGlassBackdrop() {
    val request = LocalGlassBackdropRequest.current ?: return
    SideEffect { request() }
}

/** A [LiquidGlassLayer] for one composition, or `null` on the `NATIVE` tier. */
@Composable
internal fun rememberLiquidGlassLayer(): LiquidGlassLayer? {
    val layer = remember { LiquidGlassLayer() }
    return layer.takeIf { EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE }
}

/**
 * Renders the view behind, recording it into [layer] once requested.
 *
 * Always wrapped in the same `Box`, so starting to record never resets the content's state.
 *
 * @param modifier Applied to the wrapping `Box`.
 * @param propagateMinConstraints Whether the `Box` passes minimum constraints through, so content lays out as unwrapped.
 */
@Composable
internal fun LiquidGlassBehind(
    layer: LiquidGlassLayer?,
    modifier: Modifier = Modifier,
    propagateMinConstraints: Boolean = true,
    content: @Composable () -> Unit
) {
    if (layer == null) return content()
    val recording = if (layer.isRequested) rememberLayerBackdrop() else null
    SideEffect { layer.recording = recording }
    Box(modifier = modifier.then(recording?.let { Modifier.layerBackdrop(it) } ?: Modifier), propagateMinConstraints = propagateMinConstraints) {
        content()
    }
}

/** Renders the view in front, giving its glass [layer] on top of any backdrop from further out. */
@Composable
internal fun LiquidGlassInFront(layer: LiquidGlassLayer?, content: @Composable () -> Unit) {
    if (layer == null) return content()
    val outsideBackdrop = LocalGlassBackdrop.current
    val outsideRequest = LocalGlassBackdropRequest.current
    val backdrop = outsideBackdrop?.let { rememberCombinedBackdrop(it, layer) } ?: layer
    val request = remember(layer, outsideRequest) {
        {
            layer.isRequested = true
            outsideRequest?.invoke()
            Unit
        }
    }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalGlassBackdropRequest provides request, content = content)
}

/**
 * Renders content over a solid [color] background, giving its glass that color to sample.
 *
 * `background(Color)` paints the view itself, so there is nothing to record; a backdrop painting the color is exact
 * and free. A translucent color is painted over any backdrop from further out.
 */
@Composable
internal fun LiquidGlassColorBehind(color: Color, content: @Composable () -> Unit) {
    val colorBackdrop = rememberCanvasBackdrop { drawRect(color) }
    val outsideBackdrop = LocalGlassBackdrop.current?.takeIf { color.alpha < 1f }
    val backdrop = outsideBackdrop?.let { rememberCombinedBackdrop(it, colorBackdrop) } ?: colorBackdrop
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = content)
}
