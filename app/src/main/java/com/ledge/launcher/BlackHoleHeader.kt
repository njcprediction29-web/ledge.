package com.ledge.launcher

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ONE place to change the background color (charcoal black, like the reference)
private val BH_CHARCOAL = Color(0xFF0D0E10)

// Reads the phone's direction (0 = north) and smooths it so the compass moves gently
@Composable
private fun rememberBhAzimuth(): MutableState<Float> {
    val ctx = LocalContext.current
    val az = remember { mutableStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(9)
        val rem = FloatArray(9)
        val ori = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                // phone held upright (screen facing you), like the reference video
                SensorManager.remapCoordinateSystem(
                    rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, rem
                )
                SensorManager.getOrientation(rem, ori)
                val target = Math.toDegrees(ori[0].toDouble()).toFloat()
                var d = target - az.value
                while (d > 180f) d -= 360f
                while (d < -180f) d += 360f
                az.value = az.value + d * 0.12f
            }

            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm.unregisterListener(listener) }
    }
    return az
}

// The whole background of the home screen: charcoal black + glowing black hole + compass
@Composable
fun BlackHoleBackground() {
    val az = rememberBhAzimuth()
    val paint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    Box(Modifier.fillMaxSize().background(BH_CHARCOAL)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * 0.42f          // height of the flat bottom edge (bigger number = lower)
            val r = w * 0.22f           // size of the glowing ring (bigger number = bigger hole)
            val pr = r * 0.50f          // size of the black center where the compass lives
            val thin = 1.dp.toPx()
            val center = Offset(cx, cy)

            // top half: glow, streaks, bright ring, compass (cut flat at the bottom)
            clipRect(left = 0f, top = 0f, right = w, bottom = cy) {
                // wide soft glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.07f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = r * 2.4f
                    ),
                    radius = r * 2.4f,
                    center = center
                )

                // thin light streaks bending around the hole
                for (i in 0..13) {
                    val f = i.toFloat()
                    val rx = r * (1.05f + 0.10f * f)
                    val ry = r * (0.16f + 0.075f * f)
                    drawArc(
                        color = Color.White.copy(alpha = 0.60f - 0.04f * f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = thin)
                    )
                }

                // bright glowing ring with a black center
                drawCircle(
                    brush = Brush.radialGradient(
                        0.00f to Color.Black,
                        0.48f to Color.Black,
                        0.52f to Color.White,
                        0.68f to Color.White.copy(alpha = 0.85f),
                        0.86f to Color.White.copy(alpha = 0.25f),
                        1.00f to Color.Transparent,
                        center = center,
                        radius = r
                    ),
                    radius = r,
                    center = center
                )

                // crisp edge of the black center
                drawCircle(
                    color = Color.White,
                    radius = pr,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // compass: turns the opposite way of the phone so N always points north
                paint.textSize = pr * 0.30f
                rotate(degrees = -az.value, pivot = center) {
                    for (k in 0 until 24) {
                        val big = k % 6 == 0
                        rotate(degrees = k * 15f, pivot = center) {
                            drawLine(
                                color = Color.White.copy(alpha = if (big) 0.9f else 0.5f),
                                start = Offset(cx, cy - pr * 0.86f),
                                end = Offset(cx, cy - pr * (if (big) 0.70f else 0.77f)),
                                strokeWidth = thin * 1.5f
                            )
                        }
                    }
                    val letters = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
                    for (pair in letters) {
                        rotate(degrees = pair.second, pivot = center) {
                            drawIntoCanvas { c ->
                                c.nativeCanvas.drawText(pair.first, cx, cy - pr * 0.50f, paint)
                            }
                        }
                    }
                }

                // small fixed black dot on the ring at the top: shows which way you face
                drawCircle(
                    color = Color.Black,
                    radius = 3.dp.toPx(),
                    center = Offset(cx, cy - r * 0.62f)
                )
            }

            // under the flat edge: small curved light "smile" like the reference
            clipRect(left = 0f, top = cy, right = w, bottom = h) {
                for (j in 0..2) {
                    val g = j.toFloat()
                    val rx = r * (0.62f - 0.10f * g)
                    val ry = r * (0.26f - 0.05f * g)
                    drawArc(
                        color = Color.White.copy(alpha = 0.70f - 0.20f * g),
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - rx, cy + r * 0.55f - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = thin * 1.5f)
                    )
                }
            }
        }
    }
}
