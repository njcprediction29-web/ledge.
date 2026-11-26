package com.ledge.launcher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Which way the phone points, in degrees (0 = north). Uses the phone's rotation sensor.
@Composable
fun rememberHeading(): State<Float> {
    val ctx = LocalContext.current
    val heading = remember { mutableStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val r = FloatArray(9)
        val r2 = FloatArray(9)
        val o = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(r, e.values)
                val upright = abs(r[8]) < 0.7f
                if (upright) {
                    SensorManager.remapCoordinateSystem(r, SensorManager.AXIS_X, SensorManager.AXIS_Z, r2)
                    SensorManager.getOrientation(r2, o)
                } else {
                    SensorManager.getOrientation(r, o)
                }
                val target = ((Math.toDegrees(o[0].toDouble()) + 360.0) % 360.0).toFloat()
                var d = target - heading.value
                while (d > 180f) d -= 360f
                while (d < -180f) d += 360f
                heading.value = (heading.value + d * 0.15f + 360f) % 360f
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return heading
}

@Composable
fun BlackHoleHeader(modifier: Modifier = Modifier) {
    val heading = rememberHeading()
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    }
    Canvas(modifier = modifier.fillMaxWidth().height(230.dp)) {
        val hd = heading.value
        val w = size.width
        val h = size.height
        val c = Offset(w * 0.62f, h * 0.94f)
        val rad = w * 0.30f
        val thin = 1.2.dp.toPx()

        // Only the upper half is visible, cut flat at the centre line (like the reference).
        clipRect(top = 0f, bottom = c.y) {
            // soft glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                    center = c,
                    radius = rad * 2.3f
                ),
                radius = rad * 2.3f,
                center = c
            )
            // thin disk streaks
            for (i in 0..13) {
                val a = w * (1.0f - 0.04f * i)
                val b = rad * (0.10f + 0.045f * i)
                val alpha = 0.75f - 0.045f * i
                drawOval(
                    color = Color.White.copy(alpha = alpha.coerceIn(0.1f, 0.8f)),
                    topLeft = Offset(c.x - a, c.y - b),
                    size = Size(2f * a, 2f * b),
                    style = Stroke(width = thin)
                )
            }
            // bright eye
            drawCircle(color = Color.White, radius = rad, center = c)
            drawCircle(
                color = Color(0xFFBBBBBB),
                radius = rad * 0.93f,
                center = c,
                style = Stroke(width = thin)
            )

            // compass ticks (they turn with the phone)
            for (deg in 0 until 360 step 15) {
                val ang = Math.toRadians((deg - hd).toDouble())
                val long = deg % 90 == 0
                val r1 = rad * (if (long) 0.80f else 0.86f)
                val r2 = rad * 0.92f
                val sx = sin(ang).toFloat()
                val sy = -cos(ang).toFloat()
                drawLine(
                    color = Color.Black,
                    start = Offset(c.x + sx * r1, c.y + sy * r1),
                    end = Offset(c.x + sx * r2, c.y + sy * r2),
                    strokeWidth = if (long) thin * 1.6f else thin
                )
            }
            // the letter N sits at true north
            val an = Math.toRadians((-hd).toDouble())
            val nr = rad * 0.66f
            paint.textSize = rad * 0.22f
            drawContext.canvas.nativeCanvas.drawText(
                "N",
                c.x + sin(an).toFloat() * nr,
                c.y - cos(an).toFloat() * nr + paint.textSize * 0.35f,
                paint
            )
            // fixed pointer (where the phone points)
            drawLine(Color.Black, c, Offset(c.x, c.y - rad * 0.45f), strokeWidth = thin * 1.4f)
            drawLine(Color.Black, c, Offset(c.x + rad * 0.3f, c.y), strokeWidth = thin * 1.4f)
        }
    }
}
