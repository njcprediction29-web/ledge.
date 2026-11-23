package com.ledge.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

// Turns any app icon into a cream tile with the app's logo shape cut out in a dark color.
// The original icon is never changed: this makes a separate picture and keeps it in its own cache.
object IconEngine {
    private const val S = 108
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun clear() { cache.clear() }

    fun render(ctx: Context, a: AppItem, tile: Int, glyph: Int): ImageBitmap =
        cache.getOrPut(a.pkg + "|" + tile + "|" + glyph) {
            try { build(ctx, a, tile, glyph).asImageBitmap() } catch (e: Exception) { loadIcon(ctx, a) }
        }

    private fun original(ctx: Context, a: AppItem): Drawable {
        val pm = ctx.packageManager
        val d = try { pm.getActivityIcon(ComponentName(a.pkg, a.cls)) } catch (e: Exception) { pm.defaultActivityIcon }
        return d.constantState?.newDrawable()?.mutate() ?: d
    }

    private fun draw(d: Drawable, grow: Float): Bitmap {
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val p = (S * grow).toInt()
        d.setBounds(-p, -p, S + p, S + p)
        d.draw(Canvas(bmp))
        return bmp
    }

    // 0..255 per pixel: how much "logo" is there. Transparent icons use their own see-through shape,
    // full-square icons use how different each pixel is from the background color at the edges.
    private fun maskOf(src: Bitmap): IntArray {
        val px = IntArray(S * S)
        src.getPixels(px, 0, S, 0, 0, S, S)
        var clear = 0
        for (c in px) if ((c ushr 24) < 40) clear++
        val out = IntArray(S * S)
        if (clear > px.size * 0.15f) {
            for (i in px.indices) out[i] = px[i] ushr 24
            return out
        }
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        for (i in 0 until S) {
            for (idx in intArrayOf(i, (S - 1) * S + i, i * S, i * S + S - 1)) {
                val c = px[idx]
                r += (c shr 16) and 255
                g += (c shr 8) and 255
                b += c and 255
                n++
            }
        }
        r /= n
        g /= n
        b /= n
        for (i in px.indices) {
            val c = px[i]
            val diff = (abs(((c shr 16) and 255) - r) + abs(((c shr 8) and 255) - g) + abs((c and 255) - b)) / 3
            out[i] = minOf(255, diff * 3) * (c ushr 24) / 255
        }
        return out
    }

    private fun glyphAlpha(d: Drawable): IntArray {
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            if (Build.VERSION.SDK_INT >= 33) {
                val mono = d.monochrome
                if (mono != null) return maskOf(draw(mono, 0.10f))
            }
            val fg = d.foreground
            if (fg != null) return maskOf(draw(fg, 0.10f))
        }
        return maskOf(draw(d, 0f))
    }

    private fun build(ctx: Context, a: AppItem, tile: Int, glyph: Int): Bitmap {
        val alpha = glyphAlpha(original(ctx, a))
        var covered = 0
        for (v in alpha) if (v > 60) covered++
        val out = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(tile)
        if (covered < S * S * 0.01f) {
            c.drawBitmap(draw(original(ctx, a), 0f), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        }
        val m = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        m.setPixels(IntArray(S * S) { alpha[it] shl 24 }, 0, S, 0, 0, S, S)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = PorterDuffColorFilter(glyph, PorterDuff.Mode.SRC_IN)
        c.drawBitmap(m, 0f, 0f, paint)
        return out
    }
}
