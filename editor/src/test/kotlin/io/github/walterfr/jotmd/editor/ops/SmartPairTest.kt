package io.github.walterfr.jotmd.editor.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Testes de auto-close, overtype e surroundPair (ver DESIGN.md, "Auto-pair"). */
class SmartPairTest {

    @Test
    fun `surroundPair envolve seleção com todos os delimitadores configurados`() {
        for ((open, close) in AUTO_PAIRS) {
            assertEquals(
                SmartPair("olá ${open}mundo${close}", 5, 10),
                applySmartPair("olá mundo", 4, 9, "olá $open", 5, 5),
            )
        }
        // Seleção invertida também normaliza e envolve mantendo o trecho selecionado.
        assertEquals(
            SmartPair("olá *mundo*", 5, 10),
            applySmartPair("olá mundo", 9, 4, "olá *", 5, 5),
        )
        // Segunda aplicação de '*' produz negrito **mundo**.
        assertEquals(
            SmartPair("olá **mundo**", 6, 11),
            applySmartPair("olá *mundo*", 5, 10, "olá ***", 6, 6),
        )
    }

    @Test
    fun `auto-close fecha abertura quando próximo caractere permite`() {
        assertEquals(SmartPair("()", 1), applySmartPair("", 0, 0, "(", 1, 1))
        assertEquals(SmartPair("[]", 1), applySmartPair("", 0, 0, "[", 1, 1))
        assertEquals(SmartPair("{}", 1), applySmartPair("", 0, 0, "{", 1, 1))
        assertEquals(SmartPair("\"\"", 1), applySmartPair("", 0, 0, "\"", 1, 1))
        assertEquals(SmartPair("''", 1), applySmartPair("", 0, 0, "'", 1, 1))
        assertEquals(SmartPair("``", 1), applySmartPair("", 0, 0, "`", 1, 1))

        assertEquals(SmartPair("a() b", 2), applySmartPair("a b", 1, 1, "a( b", 2, 2))
        assertEquals(SmartPair("(())", 2), applySmartPair("()", 1, 1, "(()", 2, 2))
        assertEquals(SmartPair("[{}]", 2), applySmartPair("[]", 1, 1, "[{]", 2, 2))
        assertEquals(SmartPair("{()}", 2), applySmartPair("{}", 1, 1, "{(}", 2, 2))
        assertNull(applySmartPair("a b", 2, 2, "a (b", 3, 3))
    }

    @Test
    fun `auto-close não dispara para aspas ou crase após alfanumérico ou caractere igual`() {
        assertNull(applySmartPair("don", 3, 3, "don'", 4, 4))
        assertNull(applySmartPair("item1", 5, 5, "item1\"", 6, 6))
        assertNull(applySmartPair("a", 1, 1, "a`", 2, 2))
        assertNull(applySmartPair("``", 2, 2, "```", 3, 3))
        assertNull(applySmartPair("\"\"", 2, 2, "\"\"\"", 3, 3))
        assertNull(applySmartPair("''", 2, 2, "'''", 3, 3))
        // '*' / '~' / '=' só atuam em surroundPair, não auto-fecham ao digitar sem seleção.
        assertNull(applySmartPair("*", 1, 1, "**", 2, 2))
        assertNull(applySmartPair("~", 1, 1, "~~", 2, 2))
        assertNull(applySmartPair("=", 1, 1, "==", 2, 2))
    }

    @Test
    fun `overtype consome fechamento duplicado e respeita escape em aspas e crase`() {
        assertEquals(SmartPair("()", 2), applySmartPair("()", 1, 1, "())", 2, 2))
        assertEquals(SmartPair("[]", 2), applySmartPair("[]", 1, 1, "[]]", 2, 2))
        assertEquals(SmartPair("{}", 2), applySmartPair("{}", 1, 1, "{}}", 2, 2))
        assertEquals(SmartPair("\"\"", 2), applySmartPair("\"\"", 1, 1, "\"\"\"", 2, 2))
        assertEquals(SmartPair("''", 2), applySmartPair("''", 1, 1, "'''", 2, 2))
        assertEquals(SmartPair("``", 2), applySmartPair("``", 1, 1, "```", 2, 2))

        assertNull(applySmartPair("\"\\\"", 2, 2, "\"\\\"\"", 3, 3))
        assertNull(applySmartPair("'\\'", 2, 2, "'\\''", 3, 3))
        assertNull(applySmartPair("`\\`", 2, 2, "`\\``", 3, 3))
        assertEquals(SmartPair("(\\)", 3), applySmartPair("(\\)", 2, 2, "(\\))", 3, 3))
    }

    @Test
    fun `ignora seleção ativa no novo valor, colagens, apagamento e bounds inválidos`() {
        assertNull(applySmartPair("a", 0, 0, "(a", 0, 2))
        assertNull(applySmartPair("", 0, 0, "()", 2, 2))
        assertNull(applySmartPair("(a)", 1, 2, "()", 1, 1))
        assertNull(applySmartPair("abc", 0, 3, "x", 1, 1))
        assertNull(applySmartPair("abc", -1, 0, "(abc", 0, 0))
    }
}
