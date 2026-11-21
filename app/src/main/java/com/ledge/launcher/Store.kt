package com.ledge.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class AppItem(val label: String, val pkg: String, val cls: String, val installed: Long = 0L)

sealed class Entry {
    data class Head(val c: Char) : Entry()
    data class App(val app: AppItem) : Entry()
}

fun buildEntries(apps: List<AppItem>, from: Char?): List<Entry> {
    val out = ArrayList<Entry>()
    var last: Char? = null
    for (a in apps) {
        val c = a.label.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar() ?: '#'
        if (from != null && from != '#' && (c == '#' || c < from)) continue
        if (c != last) { out.add(Entry.Head(c)); last = c }
        out.add(Entry.App(a))
    }
    return out
}

class Pal(val text: Color, val sub: Color, val card: Color, val scrim: Color, val accent: Color)
class Pack(val name: String, val colors: List<Color>)

val ACCENTS = listOf(
    Color(0xFFFFB27D), Color(0xFF7CC4FF), Color(0xFF7DE0B0), Color(0xFFFF8FA3), Color(0xFFB9A4FF)
)
val LETTER_SP = listOf(9, 11, 13)
val PACKS = listOf(
    Pack("Forest", listOf(Color(0xFF6D7D75), Color(0xFF2D3834), Color(0xFF0F1311))),
    Pack("Midnight", listOf(Color(0xFF1B2A4A), Color(0xFF0B1020), Color(0xFF05070F))),
    Pack("Sunset", listOf(Color(0xFFFF9A76), Color(0xFFC0504D), Color(0xFF2B1B3D))),
    Pack("Ocean", listOf(Color(0xFF58C3D9), Color(0xFF1B6B99), Color(0xFF06243D))),
    Pack("Aurora", listOf(Color(0xFF0A0F1C), Color(0xFF2A7F6B), Color(0xFF3B2C8F))),
    Pack("Black", listOf(Color.Black, Color.Black)),
    Pack("Sand", listOf(Color(0xFFE9DCC3), Color(0xFFB89F7A))),
    Pack("Mono", listOf(Color(0xFF6B6B6B), Color(0xFF1A1A1A))),
    Pack("Rose", listOf(Color(0xFFFFB3C1), Color(0xFF7A3B6E), Color(0xFF1C1030)))
)

fun palette(theme: Int, accent: Int): Pal {
    val a = ACCENTS[accent.coerceIn(0, ACCENTS.lastIndex)]
    return when (theme) {
        1 -> Pal(Color(0xFF1B1B1F), Color(0xFF55555C), Color.White, Color(0x80FFFFFF), a)
        2 -> Pal(Color.White, Color(0xFFB5B5BC), Color(0xFF17171A), Color.Black, a)
        else -> Pal(Color.White, Color(0xFFD6D6DC), Color(0xFF1F1F24), Color(0x66000000), a)
    }
}

private val iconCache = HashMap<String, ImageBitmap>()

fun loadIcon(ctx: Context, a: AppItem): ImageBitmap = iconCache.getOrPut(a.pkg) {
    val pm = ctx.packageManager
    try {
        pm.getActivityIcon(ComponentName(a.pkg, a.cls)).toBitmap(108, 108).asImageBitmap()
    } catch (e: Exception) {
        pm.defaultActivityIcon.toBitmap(108, 108).asImageBitmap()
    }
}

fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..67 -> "🌧️"
    in 71..77 -> "❄️"
    in 80..82 -> "🌦️"
    in 95..99 -> "⛈️"
    else -> "🌡️"
}

class Store(private val app: Application) : AndroidViewModel(app) {
    private val sp = app.getSharedPreferences("ledge", Context.MODE_PRIVATE)

    // font=A-Z size, theme, accent, icon=icon style, side/rh/pos=A-Z placement, dim, wp=wallpaper (-1 system, 0.. pack, 100 photo)
    private val defs = mapOf(
        "font" to 1, "theme" to 0, "accent" to 0, "icon" to 0, "side" to 0, "rh" to 1, "pos" to 1,
        "dim" to 30, "wp" to -1, "useF" to 0, "sw" to 1, "sb" to 1, "sv" to 1, "st" to 1, "sn" to 1
    )
    private val ints = mutableStateMapOf<String, Int>().also { m -> defs.forEach { (k, d) -> m[k] = sp.getInt(k, d) } }
    fun i(k: String): Int = ints[k] ?: 0
    fun setI(k: String, v: Int) { ints[k] = v; sp.edit().putInt(k, v).apply() }

