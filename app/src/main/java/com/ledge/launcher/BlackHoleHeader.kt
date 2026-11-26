package com.ledge.launcher

import android.content.Context
import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ---- the black hole picture: app/src/main/assets/blackhole.png (941 x 420) ----
// Measurements inside that picture (in picture pixels):
private const val IMG_W = 941f
private const val DISK_Y = 269f      // the bright flat line across the middle
private const val HOLE_CY = 233f     // center of the black ball
private const val COMPASS_R = 112f   // size of the compass inside the black ball
private const val COMPASS_CUT = 255f // the compass is cut flat here (above the bright line)

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

// The whole background of the home screen: pitch black + the black hole picture + live compass
@Composable
fun BlackHoleBackground() {
    val ctx = LocalContext.current
    val az = rememberBhAzimuth()
    val img = remember {
        try {
            ctx.assets.open("blackhole.png").use { BitmapFactory.decodeStream(it) }
                ?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
    val paint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h * 0.42f           // where the bright flat line sits (bigger number = lower)
            val s = w / IMG_W            // the picture is stretched to the screen width
            val thin = 1.dp.toPx()

            if (img != null) {
                val top = cy - DISK_Y * s
                drawImage(
                    image = img,
                    dstOffset = IntOffset(0, top.roundToInt()),
                    dstSize = IntSize(w.roundToInt(), (img.height * s).roundToInt())
                )
            }

            // compass inside the black ball (only the top part shows, cut flat at the bottom)
            val hy = cy - (DISK_Y - HOLE_CY) * s
            val hc = Offset(cx, hy)
            val pr = COMPASS_R * s
            clipRect(left = 0f, top = 0f, right = w, bottom = cy - (DISK_Y - COMPASS_CUT) * s) {
                paint.textSize = pr * 0.22f
                // turns the opposite way of the phone so N always points north
                rotate(degrees = -az.value, pivot = hc) {
                    for (k in 0 until 24) {
                        val big = k % 6 == 0
                        rotate(degrees = k * 15f, pivot = hc) {
                            drawLine(
                                color = Color.White.copy(alpha = if (big) 0.95f else 0.55f),
                                start = Offset(cx, hy - pr * 0.96f),
                                end = Offset(cx, hy - pr * (if (big) 0.82f else 0.88f)),
                                strokeWidth = thin * 1.5f
                            )
                        }
                    }
                    val letters = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
                    for (pair in letters) {
                        rotate(degrees = pair.second, pivot = hc) {
                            drawIntoCanvas { c ->
                                c.nativeCanvas.drawText(pair.first, cx, hy - pr * 0.62f, paint)
                            }
                        }
                    }
                }
                // fixed marker at the top: shows which way you are facing
                drawLine(
                    color = Color.White,
                    start = Offset(cx, hy - pr * 1.00f),
                    end = Offset(cx, hy - pr * 0.80f),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }
    }
}
