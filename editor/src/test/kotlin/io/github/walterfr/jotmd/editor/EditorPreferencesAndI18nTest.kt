package io.github.walterfr.jotmd.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.walterfr.jotmd.editor.ops.SmartTab
import io.github.walterfr.jotmd.editor.ops.smartTab
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorPreferencesAndI18nTest {

    @Test
    fun `EditorPrefs normalized limita fonte, largura e indentação`() {
        val low = EditorPrefs(
            fontSizeSp = 6f,
            maxColumnWidthDp = 300,
            indentSize = 1,
        ).normalized()
        assertEquals(12f, low.fontSizeSp)
        assertEquals(600, low.maxColumnWidthDp)
        assertEquals(2, low.indentSize)

        val high = EditorPrefs(
            fontSizeSp = 40f,
            maxColumnWidthDp = 2000,
            indentSize = 6,
        ).normalized()
        assertEquals(24f, high.fontSizeSp)
        assertEquals(1200, high.maxColumnWidthDp)
        assertEquals(4, high.indentSize)

        val nan = EditorPrefs(fontSizeSp = Float.NaN, indentSize = 3).normalized()
        assertEquals(16f, nan.fontSizeSp)
        assertEquals(2, nan.indentSize)
    }

    @Test
    fun `EditorPrefs toMap e fromMap preservam valores e resistem a entradas inválidas`() {
        val original = EditorPrefs(
            themePreset = MdThemePreset.NIGHT,
            fontSizeSp = 19.5f,
            maxColumnWidthDp = 980,
            autoPairEnabled = false,
            indentSize = 4,
            language = AppLanguage.EN,
            restoreSession = false,
        )
        assertEquals(original, EditorPrefs.fromMap(original.toMap()))

        val fallback = EditorPrefs.fromMap(
            mapOf(
                "themePreset" to "desconhecido",
                "fontSizeSp" to "abc",
                "maxColumnWidthDp" to "9999",
                "autoPairEnabled" to "talvez",
                "indentSize" to "8",
                "language" to "fr",
                "restoreSession" to "",
            ),
        )
        assertEquals(MdThemePreset.GITHUB, fallback.themePreset)
        assertEquals(16f, fallback.fontSizeSp)
        assertEquals(1200, fallback.maxColumnWidthDp)
        assertTrue(fallback.autoPairEnabled)
        assertEquals(4, fallback.indentSize)
        assertEquals(AppLanguage.SYSTEM, fallback.language)
        assertTrue(fallback.restoreSession)
    }

    @Test
    fun `stringsFor resolve PT-BR, EN e SYSTEM pelo locale`() {
        assertEquals(PtBrStrings, stringsFor(AppLanguage.PT_BR, Locale.US))
        assertEquals(EnStrings, stringsFor(AppLanguage.EN, Locale("pt", "BR")))
        assertEquals(PtBrStrings, stringsFor(AppLanguage.SYSTEM, Locale("pt", "BR")))
        assertEquals(PtBrStrings, stringsFor(AppLanguage.SYSTEM, Locale("pt", "PT")))
        assertEquals(EnStrings, stringsFor(AppLanguage.SYSTEM, Locale.US))

        assertEquals("Arquivos", PtBrStrings.files)
        assertEquals("Files", EnStrings.files)
        assertEquals("10 palavras · 50 caracteres · 1 min", PtBrStrings.wordCountSummary(10, 50, 1))
        assertEquals("10 words · 50 characters · 1 min", EnStrings.wordCountSummary(10, 50, 1))
        assertEquals("3 substituído(s)", PtBrStrings.replacedCount(3))
        assertEquals("3 replaced", EnStrings.replacedCount(3))
        assertEquals("Na seleção", PtBrStrings.inSelection)
        assertEquals("Salvo", PtBrStrings.savedNotice)
        assertEquals("In Selection", EnStrings.inSelection)
        assertEquals("Saved", EnStrings.savedNotice)
        assertTrue(PtBrStrings.inSelection.isNotBlank() && PtBrStrings.savedNotice.isNotBlank())
        assertTrue(EnStrings.inSelection.isNotBlank() && EnStrings.savedNotice.isNotBlank())
    }

    @Test
    fun `MdTokens applyPreferences atualiza tema, fonte, largura e tokens de sidebar`() {
        val previous = MdTokens.themePreset
        try {
            MdTokens.applyPreferences(
                EditorPrefs(
                    themePreset = MdThemePreset.NEWSPRINT,
                    fontSizeSp = 18f,
                    maxColumnWidthDp = 960,
                ),
            )
            assertEquals(MdThemePreset.NEWSPRINT, MdTokens.themePreset)
            assertEquals(18.sp, MdTokens.base)
            assertEquals((18f * 1.6f).sp, MdTokens.bodyLineHeight)
            assertEquals(960.dp, MdTokens.maxColumnWidth)
            assertEquals(900.dp, MdTokens.sourceMaxColumnWidth)
            assertEquals(NewsprintMdPalette.sidebarBackground, MdTokens.sidebarBackground)
            assertEquals(NewsprintMdPalette.activeItemBackground, MdTokens.activeItemBackground)
            assertEquals(NewsprintMdPalette.activeItemBorder, MdTokens.activeItemBorder)

            MdTokens.applyPreferences(EditorPrefs(maxColumnWidthDp = 600))
            assertEquals(560.dp, MdTokens.sourceMaxColumnWidth)
        } finally {
            MdTokens.applyPreferences(EditorPrefs(themePreset = previous))
        }
    }

    @Test
    fun `EditorState applyPreferences configura autoPair e tab com indentSize 4`() {
        assertEquals(SmartTab("    - item", 6), smartTab("- item", 2, indentSize = 4))
        assertEquals(SmartTab("- item", 2), smartTab("    - item", 6, shift = true, indentSize = 4))

        val s = EditorState("- item\n")
        s.applyPreferences(EditorPrefs(autoPairEnabled = false, indentSize = 4))
        assertFalse(s.autoPairEnabled)
        assertEquals(4, s.indentSize)

        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("- item", TextRange(6)))
        assertTrue(s.tab(shift = false))
        assertEquals("    - item", s.doc.blocks[0].source)
        assertEquals(TextRange(10), s.editValue.selection)

        assertTrue(s.tab(shift = true))
        assertEquals("- item", s.doc.blocks[0].source)
        assertEquals(TextRange(6), s.editValue.selection)

        // Com autoPairEnabled = false, digitar '(' não fecha automaticamente ')'
        s.edit(TextFieldValue("- item (", TextRange(8)))
        assertEquals("- item (", s.editValue.text)
    }

    @Test
    fun `toggleSourceMode preserva focusedId e editValue intactos`() {
        val s = EditorState("# Título\n\nParágrafo\n")
        val second = s.doc.blocks[1]
        s.focus(second)
        s.edit(TextFieldValue("Parágrafo editado", TextRange(9)))

        s.toggleSourceMode()
        assertTrue(s.isSourceMode)
        assertEquals(second.id, s.focusedId)
        assertEquals("Parágrafo editado", s.editValue.text)
        assertEquals(TextRange(9), s.editValue.selection)

        s.toggleSourceMode()
        assertFalse(s.isSourceMode)
        assertEquals(second.id, s.focusedId)
        assertEquals("Parágrafo editado", s.editValue.text)
        assertEquals(TextRange(9), s.editValue.selection)
    }
}
