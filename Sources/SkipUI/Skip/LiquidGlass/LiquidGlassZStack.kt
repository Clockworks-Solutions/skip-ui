// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: lets glass in a ZStack refract the layers behind it, built on Kyant's backdrop library.
// https://github.com/Kyant0/AndroidLiquidGlass
package skip.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/**
 * Renders the children of one `ZStack` so glass in each child samples the children behind it.
 *
 * Every child except the frontmost is recorded into its own layer, and each child is given [LocalGlassBackdrop] combining
 * the layers of the children before it (plus any backdrop from outside the `ZStack`). A glass button floating over a list
 * then blurs and refracts the list, like the glass tab bar does. A child never samples its own layer, which would make the
 * layer draw itself and crash the renderer.
 *
 * A recorded child is wrapped in a `Box` that carries the recording, because SkipUI views can apply their context modifier
 * to more than one node, and recording the same layer twice crashes. The child's `zIndex` moves to the wrapper, so it
 * still orders the child among its siblings.
 *
 * Only records when the Liquid Glass tier is not `NATIVE`; otherwise children render exactly as in upstream SkipUI.
 * Create one per `ZStack` composition and render every child through [RenderChild], in back-to-front order.
 *
 * @param count The number of children in the `ZStack`.
 */
internal class LiquidGlassZStackLayers(private val count: Int) {
    private val layers = arrayOfNulls<LayerBackdrop>(count)

    /**
     * Renders the child at [index], recording it for the children in front and providing it the layers behind.
     */
    @Composable
    fun RenderChild(renderable: Renderable, index: Int, context: ComposeContext) {
        val layer = rememberLayerBackdrop()
        val outsideBackdrop = LocalGlassBackdrop.current
        val isGlass = EnvironmentValues.shared.liquidGlassTier() != LiquidGlassTier.NATIVE
        // Only the children behind this one; never this child's own layer
        val behind = listOfNotNull(outsideBackdrop) + layers.take(index).filterNotNull()
        val backdrop = if (isGlass) rememberBackdrop(behind) else outsideBackdrop
        // The frontmost child has nothing in front of it to sample its layer
        val recordsLayer = isGlass && index < count - 1
        layers[index] = if (recordsLayer) layer else null

        CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
            if (recordsLayer) {
                Box(modifier = ZIndexModifier.consume(renderable, Modifier.layerBackdrop(layer))) {
                    renderable.Render(context = context)
                }
            } else {
                renderable.Render(context = context)
            }
        }
    }

    @Composable
    private fun rememberBackdrop(backdrops: kotlin.collections.List<Backdrop>): Backdrop? = when (backdrops.size) {
        0 -> null
        1 -> backdrops[0]
        else -> rememberCombinedBackdrop(*backdrops.toTypedArray())
    }
}
