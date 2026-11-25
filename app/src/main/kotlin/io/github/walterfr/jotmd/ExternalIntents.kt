package io.github.walterfr.jotmd

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.IntentCompat
import io.github.walterfr.jotmd.editor.AppLanguage
import io.github.walterfr.jotmd.editor.EditorPrefs
import io.github.walterfr.jotmd.editor.MdThemePreset

const val PREF_ROOT = "root_uri"
const val PREF_DARK = "dark_theme"
const val PREF_THEME = "theme_preset"

private const val PREF_FONT_SIZE = "font_size_sp"
private const val PREF_MAX_COLUMN_WIDTH = "max_column_width_dp"
private const val PREF_AUTO_PAIR = "auto_pair"
private const val PREF_INDENT_SIZE = "indent_size"
private const val PREF_LANGUAGE = "language"
private const val PREF_RESTORE_SESSION = "restore_session"

private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mmd", "mkd", "mdown", "txt")
private val IMPORTABLE_EXTENSIONS = setOf("html", "htm", "xhtml", "docx", "epub")

private val MARKDOWN_MIME_TYPES = setOf("text/markdown", "text/x-markdown", "text/plain")
private val IMPORTABLE_MIME_TYPES = setOf(
    "text/html",
    "application/xhtml+xml",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/epub+zip",
)

enum class IncomingKind {
    MARKDOWN,
    IMPORTABLE,
    UNKNOWN,
}

sealed interface IncomingAction {
    data class OpenMarkdownUri(val uri: Uri) : IncomingAction
    data class ImportUri(val uri: Uri, val suggestedName: String) : IncomingAction
    data class SharedText(val title: String, val text: String) : IncomingAction
}

/** Envelope sequencial para evitar auto-cancelamento de `LaunchedEffect` ao consumir intent. */
data class IncomingIntentEvent<T>(val seq: Long, val payload: T)

fun <T> consumeIncomingEvent(
    current: IncomingIntentEvent<T>?,
    handledSeq: Long,
): IncomingIntentEvent<T>? = if (current?.seq == handledSeq) null else current

fun sharedTextAction(subject: String?, text: String?): IncomingAction.SharedText? {
    if (text.isNullOrEmpty()) return null
    val title = subject
        ?.takeIf(String::isNotBlank)
        ?.let { sanitizeFileName(it, ensureMarkdownExtension = true) }
        ?: "Sem Título.md"
    return IncomingAction.SharedText(title = title, text = text)
}

/**
 * Classifica um arquivo recebido por extensão de [fileName] e, se ausente ou
 * genérica, pelo [mimeType] normalizado. Pura para testes JVM.
 */
fun classifyIncomingMimeOrName(mimeType: String?, fileName: String?): IncomingKind {
    val baseName = fileName?.trim()?.substringAfterLast('/')?.substringAfterLast('\\')
    val ext = baseName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
    if (!ext.isNullOrEmpty()) {
        if (ext in MARKDOWN_EXTENSIONS) return IncomingKind.MARKDOWN
        if (ext in IMPORTABLE_EXTENSIONS) return IncomingKind.IMPORTABLE
    }

    val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
    return when {
        normalizedMime in MARKDOWN_MIME_TYPES -> IncomingKind.MARKDOWN
        normalizedMime in IMPORTABLE_MIME_TYPES -> IncomingKind.IMPORTABLE
        else -> IncomingKind.UNKNOWN
    }
}

/**
 * Extrai a ação inicial de uma [Intent] (`VIEW`, `EDIT` ou `SEND`), usando
 * [resolver] quando disponível para obter `DISPLAY_NAME` real no SAF.
 */
fun extractIncomingAction(intent: Intent?, resolver: ContentResolver?): IncomingAction? {
    if (intent == null) return null
    return when (intent.action) {
        Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
            val uri = intent.data ?: return null
            classifyUriAction(uri, intent.type, resolver)
        }

        Intent.ACTION_SEND -> {
            val extraStream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            if (extraStream != null) {
                classifyUriAction(extraStream, intent.type, resolver)?.let { return it }
            }
            return sharedTextAction(
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                text = intent.getStringExtra(Intent.EXTRA_TEXT),
            )
        }

        else -> null
    }
}

