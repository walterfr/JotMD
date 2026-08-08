package io.github.walterfr.jotmd.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.walterfr.jotmd.core.parse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditorStateTest {

    private fun state(text: String) = EditorState(parse(text))

    @Test
    fun `focar carrega o source cru, sem marcadores, com cursor no fim`() {
        val s = state("# Título\n\nparágrafo\n")
        s.focus(s.doc.blocks[0])
        assertEquals("# Título", s.editValue.text)
        assertEquals(TextRange(8), s.editValue.selection)
    }

    @Test
    fun `editar propaga pro doc na hora, sem esperar perder o foco`() {
        val s = state("texto\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto novo", TextRange(10)))
        assertEquals("texto novo", s.doc.blocks[0].source)
    }

    @Test
    fun `trailing do bloco não muda com a edição`() {
        val s = state("um\n\ndois\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("UM", TextRange(2)))
        assertEquals("\n\n", s.doc.blocks[0].trailing)
        assertEquals("dois", s.doc.blocks[1].source)
    }

    @Test
    fun `trocar de bloco preserva a edição do anterior`() {
        val s = state("um\n\ndois\n")
        val (primeiro, segundo) = s.doc.blocks
        s.focus(primeiro)
        s.edit(TextFieldValue("UM", TextRange(2)))
        s.focus(segundo)
        assertEquals("UM", s.doc.blocks[0].source)
        assertEquals("dois", s.editValue.text)
    }

    @Test
    fun `wrapSelection envolve o trecho selecionado e reposiciona a seleção`() {
        val s = state("um dois tres\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("um dois tres", TextRange(3, 7))) // "dois"
        s.wrapSelection("**")
        assertEquals("um **dois** tres", s.editValue.text)
        assertEquals(TextRange(5, 9), s.editValue.selection)
        assertEquals("um **dois** tres", s.doc.blocks[0].source)
    }

    @Test
    fun `wrapSelection sem cursor, sem seleção, insere os marcadores colados`() {
        val s = state("texto\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto", TextRange(5)))
        s.wrapSelection("**")
        assertEquals("texto****", s.editValue.text)
        assertEquals(TextRange(7, 7), s.editValue.selection)
    }

    @Test
    fun `wrapSelection sem bloco focado não faz nada`() {
        val s = state("um\n")
        s.wrapSelection("**")
        assertEquals("", s.editValue.text)
    }

    @Test
    fun `release com id diferente do focado não limpa o foco`() {
        val s = state("um\n\ndois\n")
        val (primeiro, segundo) = s.doc.blocks
        s.focus(primeiro)
        s.release(segundo.id)
        assertEquals(primeiro.id, s.focusedId)
    }

    @Test
    fun `release com o id certo limpa o foco`() {
        val s = state("um\n")
        val block = s.doc.blocks[0]
        s.focus(block)
        s.release(block.id)
        assertNull(s.focusedId)
    }

    @Test
    fun `load reseta foco e buffer de edição`() {
        val s = state("um\n")
        s.focus(s.doc.blocks[0])
        s.load(parse("novo\n"))
        assertNull(s.focusedId)
        assertEquals("", s.editValue.text)
        assertEquals("novo", s.doc.blocks[0].source)
    }
}
