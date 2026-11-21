package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import io.github.walterfr.jotmd.core.docRefsOf
import io.github.walterfr.jotmd.core.outlineOf

/**
 * Documento editável: [LazyColumn] de [BlockEditor] mais a [EditorToolbar].
 *
 * `key = block.id.v` preserva scroll e foco entre recomposições — sem isso o
 * LazyColumn recria itens a cada mudança de estado (Armadilha 3 do AI_CONTEXT).
 */
@Composable
fun EditorScreen(
    state: EditorState,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val outline = remember(state.doc) { outlineOf(state.doc) }
    val refs = remember(state.doc) { docRefsOf(state.doc) }
    var viewportHeightPx by remember { mutableStateOf(0) }

    LaunchedEffect(state.isTypewriterMode, state.focusedId, state.editValue.selection, viewportHeightPx) {
        if (!state.isTypewriterMode || viewportHeightPx <= 0) return@LaunchedEffect
        val fid = state.focusedId ?: return@LaunchedEffect
        val focusedIndex = state.doc.blocks.indexOfFirst { it.id == fid }
        if (focusedIndex >= 0) {
            listState.animateScrollToItem(focusedIndex, scrollOffset = -(viewportHeightPx / 3))
        }
    }

    LaunchedEffect(state.scrollRequestNonce) {
        if (state.scrollRequestNonce == 0) return@LaunchedEffect
        val targetIndex = state.scrollRequestBlockIndex ?: return@LaunchedEffect
        if (targetIndex in state.doc.blocks.indices) {
            val offset = if (state.isTypewriterMode && viewportHeightPx > 0) -(viewportHeightPx / 3) else 0
            listState.animateScrollToItem(targetIndex, scrollOffset = offset)
        }
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MdTokens.background)
                .onSizeChanged { viewportHeightPx = it.height },
            state = listState,
            contentPadding = MdTokens.documentPadding,
        ) {
            itemsIndexed(
                state.doc.blocks,
                key = { _, block -> block.id.v },
                contentType = { _, block -> block.type },
            ) { index, block ->
                val maxWidth = if (state.isSourceMode) MdTokens.sourceMaxColumnWidth else MdTokens.maxColumnWidth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (index == 0) 0.dp else gapBefore(block.type)),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    BlockEditor(
                        block = block,
                        state = state,
                        modifier = Modifier.widthIn(max = maxWidth).fillMaxWidth(),
                        outline = outline,
                        refs = refs,
                    )
                }
            }
        }
        EditorToolbar(state)
    }
}
