package com.ledge.launcher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

data class MusicInfo(
    val title: String, val artist: String, val art: Bitmap?,
    val playing: Boolean, val progress: Float, val ctl: MediaController
)

// The display designs you can pick in Settings > Music box
val MUSIC_STYLES = listOf("Vinyl", "Cover", "Wave", "Pill", "Ring")

// Finds the cover picture from every place a music app might put it
fun artOf(ctx: Context, md: MediaMetadata?): Bitmap? {
    if (md == null) return null
    val bitmapKeys = listOf(
        MediaMetadata.METADATA_KEY_ART,
        MediaMetadata.METADATA_KEY_ALBUM_ART,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON
    )
    for (k in bitmapKeys) md.getBitmap(k)?.let { return it }
    val uriKeys = listOf(
        MediaMetadata.METADATA_KEY_ART_URI,
        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
    )
    for (k in uriKeys) {
        val u = md.getString(k) ?: continue
        if (u.startsWith("content") || u.startsWith("file") || u.startsWith("android.resource")) {
            try {
                ctx.contentResolver.openInputStream(Uri.parse(u))?.use { st ->
                    val b = BitmapFactory.decodeStream(st)
                    if (b != null) return b
                }
            } catch (e: Exception) { }
        }
    }
    return null
}

// A moving value in degrees (0..360) that speeds up smoothly when music plays and slows down
// smoothly to a stop when it is paused. The frame loop ends by itself once everything has stopped.
@Composable
fun rememberMotion(playing: Boolean, degreesPerSecond: Float): State<Float> {
    val ctx = LocalContext.current
    val still = remember { reducedMotion(ctx) }
    val speed by animateFloatAsState(
        if (playing && !still) 1f else 0f, animationSpec = tween(700), label = "motionSpeed"
    )
    val pos = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        var last = 0L
        while ((playing && !still) || speed > 0.002f) {
            withInfiniteAnimationFrameNanos { t ->
                if (last != 0L) {
                    pos.floatValue = (pos.floatValue + (t - last) / 1_000_000_000f * degreesPerSecond * speed) % 360f
                }
                last = t
            }
        }
    }
    return pos
}

// STYLE 1: a vinyl record with the song cover as its center label
@Composable
fun Vinyl(art: Bitmap?, playing: Boolean, pal: Pal) {
    val angle by rememberMotion(playing, 60f)
    val dim by animateFloatAsState(if (playing) 1f else 0.78f, animationSpec = tween(500), label = "vinylDim")
    Box(
        Modifier.size(78.dp).graphicsLayer { rotationZ = angle; alpha = dim },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF0E0E11))
            var rr = r * 0.96f
            while (rr > r * 0.42f) {
                drawCircle(Color.White.copy(alpha = 0.07f), radius = rr, style = Stroke(width = 1f))
                rr -= r * 0.065f
            }
            val inset = r * 0.16f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            drawArc(Color.White.copy(alpha = 0.13f), 205f, 40f, false, Offset(inset, inset), arcSize, style = Stroke(width = r * 0.2f))
            drawArc(Color.White.copy(alpha = 0.13f), 25f, 40f, false, Offset(inset, inset), arcSize, style = Stroke(width = r * 0.2f))
        }
        if (art != null) {
            Image(
                art.asImageBitmap(), null,
                Modifier.size(32.dp).clip(CircleShape).border(1.dp, Color.Black.copy(alpha = 0.5f), CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.size(32.dp).clip(CircleShape).background(pal.accent))
        }
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF0E0E11)))
    }
}

