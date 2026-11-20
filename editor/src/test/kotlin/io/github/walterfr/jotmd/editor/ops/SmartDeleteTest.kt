package io.github.walterfr.jotmd.editor.ops

import io.github.walterfr.jotmd.core.Block
import io.github.walterfr.jotmd.core.BlockIdSource
import io.github.walterfr.jotmd.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Testes de Delete inteligente no fim do bloco ou em bloco vazio (ver DESIGN.md). */
class SmartDeleteTest {

    @Test
    fun `bloco vazio com próximo bloco remove o atual e foca o seguinte no offset zero`() {
        val ids = BlockIdSource()
        val empty = Block(ids.next(), BlockType.PARAGRAPH, "", "\n\n")
        val next = Block(ids.next(), BlockType.PARAGRAPH, "olá", "\n")

        assertEquals(
            SmartDelete.RemoveBlock(listOf(next), next.id, 0),
            smartDeleteAtEnd(listOf(empty, next), 0),
        )
    }

    @Test
    fun `bloco vazio no fim da lista remove o atual e foca o anterior no offset zero`() {
        val ids = BlockIdSource()
        val prev = Block(ids.next(), BlockType.PARAGRAPH, "antes", "\n\n")
        val empty = Block(ids.next(), BlockType.PARAGRAPH, "", "\n")

        assertEquals(
            SmartDelete.RemoveBlock(listOf(prev), prev.id, 0),
            smartDeleteAtEnd(listOf(prev, empty), 1),
        )
    }

    @Test
    fun `próximo bloco horizontal rule ou em branco é removido mantendo o foco no fim do atual`() {
        val ids = BlockIdSource()
        val cur = Block(ids.next(), BlockType.PARAGRAPH, "abc", "\n\n")
        val hr = Block(ids.next(), BlockType.HORIZONTAL_RULE, "---", "\n\n")
        val tail = Block(ids.next(), BlockType.PARAGRAPH, "fim", "\n")

        assertEquals(
            SmartDelete.RemoveBlock(listOf(cur, tail), cur.id, 3),
            smartDeleteAtEnd(listOf(cur, hr, tail), 0),
        )

        val blank = Block(ids.next(), BlockType.PARAGRAPH, "   ", "\n")
        assertEquals(
            SmartDelete.RemoveBlock(listOf(cur), cur.id, 3),
            smartDeleteAtEnd(listOf(cur, blank), 0),
        )
    }

    @Test
    fun `próximo bloco com conteúdo sinaliza MergeNext`() {
        val ids = BlockIdSource()
        val cur = Block(ids.next(), BlockType.PARAGRAPH, "um", "\n\n")
        val next = Block(ids.next(), BlockType.HEADING, "# dois", "\n")

        assertEquals(
            SmartDelete.MergeNext(next.id),
            smartDeleteAtEnd(listOf(cur, next), 0),
        )
    }

    @Test
    fun `retorna null em bloco único, último bloco com conteúdo ou índice inválido`() {
        val ids = BlockIdSource()
        val singleEmpty = Block(ids.next(), BlockType.PARAGRAPH, "", "")
        val singleText = Block(ids.next(), BlockType.PARAGRAPH, "texto", "")

        assertNull(smartDeleteAtEnd(listOf(singleEmpty), 0))
        assertNull(smartDeleteAtEnd(listOf(singleText), 0))
        assertNull(smartDeleteAtEnd(listOf(singleText), -1))
        assertNull(smartDeleteAtEnd(listOf(singleText), 1))
        assertNull(smartDeleteAtEnd(emptyList(), 0))
    }
}
