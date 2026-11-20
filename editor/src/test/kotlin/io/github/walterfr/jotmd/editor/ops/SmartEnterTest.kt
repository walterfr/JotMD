package io.github.walterfr.jotmd.editor.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `smartSplitInline fecha e reabre spans abertos nos dois lados`() {
        assertEquals(Triple("**olá **", "**mundo**", 2), smartSplitInline("**olá ", "mundo**"))
        assertEquals(Triple("*itá*", "*lico*", 1), smartSplitInline("*itá", "lico*"))
        assertEquals(Triple("~~ris~~", "~~cado~~", 2), smartSplitInline("~~ris", "cado~~"))
        assertEquals(Triple("==mar==", "==cado==", 2), smartSplitInline("==mar", "cado=="))
        assertEquals(Triple("`cód`", "`igo`", 1), smartSplitInline("`cód", "igo`"))
        assertEquals(
            Triple("linha 1\n**olá **", "**mundo**\nlinha 3", 2),
            smartSplitInline("linha 1\n**olá ", "mundo**\nlinha 3"),
        )
    }

    @Test
    fun `smartSplitInline não confunde negrito com asterisco isolado`() {
        assertNull(smartSplitInline("**negrito", " texto*"))
        assertNull(smartSplitInline("*itálico", " texto**"))
        assertEquals(Triple("**ok** *ab*", "*cd*", 1), smartSplitInline("**ok** *ab", "cd*"))
        assertEquals(Triple("*ok* **ab**", "**cd**", 2), smartSplitInline("*ok* **ab", "cd**"))
    }

    @Test
    fun `smartSplitInline retorna null sem fechamento, contagem par ou ponta vazia`() {
        assertNull(smartSplitInline("**sem fechar", ""))
        assertNull(smartSplitInline("**fechado** ", "depois"))
        assertNull(smartSplitInline("**", "texto**"))
        assertNull(smartSplitInline("**texto", "**"))
        assertNull(smartSplitInline("*", "texto*"))
        assertNull(smartSplitInline("*texto", "*"))
        assertNull(smartSplitInline("`", "code`"))
        assertNull(smartSplitInline("texto", "comum"))
    }

    @Test
    fun `isFrontMatterTrigger só dispara com --- no fim do bloco zero`() {
        assertTrue(isFrontMatterTrigger(0, "---", 3))
        assertFalse(isFrontMatterTrigger(1, "---", 3))
        assertFalse(isFrontMatterTrigger(0, "---", 2))
        assertFalse(isFrontMatterTrigger(0, "---", 0))
        assertFalse(isFrontMatterTrigger(0, "--", 2))
        assertFalse(isFrontMatterTrigger(0, "----", 4))
        assertFalse(isFrontMatterTrigger(0, "--- ", 4))
    }
}
