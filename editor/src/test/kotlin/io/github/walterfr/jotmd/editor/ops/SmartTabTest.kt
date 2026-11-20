package io.github.walterfr.jotmd.editor.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Testes de indentação e desindentação de `Tab` / `Shift+Tab` (ver DESIGN.md). */
class SmartTabTest {

    @Test
    fun `tab em lista ou tarefa adiciona dois espaços no início da linha`() {
        assertEquals(SmartTab("  - a", 4), smartTab("- a", 2))
        assertEquals(SmartTab("  * a", 5), smartTab("* a", 3))
        assertEquals(SmartTab("  + a", 2), smartTab("+ a", 0))
        assertEquals(SmartTab("  1. a", 5), smartTab("1. a", 3))
        assertEquals(SmartTab("  1) a", 6), smartTab("1) a", 4))
        assertEquals(SmartTab("  - [ ] a", 8), smartTab("- [ ] a", 6))
        assertEquals(SmartTab("  - [x] a", 9), smartTab("- [x] a", 7))
    }

    @Test
    fun `shift-tab em lista indentada remove tab ou até dois espaços`() {
        assertEquals(SmartTab("- a", 2), smartTab("  - a", 4, shift = true))
        assertEquals(SmartTab("  - a", 4), smartTab("    - a", 6, shift = true))
        assertEquals(SmartTab("- a", 2), smartTab(" - a", 3, shift = true))
        assertEquals(SmartTab("- a", 2), smartTab("\t- a", 3, shift = true))
        assertEquals(SmartTab("- [ ] a", 6), smartTab("  - [ ] a", 8, shift = true))
        assertEquals(SmartTab("1. a", 0), smartTab("  1. a", 0, shift = true))
    }

    @Test
    fun `shift-tab em lista na raiz remove o marcador mantendo cursor não negativo`() {
        assertEquals(SmartTab("a", 0), smartTab("- a", 2, shift = true))
        assertEquals(SmartTab("a", 1), smartTab("- a", 3, shift = true))
        assertEquals(SmartTab("a", 0), smartTab("- a", 1, shift = true))
        assertEquals(SmartTab("a", 0), smartTab("1. a", 3, shift = true))
        assertEquals(SmartTab("a", 0), smartTab("- [ ] a", 6, shift = true))
        assertEquals(SmartTab("a", 1), smartTab("- [x] a", 7, shift = true))
    }

    @Test
    fun `tab e shift-tab em citação adicionam e removem nível de citação`() {
        assertEquals(SmartTab("> > a", 4), smartTab("> a", 2))
        assertEquals(SmartTab("> > > a", 7), smartTab("> > a", 5))
        assertEquals(SmartTab("  > > a", 6), smartTab("  > a", 4))
        assertEquals(SmartTab("> a", 2), smartTab("> > a", 4, shift = true))
        assertEquals(SmartTab("a", 0), smartTab("> a", 2, shift = true))
        assertEquals(SmartTab("a", 1), smartTab("> a", 3, shift = true))
    }

    @Test
    fun `tab e shift-tab em linha comum sem lista ou citação`() {
        assertEquals(SmartTab("olá   mundo", 5), smartTab("olá mundo", 3))
        assertEquals(SmartTab("texto", 2), smartTab("  texto", 4, shift = true))
        assertEquals(SmartTab("texto", 1), smartTab("\ttexto", 2, shift = true))
        assertNull(smartTab("texto", 2, shift = true))
    }

    @Test
    fun `seleção multilinha ajusta linhas cobertas sem invadir linha após quebra final`() {
        val lista = "- um\n- dois\n- tres"
        assertEquals(SmartTab("  - um\n  - dois\n- tres", 0, 15), smartTab(lista, 0, 11))
        assertEquals(SmartTab("  - um\n- dois\n- tres", 0, 7), smartTab(lista, 0, 5))

        assertEquals(SmartTab("  um\n  dois", 0, 11), smartTab("um\ndois", 0, 7))
        assertEquals(SmartTab("  um\n  dois", 0, 11), smartTab("um\ndois", 7, 0))
        assertEquals(SmartTab("um\ndois", 0, 7), smartTab("  um\n  dois", 0, 11, shift = true))
        assertNull(smartTab("um\ndois", 0, 7, shift = true))
    }

    @Test
    fun `seleção mista de lista na raiz e sublinha indentada desindenta ambas`() {
        assertEquals(
            SmartTab("a\nb", 0, 3),
            smartTab("- a\n  b", 0, 7, shift = true),
        )
        assertEquals(
            SmartTab("a\nplain", 0, 7),
            smartTab("- a\nplain", 0, 9, shift = true),
        )
    }
}
