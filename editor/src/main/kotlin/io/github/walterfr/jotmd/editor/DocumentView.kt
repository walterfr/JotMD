package io.github.walterfr.jotmd.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.walterfr.jotmd.core.DocState

/**
 * Documento inteiro, só leitura.
 *
 * `key = block.id.v` é o que preserva scroll e, mais para frente, o foco — sem
 * ele o LazyColumn recria itens a cada reparse (Armadilha 3 do AI_CONTEXT).
 */
@Composable
fun DocumentView(
    doc: DocState,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().background(MdTokens.background),
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
    ) {
        itemsIndexed(doc.blocks, key = { _, block -> block.id.v }) { index, block ->
            BlockRenderer(
                block = block,
                modifier = Modifier.padding(top = if (index == 0) 0.dp else gapBefore(block.type)),
            )
        }
    }
}
