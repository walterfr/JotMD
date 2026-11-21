package io.github.walterfr.jotmd.core

/**
 * Extrai a expressão LaTeX interna de um bloco delimitado por `$$ ... $$`
 * (tanto `$$\n...\n$$` quanto `$$ expr $$` em bloco próprio), ou `null`.
 */
fun extractMathBlock(source: String): String? {
    val trimmed = source.trim()
    if (trimmed.length < 4 || !trimmed.startsWith("$$") || !trimmed.endsWith("$$")) return null
    if (isEscapedDollar(trimmed, trimmed.length - 2)) return null
    val inner = trimmed.substring(2, trimmed.length - 2)
    if (containsUnescapedDoubleDollar(inner)) return null
    val cleaned = inner.trim()
    return cleaned.ifEmpty { null }
}

/**
 * Encontra spans de matemática inline `$...$` em linha única, seguindo as regras do Typora/Pandoc:
 * - não abre em `$$` nem `\$` escapado;
 * - primeiro caractere interno não pode ser whitespace;
 * - último caractere interno não pode ser whitespace nem `\`;
 * - caractere logo após `$` de fechamento não pode ser dígito (`$5 e $10`);
 * - ignora spans vazios ou sobrepostos a [skipRanges].
 */
fun inlineMathSpansIn(
    source: String,
    skipRanges: List<IntRange> = emptyList(),
): List<Pair<IntRange, String>> {
    if (source.isEmpty()) return emptyList()
    val out = ArrayList<Pair<IntRange, String>>()
    var i = 0
    while (i < source.length) {
        val skipEnd = skipEndAt(i, skipRanges)
        if (skipEnd > i) {
            i = skipEnd
            continue
        }
        if (source[i] != '$') {
            i++
            continue
        }
        if (isEscapedDollar(source, i)) {
            i++
            continue
        }
        // Ignora sequência `$$` inteira para não abrir inline dentro de bloco `$$...$$`.
        if (source.getOrNull(i + 1) == '$') {
            while (i < source.length && source[i] == '$') i++
            continue
        }
        val firstInner = source.getOrNull(i + 1)
        if (firstInner == null || firstInner == '\n' || firstInner == '\r' || firstInner.isWhitespace()) {
            i++
            continue
        }

        var closing = -1
        var j = i + 1
        while (j < source.length) {
            val ch = source[j]
            if (ch == '\n' || ch == '\r') break
            if (skipEndAt(j, skipRanges) > j) break
            if (ch == '$') {
                val prev = source[j - 1]
                val next = source.getOrNull(j + 1)
                if (next == '$') {
                    // Evita parear abertura `$` com metade de um fechamento `$$`.
                    break
                }
                val validClose = j > i + 1 &&
                    !prev.isWhitespace() &&
                    prev != '\\' &&
                    (next == null || !next.isDigit())
                if (validClose) {
                    closing = j
                    break
                } else if (prev != '\\') {
                    // Outro '$' não escapado inválido impede capturar texto entre preços (ex.: `$5 e $10 ... $x$`).
                    break
                }
            }
            j++
        }

        if (closing > i + 1) {
            out += (i..closing) to source.substring(i + 1, closing)
            i = closing + 1
        } else {
            i++
        }
    }
    return out
}

/**
 * Converte uma expressão LaTeX para representação Unicode legível sem dependências externas.
 */
fun renderLatexToUnicode(latex: String): String {
    if (latex.isBlank()) return ""
    return LatexConverter(latex).convert().trim()
}

private fun skipEndAt(index: Int, skips: List<IntRange>): Int {
    for (r in skips) {
        if (!r.isEmpty() && index in r) return r.last + 1
    }
    return index
}

private fun isEscapedDollar(text: String, index: Int): Boolean {
    var backslashes = 0
    var i = index - 1
    while (i >= 0 && text[i] == '\\') {
        backslashes++
        i--
    }
    return backslashes % 2 == 1
}

