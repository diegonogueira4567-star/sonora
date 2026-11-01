package com.sonora.player.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Minimal drag-and-drop reordering for LazyColumn. Dragging starts only from a handle, so normal
 * list scrolling is never hijacked. Indexes are absolute LazyColumn indexes.
 */
class ReorderableListState(
    val listState: LazyListState,
    private val scope: CoroutineScope,
    private val canMoveTo: (Int) -> Boolean,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onDragEnd: (from: Int, to: Int) -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set
    private var initialIndex = -1

    fun start(index: Int) {
        draggingIndex = index
        initialIndex = index
        dragOffset = 0f
    }

    fun drag(delta: Float) {
        val current = draggingIndex ?: return
        dragOffset += delta
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == current } ?: return
        val center = item.offset + dragOffset + item.size / 2f
        val target = info.visibleItemsInfo.firstOrNull {
            it.index != current && canMoveTo(it.index) && center > it.offset && center < it.offset + it.size
        }
        if (target != null) {
            onMove(current, target.index)
            dragOffset += (item.offset - target.offset)
            draggingIndex = target.index
        }
        val top = item.offset + dragOffset
        val bottom = top + item.size
        when {
            bottom > info.viewportEndOffset - EDGE -> scope.launch { listState.scrollBy(SCROLL_STEP) }
            top < info.viewportStartOffset + EDGE -> scope.launch { listState.scrollBy(-SCROLL_STEP) }
        }
    }

    fun end() {
        val from = initialIndex
        val to = draggingIndex
        draggingIndex = null
        dragOffset = 0f
        initialIndex = -1
        if (to != null && from >= 0 && from != to) onDragEnd(from, to)
    }

    private companion object {
        const val EDGE = 96
        const val SCROLL_STEP = 24f
    }
}

@Composable
fun rememberReorderableListState(
    listState: LazyListState,
    canMoveTo: (Int) -> Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onDragEnd: (from: Int, to: Int) -> Unit,
): ReorderableListState {
    val scope = rememberCoroutineScope()
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onDragEnd)
    val can by rememberUpdatedState(canMoveTo)
    return remember(listState) {
        ReorderableListState(listState, scope, { can(it) }, { a, b -> move(a, b) }, { a, b -> end(a, b) })
    }
}

fun Modifier.reorderableItem(state: ReorderableListState, index: Int): Modifier {
    val dragging = state.draggingIndex == index
    return this
        .zIndex(if (dragging) 1f else 0f)
        .graphicsLayer {
            translationY = if (dragging) state.dragOffset else 0f
            shadowElevation = if (dragging) 16f else 0f
            alpha = if (dragging) 0.95f else 1f
        }
}

@Composable
fun Modifier.reorderHandle(state: ReorderableListState, index: Int, key: Any): Modifier {
    val currentIndex by rememberUpdatedState(index)
    return this.pointerInput(state, key) {
        detectDragGestures(
            onDragStart = { state.start(currentIndex) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
        )
    }
}
