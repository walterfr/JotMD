package io.github.walterfr.jotmd.editor

/**
 * Idioma da interface do editor. [SYSTEM] acompanha o locale do sistema operacional.
 */
enum class AppLanguage(val id: String) {
    SYSTEM("system"),
    PT_BR("pt-BR"),
    EN("en"),
    ;

    companion object {
        fun fromId(id: String?): AppLanguage {
            val normalized = id?.trim() ?: return SYSTEM
            return entries.firstOrNull { it.id.equals(normalized, ignoreCase = true) } ?: SYSTEM
        }
    }
}

/**
 * Preferências persistentes do editor (compartilhadas entre Android e Desktop).
 */
data class EditorPrefs(
    val themePreset: MdThemePreset = MdThemePreset.GITHUB,
    val fontSizeSp: Float = 16f,
    val maxColumnWidthDp: Int = 860,
    val autoPairEnabled: Boolean = true,
    val indentSize: Int = 2,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val restoreSession: Boolean = true,
) {
    /**
     * Garante valores dentro das faixas suportadas pelo editor:
     * fonte `12..24 sp`, largura `600..1200 dp` e indentação `2` ou `4`.
     */
    fun normalized(): EditorPrefs {
        val safeFont = if (fontSizeSp.isFinite()) fontSizeSp.coerceIn(12f, 24f) else 16f
        val safeWidth = maxColumnWidthDp.coerceIn(600, 1200)
        val safeIndent = if (indentSize >= 4) 4 else 2
        return copy(
            fontSizeSp = safeFont,
            maxColumnWidthDp = safeWidth,
            indentSize = safeIndent,
        )
    }

    fun toMap(): Map<String, String> = mapOf(
        KEY_THEME_PRESET to themePreset.id,
        KEY_FONT_SIZE_SP to fontSizeSp.toString(),
        KEY_MAX_COLUMN_WIDTH_DP to maxColumnWidthDp.toString(),
        KEY_AUTO_PAIR_ENABLED to autoPairEnabled.toString(),
        KEY_INDENT_SIZE to indentSize.toString(),
        KEY_LANGUAGE to language.id,
        KEY_RESTORE_SESSION to restoreSession.toString(),
    )

    companion object {
        private const val KEY_THEME_PRESET = "themePreset"
        private const val KEY_FONT_SIZE_SP = "fontSizeSp"
        private const val KEY_MAX_COLUMN_WIDTH_DP = "maxColumnWidthDp"
        private const val KEY_AUTO_PAIR_ENABLED = "autoPairEnabled"
        private const val KEY_INDENT_SIZE = "indentSize"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_RESTORE_SESSION = "restoreSession"

        fun fromMap(map: Map<String, String>): EditorPrefs {
            val defaults = EditorPrefs()
            val theme = map[KEY_THEME_PRESET]?.trim()?.let { id ->
                MdThemePreset.entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
            } ?: defaults.themePreset
            val fontSize = map[KEY_FONT_SIZE_SP]?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() }
                ?: defaults.fontSizeSp
            val columnWidth = map[KEY_MAX_COLUMN_WIDTH_DP]?.trim()?.toIntOrNull()
                ?: defaults.maxColumnWidthDp
            val autoPair = map[KEY_AUTO_PAIR_ENABLED]?.trim()?.lowercase()?.toBooleanStrictOrNull()
                ?: defaults.autoPairEnabled
            val indent = map[KEY_INDENT_SIZE]?.trim()?.toIntOrNull()
                ?: defaults.indentSize
            val lang = map[KEY_LANGUAGE]?.let { AppLanguage.fromId(it) }
                ?: defaults.language
            val restore = map[KEY_RESTORE_SESSION]?.trim()?.lowercase()?.toBooleanStrictOrNull()
                ?: defaults.restoreSession

            return EditorPrefs(
                themePreset = theme,
                fontSizeSp = fontSize,
                maxColumnWidthDp = columnWidth,
                autoPairEnabled = autoPair,
                indentSize = indent,
                language = lang,
                restoreSession = restore,
            ).normalized()
        }
    }
}
