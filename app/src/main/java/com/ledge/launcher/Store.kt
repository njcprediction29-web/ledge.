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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import java.util.concurrent.ConcurrentHashMap
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
        Color(0xFFFF8FA3), Color(0xFFB9A4FF),
    Color(0xFFFF6B6B), Color(0xFFFF9F43), Color(0xFFFFD93D), Color(0xFFC6F135), Color(0xFF6BCB77), Color(0xFF2ED573),
    Color(0xFF1DD1A1), Color(0xFF00D2D3), Color(0xFF48DBFB), Color(0xFF54A0FF), Color(0xFF5F7CFF), Color(0xFF8E7CFF),
    Color(0xFFC56CF0), Color(0xFFFF6FD8), Color(0xFFFF7AB6), Color(0xFFF8A5C2), Color(0xFFFFC7A6), Color(0xFFE8D5B7),
    Color(0xFFD4A373), Color(0xFFA0E7E5), Color(0xFFB8F2E6), Color(0xFFFFE066), Color(0xFFFF5E78), Color(0xFFBDB2FF),
    Color(0xFFCAFFBF), Color(0xFFE9ECEF)
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
    Pack("Rose", listOf(Color(0xFFFFB3C1), Color(0xFF7A3B6E), Color(0xFF1C1030))),
    Pack("Dusk", listOf(Color(0xFF2C3E70), Color(0xFF7B4B94), Color(0xFFE8837A))),
    Pack("Peach", listOf(Color(0xFFFFD6B0), Color(0xFFFF9E9E), Color(0xFFB5517E))),
    Pack("Lavender ☀", listOf(Color(0xFFF3E8FF), Color(0xFFC7B3F5), Color(0xFF8E7CC3))),
    Pack("Mint ☀", listOf(Color(0xFFE3FBEA), Color(0xFF9AE3B8), Color(0xFF3FA796))),
    Pack("Slate", listOf(Color(0xFF3A4452), Color(0xFF1E252E), Color(0xFF0C1015))),
    Pack("Ember", listOf(Color(0xFFFFB347), Color(0xFFD9480F), Color(0xFF3B0A0A))),
    Pack("Candy", listOf(Color(0xFFFF9ACB), Color(0xFFB388FF), Color(0xFF4F46E5))),
    Pack("Arctic ☀", listOf(Color(0xFFEAF6FF), Color(0xFFA8D4F0), Color(0xFF5B9BC9))),
    Pack("Cream ☀", listOf(Color(0xFFFFF8EC), Color(0xFFF1DDBA), Color(0xFFD6B98B))),
    Pack("Emerald", listOf(Color(0xFF0F3D2E), Color(0xFF14664B), Color(0xFF061A14))),
    Pack("Wine", listOf(Color(0xFF5A1A2D), Color(0xFF2A0E1E), Color(0xFF0F060C))),
    Pack("Cosmos", listOf(Color(0xFF0B0B2B), Color(0xFF3A1C71), Color(0xFFD76D77))),
    Pack("Steel", listOf(Color(0xFF8D99AE), Color(0xFF2B2D42), Color(0xFF14151F))),
    Pack("Golden", listOf(Color(0xFFF6D365), Color(0xFFFDA085), Color(0xFF5E3A3A)))
)

// Solid-color wallpapers (wp = 200 + position in this list)
class SolidWall(val name: String, val color: Color)
val SOLIDS = listOf(
    SolidWall("Charcoal", Color(0xFF1C1C1E)),
    SolidWall("Navy", Color(0xFF0B1B3A)),
    SolidWall("Forest", Color(0xFF133A2B)),
    SolidWall("Wine", Color(0xFF4A1426)),
    SolidWall("Plum", Color(0xFF3B1F4D)),
    SolidWall("Teal", Color(0xFF0E4A52)),
    SolidWall("Slate", Color(0xFF2F3A47)),
    SolidWall("Rose", Color(0xFFE8A0B4)),
    SolidWall("Sand ☀", Color(0xFFE6D8BF)),
    SolidWall("Snow ☀", Color(0xFFF2F4F7))
)

// Gradient direction: 0 diagonal, 1 down, 2 across, 3 reverse diagonal, 4 glow from the middle
fun wallBrush(wp: Int, dir: Int): Brush? {
    val colors: List<Color> = when {
        wp in PACKS.indices -> PACKS[wp].colors
        wp >= 200 && wp - 200 in SOLIDS.indices -> SOLIDS[wp - 200].color.let { listOf(it, it) }
        else -> return null
    }
    return when (dir) {
        1 -> Brush.verticalGradient(colors)
        2 -> Brush.horizontalGradient(colors)
        3 -> Brush.linearGradient(colors, Offset(Float.POSITIVE_INFINITY, 0f), Offset(0f, Float.POSITIVE_INFINITY))
        4 -> Brush.radialGradient(colors)
        else -> Brush.linearGradient(colors, Offset.Zero, Offset.Infinite)
    }
}