    var apps by mutableStateOf<List<AppItem>>(emptyList()); private set
    var hidden by mutableStateOf(sp.getStringSet("hidden", emptySet())!!.toSet()); private set
    var favs by mutableStateOf(sp.getString("favs", "")!!.split("|").filter { it.isNotEmpty() }); private set
    var todos by mutableStateOf(sp.getString("todos", "")!!.split("\u0001").filter { it.isNotEmpty() }); private set
    var vFav by mutableStateOf(sp.getStringSet("vfav", emptySet())!!.toSet()); private set
    var vOff by mutableIntStateOf(0)
    var tempC by mutableStateOf<Double?>(null); private set
    var wCode by mutableIntStateOf(0); private set
    var homeTick by mutableIntStateOf(0)
    var wallBmp by mutableStateOf<ImageBitmap?>(null); private set

    init { if (i("wp") == 100) loadWall() }

    fun load() {
        val pm = app.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(q, 0)
            .map { r ->
                val p = r.activityInfo.packageName
                AppItem(r.loadLabel(pm).toString(), p, r.activityInfo.name,
                    try { pm.getPackageInfo(p, 0).firstInstallTime } catch (e: Exception) { 0L })
            }
            .filter { it.pkg != app.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
        if (list != apps) { iconCache.clear(); apps = list }
    }

    fun recent(): List<AppItem> = apps.sortedByDescending { it.installed }.take(5)

    fun toggleHidden(pkg: String) {
        hidden = if (pkg in hidden) hidden - pkg else hidden + pkg
        sp.edit().putStringSet("hidden", hidden).apply()
    }
    fun toggleFav(pkg: String) {
        favs = if (pkg in favs) favs - pkg else favs + pkg
        sp.edit().putString("favs", favs.joinToString("|")).apply()
    }
    fun addTodo(t: String) {
        if (t.isBlank()) return
        todos = todos + t.trim()
        sp.edit().putString("todos", todos.joinToString("\u0001")).apply()
    }
    fun doneTodo(index: Int) {
        todos = todos.filterIndexed { n, _ -> n != index }
        sp.edit().putString("todos", todos.joinToString("\u0001")).apply()
    }
    fun verse(): Pair<String, String> {
        val d = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return VERSES[Math.floorMod(d + vOff, VERSES.size)]
    }
    fun toggleVFav(ref: String) {
        vFav = if (ref in vFav) vFav - ref else vFav + ref
        sp.edit().putStringSet("vfav", vFav).apply()
    }

    private fun loadWall() {
        val f = File(app.filesDir, "wall.jpg")
        if (f.exists()) wallBmp = BitmapFactory.decodeFile(f.path)?.asImageBitmap()
    }

    fun setPhoto(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var sample = 1
                while (o.outWidth / sample > 1600 || o.outHeight / sample > 2400) sample *= 2
                val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
                val bmp = app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                    ?: return@launch
                File(app.filesDir, "wall.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                withContext(Dispatchers.Main) { wallBmp = bmp.asImageBitmap(); setI("wp", 100) }
            } catch (e: Exception) {
            }
        }
    }

    fun weatherText(): String? {
        val t = tempC ?: return null
        val f = i("useF") == 1
        val v = if (f) t * 9 / 5 + 32 else t
        return "${weatherEmoji(wCode)} ${v.roundToInt()}°${if (f) "F" else "C"}"
    }

    @SuppressLint("MissingPermission")
    fun refreshWeather() {
        if (app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lm = app.getSystemService(LocationManager::class.java)
                val loc = findLocation(lm) ?: return@launch
                val url = "https://api.open-meteo.com/v1/forecast?latitude=${loc.latitude}" +
                    "&longitude=${loc.longitude}&current=temperature_2m,weather_code"
                val cur = JSONObject(URL(url).readText()).getJSONObject("current")
                val t = cur.getDouble("temperature_2m")
                val c = cur.getInt("weather_code")
                withContext(Dispatchers.Main) { tempC = t; wCode = c }
            } catch (e: Exception) {
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun findLocation(lm: LocationManager): Location? {
        val providers = lm.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
        val last = providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        if (last != null || Build.VERSION.SDK_INT < 30) return last
        val p = providers.firstOrNull() ?: return null
        return withTimeoutOrNull(8000) {
            suspendCancellableCoroutine<Location?> { c ->
                lm.getCurrentLocation(p, null, app.mainExecutor) { c.resume(it) }
            }
        }
    }
}
