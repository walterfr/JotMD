package io.github.walterfr.jotmd.editor.ops

/**
 * Fechamento automático de pares, envolvimento de seleção (`surroundPair`) e
 * overtype de fechamento no estilo Typora (ver DESIGN.md, "Auto-pair").
 */
data class SmartPair(val text: String, val selectionStart: Int, val selectionEnd: Int = selectionStart)

val AUTO_PAIRS: Map<Char, Char> = mapOf(
    '(' to ')',
    '[' to ']',
    '{' to '}',
    '"' to '"',
    '\'' to '\'',
    '*' to '*',
    '`' to '`',
    '~' to '~',
    '=' to '=',
)

private val AUTO_CLOSE_OPENERS = setOf('(', '[', '{', '"', '\'', '`')
private val CLOSERS = setOf(')', ']', '}', '"', '\'', '`')

fun applySmartPair(
    oldText: String,
    oldSelStart: Int,
    oldSelEnd: Int,
    newText: String,
    newSelStart: Int,
    newSelEnd: Int,
): SmartPair? {
    val oStart = minOf(oldSelStart, oldSelEnd)
    val oEnd = maxOf(oldSelStart, oldSelEnd)
    val nStart = minOf(newSelStart, newSelEnd)
    val nEnd = maxOf(newSelStart, newSelEnd)
    if (oStart < 0 || oEnd > oldText.length || nStart < 0 || nEnd > newText.length) return null
    if (nStart != nEnd) return null

    // Caso 1: envolver seleção não vazia ao digitar um delimitador de abertura.
    if (oStart < oEnd) {
        if (newText.length != oldText.length - (oEnd - oStart) + 1) return null
        if (nStart != oStart + 1) return null
        val ch = newText.getOrNull(oStart) ?: return null
        val close = AUTO_PAIRS[ch] ?: return null
        if (!newText.regionMatches(0, oldText, 0, oStart)) return null
        if (!newText.regionMatches(oStart + 1, oldText, oEnd, oldText.length - oEnd)) return null

        val selected = oldText.substring(oStart, oEnd)
        val wrapped = oldText.substring(0, oStart) + ch + selected + close + oldText.substring(oEnd)
        return SmartPair(wrapped, oStart + 1, oEnd + 1)
    }

    // Inserção simples de exatamente 1 caractere na posição oStart.
    if (newText.length != oldText.length + 1 || nStart != oStart + 1) return null
    val ch = newText.getOrNull(oStart) ?: return null
    if (!newText.regionMatches(0, oldText, 0, oStart)) return null
    if (!newText.regionMatches(oStart + 1, oldText, oStart, oldText.length - oStart)) return null

    val prev = oldText.getOrNull(oStart - 1)
    val next = oldText.getOrNull(oStart)
    val isQuoteLike = ch == '"' || ch == '\'' || ch == '`'

    // Caso 2: overtype quando o caractere digitado já fecha o par logo à direita.
    if (ch in CLOSERS && next == ch && (!isQuoteLike || prev != '\\')) {
        return SmartPair(oldText, oStart + 1)
    }

    // Caso 3: auto-close quando o próximo char permite abertura sem colar em texto.
    if (ch in AUTO_CLOSE_OPENERS && isValidAutoCloseContext(ch, prev, next, isQuoteLike)) {
        val close = AUTO_PAIRS.getValue(ch)
        val closed = newText.substring(0, nStart) + close + newText.substring(nStart)
        return SmartPair(closed, nStart)
    }

    return null
}

private fun isValidAutoCloseContext(
    ch: Char,
    prev: Char?,
    next: Char?,
    isQuoteLike: Boolean,
): Boolean {
    val nextOk = next == null || next.isWhitespace() || next == ')' || next == ']' || next == '}'
    if (!nextOk) return false
    if (isQuoteLike && (prev?.isLetterOrDigit() == true || prev == ch)) return false
    return true
}