private fun containsUnescapedDoubleDollar(text: String): Boolean {
    var i = 0
    while (i < text.length - 1) {
        if (text[i] == '$' && text[i + 1] == '$' && !isEscapedDollar(text, i)) return true
        i++
    }
    return false
}

private class LatexConverter(private val input: String) {
    private var pos = 0

    fun convert(): String = convertUntil(stopChar = null)

    private fun convertUntil(stopChar: Char?): String {
        val out = StringBuilder()
        while (pos < input.length) {
            val ch = input[pos]
            if (stopChar != null && ch == stopChar) {
                pos++
                break
            }
            when {
                ch == '{' -> {
                    pos++
                    out.append(convertUntil('}'))
                }
                ch == '}' -> pos++
                ch == '\\' -> appendCommand(out)
                ch == '^' -> {
                    pos++
                    val (raw, braced) = readArgWithBraced()
                    val rendered = LatexConverter(raw).convert().trim()
                    out.append(formatScript('^', rendered, braced, SUPERSCRIPTS))
                }
                ch == '_' -> {
                    pos++
                    val (raw, braced) = readArgWithBraced()
                    val rendered = LatexConverter(raw).convert().trim()
                    out.append(formatScript('_', rendered, braced, SUBSCRIPTS))
                }
                ch.isWhitespace() -> {
                    while (pos < input.length && input[pos].isWhitespace()) pos++
                    if (out.isNotEmpty() && !out.endsWith('\n') && !out.endsWith(' ')) {
                        out.append(' ')
                    }
                }
                else -> {
                    pos++
                    out.append(ch)
                }
            }
        }
        return out.toString()
    }

    private fun appendCommand(out: StringBuilder) {
        pos++ // consome '\'
        if (pos >= input.length) return
        val first = input[pos]
        if (!first.isLetter()) {
            pos++
            when (first) {
                '\\' -> {
                    while (out.isNotEmpty() && out.last() == ' ') out.setLength(out.length - 1)
                    out.append('\n')
                }
                '{', '}', '%', '$', '&', '#', '_' -> out.append(first)
                ',', ';', ':', ' ' -> if (!out.endsWith(' ') && !out.endsWith('\n')) out.append(' ')
                '!' -> Unit
                '|' -> out.append('‖')
                else -> out.append(first)
            }
            return
        }

        val start = pos
        while (pos < input.length && input[pos].isLetter()) pos++
        val cmd = input.substring(start, pos)

        when (cmd) {
            "frac", "dfrac", "tfrac", "cfrac" -> {
                val num = LatexConverter(readArgWithBraced().first).convert().trim()
                val den = LatexConverter(readArgWithBraced().first).convert().trim()
                out.append(wrapIfCompound(num)).append('/').append(if (den.isEmpty()) "" else wrapIfCompound(den))
            }

            "sqrt" -> {
                skipHorizontalSpaces()
                if (pos < input.length && input[pos] == '[') {
                    val degRaw = readBracketGroup()
                    val deg = LatexConverter(degRaw).convert().trim()
                    if (deg.isNotEmpty()) {
                        val sup = toScriptOrNull(deg, SUPERSCRIPTS)
                        out.append(sup ?: "^($deg)")
                    }
                }
                val inner = LatexConverter(readArgWithBraced().first).convert().trim()
                out.append('√').append(wrapIfCompound(inner))
            }

            in TEXT_WRAPPERS -> {
                val inner = LatexConverter(readArgWithBraced().first).convert()
                out.append(inner)
            }

            in DELIMITER_SIZE_MODIFIERS -> {
                skipHorizontalSpaces()
                if (pos < input.length && input[pos] == '.') pos++
            }

            "begin", "end" -> {
                readArgWithBraced()
            }

            "quad", "qquad" -> {
                if (out.isNotEmpty() && !out.endsWith(' ') && !out.endsWith('\n')) out.append(' ')
            }

            else -> {
                val symbol = LATEX_SYMBOLS[cmd]
                when {
                    symbol != null -> out.append(symbol)
                    cmd in MATH_FUNCTIONS -> out.append(cmd)
                    else -> out.append('\\').append(cmd)
                }
            }
        }
    }

