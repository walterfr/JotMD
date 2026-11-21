package io.github.walterfr.jotmd.editor.ops

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.walterfr.jotmd.editor.MdTokens

private data class HighlightSpan(val range: IntRange, val style: SpanStyle)

private val HASH_COMMENT_LANGS = setOf(
    "py", "python", "sh", "bash", "zsh", "shell", "yaml", "yml",
    "toml", "rb", "ruby", "pl", "perl", "r", "dockerfile", "makefile",
    "conf", "ini", "properties",
)

private val SQL_LANGS = setOf(
    "sql", "sqlite", "postgres", "postgresql", "mysql", "plsql", "tsql",
)

private val DASH_COMMENT_LANGS = SQL_LANGS + setOf("lua", "hs", "haskell")
private val JSON_LANGS = setOf("json", "jsonc", "json5")

private val CODE_KEYWORDS = setOf(
    // Kotlin / Java
    "package", "import", "fun", "val", "var", "class", "interface", "object",
    "enum", "sealed", "data", "open", "abstract", "override", "private",
    "protected", "public", "internal", "companion", "init", "constructor",
    "when", "is", "as", "in", "out", "by", "where", "suspend", "inline",
    "reified", "typealias", "static", "final", "void", "extends", "implements",
    "throws", "synchronized", "volatile", "new", "instanceof", "this", "super",
    // Controle comum
    "if", "else", "for", "while", "do", "loop", "switch", "case", "default",
    "break", "continue", "return", "try", "catch", "finally", "throw",
    "true", "false", "null",
    // JS / TS
    "function", "const", "let", "export", "from", "async", "await", "yield",
    "typeof", "keyof", "declare", "namespace", "undefined",
    // Python
    "def", "elif", "except", "raise", "with", "lambda", "pass", "and", "or",
    "not", "None", "True", "False", "nonlocal", "global", "assert", "del",
    // Rust / Go
    "fn", "pub", "mod", "use", "crate", "struct", "impl", "trait", "match",
    "mut", "ref", "move", "unsafe", "self", "Self", "go", "defer", "chan",
    "select", "range", "type", "map", "make", "nil", "func",
    // Shell
    "then", "fi", "esac", "done", "local", "echo", "exit",
)

private val SQL_KEYWORDS = setOf(
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
    "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "JOIN", "INNER", "LEFT",
    "RIGHT", "OUTER", "ON", "GROUP", "BY", "ORDER", "ASC", "DESC", "LIMIT",
    "OFFSET", "HAVING", "AND", "OR", "NOT", "AS", "NULL", "IS", "IN", "LIKE",
    "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "UNIQUE", "INDEX", "BEGIN",
    "COMMIT", "ROLLBACK", "DISTINCT", "UNION", "ALL", "CASE", "WHEN", "THEN",
    "ELSE", "END", "EXISTS",
)

private val BUILTIN_TYPES = setOf(
    "int", "long", "double", "float", "bool", "boolean", "str", "string",
    "char", "byte", "short", "u8", "u16", "u32", "u64", "u128", "usize",
    "i8", "i16", "i32", "i64", "i128", "isize", "f32", "f64",
    "Int", "Long", "Double", "Float", "Boolean", "String", "Char", "Byte",
    "Short", "Unit", "Any", "Nothing", "List", "MutableList", "Map",
    "MutableMap", "Set", "MutableSet", "Result", "Option", "Vec", "Promise",
)

private val WORD_OR_NUMBER_REGEX = Regex(
    """\b(?:0[xXbBoO][0-9a-fA-F_]+|\d[\d_]*(?:\.[\d_]+)?(?:[eE][+-]?\d+)?[fFdDlLuU]*|[A-Za-z_][A-Za-z0-9_]*)\b""",
)
private val YAML_KEY_REGEX = Regex("""^[ \t-]*([A-Za-z0-9_.-]+)(?=[ \t]*:)""", RegexOption.MULTILINE)

private val FENCE_LINE_REGEX = Regex("""^([ \t]{0,3}(`{3,}|~{3,}))(.*)$""")
private val HR_OR_SETEXT_REGEX = Regex("""^[ \t]{0,3}(?:-{3,}|\*{3,}|_{3,}|={3,})[ \t]*$""")
private val HEADING_LINE_REGEX = Regex("""^([ \t]{0,3}#{1,6})(?:[ \t]+(.*)|[ \t]*)$""")
private val QUOTE_PREFIX_REGEX = Regex("""^([ \t]{0,3}(?:>[ \t]?)+)(?:\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\])?""", RegexOption.IGNORE_CASE)
private val LIST_PREFIX_REGEX = Regex("""^([ \t]*(?:[-*+]|\d{1,9}[.)])[ \t]+(?:\[[ xX]][ \t]+)?)""")
private val FRONT_MATTER_KEY_REGEX = Regex("""^([ \t]*[A-Za-z0-9_.-]+)(?=[ \t]*:)""")

