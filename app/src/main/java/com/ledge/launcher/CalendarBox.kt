package com.ledge.launcher

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalEvent(val title: String, val start: Long, val allDay: Boolean)

// Reads today's events that have not finished yet (max 3). Returns an empty list if anything goes wrong.
fun readTodayEvents(ctx: Context): List<CalEvent> {
    val out = ArrayList<CalEvent>()
    try {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, dayStart)
        ContentUris.appendId(b, dayEnd)
        ctx.contentResolver.query(
            b.build(),
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY
            ),
            null, null, "${CalendarContract.Instances.BEGIN} ASC"
        )?.use { c ->
            while (c.moveToNext() && out.size < 3) {
                val title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(No title)"
                val begin = c.getLong(1)
                val end = c.getLong(2)
                val allDay = c.getInt(3) == 1
                if (allDay || end >= now) out.add(CalEvent(title, begin, allDay))
            }
        }
    } catch (e: Exception) { }
    return out
}

// What the home screen shows under the clock: the next events of today
@Composable
fun CalendarBox(pal: Pal) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val events by produceState(emptyList<CalEvent>(), granted) {
        while (true) {
            value = if (granted) readTodayEvents(ctx) else emptyList()
            delay(60_000)
        }
    }
    val pattern = if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a"
    val fmt = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }

    Column(Modifier.padding(top = 10.dp)) {
        if (!granted) {
            SmallChip("📅 Allow calendar", pal) { ask.launch(Manifest.permission.READ_CALENDAR) }
        } else if (events.isEmpty()) {
            Text("📅 No more events today", color = pal.sub, fontSize = 12.sp, style = SH)
        } else {
            events.forEach { e ->
                val whenText = if (e.allDay) "All day" else fmt.format(Date(e.start))
                Text(
                    "📅 $whenText  ·  ${e.title}", color = pal.text, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = SH,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
