package io.github.walterfr.jotmd.editor.ops

/**
 * Backspace em cascata no estilo Typora (ver DESIGN.md, "Backspace e Delete"):
 *
 * 1. Tarefa (`- [ ] `, `- [x] `): remove só o checkbox -> vira item de lista.
 * 2. Lista / lista numerada: se indentada (`>= 2` espaços ou `\t`), remove um
 *    nível de indentação; na raiz, remove o marcador.
 * 3. Citação (`> `, `> > `): remove o último nível `>\s?` do fim do prefixo,
 *    preservando eventual indentação anterior.
 * 4. Heading (`# `..`###### `): remove o prefixo inteiro -> vira parágrafo.
 * 5. Par auto-fechado vazio (`()`, `[]`, `{}`, `""`, `''`, `**`, `` `` ``, `~~`, `__`):
 *    apaga ambos os caracteres de uma vez.
 *
 * As regras de prefixo atuam quando o cursor está no início da linha
 * (`lineStart`) ou logo após o marcador (`lineStart + marker.length`).
 * Devolve `null` quando nenhuma regra se aplica — Backspace segue o padrão.
 */
data class SmartBackspace(val text: String, val cursor: Int)

private val TASK = Regex("""^([^\S\r\n]*)([-*+]) (\[[ xX]] )""")
private val BULLET = Regex("""^([^\S\r\n]*)([-*+] )""")
private val ORDERED = Regex("""^([^\S\r\n]*)(\d{1,9}[.)] )""")
private val QUOTE = Regex("""^([^\S\r\n]*(?:>[^\S\r\n]?)+)""")
private val QUOTE_TAIL = Regex(""">[^\S\r\n]?$""")
private val HEADING = Regex("""^([^\S\r\n]*#{1,6}[^\S\r\n]+)""")

private val EMPTY_PAIRS = setOf(
    '(' to ')',
    '[' to ']',
    '{' to '}',
    '"' to '"',
    '\'' to '\'',
    '*' to '*',
    '`' to '`',
    '~' to '~',
    '_' to '_',
)

fun smartBackspace(text: String, cursor: Int): SmartBackspace? {
    if (cursor !in 0..text.length) return null

    val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
    val lineEnd = text.indexOf('\n', cursor).let { if (it < 0) text.length else it }
    val line = text.substring(lineStart, lineEnd)

    stripLinePrefix(line, lineStart, cursor)?.let { (newLine, newCursor) ->
        return SmartBackspace(
            text = text.substring(0, lineStart) + newLine + text.substring(lineEnd),
            cursor = newCursor,
        )
    }
    return tryRemovePairStart(text, cursor)
}

private fun stripLinePrefix(line: String, lineStart: Int, cursor: Int): Pair<String, Int>? {
    TASK.find(line)?.let { m ->
        val marker = m.value
        if (cursor != lineStart && cursor != lineStart + marker.length) return null
        val indent = m.groupValues[1]
        val bullet = m.groupValues[2]
        val newPrefix = "$indent$bullet "
        val newCursor = if (cursor == lineStart) lineStart else lineStart + newPrefix.length
        return (newPrefix + line.substring(marker.length)) to newCursor
    }

    (BULLET.find(line) ?: ORDERED.find(line))?.let { m ->
        val marker = m.value
        if (cursor != lineStart && cursor != lineStart + marker.length) return null
        val (indent, bullet) = m.destructured
        if (indent.length >= 2 || indent.startsWith('\t')) {
            val newIndent = if (indent.startsWith('\t')) indent.drop(1) else indent.drop(2)
            val newPrefix = newIndent + bullet
            val newCursor = if (cursor == lineStart) lineStart else lineStart + newPrefix.length
            return (newPrefix + line.substring(marker.length)) to newCursor
        }
        return line.substring(marker.length) to lineStart
    }

    QUOTE.find(line)?.let { m ->
        val marker = m.value
        if (cursor != lineStart && cursor != lineStart + marker.length) return null
        val remaining = marker.replace(QUOTE_TAIL, "")
        val newCursor = if (cursor == lineStart) lineStart else lineStart + remaining.length
        return (remaining + line.substring(marker.length)) to newCursor
    }

    HEADING.find(line)?.let { m ->
        val marker = m.value
        if (cursor != lineStart && cursor != lineStart + marker.length) return null
        return line.substring(marker.length) to lineStart
    }

    return null
}

private fun tryRemovePairStart(text: String, cursor: Int): SmartBackspace? {
    if (cursor !in 1 until text.length) return null
    if ((text[cursor - 1] to text[cursor]) !in EMPTY_PAIRS) return null
    return SmartBackspace(text.removeRange(cursor - 1, cursor + 1), cursor - 1)
}
