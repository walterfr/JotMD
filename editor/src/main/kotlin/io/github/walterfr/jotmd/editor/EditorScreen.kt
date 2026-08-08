package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().background(MdTokens.background),
            state = listState,
            contentPadding = MdTokens.documentPadding,
        ) {
            itemsIndexed(state.doc.blocks, key = { _, block -> block.id.v }) { index, block ->
                BlockEditor(
                    block = block,
                    state = state,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else gapBefore(block.type)),
                )
            }
        }
        EditorToolbar(state)
    }
}
