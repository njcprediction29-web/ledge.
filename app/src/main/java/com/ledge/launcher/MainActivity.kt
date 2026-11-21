package com.ledge.launcher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val store by viewModels<Store>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
        setContent { Home(store) }
    }

    override fun onResume() {
        super.onResume()
        store.load()
        store.refreshWeather()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        store.homeTick++
    }
}

fun go(ctx: Context, i: Intent) {
    try { ctx.startActivity(i) } catch (e: Exception) { }
}

fun launchApp(ctx: Context, a: AppItem) = go(
    ctx,
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(a.pkg, a.cls)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
)

data class Contact(val name: String, val uri: Uri)

fun searchContacts(ctx: Context, q: String): List<Contact> {
    val out = ArrayList<Contact>()
    try {
        ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME),
            "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?", arrayOf("%$q%"),
            "${ContactsContract.Contacts.DISPLAY_NAME} ASC"
        )?.use { c ->
            while (c.moveToNext() && out.size < 4) {
                out.add(Contact(c.getString(2) ?: "", ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))))
            }
        }
    } catch (e: Exception) { }
    return out
}

val SH = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 6f))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Home(s: Store) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val pal = palette(s.i("theme"), s.i("accent"))
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current

    var menuApp by remember { mutableStateOf<AppItem?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var verseOpen by remember { mutableStateOf(false) }
    var todoOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var from by remember { mutableStateOf<Char?>(null) }
    var active by remember { mutableStateOf<Char?>(null) }
    var bubY by remember { mutableFloatStateOf(0f) }
    var hasContacts by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val locL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { s.refreshWeather() }
    val conL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasContacts = it }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) s.setPhoto(u) }
    val overlay = menuApp != null || settingsOpen || verseOpen || todoOpen

    fun closeSearch() { query = ""; searchOpen = false; focus.clearFocus() }

    LaunchedEffect(Unit) {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            locL.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    LaunchedEffect(s.homeTick) {
        if (s.homeTick > 0) {
            menuApp = null; settingsOpen = false; verseOpen = false; todoOpen = false; closeSearch(); from = null
        }
    }
    LaunchedEffect(overlay) {
        if (Build.VERSION.SDK_INT >= 31) {
            try { act.window.setBackgroundBlurRadius(if (overlay) 70 else 0) } catch (e: Exception) { }
        }
    }
    LaunchedEffect(active) {
        if (active != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    BackHandler(overlay || searchOpen || from != null) {
        when {
            menuApp != null -> menuApp = null
            settingsOpen -> settingsOpen = false
            verseOpen -> verseOpen = false
            todoOpen -> todoOpen = false
            searchOpen -> closeSearch()
            else -> from = null
        }
    }

    val vis = s.apps.filter { it.pkg !in s.hidden }
    val groups = remember(s.apps, s.hidden) { vis.groupBy { letterOf(it) } }
    val groupLetters = remember(groups) { groups.keys.filter { it != '#' }.sorted() }
    val allTop = (LocalConfiguration.current.screenHeightDp * 0.27f).dp
    val shownGroup = from?.let { groupFor(groupLetters, groups.containsKey('#'), it) }
    val homeState = rememberLazyListState()
    val allState = rememberLazyListState()
    LaunchedEffect(shownGroup) { allState.scrollToItem(0) }
    val favApps = s.favs.mapNotNull { p -> vis.firstOrNull { it.pkg == p } }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val left = s.i("side") == 1
    val icon = s.i("icon")
    val clean = s.i("clean") == 1
    val homeTop = floatArrayOf(14f, 90f, 170f)[s.i("hy").coerceIn(0, 2)].dp
    val showN = s.i("sn") == 1 && !clean
    val wp = s.i("wp")
    val wb = if (overlay) Modifier.blur(18.dp, BlurredEdgeTreatment.Unbounded) else Modifier

    Box(Modifier.fillMaxSize()) {
        // wallpaper
        if (wp in PACKS.indices) {
            Box(Modifier.fillMaxSize().then(wb).background(Brush.linearGradient(PACKS[wp].colors, Offset.Zero, Offset(1200f, 2600f))))
        } else if (wp == 100) {
            s.wallBmp?.let { Image(it, null, Modifier.fillMaxSize().then(wb), contentScale = ContentScale.Crop) }
        }
        Box(Modifier.fillMaxSize().background(pal.scrim))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = s.i("dim") / 100f * 0.65f)))

        Box(Modifier.fillMaxSize().then(if (overlay) Modifier.blur(18.dp) else Modifier)) {
            Crossfade(targetState = from == null, animationSpec = tween(120), label = "homeOrAll") { isHome ->
                if (isHome) {
                    LazyColumn(
                        state = homeState,
                        modifier = Modifier.fillMaxSize().imePadding(),
                        contentPadding = PaddingValues(
                            top = top + homeTop, bottom = bottom + 40.dp,
                            start = if (left) 46.dp else 20.dp, end = if (left) 20.dp else 46.dp
                        )
                    ) {
                        item(key = "head") {
                            HeaderBlock(s, pal, { locL.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                                { settingsOpen = true }, { verseOpen = true }, { todoOpen = true })
                        }
                        items(favApps, key = { "f" + it.pkg }) { a ->
                            FavRow(a, pal, icon, if (showN) Badges.counts[a.pkg] ?: 0 else 0,
                                if (showN) Badges.last[a.pkg] else null, { launchApp(ctx, a) }) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuApp = a
                            }
                        }
                        if (!clean) {
                            item(key = "hint") {
                                Text(
                                    if (favApps.isEmpty()) "Slide the letters to find apps. Long-press an app to add favorites."
                                    else "Slide the letters to see all apps",
                                    color = pal.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp, start = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    val g = shownGroup
                    val list = if (g != null) groups[g].orEmpty() else emptyList()
                    LazyColumn(
                        state = allState,
                        modifier = Modifier.fillMaxSize().imePadding(),
                        contentPadding = PaddingValues(
                            top = top + allTop, bottom = bottom + 40.dp,
                            start = if (left) 46.dp else 20.dp, end = if (left) 20.dp else 46.dp
                        )
                    ) {
                        if (g != null) {
                            item(key = "gh$g") {
                                Text(
                                    g.toString(), color = pal.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                                )
                            }
                            items(list, key = { "g" + it.pkg }) { a ->
                                AppRow(a, pal, icon, if (showN) Badges.counts[a.pkg] ?: 0 else 0, null,
                                    { launchApp(ctx, a) }) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuApp = a
                                }
                            }
                        }
                    }
                }
            }

            val h = floatArrayOf(0.6f, 0.72f, 0.84f)[s.i("rh").coerceIn(0, 2)]
            val bias = floatArrayOf(-1f, 0f, 1f)[s.i("pos").coerceIn(0, 2)]
            Box(Modifier.fillMaxSize().padding(top = top + 56.dp, bottom = bottom + 40.dp)) {
                Rail(
                    LETTER_SP[s.i("font").coerceIn(0, 2)], pal, left, active,
                    Modifier.align(BiasAlignment(if (left) -1f else 1f, bias)).fillMaxHeight(h),
                    onTouch = { c, y ->
                        bubY = y
                        if (c != active) {
                            active = c
                            if (c == '★') from = null
                            else from = c
                        }
                    },
                    onEnd = { active = null }
                )
            }
            // the blue letter bubble: not shown for the star (Favorites)
            active?.takeIf { it != '★' }?.let { letter ->
                Box(
                    Modifier.align(if (left) Alignment.TopStart else Alignment.TopEnd)
                        .padding(start = if (left) 134.dp else 0.dp, end = if (left) 0.dp else 134.dp)
                        .offset { IntOffset(0, (bubY - 27.dp.toPx()).roundToInt()) }
                        .size(54.dp).clip(CircleShape).background(pal.accent),
                    contentAlignment = Alignment.Center
                ) { Text(letter.toString(), color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            }

            if (!clean || searchOpen) {
                SearchBar(s, pal, top, icon, searchOpen, query, hasContacts,
                    onOpen = { searchOpen = true }, onQuery = { query = it }, onClose = { closeSearch() },
                    onAskContacts = { conL.launch(Manifest.permission.READ_CONTACTS) })
            }
        }

        menuApp?.let { a ->
            val isFav = a.pkg in s.favs
            Popup(pal, { menuApp = null }) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a, icon, pal, 56.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(a.label, color = pal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val n = Badges.counts[a.pkg] ?: 0
                        Text(if (n > 0) "$n notification${if (n > 1) "s" else ""}" else "App options", color = pal.sub, fontSize = 14.sp)
                    }
                }
                MenuRow("⭐", if (isFav) "Remove from favorites" else "Add to favorites", pal) { s.toggleFav(a.pkg); menuApp = null }
                MenuRow("🙈", "Hide app", pal) { s.toggleHidden(a.pkg); menuApp = null }
                MenuRow("ℹ️", "App info", pal) {
                    go(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}"))); menuApp = null
                }
                MenuRow("🗑️", "Uninstall", pal, Color(0xFFFF6B6B)) {
                    go(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}"))); menuApp = null
                }
            }
        }

        if (verseOpen) {
            val v = s.verse()
            Popup(pal, { verseOpen = false }) {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Text("📖 ${v.first}", color = pal.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("“${v.second}”", color = pal.text, fontSize = 17.sp, fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(vertical = 12.dp))
                    Text("King James Version", color = pal.sub, fontSize = 12.sp)
                    Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("‹ Previous", false, pal, Modifier.weight(1f)) { s.vOff-- }
                        Chip(if (v.first in s.vFav) "♥ Saved" else "♡ Save", v.first in s.vFav, pal, Modifier.weight(1f)) { s.toggleVFav(v.first) }
                        Chip("Next ›", false, pal, Modifier.weight(1f)) { s.vOff++ }
                    }
                }
            }
        }

        if (todoOpen) {
            var nt by remember { mutableStateOf("") }
            Popup(pal, { todoOpen = false }) {
                Column(Modifier.padding(horizontal = 22.dp)) {
                    Text("✓ To-do", color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    s.todos.forEachIndexed { n, t ->
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(26.dp).clip(CircleShape).border(2.dp, pal.accent, CircleShape).clickable { s.doneTodo(n) })
                            Spacer(Modifier.width(12.dp))
                            Text(t, color = pal.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        }
                    }
                    BasicTextField(
                        nt, { nt = it }, modifier = Modifier.padding(top = 16.dp).fillMaxWidth(), singleLine = true,
                        textStyle = TextStyle(color = pal.text, fontSize = 16.sp), cursorBrush = SolidColor(pal.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { s.addTodo(nt); nt = "" }),
                        decorationBox = { inner ->
                            if (nt.isEmpty()) Text("＋ Add a task, then press Done", color = pal.sub, fontSize = 16.sp)
                            inner()
                        }
                    )
                }
            }
        }

        if (settingsOpen) {
            Popup(pal, { settingsOpen = false }) {
                SettingsBody(s, pal, { pick.launch("image/*") }, { locL.launch(Manifest.permission.ACCESS_COARSE_LOCATION) })
            }
        }
    }
}

@Composable
fun SettingsBody(s: Store, pal: Pal, onPick: () -> Unit, onLoc: () -> Unit) {
    val ctx = LocalContext.current
    var torch by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 22.dp)) {
        Text("Lumen settings", color = pal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        SectionLabel("Clean Mode", pal)
        Seg(listOf("Off", "On"), s.i("clean"), pal) { s.setI("clean", it) }

        SectionLabel("Home position (clock + favorites)", pal)
        Seg(listOf("High", "Medium", "Low"), s.i("hy"), pal) { s.setI("hy", it) }

        SectionLabel("Wallpaper", pal)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("System", null, s.i("wp") == -1, pal) { s.setI("wp", -1) }
            PACKS.forEachIndexed { n, p -> Tile(p.name, Brush.verticalGradient(p.colors), s.i("wp") == n, pal) { s.setI("wp", n) } }
            Tile("📷 My photo", null, s.i("wp") == 100, pal) { onPick() }
        }
        SectionLabel("Dim: ${s.i("dim")}%", pal)
        Slider(
            value = s.i("dim").toFloat(), onValueChange = { s.setI("dim", it.roundToInt()) }, valueRange = 0f..70f,
            colors = SliderDefaults.colors(thumbColor = pal.accent, activeTrackColor = pal.accent)
        )

        SectionLabel("Icon style", pal)
        Seg(listOf("Color", "Black & white", "Simple"), s.i("icon"), pal) { s.setI("icon", it) }

        SectionLabel("Show on home", pal)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Weather", s.i("sw") == 1, pal, Modifier.weight(1f)) { s.setI("sw", 1 - s.i("sw")) }
            Chip("Battery", s.i("sb") == 1, pal, Modifier.weight(1f)) { s.setI("sb", 1 - s.i("sb")) }
            Chip("Verse", s.i("sv") == 1, pal, Modifier.weight(1f)) { s.setI("sv", 1 - s.i("sv")) }
        }
        Spacer(Modifier.size(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("To-do", s.i("st") == 1, pal, Modifier.weight(1f)) { s.setI("st", 1 - s.i("st")) }
            Chip("Notifications", s.i("sn") == 1, pal, Modifier.weight(2f)) { s.setI("sn", 1 - s.i("sn")) }
        }

        SectionLabel("A–Z letter size", pal)
        Seg(listOf("Small", "Medium", "Large"), s.i("font"), pal) { s.setI("font", it) }
        SectionLabel("A–Z side", pal)
        Seg(listOf("Right", "Left"), s.i("side"), pal) { s.setI("side", it) }
        SectionLabel("A–Z height", pal)
        Seg(listOf("Short", "Medium", "Tall"), s.i("rh"), pal) { s.setI("rh", it) }
        SectionLabel("A–Z position", pal)
        Seg(listOf("Up", "Middle", "Down"), s.i("pos"), pal) { s.setI("pos", it) }

        SectionLabel("Theme", pal)
        Seg(listOf("Dark", "Light", "Black"), s.i("theme"), pal) { s.setI("theme", it) }
        SectionLabel("Accent color", pal)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        MenuRow("🔔", "Turn on notification badges", pal) { go(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        Text(
            "If Android says \"restricted setting\": Settings > Apps > Lumen > ⋮ (top right) > Allow restricted settings, then try again.",
            color = pal.sub, fontSize = 12.sp
        )

        val hiddenApps = s.apps.filter { it.pkg in s.hidden }
        SectionLabel("Hidden apps (tap to unhide)", pal)
        if (hiddenApps.isEmpty()) Text("None. Long-press an app and choose Hide app.", color = pal.sub, fontSize = 14.sp)
        hiddenApps.forEach { a -> MenuRow("👁️", a.label, pal) { s.toggleHidden(a.pkg) } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeaderBlock(s: Store, pal: Pal, onWeather: () -> Unit, onSettings: () -> Unit, onVerse: () -> Unit, onTodo: () -> Unit) {
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(10_000) } }
    val battery by produceState(-1) {
        while (true) {
            value = try { ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) } catch (e: Exception) { -1 }
            delay(30_000)
        }
    }
    val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
    val time = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(now))
    val verse = s.verse()
    val weather = s.weatherText()
    val clean = s.i("clean") == 1

    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(
            time, color = pal.text, fontSize = 34.sp, fontWeight = FontWeight.Light, style = SH,
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onSettings)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, color = pal.sub, fontSize = 12.sp, style = SH)
            if (s.i("sw") == 1) {
                Text(
                    "  ·  ${weather ?: "🌡️ tap for weather"}", color = pal.sub, fontSize = 12.sp, style = SH,
                    modifier = Modifier.clickable { if (weather == null) onWeather() }
                )
            }
            if (s.i("sb") == 1 && battery >= 0) Text("  ·  🔋 $battery%", color = pal.sub, fontSize = 12.sp, style = SH)
        }
        if (!clean) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.i("sv") == 1) SmallChip("📖 ${verse.first}", pal, onVerse)
                if (s.i("st") == 1) SmallChip("✓ ${s.todos.size} task${if (s.todos.size == 1) "" else "s"}", pal, onTodo)
            }
        }
    }
}