    private fun readArgWithBraced(): Pair<String, Boolean> {
        skipHorizontalSpaces()
        if (pos >= input.length) return "" to false
        return when (input[pos]) {
            '{' -> {
                pos++
                val start = pos
                var depth = 1
                while (pos < input.length && depth > 0) {
                    when (input[pos]) {
                        '\\' -> pos += if (pos + 1 < input.length) 2 else 1
                        '{' -> {
                            depth++
                            pos++
                        }
                        '}' -> {
                            depth--
                            if (depth == 0) {
                                val slice = input.substring(start, pos)
                                pos++
                                return slice to true
                            }
                            pos++
                        }
                        else -> pos++
                    }
                }
                input.substring(start, pos) to true
            }

            '\\' -> {
                val start = pos
                pos++
                if (pos < input.length) {
                    if (input[pos].isLetter()) {
                        while (pos < input.length && input[pos].isLetter()) pos++
                    } else {
                        pos++
                    }
                }
                input.substring(start, pos) to false
            }

            else -> {
                val ch = input[pos++]
                ch.toString() to false
            }
        }
    }

    private fun readBracketGroup(): String {
        if (pos >= input.length || input[pos] != '[') return ""
        pos++
        val start = pos
        while (pos < input.length) {
            when (input[pos]) {
                '\\' -> pos += if (pos + 1 < input.length) 2 else 1
                ']' -> {
                    val slice = input.substring(start, pos)
                    pos++
                    return slice
                }
                else -> pos++
            }
        }
        return input.substring(start, pos)
    }

    private fun skipHorizontalSpaces() {
        while (pos < input.length && (input[pos] == ' ' || input[pos] == '\t')) pos++
    }
}

private fun formatScript(
    prefix: Char,
    rendered: String,
    wasBraced: Boolean,
    table: Map<Char, Char>,
): String {
    if (rendered.isEmpty()) return ""
    val mapped = toScriptOrNull(rendered, table)
    if (mapped != null) return mapped
    return if (!wasBraced && rendered.length == 1 && rendered[0].isLetterOrDigit()) {
        "$prefix$rendered"
    } else {
        "$prefix($rendered)"
    }
}

private fun toScriptOrNull(text: String, table: Map<Char, Char>): String? {
    val compact = text.filterNot { it == ' ' }
    if (compact.isEmpty()) return null
    val sb = StringBuilder(compact.length)
    for (ch in compact) {
        val mapped = table[ch] ?: return null
        sb.append(mapped)
    }
    return sb.toString()
}

private fun wrapIfCompound(expr: String): String {
    if (expr.isEmpty()) return "()"
    if (isSimpleAtom(expr)) return expr
    return if (expr.startsWith('(') && expr.endsWith(')') && hasBalancedOuterParens(expr)) expr else "($expr)"
}

private fun isSimpleAtom(expr: String): Boolean {
    if (expr.any { it.isWhitespace() }) return false
    if (expr.all { it.isDigit() }) return true
    if (expr.length == 1 && expr[0] !in "+-*/=<>()[]{}") return true
    // Base única seguida de sobrescritos/subscritos Unicode (ex.: `x²`, `a₁`).
    val first = expr[0]
    if (first.isLetter() && expr.drop(1).all { it in UNICODE_SCRIPT_CHARS }) return true
    return false
}

private fun hasBalancedOuterParens(expr: String): Boolean {
    var depth = 0
    for (i in expr.indices) {
        when (expr[i]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0 && i < expr.lastIndex) return false
            }
        }
    }
    return depth == 0
}

private val TEXT_WRAPPERS = setOf(
    "text", "textrm", "textit", "textbf", "mathrm", "mathbf", "mathit",
    "mathsf", "mathtt", "mathcal", "mathbb", "operatorname", "mbox",
    "overline", "underline", "widehat", "widetilde", "hat", "bar", "vec", "tilde", "dot", "ddot",
)