// STYLE 2: a rounded album cover that gently shrinks and dims with a pause sign when paused
@Composable
fun CoverArt(art: Bitmap?, playing: Boolean, pal: Pal) {
    val scale by animateFloatAsState(if (playing) 1f else 0.93f, animationSpec = tween(400), label = "coverScale")
    val dim by animateFloatAsState(if (playing) 0f else 0.5f, animationSpec = tween(400), label = "coverDim")
    Box(
        Modifier.size(76.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp)).background(pal.accent.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        if (art != null) {
            Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text("♪", color = pal.text, fontSize = 30.sp)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        Canvas(Modifier.size(22.dp).graphicsLayer { alpha = dim * 2f }) {
            drawRoundRect(Color.White, Offset(size.width * 0.12f, 0f), Size(size.width * 0.28f, size.height), CornerRadius(3f, 3f))
            drawRoundRect(Color.White, Offset(size.width * 0.60f, 0f), Size(size.width * 0.28f, size.height), CornerRadius(3f, 3f))
        }
    }
}

// STYLE 3: music bars that dance while playing and settle down flat when paused
@Composable
fun WaveBars(playing: Boolean, pal: Pal) {
    val phase by rememberMotion(playing, 300f)
    val level by animateFloatAsState(if (playing) 1f else 0f, animationSpec = tween(600), label = "waveLevel")
    val col = pal.accent
    Canvas(Modifier.size(84.dp, 44.dp)) {
        val n = 14
        val bw = size.width / (n * 1.7f)
        val gap = (size.width - bw * n) / (n - 1)
        for (i in 0 until n) {
            val a = (phase + i * 47f) * 0.0174533f
            val b = (phase * 2f + i * 131f) * 0.0174533f
            val wave = sin(a) * 0.5f + 0.5f
            val wave2 = sin(b) * 0.5f + 0.5f
            val frac = 0.14f + 0.86f * (wave * 0.6f + wave2 * 0.4f) * level
            val h = size.height * frac
            drawRoundRect(
                col, Offset(i * (bw + gap), (size.height - h) / 2f), Size(bw, h), CornerRadius(bw / 2f, bw / 2f)
            )
        }
    }
}

// STYLE 5: round cover with a ring around it that fills as the song goes on
@Composable
fun ProgressRing(art: Bitmap?, progress: Float, playing: Boolean, pal: Pal) {
    val p by animateFloatAsState(if (progress < 0f) 0f else progress, animationSpec = tween(900), label = "ringProgress")
    val scale by animateFloatAsState(if (playing) 1f else 0.9f, animationSpec = tween(400), label = "ringScale")
    val col = pal.accent
    Box(
        Modifier.size(78.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(Color.White.copy(alpha = 0.2f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (progress >= 0f) {
                drawArc(col, -90f, 360f * p, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        if (art != null) {
            Image(art.asImageBitmap(), null, Modifier.size(60.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(60.dp).clip(CircleShape).background(col.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Text("♪", color = pal.text, fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun TrackText(title: String, artist: String, playing: Boolean, pal: Pal, onOpen: () -> Unit) {
    val a by animateFloatAsState(if (playing) 1f else 0.65f, animationSpec = tween(400), label = "trackAlpha")
    Column(Modifier.graphicsLayer { alpha = a }.clickable(onClick = onOpen)) {
        Text(title, color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = SH)
        if (artist.isNotBlank()) Text(artist, color = pal.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = SH)
    }
}

@Composable
fun MusicControls(playing: Boolean, pal: Pal, onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CtlIcon(Ctl.PREV, pal.text, onPrev)
        CtlIcon(if (playing) Ctl.PAUSE else Ctl.PLAY, pal.accent, onToggle)
        CtlIcon(Ctl.NEXT, pal.text, onNext)
    }
}

// STYLE 4: a slim capsule with a small cover, the song and two buttons
@Composable
fun PillPlayer(
    title: String, artist: String, art: Bitmap?, playing: Boolean, pal: Pal,
    onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit
) {
    val angle by rememberMotion(playing, 40f)
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(99.dp))
            .background(pal.text.copy(alpha = 0.14f)).padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).graphicsLayer { rotationZ = angle }.clip(CircleShape).background(pal.accent.copy(alpha = 0.5f))) {
            if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) { TrackText(title, artist, playing, pal, onOpen) }
        CtlIcon(if (playing) Ctl.PAUSE else Ctl.PLAY, pal.accent, onToggle)
        CtlIcon(Ctl.NEXT, pal.text, onNext)
    }
}

// Draws the chosen design. It only draws: the real player and the Settings preview both use it.
@Composable
fun MusicView(
    style: Int, title: String, artist: String, art: Bitmap?, playing: Boolean, progress: Float, pal: Pal,
    onOpen: () -> Unit, onPrev: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit
) {
    val st = style.coerceIn(0, MUSIC_STYLES.lastIndex)
    if (st == 3) {
        PillPlayer(title, artist, art, playing, pal, onOpen, onToggle, onNext)
    } else {
        Row(Modifier.padding(top = 10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            when (st) {
                0 -> Vinyl(art, playing, pal)
                1 -> CoverArt(art, playing, pal)
                2 -> WaveBars(playing, pal)
                else -> ProgressRing(art, progress, playing, pal)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                TrackText(title, artist, playing, pal, onOpen)
                Box(Modifier.padding(top = 4.dp)) { MusicControls(playing, pal, onPrev, onToggle, onNext) }
            }
        }
    }
}

// The player on the home screen. It slides in when music starts, stays (with a paused look) while the
// song is paused, and slides away only when the music app is closed or stops completely.
@Composable
fun MusicPlayer(pal: Pal) {
    val ctx = LocalContext.current
    val store = LocalStore.current
    val info by produceState<MusicInfo?>(null) {
        var lastKey = ""
        var lastArt: Bitmap? = null
        var misses = 0
        while (true) {
            try {
                val mgr = ctx.getSystemService(MediaSessionManager::class.java)
                val list = mgr.getActiveSessions(ComponentName(ctx, NotifService::class.java))
                val live = list.filter {
                    val s = it.playbackState?.state
                    s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING
                }
                val c = live.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: live.firstOrNull()
                if (c == null) {
                    // wait for two empty checks in a row so a short gap between songs does not make it blink away
                    misses++
                    if (misses >= 2) value = null
                } else {
                    misses = 0
                    val md = c.metadata
                    val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
                    val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    val key = c.packageName + "|" + title + "|" + artist
                    if (key != lastKey || lastArt == null) {
                        lastArt = artOf(ctx, md)
                        lastKey = key
                    }
                    val ps = c.playbackState
                    val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
                    var progress = -1f
                    if (ps != null && dur > 0L && ps.position >= 0L) {
                        var pos = ps.position
                        if (ps.state == PlaybackState.STATE_PLAYING) {
                            pos += ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
                        }
                        progress = (pos.toFloat() / dur).coerceIn(0f, 1f)
                    }
                    value = MusicInfo(title, artist, lastArt, ps?.state == PlaybackState.STATE_PLAYING, progress, c)
                }
            } catch (e: Exception) {
                misses++
                if (misses >= 2) value = null
            }
            delay(1000)
        }
    }
    // keep the last song while the box slides away, so it does not go blank during the animation
    var last by remember { mutableStateOf<MusicInfo?>(null) }
    LaunchedEffect(info) { if (info != null) last = info }
    val m = info ?: last

    AnimatedVisibility(
        visible = info != null,
        enter = fadeIn(tween(350)) + expandVertically(tween(350)),
        exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
    ) {
        if (m != null) {
            MusicView(
                store.i("mstyle"), m.title, m.artist, m.art, m.playing, m.progress, pal,
                onOpen = {
                    try {
                        ctx.packageManager.getLaunchIntentForPackage(m.ctl.packageName)?.let { go(ctx, it) }
                    } catch (e: Exception) { }
                },
                onPrev = { m.ctl.transportControls.skipToPrevious() },
                onToggle = { if (m.playing) m.ctl.transportControls.pause() else m.ctl.transportControls.play() },
                onNext = { m.ctl.transportControls.skipToNext() }
            )
        }
    }
}

enum class Ctl { PREV, PLAY, PAUSE, NEXT }

// Plain shapes: triangle left = previous, triangle right = next / play, two bars = pause
@Composable
fun CtlIcon(kind: Ctl, color: Color, onClick: () -> Unit) {
    Canvas(Modifier.size(46.dp).clickable(onClick = onClick).padding(13.dp)) {
        val w = size.width
        val h = size.height
        when (kind) {
            Ctl.PREV -> {
                val p = Path().apply { moveTo(w, 0f); lineTo(0f, h / 2f); lineTo(w, h); close() }
                drawPath(p, color)
            }
            Ctl.NEXT, Ctl.PLAY -> {
                val p = Path().apply { moveTo(0f, 0f); lineTo(w, h / 2f); lineTo(0f, h); close() }
                drawPath(p, color)
            }
            Ctl.PAUSE -> {
                drawRect(color, Offset(w * 0.08f, 0f), Size(w * 0.30f, h))
                drawRect(color, Offset(w * 0.62f, 0f), Size(w * 0.30f, h))
            }
        }
    }
}
