// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: glass in a `ZStack` refracts the children behind it.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop

import skip.ui.ComposeContext
import skip.ui.Renderable
import skip.ui.ZIndexModifier

/**
 * Renders one `ZStack`'s children, back to front, so glass in each refracts the children behind it.
 *
 * Each child but the frontmost gets a [LiquidGlassLayer], recorded once glass in front asks for it, and never sampled
 * by its own child. Its `zIndex` moves to the recording wrapper so it still orders among siblings. Nothing changes on
 * the `NATIVE` tier.
 *
 * @param count The number of children.
 */
internal class LiquidGlassZStackLayers(private val count: Int) {
    private val layers = arrayOfNulls<LiquidGlassLayer>(count)

    /** Renders the child at [index], refracting the children before it and recorded for those after. */
    @Composable
    fun RenderChild(renderable: Renderable, index: Int, context: ComposeContext) {
        val layer = rememberLiquidGlassLayer()?.takeIf { index < count - 1 }
        val behind = layers.take(index).filterNotNull()
        layers[index] = layer
        val outsideRequest = LocalGlassBackdropRequest.current
        val backdrop = rememberBackdrop(listOfNotNull(LocalGlassBackdrop.current) + behind)
        val request = remember(behind, outsideRequest) {
            {
                behind.forEach { it.isRequested = true }
                outsideRequest?.invoke()
                Unit
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalGlassBackdropRequest provides (request.takeIf { behind.isNotEmpty() } ?: outsideRequest)) {
            val modifier = if (layer != null) ZIndexModifier.consume(renderable, Modifier) else Modifier
            LiquidGlassBehind(layer, modifier = modifier, propagateMinConstraints = false) {
                renderable.Render(context = context)
            }
        }
    }

    @Composable
    private fun rememberBackdrop(backdrops: List<Backdrop>): Backdrop? = when (backdrops.size) {
        0 -> null
        1 -> backdrops[0]
        else -> rememberCombinedBackdrop(*backdrops.toTypedArray())
    }
}
