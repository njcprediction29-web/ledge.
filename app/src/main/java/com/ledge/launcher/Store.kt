package com.ledge.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class AppItem(val label: String, val pkg: String, val cls: String)

sealed class Entry {
    data class Head(val c: Char) : Entry()
    data class App(val app: AppItem) : Entry()
}

fun buildEntries(apps: List<AppItem>): List<Entry> {
    val out = ArrayList<Entry>()
    var last: Char? = null
    for (a in apps) {
        val c = a.label.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar() ?: '#'
        if (c != last) { out.add(Entry.Head(c)); last = c }
        out.add(Entry.App(a))
    }
    return out
}

class Pal(val text: Color, val sub: Color, val card: Color, val scrim: Color, val accent: Color)

val ACCENTS = listOf(
    Color(0xFFFFB27D), Color(0xFF7CC4FF), Color(0xFF7DE0B0), Color(0xFFFF8FA3), Color(0xFFB9A4FF)
)
val LETTER_SP = listOf(11, 14, 17, 19)   // A-Z rail sizes: Small, Medium, Large, Extra large

fun palette(theme: Int, accent: Int): Pal {
    val a = ACCENTS[accent.coerceIn(0, ACCENTS.lastIndex)]
    return when (theme) {
        1 -> Pal(Color(0xFF1B1B1F), Color(0xFF55555C), Color.White, Color(0x80FFFFFF), a)      // Light
        2 -> Pal(Color.White, Color(0xFFB5B5BC), Color(0xFF17171A), Color.Black, a)            // Black
        else -> Pal(Color.White, Color(0xFFD6D6DC), Color(0xFF1F1F24), Color(0x66000000), a)   // Dark
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

    var apps by mutableStateOf<List<AppItem>>(emptyList()); private set
    var hidden by mutableStateOf(sp.getStringSet("hidden", emptySet())!!.toSet()); private set
    var favs by mutableStateOf(sp.getString("favs", "")!!.split("|").filter { it.isNotEmpty() }); private set
    var fontLevel by mutableIntStateOf(sp.getInt("font", 1)); private set
    var theme by mutableIntStateOf(sp.getInt("theme", 0)); private set
    var accent by mutableIntStateOf(sp.getInt("accent", 0)); private set
    var useF by mutableStateOf(sp.getBoolean("useF", false)); private set
    var todos by mutableStateOf(sp.getString("todos", "")!!.split("\u0001").filter { it.isNotEmpty() }); private set
    var tempC by mutableStateOf<Double?>(null); private set
    var wCode by mutableIntStateOf(0); private set
    var homeTick by mutableIntStateOf(0)

    fun load() {
        val pm = app.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(i, 0)
            .map { AppItem(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .filter { it.pkg != app.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
        if (list != apps) { iconCache.clear(); apps = list }
    }

    fun toggleHidden(pkg: String) {
        hidden = if (pkg in hidden) hidden - pkg else hidden + pkg
        sp.edit().putStringSet("hidden", hidden).apply()
    }
    fun toggleFav(pkg: String) {
        favs = if (pkg in favs) favs - pkg else favs + pkg
        sp.edit().putString("favs", favs.joinToString("|")).apply()
    }
    fun setFont(v: Int) { fontLevel = v; sp.edit().putInt("font", v).apply() }
   @JvmName("applyTheme") fun setTheme(v: Int) { theme = v; sp.edit().putInt("theme", v).apply() }
   @JvmName("applyAccent") fun setAccent(v: Int) { accent = v; sp.edit().putInt("accent", v).apply() }
   @JvmName("applyUseF") fun setUseF(v: Boolean) { useF = v; sp.edit().putBoolean("useF", v).apply() }
    fun addTodo(t: String) {
        if (t.isBlank()) return
        todos = todos + t.trim()
        sp.edit().putString("todos", todos.joinToString("\u0001")).apply()
    }
    fun doneTodo(index: Int) {
        todos = todos.filterIndexed { i, _ -> i != index }
        sp.edit().putString("todos", todos.joinToString("\u0001")).apply()
    }

    fun weatherText(): String? {
        val t = tempC ?: return null
        val v = if (useF) t * 9 / 5 + 32 else t
        return "${weatherEmoji(wCode)} ${v.roundToInt()}°${if (useF) "F" else "C"}"
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