private fun classifyUriAction(
    uri: Uri,
    intentMimeType: String?,
    resolver: ContentResolver?,
): IncomingAction? {
    val name = resolver?.let { displayName(it, uri) } ?: uri.lastPathSegment
    val mime = intentMimeType ?: runCatching { resolver?.getType(uri) }.getOrNull()
    return when (classifyIncomingMimeOrName(mime, name)) {
        IncomingKind.MARKDOWN -> IncomingAction.OpenMarkdownUri(uri)
        IncomingKind.IMPORTABLE -> IncomingAction.ImportUri(
            uri = uri,
            suggestedName = name?.takeIf { it.isNotBlank() } ?: "documento.html",
        )
        IncomingKind.UNKNOWN -> null
    }
}

/**
 * Lê [EditorPrefs] do [SharedPreferences] mantendo compatibilidade com as
 * chaves legadas (`theme_preset` e `dark_theme`) e tolerando tipos divergentes.
 */
fun loadAndroidPrefs(prefs: SharedPreferences): EditorPrefs {
    val defaults = EditorPrefs()
    val savedThemeId = runCatching { prefs.getString(PREF_THEME, null) }.getOrNull()
    val legacyDark = runCatching { prefs.getBoolean(PREF_DARK, false) }.getOrDefault(false)
    val themePreset = MdThemePreset.entries.firstOrNull { it.id == savedThemeId }
        ?: if (legacyDark) MdThemePreset.NIGHT else MdThemePreset.GITHUB

    val fontSizeSp = runCatching { prefs.getFloat(PREF_FONT_SIZE, defaults.fontSizeSp) }
        .getOrDefault(defaults.fontSizeSp)
    val maxColumnWidthDp = runCatching { prefs.getInt(PREF_MAX_COLUMN_WIDTH, defaults.maxColumnWidthDp) }
        .getOrDefault(defaults.maxColumnWidthDp)
    val autoPairEnabled = runCatching { prefs.getBoolean(PREF_AUTO_PAIR, defaults.autoPairEnabled) }
        .getOrDefault(defaults.autoPairEnabled)
    val indentSize = runCatching { prefs.getInt(PREF_INDENT_SIZE, defaults.indentSize) }
        .getOrDefault(defaults.indentSize)
    val languageId = runCatching { prefs.getString(PREF_LANGUAGE, null) }.getOrNull()
    val restoreSession = runCatching { prefs.getBoolean(PREF_RESTORE_SESSION, defaults.restoreSession) }
        .getOrDefault(defaults.restoreSession)

    return EditorPrefs(
        themePreset = themePreset,
        fontSizeSp = fontSizeSp,
        maxColumnWidthDp = maxColumnWidthDp,
        autoPairEnabled = autoPairEnabled,
        indentSize = indentSize,
        language = AppLanguage.fromId(languageId),
        restoreSession = restoreSession,
    ).normalized()
}

/** Persiste [EditorPrefs] normalizado junto das chaves legadas de tema. */
fun saveAndroidPrefs(prefs: SharedPreferences, value: EditorPrefs) {
    val normalized = value.normalized()
    prefs.edit()
        .putString(PREF_THEME, normalized.themePreset.id)
        .putBoolean(PREF_DARK, normalized.themePreset.isDark)
        .putFloat(PREF_FONT_SIZE, normalized.fontSizeSp)
        .putInt(PREF_MAX_COLUMN_WIDTH, normalized.maxColumnWidthDp)
        .putBoolean(PREF_AUTO_PAIR, normalized.autoPairEnabled)
        .putInt(PREF_INDENT_SIZE, normalized.indentSize)
        .putString(PREF_LANGUAGE, normalized.language.id)
        .putBoolean(PREF_RESTORE_SESSION, normalized.restoreSession)
        .apply()
}
