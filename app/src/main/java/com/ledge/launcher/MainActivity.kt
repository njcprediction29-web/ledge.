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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        store.homeTick++   // Home button pressed: close menus and go back to top
    }
}

// ---------- small helpers ----------

fun go(ctx: Context, i: Intent) {
    try { ctx.startActivity(i) } catch (e: Exception) { }
}

fun launchApp(ctx: Context, a: AppItem) = go(
    ctx,
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(a.pkg, a.cls)
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
                val uri = ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))
                out.add(Contact(c.getString(2) ?: "", uri))
            }
        }
    } catch (e: Exception) { }
    return out
}

fun shadowStyle() = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 6f))

// ---------- main screen ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Home(s: Store) {
    val ctx = LocalContext.current
    val act = ctx as Activity
    val pal = palette(s.theme, s.accent)
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()

    var menuApp by remember { mutableStateOf<AppItem?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var active by remember { mutableStateOf<Char?>(null) }
    var hasContacts by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { s.refreshWeather() }
    val contactLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasContacts = it }
    val overlay = menuApp != null || settingsOpen

    fun closeSearch() { query = ""; searchOpen = false; focus.clearFocus() }

    LaunchedEffect(Unit) {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    LaunchedEffect(s.homeTick) {
        if (s.homeTick > 0) {
            menuApp = null; settingsOpen = false; closeSearch()
            listState.animateScrollToItem(0)
        }
    }
    // Blur the wallpaper behind the window while a menu is open (Android 12+ phones that support it)
    LaunchedEffect(overlay) {
        if (Build.VERSION.SDK_INT >= 31) {
            try { act.window.setBackgroundBlurRadius(if (overlay) 70 else 0) } catch (e: Exception) { }
        }
    }
    BackHandler(overlay || searchOpen) {
        if (menuApp != null) menuApp = null else if (settingsOpen) settingsOpen = false else closeSearch()
    }

    val visible = s.apps.filter { it.pkg !in s.hidden }
    val entries = remember(visible) { buildEntries(visible) }
    val favApps = s.favs.mapNotNull { p -> visible.firstOrNull { it.pkg == p } }
    // number of list items that come before the alphabetical entries
    val base = 1 + (if (favApps.isNotEmpty()) favApps.size + 1 else 0) + 1

    LaunchedEffect(active) {
        val c = active ?: return@LaunchedEffect
        val idx = if (c == '#') 0 else entries.indexOfFirst { it is Entry.Head && it.c != '#' && it.c >= c }
            .let { if (it < 0) entries.lastIndex else it }
        if (entries.isNotEmpty()) listState.scrollToItem(base + idx)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(pal.scrim)) {
        // ===== everything that gets blurred when a menu is open =====
        Box(Modifier.fillMaxSize().then(if (overlay) Modifier.blur(18.dp) else Modifier)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(top = top + 12.dp, bottom = bottom + 24.dp, start = 22.dp, end = 54.dp)
            ) {
                item(key = "head") {
                    HeaderBlock(s, pal, onWeather = { locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }, onSettings = { settingsOpen = true })
                }
                if (favApps.isNotEmpty()) {
                    item(key = "favt") { Title("★ Favorites", pal) }
                    items(favApps, key = { "f" + it.pkg }) { a ->
                        AppRow(a, pal, Badges.counts[a.pkg] ?: 0, { launchApp(ctx, a) }) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuApp = a
                        }
                    }
                }
                item(key = "allt") { Title("All apps", pal) }
                itemsIndexed(entries, key = { i, _ -> "e$i" }) { _, e ->
                    when (e) {
                        is Entry.Head -> Text(
                            e.c.toString(), color = pal.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp, start = 4.dp)
                        )
                        is Entry.App -> AppRow(e.app, pal, Badges.counts[e.app.pkg] ?: 0, { launchApp(ctx, e.app) }) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress); menuApp = e.app
                        }
                    }
                }
            }

            Rail(LETTER_SP[s.fontLevel], pal, active, Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
                onLetter = { active = it }, onEnd = { active = null })

            active?.let {
                Box(
                    Modifier.align(Alignment.Center).size(84.dp).clip(CircleShape).background(pal.accent),
                    contentAlignment = Alignment.Center
                ) { Text(it.toString(), color = Color.Black, fontSize = 38.sp, fontWeight = FontWeight.Bold) }
            }

            SearchBar(s, pal, top, searchOpen, query, hasContacts,
                onOpen = { searchOpen = true }, onQuery = { query = it }, onClose = { closeSearch() },
                onAskContacts = { contactLauncher.launch(Manifest.permission.READ_CONTACTS) })
        }

        // ===== long-press menu =====
        menuApp?.let { a ->
            var shown by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { shown = true }
            Box(
                Modifier.fillMaxSize().background(Color(0x59000000))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { menuApp = null },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(shown, enter = fadeIn() + scaleIn(initialScale = 0.85f)) {
                    val isFav = a.pkg in s.favs
                    Column(
                        Modifier.padding(28.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(pal.card)
                            .pointerInput(Unit) { detectTapGestures { } }.padding(vertical = 10.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(loadIcon(ctx, a), null, Modifier.size(56.dp))
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(a.label, color = pal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val n = Badges.counts[a.pkg] ?: 0
                                Text(if (n > 0) "$n notification${if (n > 1) "s" else ""}" else "App options", color = pal.sub, fontSize = 14.sp)
                            }
                        }
                        Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp).size(1.dp).background(pal.text.copy(alpha = .12f)))
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
            }
        }

        // ===== Ledge settings =====
        if (settingsOpen) {
            Box(
                Modifier.fillMaxSize().background(Color(0x59000000))
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { settingsOpen = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.padding(22.dp).fillMaxWidth().heightIn(max = 640.dp).clip(RoundedCornerShape(28.dp)).background(pal.card)
                        .pointerInput(Unit) { detectTapGestures { } }
                        .verticalScroll(rememberScrollState()).padding(22.dp)
                ) {
                    Text("Ledge settings", color = pal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)

                    SectionLabel("A–Z letter size", pal)
                    Text("A B C D E F", color = pal.accent, fontSize = LETTER_SP[s.fontLevel].sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.size(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Small", "Medium", "Large", "XL").forEachIndexed { i, l ->
                            Chip(l, s.fontLevel == i, pal, Modifier.weight(1f)) { s.setFont(i) }
                        }
                    }

                    SectionLabel("Theme", pal)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Dark", "Light", "Black").forEachIndexed { i, l ->
                            Chip(l, s.theme == i, pal, Modifier.weight(1f)) { s.setTheme(i) }
                        }
                    }

                    SectionLabel("Accent color", pal)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ACCENTS.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(38.dp).clip(CircleShape).background(c)
                                    .then(if (s.accent == i) Modifier.border(3.dp, pal.text, CircleShape) else Modifier)
                                    .clickable { s.setAccent(i) }
                            )
                        }
                    }

                    SectionLabel("Temperature", pal)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("°C", !s.useF, pal, Modifier.weight(1f)) { s.setUseF(false) }
                        Chip("°F", s.useF, pal, Modifier.weight(1f)) { s.setUseF(true) }
                    }

                    SectionLabel("Permissions", pal)
                    MenuRow("📍", "Allow location (for weather)", pal) { locLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
                    MenuRow("🔔", "Turn on notification badges", pal) { go(ctx, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    Text(
                        "If Android blocks it as a \"restricted setting\": Settings > Apps > Ledge > ⋮ (top right) > Allow restricted settings, then try again.",
                        color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    val hiddenApps = s.apps.filter { it.pkg in s.hidden }
                    SectionLabel("Hidden apps (tap to unhide)", pal)
                    if (hiddenApps.isEmpty()) Text("None. Long-press an app and choose Hide app.", color = pal.sub, fontSize = 14.sp)
                    hiddenApps.forEach { a -> MenuRow("👁️", a.label, pal) { s.toggleHidden(a.pkg) } }
                }
            }
        }
    }
}

