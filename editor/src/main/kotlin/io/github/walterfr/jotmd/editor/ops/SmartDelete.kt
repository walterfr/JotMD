package io.github.walterfr.jotmd.editor.ops

import io.github.walterfr.jotmd.core.Block
import io.github.walterfr.jotmd.core.BlockId
import io.github.walterfr.jotmd.core.BlockType

/**
 * Ações de `Delete` no fim do bloco focado (ou em bloco vazio) no estilo
 * Typora (ver DESIGN.md, "Backspace e Delete"):
 *
 * - Bloco atual vazio com outros blocos: remove o próprio bloco e foca o
 *   próximo (ou o último restante) no cursor `0`.
 * - Próximo bloco é `HORIZONTAL_RULE` ou em branco: remove o próximo bloco e
 *   mantém o cursor no fim do bloco atual.
 * - Próximo bloco tem conteúdo: sinaliza [MergeNext] com o ID seguinte para o
 *   estado do editor fundir pelo caminho normal de merge.
 *
 * Devolve `null` quando não há nada para remover adiante.
 */
sealed interface SmartDelete {
    data class RemoveBlock(val blocks: List<Block>, val focusId: BlockId?, val cursor: Int) : SmartDelete
    data class MergeNext(val nextId: BlockId) : SmartDelete
}

fun smartDeleteAtEnd(blocks: List<Block>, index: Int): SmartDelete? {
    if (index !in blocks.indices) return null

    val current = blocks[index]
    if (current.source.isEmpty() && blocks.size > 1) {
        val remaining = blocks.toMutableList().apply { removeAt(index) }
        val target = remaining.getOrNull(index) ?: remaining.lastOrNull()
        return SmartDelete.RemoveBlock(remaining, target?.id, 0)
    }

    if (index + 1 < blocks.size) {
        val next = blocks[index + 1]
        if (next.type == BlockType.HORIZONTAL_RULE || next.source.isBlank()) {
            val remaining = blocks.toMutableList().apply { removeAt(index + 1) }
            return SmartDelete.RemoveBlock(remaining, current.id, current.source.length)
        }
        return SmartDelete.MergeNext(next.id)
    }

    return null
}
