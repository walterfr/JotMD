package io.github.walterfr.jotmd.editor.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Comportamento observado no Typora 1.x (ver DESIGN.md, "Enter em listas e citações"). */
class SmartEnterTest {

    private fun enter(text: String, cursor: Int = text.length) = smartEnter(text, cursor)

    @Test
    fun `lista continua com o mesmo marcador`() {
        assertEquals(SmartEnter.Insert("- um\n- ", 7), enter("- um"))
        assertEquals(SmartEnter.Insert("* um\n* ", 7), enter("* um"))
    }

    @Test
    fun `lista aninhada mantém a indentação`() {
        assertEquals(SmartEnter.Insert("- a\n  - b\n  - ", 14), enter("- a\n  - b"))
    }

    @Test
    fun `numerada incrementa e mantém o delimitador`() {
        assertEquals(SmartEnter.Insert("1. a\n2. ", 8), enter("1. a"))
        assertEquals(SmartEnter.Insert("9) a\n10) ", 9), enter("9) a"))
    }

    @Test
    fun `tarefa continua sempre desmarcada`() {
        assertEquals(SmartEnter.Insert("- [x] a\n- [ ] ", 14), enter("- [x] a"))
    }

    @Test
    fun `citação abre novo parágrafo dentro dela`() {
        assertEquals(SmartEnter.Insert("> a\n>\n> ", 8), enter("> a"))
    }

    @Test
    fun `enter no meio do item leva o resto para o novo item`() {
        assertEquals(SmartEnter.Insert("- ab\n- cd", 7), enter("- abcd", 4))
    }

    @Test
    fun `item vazio sai da lista e tira o marcador`() {
        assertEquals(SmartEnter.Exit("- um"), enter("- um\n- "))
        assertEquals(SmartEnter.Exit("1. a"), enter("1. a\n2. "))
        assertEquals(SmartEnter.Exit("- [ ] a"), enter("- [ ] a\n- [ ] "))
    }

    @Test
    fun `citação vazia sai e leva junto o separador vazio`() {
        assertEquals(SmartEnter.Exit("> a"), enter("> a\n>\n> "))
    }

    @Test
    fun `linha sem marcador não é com a gente`() {
        assertNull(enter("texto solto"))
        assertNull(enter("- a\ncontinuação"))
    }
}
