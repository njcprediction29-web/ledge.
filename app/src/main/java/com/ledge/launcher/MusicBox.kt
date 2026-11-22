package com.ledge.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
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

@Composable
fun MusicBox(pal: Pal) {
    val ctx = LocalContext.current
    val info by produceState<MusicInfo?>(null) {
        while (true) {
            value = try {
                val m = ctx.getSystemService(MediaSessionManager::class.java)
                val list = m.getActiveSessions(ComponentName(ctx, NotifService::class.java))
                val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    ?: list.firstOrNull()
                if (c == null) null else {
                    val md = c.metadata
                    MusicInfo(
                        md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown",
                        md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
                        md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
                        c.playbackState?.state == PlaybackState.STATE_PLAYING,
                        c
                    )
                }
            } catch (e: Exception) { null }
            delay(1500)
        }
    }
    val m = info ?: return
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(pal.text.copy(alpha = .14f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(pal.text.copy(alpha = .15f)),
            contentAlignment = Alignment.Center
        ) {
            val art = m.art
            if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("♪", color = pal.text, fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.artist.isNotBlank()) Text(m.artist, color = pal.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("⏮", fontSize = 20.sp, modifier = Modifier.clickable { m.ctl.transportControls.skipToPrevious() }.padding(8.dp))
        Text(if (m.playing) "⏸" else "▶", fontSize = 22.sp, color = pal.accent,
            modifier = Modifier.clickable { if (m.playing) m.ctl.transportControls.pause() else m.ctl.transportControls.play() }.padding(8.dp))
        Text("⏭", fontSize = 20.sp, modifier = Modifier.clickable { m.ctl.transportControls.skipToNext() }.padding(8.dp))
    }
}
