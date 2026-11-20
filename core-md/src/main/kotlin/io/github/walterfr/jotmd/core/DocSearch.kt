package io.github.walterfr.jotmd.core

/** Teto de ocorrências retornadas por [findInDocument], alinhado ao Typora. */
const val MAX_HITS_COUNT = 5000

/** Ocorrência de busca dentro de um bloco e sua faixa global no documento serializado. */
data class DocMatch(
    val blockIndex: Int,
    val blockId: BlockId,
    val rangeInBlock: IntRange,
    val globalRange: IntRange,
)

/**
 * Encontra todas as ocorrências não-sobrepostas de [query] nos blocos de [doc],
 * até [MAX_HITS_COUNT].
 */
fun findInDocument(
    doc: DocState,
    query: String,
    caseSensitive: Boolean = false,
    wholeWord: Boolean = false,
): List<DocMatch> {
    if (query.isEmpty()) return emptyList()
    val hits = ArrayList<DocMatch>()
    var globalOffset = doc.leading.length
    for ((blockIndex, block) in doc.blocks.withIndex()) {
        forEachMatch(block.source, query, caseSensitive, wholeWord) { start, endExclusive ->
            hits += DocMatch(
                blockIndex = blockIndex,
                blockId = block.id,
                rangeInBlock = start until endExclusive,
                globalRange = (globalOffset + start) until (globalOffset + endExclusive),
            )
            hits.size < MAX_HITS_COUNT
        }
        if (hits.size >= MAX_HITS_COUNT) break
        globalOffset += block.source.length + block.trailing.length
    }
    return hits
}

/**
 * Substitui todas as ocorrências não-sobrepostas de [query] por [replacement]
 * em [text], devolvendo o texto resultante e o total substituído.
 */
fun replaceAllInText(
    text: String,
    query: String,
    replacement: String,
    caseSensitive: Boolean = false,
    wholeWord: Boolean = false,
): Pair<String, Int> {
    if (query.isEmpty()) return text to 0
    var copyFrom = 0
    var count = 0
    val out = StringBuilder(text.length)
    forEachMatch(text, query, caseSensitive, wholeWord) { start, endExclusive ->
        out.append(text, copyFrom, start)
        out.append(replacement)
        copyFrom = endExclusive
        count++
        true
    }
    if (count == 0) return text to 0
    out.append(text, copyFrom, text.length)
    return out.toString() to count
}

private inline fun forEachMatch(
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

private fun isWholeWord(text: String, start: Int, endExclusive: Int): Boolean {
    val before = text.getOrNull(start - 1)
    val after = text.getOrNull(endExclusive)
    return (before == null || !before.isWordChar()) && (after == null || !after.isWordChar())
}

private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '_'
