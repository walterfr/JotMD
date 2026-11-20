package io.github.walterfr.jotmd.editor.ops

/**
 * Enter dentro de lista, lista numerada, tarefa ou citação, reproduzindo o que
 * o Typora grava (observado em caixa-preta; ver DESIGN.md):
 *
 * - item com conteúdo → novo item com o mesmo marcador e indentação; numerada
 *   incrementa; tarefa nova nasce desmarcada; o texto depois do cursor vai junto.
 * - citação → novo parágrafo DENTRO dela (`> a` / `>` / `> `).
 * - item vazio (só o marcador) → sai da estrutura: a linha do marcador some e o
 *   chamador abre um parágrafo novo abaixo.
 *
 * Null quando a linha do cursor não começa com marcador — Enter segue o padrão.
 */
sealed interface SmartEnter {
    data class Insert(val text: String, val cursor: Int) : SmartEnter
    data class Exit(val text: String) : SmartEnter
}

private val TASK = Regex("""^(\s*)([-*+]) \[[ xX]] """)
private val BULLET = Regex("""^(\s*)([-*+]) """)
private val ORDERED = Regex("""^(\s*)(\d{1,9})([.)]) """)
private val QUOTE = Regex("""^\s*(?:>\s?)+""")
private val BARE_QUOTE = Regex("""^\s*(?:>\s?)+$""")

fun smartEnter(text: String, cursor: Int): SmartEnter? {
    val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
    val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
    val line = text.substring(lineStart, lineEnd)

    val task = TASK.find(line)
    val bullet = BULLET.find(line)
    val ordered = ORDERED.find(line)
    val quote = QUOTE.find(line)

    val (marker, next, isQuote) = when {
        task != null -> Triple(task.value, "${task.groupValues[1]}${task.groupValues[2]} [ ] ", false)
        bullet != null -> Triple(bullet.value, bullet.value, false)
        ordered != null -> {
            val (indent, n, delim) = ordered.destructured
            Triple(ordered.value, "$indent${n.toLong() + 1}$delim ", false)
        }
        quote != null -> Triple(quote.value, quote.value.trimEnd(), true)
        else -> return null
    }
    // Cursor dentro do próprio marcador: não há item para continuar.
    if (cursor < lineStart + marker.length && line.substring(marker.length).isNotBlank()) return null

    if (line.substring(marker.length).isBlank()) {
        // Remove a linha vazia e o "\n" que a precede.
        var cut = (lineStart - 1).coerceAtLeast(0)
        var before = text.substring(0, cut)
        // Na citação, o Enter anterior deixou um separador `>` vazio; sai junto.
        if (isQuote) {
            val prevStart = before.lastIndexOf('\n') + 1
            if (prevStart > 0 && BARE_QUOTE.matches(before.substring(prevStart))) {
                cut = prevStart - 1
                before = text.substring(0, cut)
            }
        }
        return SmartEnter.Exit(before + text.substring(lineEnd))
    }

    val insertion = if (isQuote) "\n$next\n$next " else "\n$next"
    return SmartEnter.Insert(
        text.substring(0, cursor) + insertion + text.substring(cursor),
        cursor + insertion.length,
    )
}

private val INLINE_SPLIT_DELIMS = listOf("**", "~~", "==", "*", "`")

/**
 * Divide um span inline aberto ao pressionar Enter no meio dele (`**a|b**` ->
 * `**a**` e `**b**`), devolvendo o texto superior fechado, o inferior
 * reaberto e o cursor logo após a reabertura. Devolve `null` se não houver
 * delimitador aberto com fechamento correspondente ou se alguma ponta fecharia
 * vazia.
 */
fun smartSplitInline(before: String, after: String): Triple<String, String, Int>? {
    val beforeLine = before.substringAfterLast('\n')
    val afterLine = after.substringBefore('\n')
    for (delim in INLINE_SPLIT_DELIMS) {
        if (countInlineDelim(beforeLine, delim) % 2 == 1 &&
            countInlineDelim(afterLine, delim) > 0 &&
            !endsWithInlineDelim(beforeLine, delim) &&
            !startsWithInlineDelim(afterLine, delim)
        ) {
            return Triple(before + delim, delim + after, delim.length)
        }
    }
    return null
}

/** Gatilho de YAML front matter: `---` digitado sozinho no primeiro bloco. */
fun isFrontMatterTrigger(blockIndex: Int, text: String, cursor: Int): Boolean =
    blockIndex == 0 && text == "---" && cursor == 3

private fun countInlineDelim(line: String, delim: String): Int {
    var count = 0
    var i = 0
    if (delim == "*") {
        while (i < line.length) {
            if (line[i] == '*') {
                var end = i + 1
                while (end < line.length && line[end] == '*') end++
                // Runs pares pertencem a "**"; só sobra "*" isolado em runs ímpares.
                if ((end - i) % 2 == 1) count++
                i = end
            } else {
                i++
            }
        }
        return count
    }
    while (i <= line.length - delim.length) {
        if (line.startsWith(delim, i)) {
            count++
            i += delim.length
        } else {
            i++
        }
    }
    return count
}

private fun endsWithInlineDelim(line: String, delim: String): Boolean {
    if (delim != "*") return line.endsWith(delim)
    var stars = 0
    var i = line.length - 1
    while (i >= 0 && line[i] == '*') {
        stars++
        i--
    }
    return stars % 2 == 1
}

private fun startsWithInlineDelim(line: String, delim: String): Boolean {
    if (delim != "*") return line.startsWith(delim)
    var stars = 0
    while (stars < line.length && line[stars] == '*') stars++
    return stars % 2 == 1
}
