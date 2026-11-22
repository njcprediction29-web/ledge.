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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

data class MusicInfo(
    val title: String, val artist: String, val art: Bitmap?,
    val playing: Boolean, val ctl: MediaController
)

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

// A vinyl record with the song cover as its center label. Everything spins together while playing.
@Composable
fun Vinyl(art: Bitmap?, playing: Boolean, pal: Pal) {
    val ctx = LocalContext.current
    val still = remember { reducedMotion(ctx) }
    val angle = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing && !still) {
            while (true) {
                val start = angle.value % 360f
                angle.snapTo(start)
                angle.animateTo(start + 360f, tween(6000, easing = LinearEasing))
            }
        }
    }
    Box(
        Modifier.size(78.dp).graphicsLayer { rotationZ = angle.value },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Color(0xFF0E0E11))
            // grooves
            var rr = r * 0.96f
            while (rr > r * 0.42f) {
                drawCircle(Color.White.copy(alpha = 0.07f), radius = rr, style = Stroke(width = 1f))
                rr -= r * 0.065f
            }
            // light sheen so you can see it turning
            val inset = r * 0.16f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            drawArc(Color.White.copy(alpha = 0.13f), 205f, 40f, false, Offset(inset, inset), arcSize, style = Stroke(width = r * 0.2f))
            drawArc(Color.White.copy(alpha = 0.13f), 25f, 40f, false, Offset(inset, inset), arcSize, style = Stroke(width = r * 0.2f))
        }
        // center label = the song cover
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

@Composable
fun MusicBox(pal: Pal) {
    val ctx = LocalContext.current
    val info by produceState<MusicInfo?>(null) {
        var lastKey = ""
        var lastArt: Bitmap? = null
        while (true) {
            value = try {
                val m = ctx.getSystemService(MediaSessionManager::class.java)
                val list = m.getActiveSessions(ComponentName(ctx, NotifService::class.java))
                val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    ?: list.firstOrNull()
                if (c == null) null else {
                    val md = c.metadata
                    val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
                    val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    val key = c.packageName + "|" + title + "|" + artist
                    if (key != lastKey || lastArt == null) {
                        lastArt = artOf(ctx, md)
                        lastKey = key
                    }
                    MusicInfo(title, artist, lastArt, c.playbackState?.state == PlaybackState.STATE_PLAYING, c)
                }
            } catch (e: Exception) { null }
            delay(1500)
        }
    }
    val m = info ?: return
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(pal.text.copy(alpha = .14f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Vinyl(m.art, m.playing, pal)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Column(
                Modifier.clickable {
                    try {
                        ctx.packageManager.getLaunchIntentForPackage(m.ctl.packageName)?.let { go(ctx, it) }
                    } catch (e: Exception) { }
                }
            ) {
                Text(m.title, color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (m.artist.isNotBlank()) Text(m.artist, color = pal.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⏮", fontSize = 22.sp, modifier = Modifier.clickable { m.ctl.transportControls.skipToPrevious() }.padding(horizontal = 10.dp, vertical = 6.dp))
                Text(
                    if (m.playing) "⏸" else "▶", fontSize = 24.sp, color = pal.accent,
                    modifier = Modifier.clickable { if (m.playing) m.ctl.transportControls.pause() else m.ctl.transportControls.play() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Text("⏭", fontSize = 22.sp, modifier = Modifier.clickable { m.ctl.transportControls.skipToNext() }.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}
