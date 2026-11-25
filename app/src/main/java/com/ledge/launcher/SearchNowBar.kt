package com.ledge.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A cream pill with "Search Now" and a dark round arrow button, at the bottom of the home screen
@Composable
fun BoxScope.SearchNowBar(fill: Color, ink: Color, bottomInset: Dp, onClick: () -> Unit) {
    Row(
        Modifier.align(Alignment.BottomCenter)
            .padding(start = 24.dp, end = 60.dp, bottom = bottomInset + 24.dp)
            .fillMaxWidth().height(60.dp)
            .clip(RoundedCornerShape(30.dp)).background(fill)
            .clickable(onClick = onClick).padding(start = 26.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Search Now", color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Box(Modifier.size(44.dp).clip(CircleShape).background(ink), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(18.dp)) {
                val w = size.width
                val h = size.height
                val sw = 2.2.dp.toPx()
                drawLine(fill, Offset(0f, h / 2f), Offset(w, h / 2f), sw, StrokeCap.Round)
                drawLine(fill, Offset(w * 0.55f, h * 0.1f), Offset(w, h / 2f), sw, StrokeCap.Round)
                drawLine(fill, Offset(w * 0.55f, h * 0.9f), Offset(w, h / 2f), sw, StrokeCap.Round)
            }
        }
    }
}