// ---------- pieces ----------

@Composable
fun HeaderBlock(s: Store, pal: Pal, onWeather: () -> Unit, onSettings: () -> Unit) {
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
    val verse = remember(now / 3_600_000) { todayVerse() }
    var verseOpen by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var newTask by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(time, color = pal.accent, fontSize = 56.sp, fontWeight = FontWeight.Bold, style = shadowStyle())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(date, color = pal.text, fontSize = 15.sp, style = shadowStyle())
            if (battery >= 0) Text("   🔋 $battery%", color = pal.text, fontSize = 15.sp, style = shadowStyle())
        }
        Text(
            s.weatherText() ?: "🌡️ Tap to show weather",
            color = pal.text, fontSize = 17.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp).clickable { if (s.weatherText() == null) onWeather() },
            style = shadowStyle()
        )

        // Verse of the day
        Column(
            Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(pal.card.copy(alpha = .62f)).clickable { verseOpen = !verseOpen }.padding(14.dp)
        ) {
            Text("📖 ${verse.first}", color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                "“${verse.second}”", color = pal.text, fontSize = 14.sp, fontStyle = FontStyle.Italic,
                maxLines = if (verseOpen) 12 else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Quick settings
        Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("📶 Wi-Fi", false, pal, Modifier.weight(1f)) {
                go(ctx, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
            }
            Chip("🔵 BT", false, pal, Modifier.weight(1f)) { go(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            Chip("🔦 Light", torch, pal, Modifier.weight(1f)) {
                try {
                    val cm = ctx.getSystemService(CameraManager::class.java)
                    val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                    if (id != null) { torch = !torch; cm.setTorchMode(id, torch) }
                } catch (e: Exception) { torch = false }
            }
            Chip("⚙️", false, pal, Modifier.weight(0.6f)) { onSettings() }
        }

        // To-do
        Column(
            Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(pal.card.copy(alpha = .62f)).padding(14.dp)
        ) {
            Text("📝 To-do (${s.todos.size})", color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            s.todos.forEachIndexed { i, t ->
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).border(2.dp, pal.accent, CircleShape).clickable { s.doneTodo(i) }
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(t, color = pal.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                }
            }
            BasicTextField(
                newTask, { newTask = it },
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(color = pal.text, fontSize = 15.sp),
                cursorBrush = SolidColor(pal.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { s.addTodo(newTask); newTask = "" }),
                decorationBox = { inner ->
                    if (newTask.isEmpty()) Text("＋ Add a task, then press Done", color = pal.sub, fontSize = 15.sp)
                    inner()
                }
            )
        }
    }
}

@Composable
fun Title(t: String, pal: Pal) {
    Text(t, color = pal.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp, bottom = 4.dp, start = 4.dp))
}

@Composable
fun SectionLabel(t: String, pal: Pal) {
    Text(t, color = pal.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(a: AppItem, pal: Pal, badge: Int, onOpen: () -> Unit, onMenu: () -> Unit) {
    val ctx = LocalContext.current
    val icon = remember(a.pkg) { loadIcon(ctx, a) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onMenu).padding(vertical = 9.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(icon, null, Modifier.size(42.dp))
        Spacer(Modifier.width(16.dp))
        Text(a.label, color = pal.text, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = shadowStyle())
        if (badge > 0) Box(
            Modifier.defaultMinSize(22.dp, 22.dp).clip(CircleShape).background(pal.accent).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) { Text(if (badge > 99) "99+" else "$badge", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun MenuRow(icon: String, label: String, pal: Pal, color: Color = pal.text, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(16.dp))
        Text(label, color = color, fontSize = 18.sp)
    }
}

@Composable
fun Chip(label: String, sel: Boolean, pal: Pal, mod: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        mod.clip(RoundedCornerShape(14.dp))
            .background(if (sel) pal.accent else pal.text.copy(alpha = .12f))
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (sel) Color.Black else pal.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
}

@Composable
fun Rail(sizeSp: Int, pal: Pal, active: Char?, modifier: Modifier, onLetter: (Char) -> Unit, onEnd: () -> Unit) {
    val letters = ('A'..'Z').toList() + '#'
    var h by remember { mutableIntStateOf(1) }
    androidx.compose.foundation.layout.Column(
        modifier.width(38.dp).fillMaxHeight(0.8f).onSizeChanged { h = it.height }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val p = awaitPointerEvent().changes.first()
                        if (p.pressed) {
                            val i = (p.position.y / h * letters.size).toInt().coerceIn(0, letters.size - 1)
                            onLetter(letters[i]); p.consume()
                        } else onEnd()
                    }
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach {
            Text(
                it.toString(), fontSize = sizeSp.sp, fontWeight = FontWeight.Bold,
                color = if (it == active) pal.accent else pal.sub, style = shadowStyle()
            )
        }
    }
}

@Composable
fun SearchBar(
    s: Store, pal: Pal, top: androidx.compose.ui.unit.Dp, open: Boolean, query: String, hasContacts: Boolean,
    onOpen: () -> Unit, onQuery: (String) -> Unit, onClose: () -> Unit, onAskContacts: () -> Unit
) {
    val ctx = LocalContext.current
    val fr = remember { FocusRequester() }
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = top + 8.dp, start = 14.dp, end = 14.dp)) {
        val w by animateDpAsState(if (open) maxWidth else 42.dp, label = "searchWidth")
        val q = query.trim()
        val appHits = if (q.isEmpty()) emptyList() else
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
            if (open && q.isNotEmpty()) {
                Column(
                    Modifier.padding(top = 8.dp).width(w).heightIn(max = 420.dp).clip(RoundedCornerShape(22.dp))
                        .background(pal.card.copy(alpha = .96f)).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)
                ) {
                    appHits.forEach { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable { launchApp(ctx, a); onClose() }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(loadIcon(ctx, a), null, Modifier.size(34.dp))
                            Spacer(Modifier.width(14.dp))
                            Text(a.label, color = pal.text, fontSize = 17.sp)
                        }
                    }
                    contactHits.forEach { c ->
                        MenuRow("👤", c.name, pal) { go(ctx, Intent(Intent.ACTION_VIEW, c.uri)); onClose() }
                    }
                    if (!hasContacts) MenuRow("👤", "Allow contact search", pal) { onAskContacts() }
                    MenuRow("🌐", "Search web for “$q”", pal) {
                        go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))); onClose()
                    }
                }
            }
        }
    }
}
