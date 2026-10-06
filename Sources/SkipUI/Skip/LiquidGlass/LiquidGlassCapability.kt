// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: the rendering tier for this device, from Blinkit's Droid Dex.
// https://github.com/grofers/droid-dex
package skip.ui.liquidglass

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
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

/** How Liquid Glass renders on this device. */
internal enum class LiquidGlassTier {
    /** Blur, lens refraction with chromatic aberration, highlights and animations. */
    FULL,

    /** Blur, rim highlight and animations, without refraction or the tab bar's accent layer. */
    REDUCED,

    /** No glass: upstream SkipUI's Material rendering. */
    NATIVE;

    /** The tier's glass settings, or `null` for [NATIVE]. */
    val style: LiquidGlassStyle?
        get() = when (this) {
            FULL -> LiquidGlassStyle.Full
            REDUCED -> LiquidGlassStyle.Reduced
            NATIVE -> null
        }

    companion object {
        /** `EXCELLENT`/`HIGH` → [FULL], `AVERAGE` → [REDUCED], `LOW`/`UNKNOWN` → [NATIVE]. */
        fun from(level: PerformanceLevel): LiquidGlassTier = when (level) {
            PerformanceLevel.EXCELLENT, PerformanceLevel.HIGH -> FULL
            PerformanceLevel.AVERAGE -> REDUCED
            PerformanceLevel.LOW, PerformanceLevel.UNKNOWN -> NATIVE
        }

        /** As [from], but never [NATIVE]: for `LiquidGlass.forcedOptimized`. */
        fun optimized(level: PerformanceLevel): LiquidGlassTier =
            if (level == PerformanceLevel.EXCELLENT || level == PerformanceLevel.HIGH) FULL else REDUCED
    }
}

/**
 * The glass effects that differ between tiers.
 *
 * @property lens Whether glass refracts its backdrop.
 * @property chromaticAberration Whether the lens splits colors at the edge. Ignored without [lens].
 * @property accentLayer Whether the tab bar records an accent-tinted copy of its icons for the selection pill.
 */
internal data class LiquidGlassStyle(val lens: Boolean, val chromaticAberration: Boolean, val accentLayer: Boolean) {
    companion object {
        val Full = LiquidGlassStyle(lens = true, chromaticAberration = true, accentLayer = true)
        // Blur and chrome only: refraction is reserved for `HIGH` and above
        val Reduced = LiquidGlassStyle(lens = false, chromaticAberration = false, accentLayer = false)

        /** The resolved tier's settings; [Full] on `NATIVE`, for a component rendered directly. */
        val current: LiquidGlassStyle
            @Composable get() = EnvironmentValues.shared.liquidGlassTier().style ?: Full
    }
}

/**
 * The device class, fixed for the session at the first [resolve] so glass never switches while the app is open.
 *
 * The class comes from the level Droid Dex measured on an earlier launch, stored in the `skip.ui.liquidglass`
 * preferences; on the very first launch, from an estimate made from memory and cores. Either is available
 * synchronously, so the first frame already renders the right tier. Droid Dex still measures in the background each
 * launch, and its result is stored for the next one.
 */
internal object LiquidGlassCapability {
    private const val preferencesName = "skip.ui.liquidglass"
    private const val levelKey = "performanceLevel"

    /** The device class for this session. `UNKNOWN` until [resolve]. */
    var performanceLevel = PerformanceLevel.UNKNOWN
        private set

    /** The tier for `LiquidGlass.adaptive`. */
    val tier: LiquidGlassTier get() = LiquidGlassTier.from(performanceLevel)

    /** The tier for `LiquidGlass.forcedOptimized`. */
    val optimizedTier: LiquidGlassTier get() = LiquidGlassTier.optimized(performanceLevel)

    private var isResolved = false

    /** Fixes the session's device class and starts measuring for the next launch. Only the first call has an effect. */
    fun resolve(context: Context) {
        if (isResolved) return
        isResolved = true
        val appContext = context.applicationContext
        val preferences = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        performanceLevel = preferences.storedLevel() ?: estimatedLevel(appContext)
        measure(appContext, preferences)
    }

    private fun SharedPreferences.storedLevel(): PerformanceLevel? =
        getString(levelKey, null)?.let { name -> PerformanceLevel.values().firstOrNull { it.name == name } }
            ?.takeIf { it != PerformanceLevel.UNKNOWN }

    /** A quick synchronous guess from total memory and cores, used until Droid Dex has measured once. */
    private fun estimatedLevel(context: Context): PerformanceLevel {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return PerformanceLevel.AVERAGE
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val gigabytes = memory.totalMem / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            activityManager.isLowRamDevice || gigabytes < 3 -> PerformanceLevel.LOW
            gigabytes < 6 || cores < 6 -> PerformanceLevel.AVERAGE
            else -> PerformanceLevel.HIGH
        }
    }

    /** Measures CPU and memory with Droid Dex and stores their average for the next launch. */
    private fun measure(context: Context, preferences: SharedPreferences) {
        DroidDex.init(context)
        MainScope().launch {
            combine(
                DroidDex.getPerformanceLevelLd(PerformanceClass.CPU).asFlow(),
                DroidDex.getPerformanceLevelLd(PerformanceClass.MEMORY).asFlow()
            ) { cpu, memory -> cpu != PerformanceLevel.UNKNOWN && memory != PerformanceLevel.UNKNOWN }
                .first { it }
            val level = DroidDex.getPerformanceLevel(PerformanceClass.CPU, PerformanceClass.MEMORY)
            preferences.edit().putString(levelKey, level.name).apply()
        }
    }

    private fun <T> LiveData<T>.asFlow(): Flow<T> = callbackFlow {
        val observer = Observer<T> { trySend(it) }
        observeForever(observer)
        awaitClose { removeObserver(observer) }
    }
}