@Composable
fun SmallChip(text: String, pal: Pal, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(99.dp)).background(pal.text.copy(alpha = .16f)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(text, color = pal.text, fontSize = 11.sp, maxLines = 1) }
}

@Composable
fun Popup(pal: Pal, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x59000000))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(24.dp).fillMaxWidth().heightIn(max = 640.dp).clip(RoundedCornerShape(28.dp)).background(pal.card)
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState()).padding(vertical = 14.dp),
            content = content
        )
    }
}

@Composable
fun AppIcon(a: AppItem, style: Int, pal: Pal, size: androidx.compose.ui.unit.Dp = 42.dp) {
    val ctx = LocalContext.current
    val bmp = remember(a.pkg) { loadIcon(ctx, a) }
    val gray = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    when (style) {
        0 -> Image(bmp, null, Modifier.size(size))
        1 -> Image(bmp, null, Modifier.size(size), colorFilter = gray)
        else -> Box(Modifier.size(size).border(1.5.dp, pal.text, RoundedCornerShape(12.dp)).padding(6.dp)) {
            Image(bmp, null, Modifier.fillMaxSize(), colorFilter = gray, alpha = 0.9f)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(a: AppItem, pal: Pal, icon: Int, badge: Int, sub: String?, onOpen: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onMenu).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(a, icon, pal)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(a.label, color = pal.text, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = SH)
            if (!sub.isNullOrBlank()) Text(sub, color = pal.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (badge > 0) Box(
            Modifier.defaultMinSize(22.dp, 22.dp).clip(CircleShape).background(pal.accent).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) { Text(if (badge > 99) "99+" else "$badge", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    }
}

fun letterOf(a: AppItem): Char = a.label.firstOrNull()?.takeIf { it.isLetter() }?.uppercaseChar() ?: '#'

// Which letter group to show for the rail letter: the same letter, or the next one that has apps (like Niagara)
fun groupFor(letters: List<Char>, hasHash: Boolean, c: Char): Char? {
    if (c == '#') return if (hasHash) '#' else letters.lastOrNull()
    return letters.firstOrNull { it >= c } ?: letters.lastOrNull()
}

// Favorites row: same as AppRow, nudged a little toward the middle of the screen
@Composable
fun FavRow(a: AppItem, pal: Pal, icon: Int, badge: Int, sub: String?, onOpen: () -> Unit, onMenu: () -> Unit) {
    Box(Modifier.padding(start = 28.dp)) { AppRow(a, pal, icon, badge, sub, onOpen, onMenu) }
}

@Composable
fun Title(t: String, pal: Pal) {
    Text(t, color = pal.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 2.dp, start = 4.dp))
}

@Composable
fun SectionLabel(t: String, pal: Pal) {
    Text(t, color = pal.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
}

@Composable
fun MenuRow(icon: String, label: String, pal: Pal, color: Color = pal.text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(16.dp))
        Text(label, color = color, fontSize = 17.sp)
    }
}

@Composable
fun Chip(label: String, sel: Boolean, pal: Pal, mod: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        mod.clip(RoundedCornerShape(14.dp)).background(if (sel) pal.accent else pal.text.copy(alpha = .12f))
            .clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (sel) Color.Black else pal.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1) }
}

