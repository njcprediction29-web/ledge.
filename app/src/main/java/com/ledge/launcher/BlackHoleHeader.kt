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

// ONE place to change the background color (dark charcoal like the reference)
private val BH_CHARCOAL = Color(0xFF1B1C1F)

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

// The whole background of the home screen: charcoal + glowing half ring + compass inside
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
            val cy = h * 0.36f          // height of the flat bottom edge (move this to go up or down)
            val r = w * 0.30f           // size of the black hole (make bigger or smaller)
            val thin = 1.dp.toPx()
            val center = Offset(cx, cy)

            // only the top half is visible, cut flat at the bottom
            clipRect(left = 0f, top = 0f, right = w, bottom = cy) {
                // soft glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = r * 2.1f
                    ),
                    radius = r * 2.1f,
                    center = center
                )

                // thin light streaks that bend around the ring
                for (i in 0..7) {
                    val f = i.toFloat()
                    val rx = r * (1.12f + 0.09f * f)
                    val ry = r * (0.10f + 0.07f * f)
                    drawArc(
                        color = Color.White.copy(alpha = 0.55f - 0.05f * f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(cx - rx, cy - ry),
                        size = Size(rx * 2f, ry * 2f),
                        style = Stroke(width = thin)
                    )
                }

                // black center
                drawCircle(color = Color.Black, radius = r * 0.86f, center = center)

                // white ring (thick) and faint outer ring
                drawCircle(
                    color = Color.White,
                    radius = r * 0.88f,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f),
                    radius = r * 0.94f,
                    center = center,
                    style = Stroke(width = thin)
                )

                // compass: turns the opposite way of the phone so N always points north
                paint.textSize = r * 0.17f
                rotate(degrees = -az.value, pivot = center) {
                    for (k in 0 until 24) {
                        val big = k % 6 == 0
                        rotate(degrees = k * 15f, pivot = center) {
                            drawLine(
                                color = Color.White.copy(alpha = if (big) 0.9f else 0.5f),
                                start = Offset(cx, cy - r * 0.78f),
                                end = Offset(cx, cy - r * (if (big) 0.66f else 0.71f)),
                                strokeWidth = thin * 1.5f
                            )
                        }
                    }
                    val letters = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
                    for (pair in letters) {
                        rotate(degrees = pair.second, pivot = center) {
                            drawIntoCanvas { c ->
                                c.nativeCanvas.drawText(pair.first, cx, cy - r * 0.46f, paint)
                            }
                        }
                    }
                }

                // small fixed dot at the top: shows which way you are facing
                drawCircle(
                    color = Color.White,
                    radius = 2.5.dp.toPx(),
                    center = Offset(cx, cy - r * 0.82f)
                )
            }
        }
    }
}
