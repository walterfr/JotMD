package io.github.walterfr.jotmd.core

// Antes do `#` só vale começo de texto, espaço, abertura ou delimitador de ênfase
// (`**#tag**` é tag em negrito): `a#b` e `/#frag` (URL) ficam de fora.
// Corpo: letras (acentuadas inclusive), dígitos, `_`, `-`, e `/` entre segmentos (tag aninhada).
private val TAG = Regex("""(?<![^\s(\[{"'*_~])#([\p{L}\p{N}_-]+(?:/[\p{L}\p{N}_-]+)*)""")

/** Tags `#tag` do corpo de [source] (sem front matter, sem código), sem `#`, únicas, em ordem. */
fun tagsIn(source: String): List<String> = tags(Scan(source, frontMatterOf(source)))

internal fun tags(scan: Scan): List<String> = tagSpans(scan).map { it.second }.distinct()

/**
 * Cada ocorrência de `#tag` em [source]: faixa no texto (com o `#`) e o nome.
 * Diferente de [tagsIn], não deduplica — o editor precisa destacar todas.
 */
fun tagSpansIn(source: String): List<Pair<IntRange, String>> = tagSpans(Scan(source, frontMatterOf(source)))

private fun tagSpans(scan: Scan): List<Pair<IntRange, String>> = TAG.findAll(scan.body).mapNotNull { m ->
    val tag = m.groupValues[1]
    // `#123` não é tag (issue/número); exige ao menos uma letra.
    if (tag.none { it.isLetter() } || scan.skipped(m.range.first)) null else m.range to tag
}.toList()