fun palette(theme: Int, accent: Int): Pal {
    val a = ACCENTS[accent.coerceIn(0, ACCENTS.lastIndex)]
    return when (theme) {
        1 -> Pal(Color(0xFF1B1B1F), Color(0xFF55555C), Color.White, Color(0x80FFFFFF), a)
        2 -> Pal(Color.White, Color(0xFFB5B5BC), Color(0xFF17171A), Color.Black, a)
        else -> Pal(Color.White, Color(0xFFD6D6DC), Color(0xFF1F1F24), Color(0x66000000), a)
    }
}

private val iconCache = ConcurrentHashMap<String, ImageBitmap>()

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
    // clean=Clean Mode, hy=home position (0 high, 1 medium, 2 low)
    private val defs = mapOf(
        "font" to 1, "theme" to 0, "accent" to 0, "icon" to 0, "side" to 0, "rh" to 1, "pos" to 1,
        "dim" to 30, "wp" to -1, "useF" to 0, "sw" to 1, "sb" to 1, "sv" to 1, "st" to 1, "sn" to 1,
        "clean" to 0, "hy" to 2,
        "ishape" to 0, "isize" to 1, "ispace" to 1, "labels" to 1, "lsize" to 1, "lweight" to 1, "lfont" to 0,
        "cstyle" to 0, "csize" to 1, "ccolor" to 0, "cweight" to 0, "calign" to 0,
        "wdir" to 0, "wmode" to 0, "wh" to 1, "hm" to 0, "hf" to 0, "hp" to 0, "mstyle" to 0, "ictheme" to 0
    )
    private val ints = mutableStateMapOf<String, Int>().also { m -> defs.forEach { (k, d) -> m[k] = sp.getInt(k, d) } }
    fun i(k: String): Int = ints[k] ?: 0
    fun setI(k: String, v: Int) { ints[k] = v; sp.edit().putInt(k, v).apply() }

    var apps by mutableStateOf<List<AppItem>>(emptyList()); private set
    var hidden by mutableStateOf(sp.getStringSet("hidden", emptySet())!!.toSet()); private set
    var favs by mutableStateOf(sp.getString("favs", "")!!.split("|").filter { it.isNotEmpty() }); private set
    // Order of the home screen widgets: clock, calendar, music, favs (changed by dragging in settings)
    var widgetOrder by mutableStateOf(loadWidgetOrder()); private set
    var todos by mutableStateOf(sp.getString("todos", "")!!.split("\u0001").filter { it.isNotEmpty() }); private set
    // Plan Your Day: each item is "minutes since midnight|text", sorted by time, repeats every day
    var plan by mutableStateOf(sp.getString("plan", "")!!.split("\u0001").filter { it.isNotEmpty() }); private set
    var vFav by mutableStateOf(sp.getStringSet("vfav", emptySet())!!.toSet()); private set
    var vOff by mutableIntStateOf(0)
    var tempC by mutableStateOf<Double?>(null); private set
    var wCode by mutableIntStateOf(0); private set
    var wHum by mutableIntStateOf(-1); private set
    var cityName by mutableStateOf(sp.getString("cname", "") ?: "")
    var cityMsg by mutableStateOf("")
    var homeTick by mutableIntStateOf(0)
    var wallBmp by mutableStateOf<ImageBitmap?>(null); private set

    // remembers when each app was installed so we do not ask Android again every time
    private val installCache = ConcurrentHashMap<String, Long>()
    @Volatile private var loading = false

    init { if (i("wp") == 100) loadWall() }

    // Reads the app list in the background so coming back from an app never freezes the home screen
    fun load() {
        if (loading) return
        loading = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val pm = app.packageManager
                val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                val list = pm.queryIntentActivities(q, 0)
                    .map { r ->
                        val p = r.activityInfo.packageName
                        val t = installCache.getOrPut(p) {
                            try { pm.getPackageInfo(p, 0).firstInstallTime } catch (e: Exception) { 0L }
                        }
                        AppItem(r.loadLabel(pm).toString(), p, r.activityInfo.name, t)
                    }
                    .filter { it.pkg != app.packageName }
                    .distinctBy { it.pkg }
                    .sortedBy { it.label.lowercase() }
                withContext(Dispatchers.Main) {
                    if (list != apps) { iconCache.clear(); apps = list }
                }
                // load every icon in the background so sliding over the letters never has to wait for one
                for (a in list) { try { loadIcon(app, a) } catch (e: Exception) { } }
            } catch (e: Exception) {
            } finally {
                loading = false
            }
        }
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
    // Drag and drop: puts the favorite [pkg] in the place of the favorite [toPkg] and saves the new order
    fun moveFav(pkg: String, toPkg: String) {
        val a = favs.indexOf(pkg)
        val b = favs.indexOf(toPkg)
        if (a < 0 || b < 0 || a == b) return
        val l = favs.toMutableList()
        l.removeAt(a)
        l.add(b, pkg)
        favs = l
        sp.edit().putString("favs", favs.joinToString("|")).apply()
    }
    private fun loadWidgetOrder(): List<String> {
        val all = listOf("clock", "calendar", "plan", "music", "favs")
        val saved = (sp.getString("worder", "") ?: "").split(",").filter { it in all }.distinct()
        return saved + all.filter { it !in saved }
    }
    fun moveWidget(from: Int, to: Int) {
        if (from !in widgetOrder.indices || to !in widgetOrder.indices || from == to) return
        val l = widgetOrder.toMutableList()
        val w = l.removeAt(from)
        l.add(to, w)
        widgetOrder = l
        sp.edit().putString("worder", l.joinToString(",")).apply()
    }
    // Accepts things like "7:30 Gym", "7 am Gym", "2 pm Call mom". Returns false if it does not start with a time.
    fun addPlan(raw: String): Boolean {
        val m = Regex("^\\s*(\\d{1,2})(?::(\\d{2}))?\\s*([aApP][mM])?\\s+(.+)$").find(raw) ?: return false
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ap = m.groupValues[3].lowercase()
        if (ap == "pm" && h < 12) h += 12
        if (ap == "am" && h == 12) h = 0
        if (h > 23 || min > 59) return false
        val text = m.groupValues[4].trim()
        if (text.isEmpty()) return false
        plan = (plan + "${h * 60 + min}|$text").sortedBy { it.substringBefore("|").toIntOrNull() ?: 0 }
        sp.edit().putString("plan", plan.joinToString("\u0001")).apply()
        return true
    }
    fun removePlan(index: Int) {
        plan = plan.filterIndexed { n, _ -> n != index }
        sp.edit().putString("plan", plan.joinToString("\u0001")).apply()
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
        val hum = if (i("wh") == 1 && wHum >= 0) "  💧 $wHum%" else ""
        return "${weatherEmoji(wCode)} ${v.roundToInt()}°${if (f) "F" else "C"}$hum"
    }

    fun weatherTemp(): String? {
    val t = tempC ?: return null
    val f = i("useF") == 1
    val v = if (f) t * 9 / 5 + 32 else t
    return "${v.roundToInt()}°${if (f) "F" else "C"}"
}

