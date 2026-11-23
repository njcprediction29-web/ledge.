package com.ledge.launcher

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar

// Each plan item is saved as "minutes since midnight|text", for example "450|Gym" = 7:30 Gym
fun planItem(raw: String): Pair<Int, String>? {
    val i = raw.indexOf('|')
    if (i < 0) return null
    val m = raw.substring(0, i).toIntOrNull() ?: return null
    return m to raw.substring(i + 1)
}

fun nowMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

fun planTimeText(ctx: Context, minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (DateFormat.is24HourFormat(ctx)) {
        "%02d:%02d".format(h, m)
    } else {
        val h12 = if (h % 12 == 0) 12 else h % 12
        "%d:%02d %s".format(h12, m, if (h < 12) "AM" else "PM")
    }
}

// The small card on the home screen: what is next on today's plan. Tap it to edit the plan.
@Composable
fun PlanBox(s: Store, pal: Pal, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val now by produceState(nowMinutes()) { while (true) { value = nowMinutes(); delay(30_000) } }
    val items = s.plan.mapNotNull { planItem(it) }
    val next = items.filter { it.first >= now }.take(3)
    Column(Modifier.padding(top = 10.dp).clickable(onClick = onOpen)) {
        if (items.isEmpty()) {
            Text("🗓️ Plan your day: tap to add", color = pal.sub, fontSize = 12.sp, style = SH)
        } else if (next.isEmpty()) {
            Text("🗓️ Your plan for today is done", color = pal.sub, fontSize = 12.sp, style = SH)
        } else {
            next.forEach { (min, text) ->
                Text(
                    "🗓️ ${planTimeText(ctx, min)}  ·  $text", color = pal.text, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = SH,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

// The window that opens when you tap the plan: the whole day, add and remove items
@Composable
fun PlanEditor(s: Store, pal: Pal) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = 22.dp)) {
        Text("🗓️ Plan your day", color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Type a time and a task, like 7:30 Gym or 2 pm Call mom. It repeats every day.", color = pal.sub, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp))
        s.plan.forEachIndexed { n, raw ->
            val p = planItem(raw)
            if (p != null) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(planTimeText(ctx, p.first), color = pal.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(84.dp))
                    Text(p.second, color = pal.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("✕", color = pal.sub, fontSize = 18.sp,
                        modifier = Modifier.clickable { s.removePlan(n) }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        BasicTextField(
            input, { input = it; msg = "" }, modifier = Modifier.padding(top = 16.dp).fillMaxWidth(), singleLine = true,
            textStyle = TextStyle(color = pal.text, fontSize = 16.sp), cursorBrush = SolidColor(pal.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (s.addPlan(input)) { input = ""; msg = "" } else msg = "Start with a time, like 7:30 Gym"
            }),
            decorationBox = { inner ->
                if (input.isEmpty()) Text("＋ Add: 7:30 Gym, then press Done", color = pal.sub, fontSize = 16.sp)
                inner()
            }
        )
        if (msg.isNotEmpty()) Text(msg, color = pal.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
