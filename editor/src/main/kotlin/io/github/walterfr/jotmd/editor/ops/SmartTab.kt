package io.github.walterfr.jotmd.editor.ops

/**
 * Indentação e desindentação por `Tab` / `Shift+Tab` no estilo Typora (ver DESIGN.md):
 *
 * - Lista, tarefa ou lista numerada: `Tab` adiciona `"  "` no início de cada linha
 *   coberta; `Shift+Tab` remove `\t` ou até 2 espaços de indentação, ou remove o
 *   marcador inteiro (`- [ ] `, `- `, `1. `) quando já está na raiz.
 * - Citação (`> `): `Tab` adiciona um nível `> `; `Shift+Tab` remove um nível `>\s?`.
 * - Outras linhas: `Tab` com cursor colapsado insere `"  "` no cursor; com seleção
 *   não colapsada adiciona `"  "` no início de cada linha coberta. `Shift+Tab`
 *   remove `\t` ou até 2 espaços das linhas que tiverem indentação, ou devolve
 *   `null` quando nenhuma tiver.
 */
data class SmartTab(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)

private const val TAB_SPACES = "  "

private val TAB_TASK = Regex("""^([^\S\r\n]*)([-*+] \[[ xX]] )""")
private val TAB_BULLET = Regex("""^([^\S\r\n]*)([-*+] )""")
private val TAB_ORDERED = Regex("""^([^\S\r\n]*)(\d{1,9}[.)] )""")
private val TAB_QUOTE = Regex("""^([^\S\r\n]*)((?:>[^\S\r\n]?)+)""")
private val TAB_QUOTE_TAIL = Regex(""">[^\S\r\n]?$""")

private data class LineEdit(val localStart: Int, val localEnd: Int, val replacement: String)

fun smartTab(
    text: String,
    selectionStart: Int,
    selectionEnd: Int = selectionStart,
    shift: Boolean = false,
): SmartTab? {
    val selStart = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val selEnd = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)

    val startLineStart = if (selStart == 0) 0 else text.lastIndexOf('\n', selStart - 1) + 1
    // Se a seleção termina logo após '\n', a linha seguinte não tem nada selecionado.
    val effectiveEnd = if (selEnd > selStart && text[selEnd - 1] == '\n') selEnd - 1 else selEnd
    val endLineEnd = text.indexOf('\n', effectiveEnd).let { if (it < 0) text.length else it }

    val slice = text.substring(startLineStart, endLineEnd)
    val lines = slice.split('\n')

    if (!shift && selStart == selEnd && lines.size == 1 && !isListOrQuote(lines[0])) {
        val updated = text.substring(0, selStart) + TAB_SPACES + text.substring(selStart)
        return SmartTab(updated, selStart + TAB_SPACES.length)
    }

    var changed = false
    var deltaStart = 0
    var deltaEnd = 0
    var lineOffset = startLineStart
    val rebuilt = StringBuilder(slice.length + lines.size * 2)

    lines.forEachIndexed { i, line ->
        if (i > 0) rebuilt.append('\n')
        val edit = computeLineEdit(line, shift)
        if (edit == null) {
            rebuilt.append(line)
        } else {
            changed = true
            rebuilt.append(line, 0, edit.localStart)
            rebuilt.append(edit.replacement)
            rebuilt.append(line, edit.localEnd, line.length)

            val editStart = lineOffset + edit.localStart
            val editEnd = lineOffset + edit.localEnd
            val added = edit.replacement.length
            val keepStart = selStart < selEnd && selStart == startLineStart
            deltaStart += offsetDelta(selStart, editStart, editEnd, added, keepStart)
            deltaEnd += offsetDelta(selEnd, editStart, editEnd, added, keepAtStart = false)
        }
        lineOffset += line.length + 1
    }

    if (!changed) return null

    val resultText = text.substring(0, startLineStart) + rebuilt.toString() + text.substring(endLineEnd)
    val newStart = (selStart + deltaStart).coerceIn(0, resultText.length)
    val newEnd = (selEnd + deltaEnd).coerceIn(newStart, resultText.length)
    return SmartTab(resultText, newStart, newEnd)
}

private fun isListOrQuote(line: String): Boolean =
    TAB_QUOTE.containsMatchIn(line) ||
        TAB_TASK.containsMatchIn(line) ||
        TAB_BULLET.containsMatchIn(line) ||
        TAB_ORDERED.containsMatchIn(line)

private fun computeLineEdit(line: String, shift: Boolean): LineEdit? {
    TAB_QUOTE.find(line)?.let { m ->
        if (!shift) {
            val indentLen = m.groupValues[1].length
            return LineEdit(indentLen, indentLen, "> ")
        }
        val marker = m.value
        val remaining = marker.replace(TAB_QUOTE_TAIL, "")
        return LineEdit(remaining.length, marker.length, "")
    }

    val listMatch = TAB_TASK.find(line) ?: TAB_BULLET.find(line) ?: TAB_ORDERED.find(line)
    if (listMatch != null) {
        if (!shift) return LineEdit(0, 0, TAB_SPACES)
        val indent = listMatch.groupValues[1]
        return when {
            indent.startsWith('\t') -> LineEdit(0, 1, "")
            indent.startsWith(' ') -> LineEdit(0, if (indent.startsWith(TAB_SPACES)) 2 else 1, "")
            else -> LineEdit(0, listMatch.value.length, "")
        }
    }

    if (!shift) return LineEdit(0, 0, TAB_SPACES)
    return when {
        line.startsWith('\t') -> LineEdit(0, 1, "")
        line.startsWith(' ') -> LineEdit(0, if (line.startsWith(TAB_SPACES)) 2 else 1, "")
        else -> null
    }
}

private fun offsetDelta(
    pos: Int,
    editStart: Int,
    editEnd: Int,
    added: Int,
    keepAtStart: Boolean,
): Int {
    val removed = editEnd - editStart
    return when {
        pos < editStart -> 0
        pos == editStart -> if (removed == 0 && !keepAtStart) added else 0
        pos < editEnd -> -(pos - editStart) + added
        else -> added - removed
    }
}
