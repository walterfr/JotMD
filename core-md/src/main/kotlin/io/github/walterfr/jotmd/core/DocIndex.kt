package io.github.walterfr.jotmd.core

/** Tudo que o índice/busca extrai de um documento. */
data class DocIndex(
    val frontMatter: FrontMatter?,
    val wikilinks: List<Wikilink>,
    /** Só as `#tags` do corpo; use [tagsOf] para unir com as do front matter. */
    val bodyTags: List<String>,
)

/** Extrai front matter, wikilinks e tags de [text] com um único parse. */
fun docIndex(text: String): DocIndex {
    val fm = frontMatterOf(text)
    val scan = Scan(text, fm)
    return DocIndex(fm, wikilinks(scan), tags(scan))
}

/** Tags do documento: campo `tags:` do front matter + `#tags` do corpo, únicas. */
fun tagsOf(doc: DocIndex): List<String> = ((doc.frontMatter?.tags ?: emptyList()) + doc.bodyTags).distinct()