private val INLINE_CODE_REGEX = Regex("""(`+)([^`\n]+?)\1""")
private val INLINE_MATH_REGEX = Regex("""\$\$[^$\n]+?\$\$|\$(?!\s)[^$\n]+?(?<!\s)\$""")
private val LINK_OR_WIKI_REGEX = Regex(
    """\[\[[^\]\n]+?\]\]|\[\^[^\]\n]+?\]|!?\[[^\]\n]*?\]\([^)\n]*?\)|!?\[[^\]\n]+?\]\[[^\]\n]*?\]""",
)
private val BOLD_REGEX = Regex("""\*\*(?=\S)([^\n]*?\S)\*\*|__(?=\S)([^\n]*?\S)__""")
private val ITALIC_REGEX = Regex("""(?<!\*)\*(?!\*|\s)([^\n*]*?\S)\*(?!\*)|(?<![\p{L}\p{N}_])_(?!_|\s)([^\n_]*?\S)_(?![\p{L}\p{N}_])""")
private val STRIKE_REGEX = Regex("""~~(?=\S)([^\n]*?\S)~~""")
private val MARK_HIGHLIGHT_REGEX = Regex("""==(?=\S)([^=\n]*?\S)==""")

/**
 * Aplica realce sintático em blocos de código preservando exatamente o mesmo
 * texto de entrada (`result.text == code`), compatível com `OffsetMapping.Identity`.
 */
fun highlightCode(code: String, language: String?): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString("")
    val lang = language?.trim()?.lowercase().orEmpty()
    val isSql = lang in SQL_LANGS
    val isHashLang = lang in HASH_COMMENT_LANGS
    val isYaml = lang == "yaml" || lang == "yml"
    val isJson = lang in JSON_LANGS
    val allowSlash = !isHashLang && !isSql && !isYaml
    val allowBlockComment = !isHashLang && !isYaml
    val allowHash = isHashLang || lang == "php" || lang.isEmpty()
    val allowDashDash = lang in DASH_COMMENT_LANGS || lang.isEmpty()

    val commentStyle = SpanStyle(color = MdTokens.syntaxComment, fontStyle = FontStyle.Italic)
    val stringStyle = SpanStyle(color = MdTokens.syntaxString)
    val keyStyle = SpanStyle(color = MdTokens.syntaxType)
    val keywordStyle = SpanStyle(color = MdTokens.syntaxKeyword, fontWeight = FontWeight.Medium)
    val numberStyle = SpanStyle(color = MdTokens.syntaxNumber)
    val typeStyle = SpanStyle(color = MdTokens.syntaxType)

    val primarySpans = ArrayList<HighlightSpan>()
    var i = 0
    while (i < code.length) {
        val c = code[i]
        when {
            allowBlockComment && code.startsWith("/*", i) -> {
                val close = code.indexOf("*/", i + 2)
                val end = if (close >= 0) close + 2 else code.length
                primarySpans += HighlightSpan(i until end, commentStyle)
                i = end
            }

            allowSlash && code.startsWith("//", i) -> {
                val end = lineEnd(code, i + 2)
                primarySpans += HighlightSpan(i until end, commentStyle)
                i = end
            }

            allowHash && c == '#' && (isHashLang || lang == "php" || i == 0 || code[i - 1].isWhitespace()) -> {
                val end = lineEnd(code, i + 1)
                primarySpans += HighlightSpan(i until end, commentStyle)
                i = end
            }

            allowDashDash && code.startsWith("--", i) && isDashCommentStart(code, i, lang in DASH_COMMENT_LANGS) -> {
                val end = lineEnd(code, i + 2)
                primarySpans += HighlightSpan(i until end, commentStyle)
                i = end
            }

            !isJson && (code.startsWith("\"\"\"", i) || code.startsWith("'''", i)) -> {
                val delim = code.substring(i, i + 3)
                val close = code.indexOf(delim, i + 3)
                val end = if (close >= 0) close + 3 else code.length
                primarySpans += HighlightSpan(i until end, stringStyle)
                i = end
            }

            c == '"' -> {
                val end = scanQuotedEnd(code, i, '"', stopAtNewline = true)
                val style = if (isJson && isFollowedByColon(code, end)) keyStyle else stringStyle
                primarySpans += HighlightSpan(i until end, style)
                i = end
            }

            c == '\'' -> {
                val end = findSingleQuotedEnd(code, i)
                if (end != null) {
                    primarySpans += HighlightSpan(i until end, stringStyle)
                    i = end
                } else {
                    i++
                }
            }

            !isJson && !isYaml && c == '`' -> {
                val end = scanQuotedEnd(code, i, '`', stopAtNewline = false)
                primarySpans += HighlightSpan(i until end, stringStyle)
                i = end
            }

            else -> i++
        }
    }

    val tokenSpans = ArrayList<HighlightSpan>()
    var gapStart = 0
    for (primary in primarySpans) {
        if (gapStart < primary.range.first) {
            collectCodeGapSpans(code, gapStart, primary.range.first, isSql, isYaml, keywordStyle, numberStyle, typeStyle, keyStyle, tokenSpans)
        }
        gapStart = primary.range.last + 1
    }
    if (gapStart < code.length) {
        collectCodeGapSpans(code, gapStart, code.length, isSql, isYaml, keywordStyle, numberStyle, typeStyle, keyStyle, tokenSpans)
    }

    return buildAnnotatedString {
        append(code)
        for (span in primarySpans) addStyle(span.style, span.range.first, span.range.last + 1)
        for (span in tokenSpans) addStyle(span.style, span.range.first, span.range.last + 1)
    }
}