private val DELIMITER_SIZE_MODIFIERS = setOf(
    "left", "right", "bigl", "bigr", "Bigl", "Bigr", "big", "Big", "bigg", "Bigg",
)

private val MATH_FUNCTIONS = setOf(
    "sin", "cos", "tan", "cot", "sec", "csc",
    "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
    "log", "ln", "exp", "lim", "sup", "inf", "max", "min",
    "arg", "det", "dim", "gcd", "deg", "Pr", "mod", "bmod",
)

private val SUPERSCRIPTS = mapOf(
    '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
    '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
    '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
    'n' to 'ⁿ', 'i' to 'ⁱ',
)

private val SUBSCRIPTS = mapOf(
    '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
    '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
    '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
)

private val UNICODE_SCRIPT_CHARS: Set<Char> =
    (SUPERSCRIPTS.values + SUBSCRIPTS.values).toSet()

private val LATEX_SYMBOLS = mapOf(
    // Gregas minúsculas
    "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ",
    "epsilon" to "ε", "varepsilon" to "ε", "zeta" to "ζ", "eta" to "η",
    "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
    "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ",
    "omicron" to "ο", "pi" to "π", "varpi" to "ϖ", "rho" to "ρ",
    "varrho" to "ϱ", "sigma" to "σ", "varsigma" to "ς", "tau" to "τ",
    "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ",
    "psi" to "ψ", "omega" to "ω",
    // Gregas maiúsculas
    "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ",
    "Xi" to "Ξ", "Pi" to "Π", "Sigma" to "Σ", "Upsilon" to "Υ",
    "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    // Operadores aritméticos e relações
    "pm" to "±", "mp" to "∓", "times" to "×", "div" to "÷",
    "cdot" to "·", "ast" to "∗", "star" to "⋆", "circ" to "∘", "bullet" to "•",
    "le" to "≤", "leq" to "≤", "ge" to "≥", "geq" to "≥",
    "neq" to "≠", "ne" to "≠", "approx" to "≈", "equiv" to "≡",
    "sim" to "~", "simeq" to "≃", "cong" to "≅", "propto" to "∝",
    "ll" to "≪", "gg" to "≫", "prec" to "≺", "succ" to "≻",
    // Cálculo e constantes
    "infty" to "∞", "sum" to "∑", "prod" to "∏",
    "int" to "∫", "iint" to "∬", "oint" to "∮",
    "partial" to "∂", "nabla" to "∇", "hbar" to "ℏ", "ell" to "ℓ",
    "Re" to "ℜ", "Im" to "ℑ", "aleph" to "ℵ",
    // Setas
    "to" to "→", "rightarrow" to "→", "leftarrow" to "←", "leftrightarrow" to "↔",
    "Rightarrow" to "⇒", "Leftarrow" to "⇐", "Leftrightarrow" to "⇔",
    "implies" to "⇒", "iff" to "⇔", "mapsto" to "↦",
    // Conjuntos e lógica
    "in" to "∈", "notin" to "∉", "ni" to "∋",
    "subset" to "⊂", "subseteq" to "⊆", "supset" to "⊃", "supseteq" to "⊇",
    "cup" to "∪", "cap" to "∩", "emptyset" to "∅", "varnothing" to "∅",
    "setminus" to "∖",
    "forall" to "∀", "exists" to "∃", "nexists" to "∄",
    "neg" to "¬", "lnot" to "¬", "land" to "∧", "wedge" to "∧",
    "lor" to "∨", "vee" to "∨", "top" to "⊤", "bot" to "⊥",
    "vdash" to "⊢", "models" to "⊨", "therefore" to "∴", "because" to "∵",
    // Diversos e delimitadores
    "degree" to "°", "angle" to "∠", "perp" to "⊥", "parallel" to "∥",
    "dots" to "…", "ldots" to "…", "cdots" to "…", "prime" to "′",
    "langle" to "⟨", "rangle" to "⟩", "lfloor" to "⌊", "rfloor" to "⌋",
    "lceil" to "⌈", "rceil" to "⌉",
)
