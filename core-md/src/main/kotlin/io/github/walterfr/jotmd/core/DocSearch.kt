package io.github.walterfr.jotmd.core

import java.util.regex.PatternSyntaxException

/** Teto de ocorrências retornadas por [findInDocument], alinhado ao Typora. */
const val MAX_HITS_COUNT = 5000

/** Ocorrência de busca dentro de um bloco e sua faixa global no documento serializado. */
data class DocMatch(
    val blockIndex: Int,
    val blockId: BlockId,
    val rangeInBlock: IntRange,
    val globalRange: IntRange,
)

/** Verifica se [query] compila como expressão regular válida (string vazia é considerada válida). */
fun isValidSearchRegex(query: String): Boolean {
    if (query.isEmpty()) return true
    return try {
        Regex(query)
        true
    } catch (_: PatternSyntaxException) {
        false
    }
}

/**
 * Encontra todas as ocorrências não-sobrepostas de [query] nos blocos de [doc],
 * até [MAX_HITS_COUNT]. Quando [regex] é `true`, padrões inválidos retornam lista vazia.
 */
fun findInDocument(
    doc: DocState,
    query: String,
    caseSensitive: Boolean = false,
    wholeWord: Boolean = false,
    regex: Boolean = false,
): List<DocMatch> {
    if (query.isEmpty()) return emptyList()
    val compiled = if (regex) compileSearchRegex(query, caseSensitive) ?: return emptyList() else null
    val hits = ArrayList<DocMatch>()
    var globalOffset = doc.leading.length
    for ((blockIndex, block) in doc.blocks.withIndex()) {
        val onMatch: (Int, Int) -> Boolean = { start, endExclusive ->
            hits += DocMatch(
                blockIndex = blockIndex,
                blockId = block.id,
                rangeInBlock = start until endExclusive,
                globalRange = (globalOffset + start) until (globalOffset + endExclusive),
            )
            hits.size < MAX_HITS_COUNT
        }
        if (compiled != null) {
            forEachRegexMatch(block.source, compiled, wholeWord, onMatch)
        } else {
            forEachLiteralMatch(block.source, query, caseSensitive, wholeWord, onMatch)
        }
        if (hits.size >= MAX_HITS_COUNT) break
        globalOffset += block.source.length + block.trailing.length
    }
    return hits
}

/**
 * Substitui todas as ocorrências não-sobrepostas de [query] por [replacement]
 * em [text], devolvendo o texto resultante e o total substituído.
 * Quando [regex] é `true`, padrões inválidos devolvem [text] inalterado e `0`.
 */
fun replaceAllInText(
    text: String,
    query: String,
    replacement: String,
    caseSensitive: Boolean = false,
    wholeWord: Boolean = false,
    regex: Boolean = false,
): Pair<String, Int> {
    if (query.isEmpty()) return text to 0
    val compiled = if (regex) compileSearchRegex(query, caseSensitive) ?: return text to 0 else null
    var copyFrom = 0
    var count = 0
    val out = StringBuilder(text.length)
    val onMatch: (Int, Int) -> Boolean = { start, endExclusive ->
        out.append(text, copyFrom, start)
        out.append(replacement)
        copyFrom = endExclusive
        count++
        true
    }
    if (compiled != null) {
        forEachRegexMatch(text, compiled, wholeWord, onMatch)
    } else {
        forEachLiteralMatch(text, query, caseSensitive, wholeWord, onMatch)
    }
    if (count == 0) return text to 0
    out.append(text, copyFrom, text.length)
    return out.toString() to count
}

/**
 * Substitui exatamente a faixa inclusiva não-vazia [range] em [text] por [replacement].
 * Devolve [text] inalterado se a faixa for vazia ou estiver fora dos limites `0 until text.length`.
 */
fun replaceRangeInText(text: String, range: IntRange, replacement: String): String {
    if (range.isEmpty() || range.first < 0 || range.last >= text.length) return text
    return buildString(text.length - (range.last - range.first + 1) + replacement.length) {
        append(text, 0, range.first)
        append(replacement)
        append(text, range.last + 1, text.length)
    }
}

private fun compileSearchRegex(query: String, caseSensitive: Boolean): Regex? {
    val options = if (caseSensitive) {
        setOf(RegexOption.MULTILINE)
    } else {
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    }
    return try {
        Regex(query, options)
    } catch (_: PatternSyntaxException) {
        null
    }
}

private inline fun forEachLiteralMatch(
    text: String,
    query: String,
    caseSensitive: Boolean,
    wholeWord: Boolean,
    onMatch: (start: Int, endExclusive: Int) -> Boolean,
) {
    if (query.isEmpty()) return
    val ignoreCase = !caseSensitive
    var searchFrom = 0
    while (searchFrom <= text.length - query.length) {
        val idx = text.indexOf(query, searchFrom, ignoreCase = ignoreCase)
        if (idx < 0) break
        val endExclusive = idx + query.length
        if (wholeWord && !isWholeWord(text, idx, endExclusive)) {
            searchFrom = idx + 1
            continue
        }
        if (!onMatch(idx, endExclusive)) break
        searchFrom = endExclusive
    }
}

private inline fun forEachRegexMatch(
    text: String,
    regex: Regex,
    wholeWord: Boolean,
    onMatch: (start: Int, endExclusive: Int) -> Boolean,
) {
    var searchFrom = 0
    while (searchFrom <= text.length) {
        val m = regex.find(text, searchFrom) ?: break
        val start = m.range.first
        val endExclusive = m.range.last + 1
        if (endExclusive <= start) {
            // Ignora matches de comprimento zero e avança +1 para evitar loop infinito (ex.: `a*`, `^`).
            searchFrom = maxOf(start, endExclusive) + 1
            continue
        }
        if (wholeWord && !isWholeWord(text, start, endExclusive)) {
            searchFrom = start + 1
            continue
        }
        if (!onMatch(start, endExclusive)) break
        searchFrom = endExclusive
    }
}

private fun isWholeWord(text: String, start: Int, endExclusive: Int): Boolean {
    val before = text.getOrNull(start - 1)
    val after = text.getOrNull(endExclusive)
    return (before == null || !before.isWordChar()) && (after == null || !after.isWordChar())
}

private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '_'
