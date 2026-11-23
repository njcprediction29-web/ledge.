package com.ledge.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlin.math.abs

// Remembers which favorite is being dragged and how far the finger has moved it
class DragState {
    var pkg by mutableStateOf<String?>(null)
    var dy by mutableFloatStateOf(0f)
    var pending: String? = null
}

// While dragging: when the dragged app passes the middle of its neighbour, swap their places
fun reorderFavsWhileDragging(ds: DragState, list: LazyListState, favs: List<AppItem>, store: Store) {
    val pkg = ds.pkg ?: return
    val order = favs.joinToString("|") { it.pkg }
    if (ds.pending == order) return
    ds.pending = null
    val idx = favs.indexOfFirst { it.pkg == pkg }
    if (idx < 0) return
    val items = list.layoutInfo.visibleItemsInfo
    val me = items.firstOrNull { it.key == "f$pkg" } ?: return
    val center = me.offset + me.size / 2f + ds.dy
    val nextApp = favs.getOrNull(idx + 1)
    val prevApp = favs.getOrNull(idx - 1)
    val next = nextApp?.let { n -> items.firstOrNull { it.key == "f${n.pkg}" } }
    val prev = prevApp?.let { p -> items.firstOrNull { it.key == "f${p.pkg}" } }
    if (nextApp != null && next != null && center > next.offset + next.size / 2f) {
        ds.pending = order
        store.moveFav(pkg, nextApp.pkg)
        ds.dy -= next.size
    } else if (prevApp != null && prev != null && center < prev.offset + prev.size / 2f) {
        ds.pending = order
        store.moveFav(pkg, prevApp.pkg)
        ds.dy += prev.size
    }
}

// One favorite on the home screen.
// Tap = open the app. Long-press and move = drag it to a new place. Long-press and let go = options menu.
@Composable
fun DragFavRow(
    a: AppItem, pal: Pal, icon: Int, badge: Int, sub: String?,
    ds: DragState, list: LazyListState, favs: List<AppItem>, store: Store,
    onOpen: () -> Unit, onMenu: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val favsNow by rememberUpdatedState(favs)
    val openNow by rememberUpdatedState(onOpen)
    val menuNow by rememberUpdatedState(onMenu)
    val dragging = ds.pkg == a.pkg
    Box(
        Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                val on = ds.pkg == a.pkg
                translationY = if (on) ds.dy else 0f
                scaleX = if (on) 1.04f else 1f
                scaleY = if (on) 1.04f else 1f
                alpha = if (on) 0.92f else 1f
            }
            .pointerInput(a.pkg) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val lp = awaitLongPressOrCancellation(down.id)
                    if (lp == null) {
                        // finger lifted quickly = a tap; finger moved = the list is scrolling
                        val c = currentEvent.changes.firstOrNull { it.id == down.id }
                        if (c != null && c.changedToUp()) openNow()
                        return@awaitEachGesture
                    }
                    ds.pkg = a.pkg
                    ds.dy = 0f
                    ds.pending = null
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    var total = 0f
                    val lifted = drag(lp.id) { change ->
                        val d = change.positionChange()
                        total += abs(d.x) + abs(d.y)
                        change.consume()
                        ds.dy += d.y
                        reorderFavsWhileDragging(ds, list, favsNow, store)
                    }
                    ds.pkg = null
                    ds.dy = 0f
                    ds.pending = null
                    if (lifted && total < 24f) menuNow()
                }
            }
    ) {
        FavRow(a, pal, icon, badge, sub, null, null)
    }
}
