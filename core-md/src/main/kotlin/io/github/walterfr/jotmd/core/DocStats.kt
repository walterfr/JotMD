package io.github.walterfr.jotmd.core

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import kotlin.math.round

/** Velocidade padrão de leitura adotada pelo Typora (palavras por minuto). */
const val TYPORA_WPM = 382

/** Estatísticas de extensão e tempo estimado de leitura de um documento Markdown. */
data class DocStats(
    val words: Int,
    val chars: Int,
    val charsNoSpaces: Int,
    val lines: Int,
    val readingTimeMinutes: Int,
)

/**
 * Calcula estatísticas de [doc] alinhadas ao contador do Typora (`Mod 37`):
 * - exclui `FRONT_MATTER`, `HORIZONTAL_RULE`, `LINK_DEFINITION` e marcadores `[TOC]`;
 * - em `CODE_FENCE`, ignora as linhas delimitadoras de abertura e fechamento;
 * - conta cada caractere CJK como 1 palavra e tokens com `'`, `’`, `-` ou `.` numérico interno.
 */
fun docStatsOf(doc: DocState, wordsPerMinute: Int = TYPORA_WPM): DocStats {
    var words = 0
    var chars = 0
    var charsNoSpaces = 0

    for (block in doc.blocks) {
        val content = countableContent(block) ?: continue
        words += countWords(content)
        chars += content.length
        for (ch in content) {
            if (!ch.isWhitespace()) charsNoSpaces++
        }
    }

    val serialized = serialize(doc)
    val lines = if (serialized.isEmpty()) 0 else serialized.lines().size
    val safeWpm = wordsPerMinute.coerceAtLeast(1)
    val readingTimeMinutes = if (words == 0) {
        0
    } else {
        maxOf(1, round(words.toDouble() / safeWpm).toInt())
    }

    return DocStats(
        words = words,
        chars = chars,
        charsNoSpaces = charsNoSpaces,
        lines = lines,
        readingTimeMinutes = readingTimeMinutes,
    )
}

/** Atalho que faz [parse] de [text] e calcula [docStatsOf]. */
fun docStatsOf(text: String, wordsPerMinute: Int = TYPORA_WPM): DocStats =
    docStatsOf(parse(text), wordsPerMinute)

private fun countableContent(block: Block): String? = when (block.type) {
    BlockType.FRONT_MATTER,
    BlockType.HORIZONTAL_RULE,
    BlockType.LINK_DEFINITION -> null

    BlockType.PARAGRAPH -> if (isTocMarker(block.source)) null else block.source
    BlockType.CODE_FENCE -> codeFenceBody(block.source)
    else -> block.source
}

private fun codeFenceBody(source: String): String {
    val node = astOf(source).children.firstOrNull { it.type == MarkdownElementTypes.CODE_FENCE }
        ?: return fallbackCodeFenceBody(source)
    val firstEol = node.children.firstOrNull { it.type == MarkdownTokenTypes.EOL } ?: return ""
    val contentStart = firstEol.endOffset
    val endFence = node.children.lastOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_END }
    val contentEnd = if (endFence != null) {
        val lastEol = node.children.lastOrNull {
            it.type == MarkdownTokenTypes.EOL && it.endOffset <= endFence.startOffset
        }
        lastEol?.startOffset ?: endFence.startOffset
    } else {
        source.length
    }
    return if (contentStart >= contentEnd) "" else source.substring(contentStart, contentEnd).trimEnd('\n', '\r')
}

private fun fallbackCodeFenceBody(source: String): String {
    val lines = source.lines()
    if (lines.size <= 1) return ""
    val body = if (lines.last().trim().let { it.startsWith("```") || it.startsWith("~~~") }) {
        lines.drop(1).dropLast(1)
    } else {
        lines.drop(1)
    }
    return body.joinToString("\n")
}

private fun countWords(text: String): Int {
    var count = 0
    var inWord = false
    for (i in text.indices) {
        val ch = text[i]
        when {
            ch.isTyporaCjk() -> {
                count++
                inWord = false
            }

            ch.isLetterOrDigit() -> {
                if (!inWord) {
                    count++
                    inWord = true
                }
            }

            inWord && isWordJoiner(text, i, ch) -> Unit

            else -> inWord = false
        }
    }
    return count
}

// Exclui surrogates UTF-16 (\uD800..\uDFFF) para não contar emoji como CJK.
private fun Char.isTyporaCjk(): Boolean =
    !isSurrogate() && (this in '\u3040'..'\uABFF' || this in '\uD7A4'..'\uFAFF')

private fun isWordJoiner(text: String, index: Int, ch: Char): Boolean {
    val next = text.getOrNull(index + 1) ?: return false
    if (next.isTyporaCjk()) return false
    return when (ch) {
        '\'', '’', '-' -> next.isLetterOrDigit()
        '.' -> text[index - 1].isDigit() && next.isDigit()
        else -> false
    }
}
