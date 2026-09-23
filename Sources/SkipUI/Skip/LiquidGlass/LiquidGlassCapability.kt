// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: device-adaptive rendering tier, resolved once per process with Blinkit's Droid Dex.
// https://github.com/grofers/droid-dex
package skip.ui.liquidglass

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import skip.ui.EnvironmentValues

import com.blinkit.droiddex.DroidDex
import com.blinkit.droiddex.constants.PerformanceClass
import com.blinkit.droiddex.constants.PerformanceLevel

/**
 * How Liquid Glass is rendered on this device.
 */
internal enum class LiquidGlassTier {
    /** The complete glass effect: blur, lens refraction, chromatic aberration, highlights, and animations. */
    FULL,

    /** Glass with blur, frost, highlights, and animations, but without lens refraction and chromatic aberration. */
    REDUCED,

    /** No glass: the upstream SkipUI Material rendering. */
    NATIVE;

    /** The glass effect settings for this tier, or `null` for [NATIVE], which renders no glass. */
    val style: LiquidGlassStyle?
        get() = when (this) {
            FULL -> LiquidGlassStyle.Full
            REDUCED -> LiquidGlassStyle.Reduced
            NATIVE -> null
        }

    companion object {
        /**
         * Maps a Droid Dex performance level to a tier.
         *
         * - `EXCELLENT`, `HIGH` → [FULL]
         * - `AVERAGE` → [REDUCED]
         * - `LOW`, `UNKNOWN` → [NATIVE]
         */
        internal fun from(level: PerformanceLevel): LiquidGlassTier = when (level) {
            PerformanceLevel.EXCELLENT, PerformanceLevel.HIGH -> FULL
            PerformanceLevel.AVERAGE -> REDUCED
            PerformanceLevel.LOW, PerformanceLevel.UNKNOWN -> NATIVE
        }
        /**
		 * The tier when glass is forced but kept affordable: the top two device classes get [FULL], every other class —
		 * including an unmeasured one — gets [REDUCED] rather than dropping to Material.
		 */
		internal fun optimized(level: PerformanceLevel): LiquidGlassTier = when (level) {
			PerformanceLevel.EXCELLENT, PerformanceLevel.HIGH -> FULL
			else -> REDUCED
		}
    }
}
/**
 * The glass effect settings that differ between tiers. Glass components read these instead of fixed values.
 *
 * @property lens Whether glass refracts its backdrop with a lens effect.
 * @property chromaticAberration Whether the lens splits colors at the glass edge. Ignored without [lens].
 * @property accentLayer Whether the tab bar draws the hidden accent-tinted layer that the selection pill magnifies.
 */
internal data class LiquidGlassStyle(
    val lens: Boolean,
    val chromaticAberration: Boolean,
    val accentLayer: Boolean
) {
    companion object {
        /** Settings for [LiquidGlassTier.FULL]: the current Liquid Glass look. */
        val Full = LiquidGlassStyle(lens = true, chromaticAberration = true, accentLayer = true)

        /** Settings for [LiquidGlassTier.REDUCED]: drops the GPU-heavy lens shader work. */
        val Reduced = LiquidGlassStyle(lens = false, chromaticAberration = false, accentLayer = false)

        /**
         * The settings for the resolved tier. [Full] while the tier is [LiquidGlassTier.NATIVE], where glass components
         * are not rendered, so a component rendered directly keeps the complete look.
         *
         * Reads Compose state, so a component reading it during composition recomposes when the tier resolves.
         */
		val current: LiquidGlassStyle
		@Composable get() = EnvironmentValues.shared.liquidGlassTier().style ?: Full
    }
}

/**
 * Resolves the device class once per process from Droid Dex CPU and memory levels, which [tier] and
 * [optimizedTier] map to a rendering tier.
 *
 * [performanceLevel] starts `UNKNOWN`. Droid Dex measures on a background thread, and once both CPU and memory have a
 * level their average is stored and observation stops, so it changes at most once per session. A device that cannot
 * measure one of them stays `UNKNOWN`. Nothing is saved; the level is kept in memory only.
 *
 * [performanceLevel] is Compose state, so composables reading a tier derived from it recompose when it lands.
 */
internal object LiquidGlassCapability {
    /** The measured device class. `UNKNOWN` until Droid Dex reports. */
    var performanceLevel: PerformanceLevel by mutableStateOf(PerformanceLevel.UNKNOWN)
        private set

    /** The tier for `LiquidGlass.adaptive`: glass only where the device can afford it. */
    val tier: LiquidGlassTier
        get() = LiquidGlassTier.from(performanceLevel)

    /** The tier for `LiquidGlass.forcedOptimized`: glass everywhere, full on the top two device classes. */
    val optimizedTier: LiquidGlassTier
        get() = LiquidGlassTier.optimized(performanceLevel)

    /** The Droid Dex level name, for diagnostics. `null` until resolved. */
    val performanceLevelName: String?
        get() = if (performanceLevel == PerformanceLevel.UNKNOWN) null else performanceLevel.name

    /** Milliseconds from [resolve] to the resolved [tier], for diagnostics. `null` until resolved. */
    var resolveDurationMs: Long? by mutableStateOf(null)
        private set

    private val scope = MainScope()
    private var hasStarted = false

    /**
     * Starts resolving the tier. Only the first call has an effect; later calls return immediately.
     *
     * Must be called on the main thread, such as during composition.
     *
     * @param context Any context; its application context initializes Droid Dex.
     */
    fun resolve(context: Context) {
        if (hasStarted) return
        hasStarted = true

        val startTime = SystemClock.elapsedRealtime()
        DroidDex.init(context.applicationContext)

        scope.launch {
            // Each level starts as UNKNOWN; suspend until both have been measured, then stop observing
            combine(
                DroidDex.getPerformanceLevelLd(PerformanceClass.CPU).asFlow(),
                DroidDex.getPerformanceLevelLd(PerformanceClass.MEMORY).asFlow()
            ) { cpu, memory -> cpu != PerformanceLevel.UNKNOWN && memory != PerformanceLevel.UNKNOWN }
                .first { isMeasured -> isMeasured }

            resolveDurationMs = SystemClock.elapsedRealtime() - startTime
            performanceLevel = DroidDex.getPerformanceLevel(PerformanceClass.CPU, PerformanceClass.MEMORY)
        }
    }

    /** Emits the LiveData's values while collected, observing on the main thread. */
    private fun <T> LiveData<T>.asFlow(): Flow<T> = callbackFlow {
        val observer = Observer<T> { value -> trySend(value) }
        observeForever(observer)
        awaitClose { removeObserver(observer) }
    }
}