/**
 * Aplica realce sintático para o modo código-fonte de Markdown preservando
 * exatamente [source] como texto (`result.text == source`).
 */
fun highlightMarkdownSource(source: String): AnnotatedString {
    if (source.isEmpty()) return AnnotatedString("")
    val spans = ArrayList<HighlightSpan>()
    var fenceChar: Char? = null
    var fenceLen = 0
    var inFrontMatter = false

    val markerStyle = SpanStyle(color = MdTokens.syntaxMarker, fontWeight = FontWeight.Bold)
    val headingStyle = SpanStyle(color = MdTokens.syntaxHeading, fontWeight = FontWeight.Bold)
    val fenceBodyStyle = SpanStyle(color = MdTokens.syntaxComment)
    val typeStyle = SpanStyle(color = MdTokens.syntaxType)

    var lineIndex = 0
    var lineStart = 0
    while (lineStart <= source.length) {
        val rawEnd = source.indexOf('\n', lineStart).let { if (it < 0) source.length else it }
        val lineEnd = if (rawEnd > lineStart && source[rawEnd - 1] == '\r') rawEnd - 1 else rawEnd
        val line = source.substring(lineStart, lineEnd)

        if (lineIndex == 0 && line.trim() == "---") {
            inFrontMatter = true
            spans += HighlightSpan(lineStart until lineEnd, markerStyle)
        } else if (inFrontMatter) {
            if (line.trim() == "---" || line.trim() == "...") {
                inFrontMatter = false
                spans += HighlightSpan(lineStart until lineEnd, markerStyle)
            } else {
                FRONT_MATTER_KEY_REGEX.find(line)?.let { m ->
                    val g = m.groups[1]!!
                    spans += HighlightSpan((lineStart + g.range.first)..(lineStart + g.range.last), typeStyle)
                }
            }
        } else {
            val fenceMatch = FENCE_LINE_REGEX.matchEntire(line)
            if (fenceChar != null) {
                val closeGroup = fenceMatch?.groups?.get(2)?.value
                val rest = fenceMatch?.groups?.get(3)?.value.orEmpty()
                if (closeGroup != null && closeGroup[0] == fenceChar && closeGroup.length >= fenceLen && rest.isBlank()) {
                    spans += HighlightSpan(lineStart until lineEnd, markerStyle)
                    fenceChar = null
                    fenceLen = 0
                } else if (lineStart < lineEnd) {
                    spans += HighlightSpan(lineStart until lineEnd, fenceBodyStyle)
                }
            } else if (fenceMatch != null) {
                val markerGroup = fenceMatch.groups[1]!!
                val delimGroup = fenceMatch.groups[2]!!
                val langGroup = fenceMatch.groups[3]!!
                fenceChar = delimGroup.value[0]
                fenceLen = delimGroup.value.length
                spans += HighlightSpan((lineStart + markerGroup.range.first)..(lineStart + markerGroup.range.last), markerStyle)
                if (langGroup.value.isNotBlank()) {
                    spans += HighlightSpan((lineStart + langGroup.range.first)..(lineStart + langGroup.range.last), typeStyle)
                }
            } else {
                val inlineStart = highlightBlockLinePrefix(line, lineStart, markerStyle, headingStyle, spans)
                val isHeading = HEADING_LINE_REGEX.matches(line)
                if (inlineStart < lineEnd) {
                    highlightMarkdownInlineLine(source, inlineStart, lineEnd, isHeading, spans)
                }
            }
        }

        if (rawEnd == source.length) break
        lineStart = rawEnd + 1
        lineIndex++
    }

    return buildAnnotatedString {
        append(source)
        for (span in spans) addStyle(span.style, span.range.first, span.range.last + 1)
    }
}

