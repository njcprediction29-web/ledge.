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
import kotlin.math.max

// Turns any app icon into ONLY its logo in one color, with a see-through background.
// The original icon is never changed: this makes a separate picture and keeps it in its own cache.
object IconEngine {
    // ONE place to change the icon color for the whole launcher (soft warm peach)
    val PEACH: Int = 0xFFF8D2BC.toInt()

    private const val S = 108
    private const val GROW = 0.18f
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun clear() { cache.clear() }

    fun lumen(ctx: Context, a: AppItem): ImageBitmap = render(ctx, a, PEACH)

    fun render(ctx: Context, a: AppItem, color: Int): ImageBitmap =
        cache.getOrPut(a.pkg + "|" + color) {
            try { build(ctx, a, color).asImageBitmap() } catch (e: Exception) { loadIcon(ctx, a) }
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

    private fun diffFrom(c: Int, r: Int, g: Int, b: Int): Int =
        (abs(((c shr 16) and 255) - r) + abs(((c shr 8) and 255) - g) + abs((c and 255) - b)) / 3

    // 0..255 per pixel: how much "logo" is there.
    private fun maskOf(src: Bitmap, allowFill: Boolean): IntArray {
        val px = IntArray(S * S)
        src.getPixels(px, 0, S, 0, 0, S, S)
        var clear = 0
        for (c in px) if ((c ushr 24) < 40) clear++
        val out = IntArray(S * S)
        if (clear > px.size * 0.15f) {
            for (i in px.indices) out[i] = px[i] ushr 24
            if (allowFill) refineFilled(px, out)
            return out
        }
        // a full square picture: the logo is whatever differs from the background color at the edges
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
            out[i] = minOf(255, diffFrom(c, r, g, b) * 3) * (c ushr 24) / 255
        }
        return out
    }

    // When the logo is one big solid shape with a drawing inside it, keep only the drawing
    private fun refineFilled(px: IntArray, out: IntArray) {
        var opaque = 0
        val hist = HashMap<Int, Int>()
        for (i in px.indices) {
            if (out[i] > 200) {
                opaque++
                val c = px[i]
                val k = (((c shr 20) and 15) shl 8) or (((c shr 12) and 15) shl 4) or ((c shr 4) and 15)
                hist[k] = (hist[k] ?: 0) + 1
            }
        }
        if (opaque < S * S * 0.35f) return
        val top = hist.maxByOrNull { it.value } ?: return
        if (top.value < opaque * 0.5f) return
        val r0 = ((top.key shr 8) and 15) * 16 + 8
        val g0 = ((top.key shr 4) and 15) * 16 + 8
        val b0 = (top.key and 15) * 16 + 8
        for (i in px.indices) {
            if (out[i] == 0) continue
            out[i] = minOf(255, diffFrom(px[i], r0, g0, b0) * 3) * out[i] / 255
        }
    }

    private fun glyphAlpha(d: Drawable): IntArray {
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            if (Build.VERSION.SDK_INT >= 33) {
                val mono = d.monochrome
                if (mono != null) return maskOf(draw(mono, GROW), false)
            }
            val fg = d.foreground
            if (fg != null) return maskOf(draw(fg, GROW), true)
        }
        return maskOf(draw(d, 0f), true)
    }

    private fun build(ctx: Context, a: AppItem, color: Int): Bitmap {
        val alpha = glyphAlpha(original(ctx, a))
        var covered = 0
        var minX = S
        var minY = S
        var maxX = -1
        var maxY = -1
        for (i in alpha.indices) {
            if (alpha[i] > 60) {
                covered++
                val x = i % S
                val y = i / S
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        val out = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        if (covered < S * S * 0.01f) {
            // could not find a clean logo: keep the original picture instead of a broken one
            c.drawBitmap(draw(original(ctx, a), 0f), 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        }
        val m = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        m.setPixels(IntArray(S * S) { alpha[it] shl 24 }, 0, S, 0, 0, S, S)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        // same visual size for every logo: fit it to about 80% of the icon box, centered
        val w = (maxX - minX + 1).toFloat()
        val h = (maxY - minY + 1).toFloat()
        val scale = (S * 0.80f / max(w, h)).coerceIn(0.85f, 1.7f)
        c.save()
        c.translate(S / 2f, S / 2f)
        c.scale(scale, scale)
        c.translate(-(minX + maxX + 1) / 2f, -(minY + maxY + 1) / 2f)
        c.drawBitmap(m, 0f, 0f, paint)
        c.restore()
        return out
    }
}
