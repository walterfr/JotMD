package io.github.walterfr.jotmd.editor.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Comportamento de Backspace em cascata e par vazio estilo Typora (ver DESIGN.md). */
class SmartBackspaceTest {

    @Test
    fun `tarefa desmarcada e marcada viram item de lista mantendo o marcador`() {
        assertEquals(SmartBackspace("- a", 2), smartBackspace("- [ ] a", 6))
        assertEquals(SmartBackspace("* a", 2), smartBackspace("* [x] a", 6))
        assertEquals(SmartBackspace("+ a", 2), smartBackspace("+ [X] a", 6))
        assertEquals(SmartBackspace("- ", 2), smartBackspace("- [ ] ", 6))
    }

    @Test
    fun `tarefa com cursor no início da linha também remove só o checkbox e mantém cursor em 0`() {
        assertEquals(SmartBackspace("- a", 0), smartBackspace("- [ ] a", 0))
        assertEquals(SmartBackspace("  - a", 0), smartBackspace("  - [x] a", 0))
    }

    @Test
    fun `tarefa indentada após marcador posiciona cursor logo após o bullet indentado`() {
        assertEquals(SmartBackspace("  - a", 4), smartBackspace("  - [x] a", 8))
        assertEquals(SmartBackspace("\t* a", 3), smartBackspace("\t* [ ] a", 7))
    }

    @Test
    fun `lista na raiz remove o marcador e coloca cursor no início da linha`() {
        assertEquals(SmartBackspace("a", 0), smartBackspace("- a", 2))
        assertEquals(SmartBackspace("a", 0), smartBackspace("* a", 0))
        assertEquals(SmartBackspace("", 0), smartBackspace("+ ", 2))
        assertEquals(SmartBackspace("a", 0), smartBackspace("1. a", 3))
        assertEquals(SmartBackspace("a", 0), smartBackspace("12) a", 0))
    }

    @Test
    fun `lista indentada com espaços ou tab remove apenas um nível de indentação`() {
        assertEquals(SmartBackspace("- a", 2), smartBackspace("  - a", 4))
        assertEquals(SmartBackspace("- a", 0), smartBackspace("  - a", 0))
        assertEquals(SmartBackspace("  - a", 4), smartBackspace("    - a", 6))
        assertEquals(SmartBackspace("* a", 2), smartBackspace("\t* a", 3))
        assertEquals(SmartBackspace("\t1. a", 4), smartBackspace("\t\t1. a", 5))
        assertEquals(SmartBackspace("2) a", 3), smartBackspace("  2) a", 5))
    }

    @Test
    fun `múltiplas linhas dentro do bloco atuam só na linha do cursor`() {
        assertEquals(
            SmartBackspace("- um\n- dois", 7),
            smartBackspace("- um\n  - dois", 9),
        )
        assertEquals(
            SmartBackspace("- um\ndois\n- tres", 5),
            smartBackspace("- um\n- dois\n- tres", 5),
        )
        assertEquals(
            SmartBackspace("- um\n- dois", 7),
            smartBackspace("- um\n- [ ] dois", 11),
        )
    }

    @Test
    fun `citação simples e aninhada removem um nível do fim do prefixo`() {
        assertEquals(SmartBackspace("a", 0), smartBackspace("> a", 2))
        assertEquals(SmartBackspace("a", 0), smartBackspace("> a", 0))
        assertEquals(SmartBackspace("a", 0), smartBackspace(">a", 1))
        assertEquals(SmartBackspace("", 0), smartBackspace(">", 1))
        assertEquals(SmartBackspace("> a", 2), smartBackspace("> > a", 4))
        assertEquals(SmartBackspace("> a", 0), smartBackspace("> > a", 0))
        assertEquals(SmartBackspace("> ", 2), smartBackspace("> >", 3))
        assertEquals(SmartBackspace("  a", 2), smartBackspace("  > a", 4))
        assertEquals(SmartBackspace("  a", 0), smartBackspace("  > a", 0))
    }

    @Test
    fun `heading de nível 1 a 6 remove o prefixo inteiro de uma vez`() {
        for (level in 1..6) {
            val hashes = "#".repeat(level)
            assertEquals(SmartBackspace("Título", 0), smartBackspace("$hashes Título", level + 1))
            assertEquals(SmartBackspace("Título", 0), smartBackspace("$hashes Título", 0))
        }
        assertEquals(SmartBackspace("", 0), smartBackspace("### ", 4))
    }

    @Test
    fun `par auto-fechado vazio apaga ambos os caracteres de uma vez`() {
        val pairs = listOf("()", "[]", "{}", "\"\"", "''", "**", "``", "~~", "__")
        for (pair in pairs) {
            assertEquals(SmartBackspace("", 0), smartBackspace(pair, 1))
            assertEquals(SmartBackspace("ab", 1), smartBackspace("a${pair}b", 2))
        }
        assertEquals(SmartBackspace("### ", 4), smartBackspace("### ()", 5))
        assertEquals(SmartBackspace("- [ ] ", 6), smartBackspace("- [ ] ()", 7))
    }

    @Test
    fun `retorna null quando não há marcador no cursor, par não é vazio ou bounds inválidos`() {
        assertNull(smartBackspace("texto solto", 5))
        assertNull(smartBackspace("texto solto", 0))
        assertNull(smartBackspace("- item", 4))
        assertNull(smartBackspace("- [ ] item", 2))
        assertNull(smartBackspace("### título", 2))
        assertNull(smartBackspace("####### não é heading", 8))
        assertNull(smartBackspace("#tag", 0))
        assertNull(smartBackspace("(a)", 1))
        assertNull(smartBackspace("(a)", 2))
        assertNull(smartBackspace("()", 0))
        assertNull(smartBackspace("()", 2))
        assertNull(smartBackspace("", 0))
        assertNull(smartBackspace("- a", -1))
        assertNull(smartBackspace("- a", 4))
    }
}