private fun highlightBlockLinePrefix(
    line: String,
    lineStart: Int,
    markerStyle: SpanStyle,
    headingStyle: SpanStyle,
    out: MutableList<HighlightSpan>,
): Int {
    if (HR_OR_SETEXT_REGEX.matches(line)) {
        out += HighlightSpan(lineStart until lineStart + line.length, markerStyle)
        return lineStart + line.length
    }
    HEADING_LINE_REGEX.matchEntire(line)?.let { m ->
        val hashes = m.groups[1]!!
        val title = m.groups[2]?.takeIf { !it.range.isEmpty() }
        out += HighlightSpan((lineStart + hashes.range.first)..(lineStart + hashes.range.last), markerStyle)
        if (title != null) {
            out += HighlightSpan((lineStart + title.range.first)..(lineStart + title.range.last), headingStyle)
            return lineStart + title.range.first
        }
        return lineStart + hashes.range.last + 1
    }
    QUOTE_PREFIX_REGEX.find(line)?.let { m ->
        val quote = m.groups[1]!!
        out += HighlightSpan((lineStart + quote.range.first)..(lineStart + quote.range.last), markerStyle)
        m.groups[2]?.let { kind ->
            val alertStart = lineStart + quote.range.last + 1
            val alertEnd = lineStart + kind.range.last + 2
            out += HighlightSpan(alertStart until alertEnd.coerceAtMost(lineStart + line.length), SpanStyle(color = MdTokens.syntaxKeyword, fontWeight = FontWeight.Bold))
        }
        return lineStart + m.range.last + 1
    }
    LIST_PREFIX_REGEX.find(line)?.let { m ->
        out += HighlightSpan((lineStart + m.range.first)..(lineStart + m.range.last), markerStyle)
        return lineStart + m.range.last + 1
    }
    return lineStart
}

private fun highlightMarkdownInlineLine(
    source: String,
    start: Int,
    endExclusive: Int,
    isHeading: Boolean,
    out: MutableList<HighlightSpan>,
) {
    val lineSlice = source.substring(start, endExclusive)
    val occupied = ArrayList<IntRange>()

    val codeStyle = SpanStyle(color = MdTokens.syntaxComment, background = MdTokens.inlineCodeBackground)
    for (m in INLINE_CODE_REGEX.findAll(lineSlice)) {
        val abs = (start + m.range.first)..(start + m.range.last)
        occupied += abs
        out += HighlightSpan(abs, codeStyle)
    }

    val mathStyle = SpanStyle(color = MdTokens.syntaxNumber)
    for (m in INLINE_MATH_REGEX.findAll(lineSlice)) {
        val abs = (start + m.range.first)..(start + m.range.last)
        if (overlapsAny(abs, occupied)) continue
        occupied += abs
        out += HighlightSpan(abs, mathStyle)
    }

    val linkStyle = SpanStyle(color = MdTokens.syntaxLink, textDecoration = TextDecoration.Underline)
    for (m in LINK_OR_WIKI_REGEX.findAll(lineSlice)) {
        val abs = (start + m.range.first)..(start + m.range.last)
        if (overlapsAny(abs, occupied)) continue
        occupied += abs
        out += HighlightSpan(abs, linkStyle)
    }

    val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
    val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
    val strikeStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
    val highlightStyle = SpanStyle(background = MdTokens.highlightBackground, color = MdTokens.highlightText)
    val delimStyle = SpanStyle(color = MdTokens.syntaxMarker)

    for (m in MARK_HIGHLIGHT_REGEX.findAll(lineSlice)) {
        val abs = (start + m.range.first)..(start + m.range.last)
        if (overlapsAny(abs, occupied)) continue
        out += HighlightSpan(abs, highlightStyle)
    }
    for (m in STRIKE_REGEX.findAll(lineSlice)) {
        val abs = (start + m.range.first)..(start + m.range.last)
        if (overlapsAny(abs, occupied)) continue
        out += HighlightSpan(abs, strikeStyle)
        if (!isHeading && abs.last - abs.first >= 4) {
            out += HighlightSpan(abs.first until abs.first + 2, delimStyle)
            out += HighlightSpan((abs.last - 1)..abs.last, delimStyle)
        }
    }
    for (m in BOLD_REGEX.findAll(lineSlice)) {
        val abs = (start + m.range.first)..(start + m.range.last)
        if (overlapsAny(abs, occupied)) continue
        out += HighlightSpan(abs, boldStyle)
        if (!isHeading && abs.last - abs.first >= 4) {
            out += HighlightSpan(abs.first until abs.first + 2, delimStyle)
            out += HighlightSpan((abs.last - 1)..abs.last, delimStyle)
        }
    }
    for (m in ITALIC_REGEX.findAll(lineSlice)) {
        val abs = (start + m.range.first)..(start + m.range.last)
        if (overlapsAny(abs, occupied)) continue
        out += HighlightSpan(abs, italicStyle)
        if (!isHeading && abs.last - abs.first >= 2) {
            out += HighlightSpan(abs.first..abs.first, delimStyle)
            out += HighlightSpan(abs.last..abs.last, delimStyle)
        }
    }
}

