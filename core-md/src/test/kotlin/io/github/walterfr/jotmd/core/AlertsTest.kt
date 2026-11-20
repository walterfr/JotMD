package io.github.walterfr.jotmd.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlertsTest {

    @Test
    fun `reconhece todos os cinco tipos de alerta com tag e rótulo`() {
        val cases = listOf(
            "NOTE" to AlertKind.NOTE,
            "TIP" to AlertKind.TIP,
            "IMPORTANT" to AlertKind.IMPORTANT,
            "WARNING" to AlertKind.WARNING,
            "CAUTION" to AlertKind.CAUTION,
        )
        for ((token, expected) in cases) {
            val info = alertOf("> [!$token]\n> Corpo")
            assertEquals(AlertInfo(expected, "Corpo"), info)
        }
        assertEquals("note" to "Note", AlertKind.NOTE.tag to AlertKind.NOTE.label)
        assertEquals("tip" to "Tip", AlertKind.TIP.tag to AlertKind.TIP.label)
        assertEquals("important" to "Important", AlertKind.IMPORTANT.tag to AlertKind.IMPORTANT.label)
        assertEquals("warning" to "Warning", AlertKind.WARNING.tag to AlertKind.WARNING.label)
        assertEquals("caution" to "Caution", AlertKind.CAUTION.tag to AlertKind.CAUTION.label)
    }

    @Test
    fun `ignora maiúsculas, minúsculas e espaços no cabeçalho`() {
        assertEquals(
            AlertInfo(AlertKind.TIP, "dica útil"),
            alertOf("  >   [!tip]  \n> dica útil"),
        )
        assertEquals(
            AlertInfo(AlertKind.WARNING, "atenção"),
            alertOf("> [!WaRnInG]\n> atenção"),
        )
    }

    @Test
    fun `inclui texto restante da primeira linha como início do corpo`() {
        assertEquals(
            AlertInfo(AlertKind.CAUTION, "perigo imediato"),
            alertOf("> [!caution] perigo imediato"),
        )
        assertEquals(
            AlertInfo(AlertKind.NOTE, "linha um\nlinha dois"),
            alertOf("> [!NOTE] linha um\n> linha dois"),
        )
    }

    @Test
    fun `preserva parágrafos internos, citação aninhada e CRLF`() {
        assertEquals(
            AlertInfo(AlertKind.IMPORTANT, "primeiro\n\nsegundo\n> citação aninhada"),
            alertOf("> [!IMPORTANT]\r\n> primeiro\r\n>\r\n> segundo\r\n> > citação aninhada"),
        )
        assertEquals(
            AlertInfo(AlertKind.TIP, "após linha vazia"),
            alertOf("> [!Tip]\n>\n> após linha vazia"),
        )
    }

    @Test
    fun `alerta sem corpo devolve string vazia`() {
        assertEquals(AlertInfo(AlertKind.NOTE, ""), alertOf("> [!NOTE]"))
    }

    @Test
    fun `devolve null quando não é alerta válido`() {
        assertNull(alertOf(""))
        assertNull(alertOf("> citação comum"))
        assertNull(alertOf("> texto antes [!NOTE]"))
        assertNull(alertOf("[!NOTE]\n> sem sinal de citação"))
        assertNull(alertOf("> [!DESCONHECIDO]\n> corpo"))
    }
}
