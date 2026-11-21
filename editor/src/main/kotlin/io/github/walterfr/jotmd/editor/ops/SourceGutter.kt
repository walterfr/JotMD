package io.github.walterfr.jotmd.editor.ops

/**
 * Regras do gutter no modo código-fonte do Typora (`codemirror.css`):
 * exibe somente a linha 1, a última linha, múltiplos de 10 e a linha ativa.
 */
fun shouldShowSourceLineNumber(
    lineNumber1Based: Int,
    totalLines: Int,
    activeLine1Based: Int?,
): Boolean {
    if (lineNumber1Based < 1 || totalLines < 1 || lineNumber1Based > totalLines) return false
    return lineNumber1Based == 1 ||
        lineNumber1Based == totalLines ||
        lineNumber1Based % 10 == 0 ||
        lineNumber1Based == activeLine1Based
}

/** Linha 1-based sob [cursorOffset] em [text]. */
fun activeLine1Based(text: String, cursorOffset: Int): Int {
    val limit = cursorOffset.coerceIn(0, text.length)
    var line = 1
    for (i in 0 until limit) {
        if (text[i] == '\n') line++
    }
    return line
}

/** Formata o texto completo do gutter para [totalLines] linhas separadas por `\n`. */
fun formatSourceGutter(totalLines: Int, activeLine1Based: Int?): String {
    if (totalLines <= 0) return ""
    return (1..totalLines).joinToString("\n") { line ->
        if (shouldShowSourceLineNumber(line, totalLines, activeLine1Based)) line.toString() else ""
    }
}
