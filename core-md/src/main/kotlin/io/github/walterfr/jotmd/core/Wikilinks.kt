package io.github.walterfr.jotmd.core

/** `[[alvo#âncora|alias]]`. [offset] é o índice do `[[` no texto original. */
data class Wikilink(
    val target: String,
    val alias: String?,
    val anchor: String?,
    val offset: Int,
)

// Alvo/âncora/alias sem colchetes nem quebra de linha: wikilink não atravessa linhas.
private val WIKILINK = Regex("""\[\[([^\[\]|#\r\n]*)(?:#([^\[\]|\r\n]*))?(?:\|([^\[\]\r\n]*))?]]""")

/** Wikilinks de [source], fora de code span, cerca, bloco indentado e front matter. */
fun wikilinksIn(source: String): List<Wikilink> = wikilinks(Scan(source, frontMatterOf(source)))

internal fun wikilinks(scan: Scan): List<Wikilink> = WIKILINK.findAll(scan.body).mapNotNull { m ->
    val target = m.groupValues[1].trim()
    val anchor = m.groups[2]?.value?.trim()?.ifEmpty { null }
    // `[[]]` e `[[|x]]` não apontam para lugar nenhum; `[[#seção]]` aponta para a própria nota.
    if ((target.isEmpty() && anchor == null) || scan.skipped(m.range.first)) return@mapNotNull null
    Wikilink(target, m.groups[3]?.value?.trim()?.ifEmpty { null }, anchor, m.range.first)
}.toList()
