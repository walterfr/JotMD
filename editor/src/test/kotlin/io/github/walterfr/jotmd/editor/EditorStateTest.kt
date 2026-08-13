package io.github.walterfr.jotmd.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.walterfr.jotmd.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorStateTest {

    @Test
    fun `focar carrega o source cru, sem marcadores, com cursor no fim`() {
        val s = EditorState("# Título\n\nparágrafo\n")
        s.focus(s.doc.blocks[0])
        assertEquals("# Título", s.editValue.text)
        assertEquals(TextRange(8), s.editValue.selection)
    }

    @Test
    fun `editar propaga pro doc na hora, sem esperar perder o foco`() {
        val s = EditorState("texto\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto novo", TextRange(10)))
        assertEquals("texto novo", s.doc.blocks[0].source)
    }

    @Test
    fun `trailing do bloco não muda com a edição`() {
        val s = EditorState("um\n\ndois\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("UM", TextRange(2)))
        assertEquals("\n\n", s.doc.blocks[0].trailing)
        assertEquals("dois", s.doc.blocks[1].source)
    }

    @Test
    fun `trocar de bloco preserva a edição do anterior`() {
        val s = EditorState("um\n\ndois\n")
        val (primeiro, segundo) = s.doc.blocks
        s.focus(primeiro)
        s.edit(TextFieldValue("UM", TextRange(2)))
        s.focus(segundo)
        assertEquals("UM", s.doc.blocks[0].source)
        assertEquals("dois", s.editValue.text)
    }

    @Test
    fun `wrapSelection envolve o trecho selecionado e reposiciona a seleção`() {
        val s = EditorState("um dois tres\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("um dois tres", TextRange(3, 7))) // "dois"
        s.wrapSelection("**")
        assertEquals("um **dois** tres", s.editValue.text)
        assertEquals(TextRange(5, 9), s.editValue.selection)
        assertEquals("um **dois** tres", s.doc.blocks[0].source)
    }

    @Test
    fun `wrapSelection sem cursor, sem seleção, insere os marcadores colados`() {
        val s = EditorState("texto\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto", TextRange(5)))
        s.wrapSelection("**")
        assertEquals("texto****", s.editValue.text)
        assertEquals(TextRange(7, 7), s.editValue.selection)
    }

    @Test
    fun `wrapSelection sem bloco focado não faz nada`() {
        val s = EditorState("um\n")
        s.wrapSelection("**")
        assertEquals("", s.editValue.text)
    }

    @Test
    fun `release com id diferente do focado não limpa o foco`() {
        val s = EditorState("um\n\ndois\n")
        val (primeiro, segundo) = s.doc.blocks
        s.focus(primeiro)
        s.release(segundo.id)
        assertEquals(primeiro.id, s.focusedId)
    }

    @Test
    fun `release com o id certo limpa o foco`() {
        val s = EditorState("um\n")
        val block = s.doc.blocks[0]
        s.focus(block)
        s.release(block.id)
        assertNull(s.focusedId)
    }

    @Test
    fun `load reseta foco, buffer e histórico de undo`() {
        val s = EditorState("um\n")
        s.focus(s.doc.blocks[0])
        s.load("novo\n")
        assertNull(s.focusedId)
        assertEquals("", s.editValue.text)
        assertEquals("novo", s.doc.blocks[0].source)
    }

    // ---- splitAtCursor / mergeWithPreviousBlock ----

    @Test
    fun `splitAtCursor no meio do texto cria dois blocos e foca o segundo no início`() {
        val s = EditorState("um dois tres\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("um dois tres", TextRange(7))) // cursor logo antes de "tres"
        s.splitAtCursor()

        assertEquals(2, s.doc.blocks.size)
        assertEquals("um dois", s.doc.blocks[0].source)
        assertEquals(" tres", s.doc.blocks[1].source)
        assertEquals(s.doc.blocks[1].id, s.focusedId)
        assertEquals(TextRange(0), s.editValue.selection)
    }

    @Test
    fun `splitAtCursor no fim do texto cria bloco vazio, não pula pro próximo bloco existente`() {
        // Enter no fim de "um" — bug real pego no tablet: reparse descarta o
        // bloco "after" vazio (parse() nunca cria bloco de texto vazio), e sem
        // este caso especial o foco pulava direto pro "dois", abaixo.
        val s = EditorState("um\n\ndois\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("um", TextRange(2)))
        s.splitAtCursor()

        assertEquals(3, s.doc.blocks.size)
        assertEquals("um", s.doc.blocks[0].source)
        assertEquals("", s.doc.blocks[1].source)
        assertEquals("dois", s.doc.blocks[2].source)
        assertEquals(s.doc.blocks[1].id, s.focusedId)
        assertEquals("", s.editValue.text)
    }

    @Test
    fun `splitAtCursor sem bloco focado não faz nada`() {
        val s = EditorState("um\n")
        s.splitAtCursor()
        assertEquals(1, s.doc.blocks.size)
    }

    @Test
    fun `mergeWithPreviousBlock com blocos separados por linha em branco reposiciona o cursor, não junta texto`() {
        // dois blocos de topo vindos de parse() nunca colam em um parágrafo só:
        // CommonMark exige linha em branco pra separá-los, e essa linha em
        // branco sobrevive à fusão. O que muda de fato é o foco, que volta pro
        // fim do bloco anterior — é o "backspace só engole a linha em branco"
        // que edição de texto real também faz.
        val s = EditorState("primeiro\n\nsegundo\n")
        val segundo = s.doc.blocks[1]
        s.mergeWithPreviousBlock(segundo.id)

        assertEquals(2, s.doc.blocks.size)
        assertEquals("primeiro", s.doc.blocks[0].source)
        assertEquals("segundo", s.doc.blocks[1].source)
        assertEquals(s.doc.blocks[0].id, s.focusedId)
        assertEquals(TextRange(8), s.editValue.selection) // fim de "primeiro"
    }

    @Test
    fun `mergeWithPreviousBlock no primeiro bloco não faz nada`() {
        val s = EditorState("um\n\ndois\n")
        val primeiro = s.doc.blocks[0]
        s.mergeWithPreviousBlock(primeiro.id)
        assertEquals(2, s.doc.blocks.size)
    }

    @Test
    fun `splitAt seguido de reparse detecta cerca aberta e absorve o resto`() {
        val s = EditorState("texto\n\noutro\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("```kotlin", TextRange(9)))
        s.splitAtCursor()

        // "```kotlin" vira uma cerca não fechada — o reparse dentro de
        // splitAtCursor deve engolir o bloco seguinte ("outro") junto
        assertTrue(s.doc.blocks.any { it.source.contains("outro") && it.type == BlockType.CODE_FENCE })
    }

    // ---- navegação ----

    @Test
    fun `focusPrevious pula pro bloco anterior com cursor no fim`() {
        val s = EditorState("um\n\ndois\n")
        val (primeiro, segundo) = s.doc.blocks
        s.focusPrevious(segundo.id)
        assertEquals(primeiro.id, s.focusedId)
        assertEquals(TextRange(primeiro.source.length), s.editValue.selection)
    }

    @Test
    fun `focusPrevious no primeiro bloco não faz nada`() {
        val s = EditorState("um\n\ndois\n")
        s.focusPrevious(s.doc.blocks[0].id)
        assertNull(s.focusedId)
    }

    @Test
    fun `focusNext pula pro bloco seguinte com cursor no início`() {
        val s = EditorState("um\n\ndois\n")
        val (primeiro, segundo) = s.doc.blocks
        s.focusNext(primeiro.id)
        assertEquals(segundo.id, s.focusedId)
        assertEquals(TextRange(0), s.editValue.selection)
    }

    @Test
    fun `focusNext no último bloco não faz nada`() {
        val s = EditorState("um\n\ndois\n")
        s.focusNext(s.doc.blocks[1].id)
        assertNull(s.focusedId)
    }

    // ---- undo/redo ----

    @Test
    fun `undo desfaz uma edição`() {
        val s = EditorState("texto\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto novo", TextRange(10)))
        s.undo()
        assertEquals("texto", s.doc.blocks[0].source)
    }

    @Test
    fun `redo refaz o que o undo desfez`() {
        val s = EditorState("texto\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto novo", TextRange(10)))
        s.undo()
        s.redo()
        assertEquals("texto novo", s.doc.blocks[0].source)
    }

    @Test
    fun `undo sem histórico não quebra`() {
        val s = EditorState("texto\n")
        s.undo()
        assertEquals("texto", s.doc.blocks[0].source)
    }

    @Test
    fun `undo desfaz um split inteiro, não caractere por caractere`() {
        val s = EditorState("um dois tres\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("um dois tres", TextRange(7)))
        s.splitAtCursor()
        assertEquals(2, s.doc.blocks.size)

        s.undo()
        assertEquals(1, s.doc.blocks.size)
        assertEquals("um dois tres", s.doc.blocks[0].source)
    }
}
