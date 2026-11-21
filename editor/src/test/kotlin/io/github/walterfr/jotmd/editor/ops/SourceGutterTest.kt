package io.github.walterfr.jotmd.editor.ops

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceGutterTest {

    @Test
    fun `shouldShowSourceLineNumber exibe primeira, última, múltipla de 10 e linha ativa`() {
        assertTrue(shouldShowSourceLineNumber(1, 25, activeLine1Based = 7))
        assertTrue(shouldShowSourceLineNumber(25, 25, activeLine1Based = 7))
        assertTrue(shouldShowSourceLineNumber(10, 25, activeLine1Based = 7))
        assertTrue(shouldShowSourceLineNumber(20, 25, activeLine1Based = 7))
        assertTrue(shouldShowSourceLineNumber(7, 25, activeLine1Based = 7))

        assertFalse(shouldShowSourceLineNumber(2, 25, activeLine1Based = 7))
        assertFalse(shouldShowSourceLineNumber(11, 25, activeLine1Based = 7))
        assertFalse(shouldShowSourceLineNumber(0, 25, activeLine1Based = 1))
        assertFalse(shouldShowSourceLineNumber(26, 25, activeLine1Based = 1))
        assertFalse(shouldShowSourceLineNumber(1, 0, activeLine1Based = 1))
    }

    @Test
    fun `activeLine1Based conta quebras até o cursor limitado ao texto`() {
        val text = "um\ndois\ntres"
        assertEquals(1, activeLine1Based("", 0))
        assertEquals(1, activeLine1Based(text, -5))
        assertEquals(1, activeLine1Based(text, 2))
        assertEquals(2, activeLine1Based(text, 3))
        assertEquals(2, activeLine1Based(text, 7))
        assertEquals(3, activeLine1Based(text, 8))
        assertEquals(3, activeLine1Based(text, 999))
    }

    @Test
    fun `formatSourceGutter formata linhas separadas por quebra e vazio para total inválido`() {
        assertEquals("", formatSourceGutter(0, null))
        assertEquals("", formatSourceGutter(-2, 1))
        assertEquals("1", formatSourceGutter(1, null))
        assertEquals("1\n\n\n4", formatSourceGutter(4, null))
        assertEquals("1\n\n\n\n5\n\n\n\n\n10\n\n12", formatSourceGutter(12, 5))
    }
}