fun weatherHumidity(): String? = if (wHum >= 0) "$wHum%" else null

fun weatherWord(): String = when (wCode) {
    0 -> "Clear"
    1, 2 -> "Partly Cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    in 95..99 -> "Thunderstorm"
    else -> ""
}

    // Weather: "wmode" 0 = automatic (phone location), 1 = the city typed in settings
    @SuppressLint("MissingPermission")
    fun refreshWeather() {
        val manual = i("wmode") == 1
        if (!manual && app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var lat = 0.0
                var lon = 0.0
                if (manual) {
                    if (!sp.contains("clat") || !sp.contains("clon")) return@launch
                    lat = sp.getString("clat", "")!!.toDouble()
                    lon = sp.getString("clon", "")!!.toDouble()
                } else {
                    val lm = app.getSystemService(LocationManager::class.java)
                    val loc = findLocation(lm) ?: return@launch
                    lat = loc.latitude
                    lon = loc.longitude
                }
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat" +
                    "&longitude=$lon&current=temperature_2m,weather_code,relative_humidity_2m"
                val cur = JSONObject(URL(url).readText()).getJSONObject("current")
                val t = cur.getDouble("temperature_2m")
                val c = cur.getInt("weather_code")
                val h = cur.optInt("relative_humidity_2m", -1)
                withContext(Dispatchers.Main) { tempC = t; wCode = c; wHum = h }
            } catch (e: Exception) {
            }
        }
    }

    // Looks up a city by name, saves it, and switches the weather to Manual
    fun setCity(q: String) {
        val name = q.trim()
        if (name.isEmpty()) { cityMsg = "Type a city name first"; return }
        cityMsg = "Searching..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + Uri.encode(name)
                val arr = JSONObject(URL(url).readText()).optJSONArray("results")
                if (arr == null || arr.length() == 0) {
                    withContext(Dispatchers.Main) { cityMsg = "City not found" }
                    return@launch
                }
                val r = arr.getJSONObject(0)
                val country = r.optString("country")
                val label = r.getString("name") + (if (country.isNotEmpty()) ", $country" else "")
                sp.edit()
                    .putString("clat", r.getDouble("latitude").toString())
                    .putString("clon", r.getDouble("longitude").toString())
                    .putString("cname", label)
                    .apply()
                withContext(Dispatchers.Main) {
                    cityName = label
                    cityMsg = "Saved: $label"
                    setI("wmode", 1)
                    refreshWeather()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { cityMsg = "Could not search. Check your internet." }
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
