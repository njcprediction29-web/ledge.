package com.ledge.launcher

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

fun reducedMotion(ctx: Context): Boolean = try {
    Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
} catch (e: Exception) { false }

@Composable
fun Fold(title: String, pal: Pal, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp))
            .background(pal.text.copy(alpha = .08f))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (open) "▲" else "▼", color = pal.sub, fontSize = 12.sp)
        }
        if (open) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), content = content)
    }
}

@Composable
fun SettingsSections(s: Store, pal: Pal, onPick: () -> Unit, onLoc: () -> Unit) {
    val ctx = LocalContext.current
    var torch by remember { mutableStateOf(false) }
    var draftWp by remember { mutableIntStateOf(s.i("wp")) }
    var draftDir by remember { mutableIntStateOf(s.i("wdir")) }
    LaunchedEffect(s.i("wp")) { draftWp = s.i("wp") }
    val clockNow = remember { clockStrings(ctx, System.currentTimeMillis()) }

    Column(Modifier.padding(horizontal = 18.dp)) {
        Text("Lumen settings", color = pal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Fold("🏠 Home", pal, true) {
            SectionLabel("Clean Mode", pal)
            Seg(listOf("Off", "On"), s.i("clean"), pal) { s.setI("clean", it) }
            SectionLabel("Home position (clock + favorites)", pal)
            Seg(listOf("High", "Medium", "Low"), s.i("hy"), pal) { s.setI("hy", it) }
            SectionLabel("Show on home", pal)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Weather", s.i("sw") == 1, pal, Modifier.weight(1f)) { s.setI("sw", 1 - s.i("sw")) }
                Chip("Battery", s.i("sb") == 1, pal, Modifier.weight(1f)) { s.setI("sb", 1 - s.i("sb")) }
                Chip("Verse", s.i("sv") == 1, pal, Modifier.weight(1f)) { s.setI("sv", 1 - s.i("sv")) }
            }
            Spacer(Modifier.size(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("To-do", s.i("st") == 1, pal, Modifier.weight(1f)) { s.setI("st", 1 - s.i("st")) }
                Chip("Calendar", s.i("hc") == 0, pal, Modifier.weight(1.3f)) { s.setI("hc", 1 - s.i("hc")) }
                Chip("Notifications", s.i("sn") == 1, pal, Modifier.weight(1.7f)) { s.setI("sn", 1 - s.i("sn")) }
            }
        }

        Fold("🕐 Clock", pal) {
            SectionLabel("Live preview", pal)
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.text.copy(alpha = .12f)).padding(16.dp),
                contentAlignment = if (s.i("calign") == 1) Alignment.Center else Alignment.CenterStart
            ) { ClockFace(s, pal, clockNow.first, clockNow.second) }
            SectionLabel("Style (tap one)", pal)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CLOCKS.forEachIndexed { n, _ -> ClockTile(s, pal, n, s.i("cstyle") == n) { s.setI("cstyle", n) } }
            }
            SectionLabel("Size", pal)
            Seg(listOf("Small", "Medium", "Large", "XL"), s.i("csize"), pal) { s.setI("csize", it) }
            SectionLabel("Weight", pal)
            Seg(listOf("Style", "Light", "Regular", "Bold"), s.i("cweight"), pal) { s.setI("cweight", it) }
            SectionLabel("Color", pal)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (n in 0..6) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(clockColor(n, pal))
                            .then(if (s.i("ccolor") == n) Modifier.border(3.dp, pal.accent, CircleShape) else Modifier.border(1.dp, pal.sub, CircleShape))
                            .clickable { s.setI("ccolor", n) }
                    )
                }
            }
            SectionLabel("Position", pal)
            Seg(listOf("Left", "Center"), s.i("calign"), pal) { s.setI("calign", it) }
        }

        Fold("🖼️ Wallpaper", pal) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { WallPreview(s, pal, draftWp, draftDir) }
            SectionLabel("Gradients", pal)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PACKS.forEachIndexed { n, p -> Tile(p.name, wallBrush(n, 1), draftWp == n, pal) { draftWp = n } }
            }
            SectionLabel("Solid colors", pal)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SOLIDS.forEachIndexed { n, c -> Tile(c.name, wallBrush(200 + n, 1), draftWp == 200 + n, pal) { draftWp = 200 + n } }
            }
            SectionLabel("System or your own photo", pal)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile("System", null, draftWp == -1, pal) { draftWp = -1 }
                Tile("📷 My photo", null, s.i("wp") == 100, pal) { onPick() }
            }
            SectionLabel("Gradient direction", pal)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Diagonal", "Down", "Across").forEachIndexed { n, l -> Chip(l, draftDir == n, pal, Modifier.weight(1f)) { draftDir = n } }
            }
            Spacer(Modifier.size(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Reverse", "Glow").forEachIndexed { k, l -> Chip(l, draftDir == k + 3, pal, Modifier.weight(1f)) { draftDir = k + 3 } }
            }
            Text("☀ wallpapers look best with the Light theme. Your own photo is applied right away.", color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.size(10.dp))
            Chip("✓ Apply wallpaper", true, pal, Modifier.fillMaxWidth()) { s.setI("wp", draftWp); s.setI("wdir", draftDir) }
        }

        Fold("🧩 Home widgets", pal) {
            Text("Hold ☰ and drag a widget up or down to change the order. Tap Shown/Hidden to show or hide it.", color = pal.sub, fontSize = 12.sp)
            val dens = LocalDensity.current.density
            val rowPx = 56f * dens
            var dragW by remember { mutableStateOf<String?>(null) }
            var dragDy by remember { mutableFloatStateOf(0f) }
            Spacer(Modifier.size(8.dp))
            s.widgetOrder.forEach { w ->
                key(w) {
                    val title = when (w) {
                        "clock" -> "🕐 Clock and weather"
                        "calendar" -> "📅 Calendar"
                        "plan" -> "🗓️ Plan your day"
                        "music" -> "🎵 Music player"
                        else -> "⭐ Favorites"
                    }
                    val hideKey = when (w) {
                        "calendar" -> "hc"
                        "plan" -> "hp"
                        "music" -> "hm"
                        "favs" -> "hf"
                        else -> ""
                    }
                    val lifted = dragW == w
                    Box(
                        Modifier.fillMaxWidth().height(56.dp).zIndex(if (lifted) 1f else 0f)
                            .graphicsLayer { translationY = if (lifted) dragDy else 0f }
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (lifted) pal.accent.copy(alpha = .35f) else pal.text.copy(alpha = .12f))
                    ) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(52.dp, 48.dp).pointerInput(w) {
                                    detectDragGestures(
                                        onDragStart = { dragW = w; dragDy = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDy += amount.y
                                            val i = s.widgetOrder.indexOf(w)
                                            if (dragDy > rowPx / 2 && i < s.widgetOrder.lastIndex) {
                                                s.moveWidget(i, i + 1); dragDy -= rowPx
                                            } else if (dragDy < -rowPx / 2 && i > 0) {
                                                s.moveWidget(i, i - 1); dragDy += rowPx
                                            }
                                        },
                                        onDragEnd = { dragW = null; dragDy = 0f },
                                        onDragCancel = { dragW = null; dragDy = 0f }
                                    )
                                },
                                contentAlignment = Alignment.Center
                            ) { Text("☰", color = pal.text, fontSize = 22.sp) }
                            Text(title, color = pal.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            if (hideKey.isNotEmpty()) {
                                Chip(
                                    if (s.i(hideKey) == 0) "Shown" else "Hidden", s.i(hideKey) == 0, pal,
                                    Modifier.width(84.dp).padding(end = 6.dp)
                                ) { s.setI(hideKey, 1 - s.i(hideKey)) }
                            } else {
                                Text("Always on", color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
                            }
                        }
                    }
                }
            }
        }

        Fold("🎵 Music box", pal) {
            Text("The music box appears when you play music, stays (paused) while the song is paused, and goes away when you close the music app.", color = pal.sub, fontSize = 12.sp)
            SectionLabel("Design", pal)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MUSIC_STYLES.forEachIndexed { n, name ->
                    Chip(name, s.i("mstyle") == n, pal, Modifier.width(84.dp)) { s.setI("mstyle", n) }
                }
            }
            SectionLabel("Preview", pal)
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(pal.text.copy(alpha = .12f)).padding(14.dp)) {
                MusicView(s.i("mstyle"), "Song title", "Artist name", null, true, 0.4f, pal, {}, {}, {}, {})
            }
        }

        Fold("🌤️ Weather", pal) {
            SectionLabel("Location", pal)
            Seg(listOf("Automatic", "My city"), s.i("wmode"), pal) { s.setI("wmode", it); s.refreshWeather() }
            if (s.i("wmode") == 1) {
                var cityIn by remember { mutableStateOf("") }
                SectionLabel("City" + if (s.cityName.isNotEmpty()) " (now: ${s.cityName})" else "", pal)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.text.copy(alpha = .12f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        cityIn, { cityIn = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = pal.text, fontSize = 16.sp), cursorBrush = SolidColor(pal.accent),
                        decorationBox = { inner ->
                            if (cityIn.isEmpty()) Text("Type a city, e.g. Manila", color = pal.sub, fontSize = 16.sp)
                            inner()
                        }
                    )
                }
                Spacer(Modifier.size(8.dp))
                Chip("Search and save city", true, pal, Modifier.fillMaxWidth()) { s.setCity(cityIn) }
                if (s.cityMsg.isNotEmpty()) Text(s.cityMsg, color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
            SectionLabel("Humidity", pal)
            Seg(listOf("Hidden", "Shown"), s.i("wh"), pal) { s.setI("wh", it) }
        }

        Fold("🔷 Icons and labels", pal) {
            s.apps.firstOrNull()?.let { AppRow(it, pal, s.i("icon"), 0, null, {}, {}) }
            SectionLabel("Icon style", pal)
            Seg(listOf("Color", "B&W", "Simple", "Lumen"), s.i("icon"), pal) { s.setI("icon", it) }
            SectionLabel("Icon shape", pal)
            Seg(listOf("Default", "Circle", "Soft", "Square"), s.i("ishape"), pal) { s.setI("ishape", it) }
            SectionLabel("Icon size", pal)
            Seg(listOf("Small", "Medium", "Large"), s.i("isize"), pal) { s.setI("isize", it) }
            SectionLabel("Icon spacing", pal)
            Seg(listOf("Tight", "Normal", "Roomy"), s.i("ispace"), pal) { s.setI("ispace", it) }
            SectionLabel("App labels", pal)
            Seg(listOf("Hidden", "Shown"), s.i("labels"), pal) { s.setI("labels", it) }
            SectionLabel("Label size", pal)
            Seg(listOf("Small", "Medium", "Large"), s.i("lsize"), pal) { s.setI("lsize", it) }
            SectionLabel("Label weight", pal)
            Seg(listOf("Light", "Normal", "Bold"), s.i("lweight"), pal) { s.setI("lweight", it) }
            SectionLabel("Label font", pal)
            Seg(listOf("Default", "Serif", "Mono", "Script"), s.i("lfont"), pal) { s.setI("lfont", it) }
            Seg(listOf("Martian"), s.i("lfont") - 6, pal) { s.setI("lfont", it + 6) }
        }

        Fold("🔤 A–Z letters", pal) {
            SectionLabel("Letter size", pal)
            Seg(listOf("Small", "Medium", "Large"), s.i("font"), pal) { s.setI("font", it) }
            SectionLabel("Side", pal)
            Seg(listOf("Right", "Left"), s.i("side"), pal) { s.setI("side", it) }
            SectionLabel("Height", pal)
            Seg(listOf("Short", "Medium", "Tall"), s.i("rh"), pal) { s.setI("rh", it) }
            SectionLabel("Position", pal)
            Seg(listOf("Up", "Middle", "Down"), s.i("pos"), pal) { s.setI("pos", it) }
        }

        Fold("🎨 Theme", pal) {
            SectionLabel("Theme", pal)
            Seg(listOf("Dark", "Light", "Black"), s.i("theme"), pal) { s.setI("theme", it) }
            SectionLabel("Accent color", pal)
                    AccentGrid(s, pal)
        if (false) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ACCENTS.forEachIndexed { n, c ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c)
                            .then(if (s.i("accent") == n) Modifier.border(3.dp, pal.text, CircleShape) else Modifier)
                            .clickable { s.setI("accent", n) }
                    )
                }
            }
            SectionLabel("Temperature", pal)
            Seg(listOf("°C", "°F"), s.i("useF"), pal) { s.setI("useF", it) }
        }

        Fold("⚙️ Tools, permissions, hidden apps", pal) {
            SectionLabel("Quick settings", pal)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("📶 Wi-Fi", false, pal, Modifier.weight(1f)) { go(ctx, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }
                Chip("🔵 BT", false, pal, Modifier.weight(1f)) { go(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                Chip("🔦 Light", torch, pal, Modifier.weight(1f)) {
                    try {
                        val cm = ctx.getSystemService(CameraManager::class.java)
                        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                        if (id != null) { torch = !torch; cm.setTorchMode(id, torch) }
                    } catch (e: Exception) { torch = false }
                }
            }
            SectionLabel("Permissions", pal)
            MenuRow("📍", "Allow location (weather)", pal) { onLoc() }
            MenuRow("🔔", "Turn on notification badges and music box", pal) { go(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            Text(
                "If Android says \"restricted setting\": Settings > Apps > Lumen > ⋮ (top right) > Allow restricted settings, then try again.",
                color = pal.sub, fontSize = 12.sp
            )
            val hiddenApps = s.apps.filter { it.pkg in s.hidden }
            SectionLabel("Hidden apps (tap to unhide)", pal)
            if (hiddenApps.isEmpty()) Text("None. Long-press an app and choose Hide app.", color = pal.sub, fontSize = 14.sp)
            hiddenApps.forEach { a -> MenuRow("👁️", a.label, pal) { s.toggleHidden(a.pkg) } }
        }
        Spacer(Modifier.size(10.dp))
    }
}
