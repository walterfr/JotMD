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