@Composable
fun Seg(labels: List<String>, sel: Int, pal: Pal, onSel: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { n, l -> Chip(l, sel == n, pal, Modifier.weight(1f)) { onSel(n) } }
    }
}

@Composable
fun Tile(label: String, brush: Brush?, sel: Boolean, pal: Pal, onClick: () -> Unit) {
    Box(
        Modifier.size(64.dp, 104.dp).clip(RoundedCornerShape(14.dp))
            .then(if (brush != null) Modifier.background(brush) else Modifier.background(pal.text.copy(alpha = .12f)))
            .then(if (sel) Modifier.border(3.dp, pal.accent, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(label, color = if (brush != null) Color.White else pal.text, fontSize = 10.sp, style = SH, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
fun Rail(
    sizeSp: Int, pal: Pal, left: Boolean, active: Char?, modifier: Modifier,
    onTouch: (Char, Float) -> Unit, onEnd: () -> Unit
) {
    val letters = remember { listOf('★') + ('A'..'Z').toList() + '#' }
    var h by remember { mutableIntStateOf(1) }
    var top by remember { mutableFloatStateOf(0f) }
    var yy by remember { mutableFloatStateOf(-1f) }
    val dens = LocalDensity.current.density
    val touchNow by rememberUpdatedState(onTouch)
    val endNow by rememberUpdatedState(onEnd)
    Column(
        modifier.width(28.dp).onSizeChanged { h = it.height }.onGloballyPositioned { top = it.positionInRoot().y }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val p = awaitPointerEvent().changes.first()
                        if (p.pressed) {
                            yy = p.position.y
                            val n = (yy / h * letters.size).toInt().coerceIn(0, letters.size - 1)
                            touchNow(letters[n], top + yy)
                            p.consume()
                        } else if (yy >= 0f) {
                            yy = -1f
                            endNow()
                        }
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEachIndexed { n, c ->
            Text(
                c.toString(), fontSize = sizeSp.sp, fontWeight = FontWeight.Bold,
                color = if (c == active) pal.accent else pal.sub, style = SH,
                modifier = Modifier.graphicsLayer {
                    val rh = h.toFloat() / letters.size
                    val d = if (yy < 0f) 99f else (rh * (n + 0.5f) - yy) / rh
                    val g = exp(-d * d / (2f * 4.2f * 4.2f))
                    val shift = 96f * dens * g
                    translationX = if (left) shift else -shift
                    val sc = 1f + 0.3f * g
                    scaleX = sc; scaleY = sc
                }
            )
        }
    }
}

@Composable
fun SearchBar(
    s: Store, pal: Pal, top: androidx.compose.ui.unit.Dp, icon: Int, open: Boolean, query: String, hasContacts: Boolean,
    onOpen: () -> Unit, onQuery: (String) -> Unit, onClose: () -> Unit, onAskContacts: () -> Unit
) {
    val ctx = LocalContext.current
    val fr = remember { FocusRequester() }
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = top + 8.dp, start = 14.dp, end = 14.dp)) {
        val w by animateDpAsState(if (open) maxWidth else 42.dp, label = "searchWidth")
        val q = query.trim()
        val appHits = if (q.isEmpty()) s.recent() else
            s.apps.filter { it.label.contains(q, true) }.sortedBy { !it.label.startsWith(q, true) }.take(6)
        val contactHits = if (hasContacts && q.length >= 2) searchContacts(ctx, q) else emptyList()

        LaunchedEffect(open) { if (open) { delay(60); try { fr.requestFocus() } catch (e: Exception) { } } }

        Column(Modifier.align(Alignment.TopEnd)) {
            Row(
                Modifier.width(w).height(42.dp).clip(CircleShape).background(pal.card.copy(alpha = .92f))
                    .clickable(enabled = !open) { onOpen() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (open) Arrangement.Start else Arrangement.Center
            ) {
                Text("🔍", fontSize = 16.sp, modifier = Modifier.padding(horizontal = if (open) 14.dp else 0.dp))
                if (open) {
                    BasicTextField(
                        query, onQuery,
                        modifier = Modifier.weight(1f).focusRequester(fr),
                        singleLine = true,
                        textStyle = TextStyle(color = pal.text, fontSize = 17.sp),
                        cursorBrush = SolidColor(pal.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            val first = appHits.firstOrNull()
                            if (first != null) { launchApp(ctx, first); onClose() }
                            else if (q.isNotEmpty()) { go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))); onClose() }
                        }),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Search apps, contacts, web", color = pal.sub, fontSize = 16.sp)
                            inner()
                        }
                    )
                    Text("✕", color = pal.text, fontSize = 18.sp, modifier = Modifier.clickable { onClose() }.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
            if (open) {
                Column(
                    Modifier.padding(top = 8.dp).width(w).heightIn(max = 420.dp).clip(RoundedCornerShape(22.dp))
                        .background(pal.card.copy(alpha = .96f)).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)
                ) {
                    if (q.isEmpty()) Text("Recently installed", color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                    appHits.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { launchApp(ctx, a); onClose() }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(a, icon, pal, 34.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(a.label, color = pal.text, fontSize = 17.sp)
                        }
                    }
                    contactHits.forEach { c ->
                        MenuRow("👤", c.name, pal) { go(ctx, Intent(Intent.ACTION_VIEW, c.uri)); onClose() }
                    }
                    if (q.isNotEmpty()) {
                        if (!hasContacts) MenuRow("👤", "Allow contact search", pal) { onAskContacts() }
                        MenuRow("🌐", "Search web for “$q”", pal) {
                            go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))); onClose()
                        }
                    }
                }
            }
        }
    }
}
