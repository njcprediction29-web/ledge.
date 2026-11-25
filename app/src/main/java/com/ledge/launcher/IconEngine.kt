package com.ledge.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

// Simple logos become ONLY the logo in one color with a see-through background.
// Complicated icons (characters, games, full artwork) keep their original icon.
// The original icon is never changed: this makes a separate picture and keeps it in its own cache.
object IconEngine {
    // ONE place to change the icon color for the whole launcher (soft warm peach)
    val PEACH: Int = 0xFFF8D2BC.toInt()

    // Exceptions by package name. Put an app's package name between the quotes, separated by commas.
    // KEEP_ORIGINAL: always show the app's own icon. FORCE_LOGO: always make it a peach logo.
    private val KEEP_ORIGINAL = setOf(
        "com.mobile.legends", "com.supercell.clashofclans", "com.supercell.clashroyale",
        "com.supercell.brawlstars", "com.tencent.ig", "com.activision.callofduty.shooter",
        "com.dts.freefireth", "com.roblox.client", "com.mojang.minecraftpe",
        "com.kiloo.subwaysurf", "com.miHoYo.GenshinImpact"
    )
    private val FORCE_LOGO = setOf<String>()

    // If an icon's artwork covers more than this share of the icon, it counts as a full picture
    // and keeps its original look. Raise it to turn more apps into logos, lower it for fewer.
    private const val COMPLEX_FILL = 0.38f

    // If an icon uses this many different main colors it is artwork (a character, a scene).
    private const val MANY_COLORS = 9

    // A logo that is a plain filled shape (a blank circle or square) has no real logo in it.
    // Such icons keep their original look. Lower it to keep more icons original.
    private const val BLANK_RATIO = 0.74f

    private const val S = 108
    private const val GROW = 0.18f
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun clear() { cache.clear() }

    fun lumen(ctx: Context, a: AppItem): ImageBitmap = render(ctx, a, PEACH)
        // theme 0 Peach, 1 Ivory, 2 Cream, 3 Soft pink, 4 Pure white, 5 Accent (follows the Theme accent color)
    fun colorOf(theme: Int, accent: Int): Int = when (theme) {
        1 -> 0xFFF2ECD8.toInt()
        2 -> 0xFFFBE7C6.toInt()
        3 -> 0xFFF9C6D8.toInt()
        4 -> 0xFFFFFFFF.toInt()
        5 -> accent
        else -> PEACH
    }

    fun render(ctx: Context, a: AppItem, color: Int): ImageBitmap {
        // the app's last update time is part of the key, so an updated app with a new logo is redrawn
        val stamp = try { ctx.packageManager.getPackageInfo(a.pkg, 0).lastUpdateTime } catch (e: Exception) { 0L }
        return cache.getOrPut(a.pkg + "|" + color + "|" + stamp) {
            try {
                val keep = a.pkg in KEEP_ORIGINAL || (a.pkg !in FORCE_LOGO && isComplex(original(ctx, a)))
                if (keep) loadIcon(ctx, a) else (build(ctx, a, color)?.asImageBitmap() ?: loadIcon(ctx, a))
            } catch (e: Exception) {
                loadIcon(ctx, a)
            }
        }
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

    // how many different main colors the picture has
    private fun colorBins(px: IntArray): Int {
        val hist = IntArray(512)
        var n = 0
        for (c in px) {
            if ((c ushr 24) > 200) {
                val k = (((c shr 21) and 7) shl 6) or (((c shr 13) and 7) shl 3) or ((c shr 5) and 7)
                hist[k]++
                n++
            }
        }
        if (n == 0) return 0
        var bins = 0
        for (h in hist) if (h > n * 0.02f) bins++
        return bins
    }

    // True for icons that are a full picture (a character, a scene, a filled artwork)
    private fun isComplex(d: Drawable): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            // an official one-color layer means the app itself says it is a simple logo
            if (Build.VERSION.SDK_INT >= 33 && d.monochrome != null) return false
            val fg = d.foreground ?: return false
            val px = IntArray(S * S)
            draw(fg, 0f).getPixels(px, 0, S, 0, 0, S, S)
            var opaque = 0
            for (c in px) if ((c ushr 24) > 200) opaque++
            if (opaque > S * S * COMPLEX_FILL) return true
            return opaque > S * S * 0.10f && colorBins(px) >= MANY_COLORS
        }
        // an old-style icon: a full square picture is artwork, a see-through logo is not
        val px = IntArray(S * S)
        draw(d, 0f).getPixels(px, 0, S, 0, 0, S, S)
        var clear = 0
        for (c in px) if ((c ushr 24) < 40) clear++
        return clear < px.size * 0.15f
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
            if (allowFill) cutDetails(px, out)
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

    // A solid shape with a drawing inside (like Facebook's circle with the f): keep the shape
    // and cut the drawing out of it, the same style as the reference image.
    private fun cutDetails(px: IntArray, out: IntArray) {
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
        if (opaque < S * S * 0.08f) return
        val top = hist.maxByOrNull { it.value } ?: return
        if (top.value < opaque * 0.5f) return
        val r0 = ((top.key shr 8) and 15) * 16 + 8
        val g0 = ((top.key shr 4) and 15) * 16 + 8
        val b0 = (top.key and 15) * 16 + 8
        for (i in px.indices) {
            if (out[i] == 0) continue
            val d = minOf(255, diffFrom(px[i], r0, g0, b0) * 3)
            out[i] = out[i] * (255 - d) / 255
        }
    }

    // the logo mask, and whether it came from the app's official one-color layer
    private class Glyph(val alpha: IntArray, val official: Boolean)

    private fun glyphOf(d: Drawable): Glyph {
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            if (Build.VERSION.SDK_INT >= 33) {
                val mono = d.monochrome
                if (mono != null) return Glyph(maskOf(draw(mono, GROW), false), true)
            }
            val fg = d.foreground
            if (fg != null) return Glyph(maskOf(draw(fg, GROW), true), false)
        }
        return Glyph(maskOf(draw(d, 0f), true), false)
    }

    // returns null when no clean logo could be found (the caller then keeps the original icon)
    private fun build(ctx: Context, a: AppItem, color: Int): Bitmap? {
        val glyph = glyphOf(original(ctx, a))
        val alpha = glyph.alpha
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
        if (covered < S * S * 0.01f) return null
        val w = (maxX - minX + 1).toFloat()
        val h = (maxY - minY + 1).toFloat()
        // a plain filled shape has no real logo in it
        if (!glyph.official && covered > w * h * BLANK_RATIO) return null

        val out = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val m = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        m.setPixels(IntArray(S * S) { alpha[it] shl 24 }, 0, S, 0, 0, S, S)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        // same visual size for every logo: fit it to about 80% of the icon box, centered
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
