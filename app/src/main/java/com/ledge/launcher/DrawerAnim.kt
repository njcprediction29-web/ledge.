package com.ledge.launcher

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

// All the drawer animation settings in one place
object DrawerAnim {
    const val FIRST_ROWS = 10
    const val STAGGER_MS = 28L
    const val DURATION_MS = 320
    const val NEW_APP_MINUTES = 10
}

private fun isRecent(ctx: Context, pkg: String): Boolean = try {
    System.currentTimeMillis() - ctx.packageManager.getPackageInfo(pkg, 0).firstInstallTime <
        DrawerAnim.NEW_APP_MINUTES * 60_000L
} catch (e: Exception) { false }

// Fades a row in and grows it slightly. The first rows animate, and so does an app installed
// in the last few minutes, so a new app appears with the same soft entrance.
@Composable
fun Entrance(index: Int, pkg: String, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val still = reducedMotion(ctx)
    val fresh = remember(pkg) { pkg.isNotEmpty() && isRecent(ctx, pkg) }
    val animate = !still && (index in 0 until DrawerAnim.FIRST_ROWS || fresh)
    val p = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            if (index in 0 until DrawerAnim.FIRST_ROWS) delay(index * DrawerAnim.STAGGER_MS)
            p.animateTo(1f, tween(DrawerAnim.DURATION_MS, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.graphicsLayer {
            val v = p.value
            alpha = v
            scaleX = 0.92f + 0.08f * v
            scaleY = 0.92f + 0.08f * v
            transformOrigin = TransformOrigin(0f, 0.5f)
        }
    ) { content() }
}
