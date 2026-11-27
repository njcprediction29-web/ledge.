package com.ledge.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// Row of favorite app icons at the bottom of the home screen (max 5, no names).
// Tap opens the app, long-press opens the options menu.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BhDock(
    apps: List<AppItem>,
    pal: Pal,
    icon: Int,
    modifier: Modifier = Modifier,
    onOpen: (AppItem) -> Unit,
    onMenu: (AppItem) -> Unit
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        apps.take(5).forEach { a ->
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .combinedClickable(onClick = { onOpen(a) }, onLongClick = { onMenu(a) }),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(a, icon, pal, 38.dp)
            }
        }
    }
}
