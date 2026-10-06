// SPDX-License-Identifier: MPL-2.0
//
// Liquid Glass: the light glass rims reflect, which follows the device's tilt.
package skip.ui.liquidglass

import android.animation.ValueAnimator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle

import kotlin.math.abs
import kotlin.math.atan2

/**
 * The angle light falls on glass rims: 45° (above left) at rest, turning as the device rolls, as on iOS 26.
 *
 * One smoothed gravity listener serves all glass, running only while some is on screen. The light holds still when
 * system animations are off.
 */
internal object LiquidGlassLight : SensorEventListener {
    private const val restingAngle = 45f
    private const val maxTurn = 60f
    private const val smoothing = 0.12f
    private const val step = 1f

    /** The light's angle in degrees. Read while drawing, so a change only redraws. */
    var angle by mutableFloatStateOf(restingAngle)
        private set

    private var users = 0
    private var sensorManager: SensorManager? = null
    private var roll = 0f

    /** Starts following the tilt for one more glass element; the sensor starts with the first. */
    fun acquire(context: Context) {
        if (users++ > 0 || !ValueAnimator.areAnimatorsEnabled()) return
        val manager = context.getSystemService(SensorManager::class.java) ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        sensorManager = manager
    }

    /** Stops following the tilt for one glass element; the sensor stops with the last. */
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) {
            sensorManager?.unregisterListener(this)
            sensorManager = null
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        // Gravity across (x) and up (y) the screen gives the roll; flat on a table there is none
        val (x, y) = event.values
        val target = if (abs(x) + abs(y) < 1f) 0f else Math.toDegrees(atan2(x, y).toDouble()).toFloat()
        roll += (target - roll) * smoothing
        val newAngle = restingAngle + roll.coerceIn(-maxTurn, maxTurn)
        if (abs(newAngle - angle) >= step) angle = newAngle
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/**
 * Follows the device's tilt while on screen and returns the rim highlight, lit from [LiquidGlassLight.angle], at a
 * given opacity. Call the result while drawing.
 */
@Composable
internal fun rememberLiquidGlassHighlight(): (alpha: Float) -> Highlight {
    val context = LocalContext.current
    DisposableEffect(context) {
        LiquidGlassLight.acquire(context)
        onDispose { LiquidGlassLight.release() }
    }
    return litHighlight
}

private val litHighlight: (Float) -> Highlight = { alpha ->
    Highlight(alpha = alpha, style = HighlightStyle.Default(angle = LiquidGlassLight.angle))
}
