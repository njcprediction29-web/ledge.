package com.ledge.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

// White weather panel like the reference: date, big temperature, small stats,
// a thin battery line, and a greeting that changes with the time of day.
@Composable
fun BhWeatherPanel(s: Store, date: String, battery: Int, onWeather: () -> Unit) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 5 -> "HAVE A GOOD NIGHT"
        hour < 12 -> "HAVE A GOOD MORNING"
        hour < 17 -> "HAVE A GOOD AFTERNOON"
        hour < 21 -> "HAVE A GOOD EVENING"
        else -> "HAVE A GOOD NIGHT"
    }
    val temp = s.weatherTemp()
    val hum = s.weatherHumidity()
    val word = s.weatherWord()
    val soft = Color.White.copy(alpha = 0.7f)

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(date.uppercase(), color = soft, fontSize = 11.sp, letterSpacing = 2.sp)

        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (temp != null) {
                Text(temp, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 8.dp)) {
                    if (word.isNotEmpty()) Text(word, color = Color.White, fontSize = 12.sp)
                    if (hum != null) Text("Humidity : $hum", color = soft, fontSize = 11.sp)
                    if (battery >= 0) Text("Battery : $battery%", color = soft, fontSize = 11.sp)
                }
            } else {
                Text(
                    "tap for weather",
                    color = soft,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable { onWeather() }
                )
            }
        }

        // thin battery line
        Box(
            Modifier.fillMaxWidth().padding(top = 10.dp).height(2.dp)
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            if (battery > 0) {
                Box(
                    Modifier.fillMaxWidth((battery / 100f).coerceIn(0f, 1f)).height(2.dp)
                        .background(Color.White)
                )
            }
        }

        Text(
            greeting,
            color = Color.White,
            fontSize = 10.sp,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
