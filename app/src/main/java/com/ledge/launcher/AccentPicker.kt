package com.ledge.launcher

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// The accent color circles, 6 in a row, so many colors fit on the screen
@Composable
fun AccentGrid(s: Store, pal: Pal) {
    Column {
        ACCENTS.withIndex().chunked(6).forEach { rowItems ->
            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (n, c) ->
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c)
                            .then(if (s.i("accent") == n) Modifier.border(3.dp, pal.text, CircleShape) else Modifier)
                            .clickable { s.setI("accent", n) }
                    )
                }
            }
        }
    }
}