private fun collectCodeGapSpans(
    code: String,
    gapStart: Int,
    gapEnd: Int,
    isSql: Boolean,
    isYaml: Boolean,
    keywordStyle: SpanStyle,
    numberStyle: SpanStyle,
    typeStyle: SpanStyle,
    keyStyle: SpanStyle,
    out: MutableList<HighlightSpan>,
) {
    val slice = code.substring(gapStart, gapEnd)
    val occupied = ArrayList<IntRange>()
    if (isYaml) {
        for (m in YAML_KEY_REGEX.findAll(slice)) {
            val g = m.groups[1] ?: continue
            val abs = (gapStart + g.range.first)..(gapStart + g.range.last)
            occupied += abs
            out += HighlightSpan(abs, keyStyle)
        }
    }
    for (m in WORD_OR_NUMBER_REGEX.findAll(slice)) {
        val abs = (gapStart + m.range.first)..(gapStart + m.range.last)
        if (overlapsAny(abs, occupied)) continue
        val token = m.value
        val style = when {
            token[0].isDigit() -> numberStyle
            token in CODE_KEYWORDS || (isSql && token.uppercase() in SQL_KEYWORDS) -> keywordStyle
            token in BUILTIN_TYPES || (token[0].isUpperCase() && token.any { it.isLowerCase() }) -> typeStyle
            else -> null
        }
        if (style != null) out += HighlightSpan(abs, style)
    }
}

private fun overlapsAny(range: IntRange, existing: List<IntRange>): Boolean =
    existing.any { range.first <= it.last && it.first <= range.last }

private fun lineEnd(text: String, from: Int): Int {
    val nl = text.indexOf('\n', from)
    return if (nl < 0) text.length else nl
}

private fun isDashCommentStart(code: String, index: Int, explicitLang: Boolean): Boolean {
    if (explicitLang) return true
    val prevOk = index == 0 || code[index - 1].isWhitespace()
    val next = code.getOrNull(index + 2) ?: return prevOk
    return prevOk && (next.isWhitespace() || next == '-')
}

private fun scanQuotedEnd(code: String, start: Int, quote: Char, stopAtNewline: Boolean): Int {
    var i = start + 1
    while (i < code.length) {
        val ch = code[i]
        if (stopAtNewline && ch == '\n') return i
        if (ch == '\\' && i + 1 < code.length) {
            i += 2
            continue
        }
        if (ch == quote) return i + 1
        i++
    }
    return code.length
}

private fun findSingleQuotedEnd(code: String, start: Int): Int? {
    var i = start + 1
    while (i < code.length) {
        val ch = code[i]
        if (ch == '\n') return null
        if (ch == '\\' && i + 1 < code.length) {
            i += 2
            continue
        }
        if (ch == '\'') return i + 1
        i++
    }
    return null
}

private fun isFollowedByColon(code: String, from: Int): Boolean {
    var i = from
    while (i < code.length && (code[i] == ' ' || code[i] == '\t')) i++
    return code.getOrNull(i) == ':'
}
