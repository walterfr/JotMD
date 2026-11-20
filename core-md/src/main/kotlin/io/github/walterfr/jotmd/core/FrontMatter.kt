package io.github.walterfr.jotmd.core

/**
 * YAML front matter: bloco `---` ... `---` que começa na linha 1.
 *
 * [raw] é o bloco cru, delimitadores incluídos, sem o terminador de linha final.
 * [end] é o offset logo após [raw] no texto original. [fields] tem valor escalar
 * por chave; lista vira "a, b". [tags] vem do campo `tags` (ou `tag`), sem `#`.
 */
data class FrontMatter(
    val raw: String,
    val end: Int,
    val fields: Map<String, String>,
    val tags: List<String>,
)

/**
 * Front matter do início de [text], ou null.
 *
 * Só vale se o `---` abre na linha 1 e há um fechamento (`---` ou `...`); um
 * bloco não fechado é um `---` comum (régua/setext), nunca metadado — tratar
 * como front matter engoliria o documento inteiro do índice.
 *
 * ponytail: não é YAML completo. Só `chave: valor`, `chave: [a, b]` e listas
 * `- item` no topo; sem aninhamento, multilinha (`|`, `>`) nem âncoras. Trocar
 * por um parser YAML se o sidebar precisar de estrutura.
 */
fun frontMatterOf(text: String): FrontMatter? {
    var pos = lineEnd(text, 0)
    if (text.substring(0, pos).trimEnd() != "---" || pos == text.length) return null
    pos = nextLine(text, pos)

    val lines = mutableListOf<String>()
    while (pos <= text.length) {
        val end = lineEnd(text, pos)
        val line = text.substring(pos, end)
        if (line.trimEnd().let { it == "---" || it == "..." }) {
            val entries = entriesOf(lines)
            return FrontMatter(
                raw = text.substring(0, end),
                end = end,
                fields = entries.mapValues { it.value.joinToString(", ") },
                tags = tagsFrom(entries),
            )
        }
        if (end >= text.length) return null
        lines += line
        pos = nextLine(text, end)
    }
    return null
}

/** Fim da linha que começa em [from], antes de `\r\n`/`\n`. */
private fun lineEnd(text: String, from: Int): Int {
    var i = from
    while (i < text.length && text[i] != '\n' && text[i] != '\r') i++
    return i
}

/** Início da linha seguinte a uma que termina em [end] (pula `\r\n`, `\n` ou `\r`). */
private fun nextLine(text: String, end: Int): Int = when {
    end >= text.length -> text.length
    text[end] == '\r' && text.getOrNull(end + 1) == '\n' -> end + 2
    else -> end + 1
}

/** Chave -> itens. Escalar vira lista de um item; `[a, b]` e `- item` viram vários. */
private fun entriesOf(lines: List<String>): Map<String, List<String>> {
    val out = LinkedHashMap<String, List<String>>()
    var key: String? = null
    for (line in lines) {
        val t = line.trim()
        if (t.isEmpty() || t.startsWith("#")) continue
        if (t.startsWith("- ") || t == "-") {
            key?.let { k -> out[k] = out.getValue(k) + unquote(t.removePrefix("-").trim()) }
            continue
        }
        val colon = t.indexOf(':')
        // Linha indentada ou sem `:` é continuação/aninhamento que não suportamos.
        if (colon <= 0 || line.first().isWhitespace()) { key = null; continue }
        val k = t.substring(0, colon).trim()
        val v = t.substring(colon + 1).trim()
        out[k] = when {
            v.isEmpty() -> emptyList()
            v.startsWith("[") && v.endsWith("]") ->
                v.substring(1, v.length - 1).split(',').map { unquote(it.trim()) }.filter { it.isNotEmpty() }
            else -> listOf(unquote(v))
        }
        key = k
    }
    return out
}

private fun unquote(s: String): String =
    if (s.length >= 2 && (s[0] == '"' || s[0] == '\'') && s.last() == s[0]) s.substring(1, s.length - 1) else s

private fun tagsFrom(entries: Map<String, List<String>>): List<String> =
    // `tags: a b` e `tags: a, b` são ambos comuns; tag não tem espaço, então separa nos dois.
    (entries["tags"] ?: entries["tag"] ?: emptyList())
        .flatMap { it.split(',', ' ', '\t') }
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotEmpty() }
        .distinct()
