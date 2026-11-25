package io.github.walterfr.jotmd.core

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

private const val MAX_ZIP_ENTRY_BYTES = 10 * 1024 * 1024
private const val MAX_ZIP_TOTAL_BYTES = 32 * 1024 * 1024

private val HEADING_STYLE_REGEX = Regex("""^(?:heading|t[ií]tulo)\s*([1-6])$""", RegexOption.IGNORE_CASE)
private val MONOSPACE_FONTS = listOf("consolas", "courier", "monospace", "mono", "menlo", "fira code", "jetbrains")

/**
 * Converte bytes HTML/XHTML em UTF-8 (removendo BOM opcional) para Markdown.
 */
fun importHtmlBytes(bytes: ByteArray): String {
    val html = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
    return htmlToMarkdown(html)
}

/**
 * Converte bytes de um arquivo DOCX (.zip com `word/document.xml`) para Markdown GFM.
 * Lança exceção se o ZIP for inválido ou não contiver `word/document.xml`.
 */
fun importDocxBytes(bytes: ByteArray): String {
    val entries = readZipEntries(bytes)
    val docXml = entries["word/document.xml"]
        ?: entries.entries.firstOrNull { it.key.equals("word/document.xml", ignoreCase = true) }?.value
        ?: throw IllegalArgumentException("DOCX sem word/document.xml")

    val relsXml = entries["word/_rels/document.xml.rels"]
        ?: entries.entries.firstOrNull { it.key.equals("word/_rels/document.xml.rels", ignoreCase = true) }?.value
    val rels = if (relsXml != null) parseDocxRels(relsXml) else emptyMap()

    val doc = parseSecureXml(docXml)
    val body = findFirstDescendant(doc.documentElement, "body") ?: doc.documentElement
    return renderDocxBody(body, rels)
}

/**
 * Converte bytes de um arquivo EPUB para Markdown juntando capítulos em ordem do `<spine>`.
 * Faz fallback para entradas XHTML/HTML ordenadas por nome se o OPF ou `<spine>` estiver ausente.
 */
fun importEpubBytes(bytes: ByteArray): String {
    val entries = readZipEntries(bytes)
    if (entries.isEmpty()) throw IllegalArgumentException("EPUB vazio ou inválido")

    val opfPath = resolveEpubOpfPath(entries)
    if (opfPath != null) {
        val opfBytes = entries[opfPath]
            ?: entries.entries.firstOrNull { it.key.equals(opfPath, ignoreCase = true) }?.value
        if (opfBytes != null) {
            val spineChapters = extractEpubSpineChapters(entries, opfPath, opfBytes)
            if (spineChapters != null) {
                return spineChapters.joinToString("\n\n---\n\n")
            }
        }
    }

    // Fallback quando não há OPF válido ou o <spine> está vazio.
    val htmlEntries = entries.entries
        .filter { (name, _) ->
            !name.startsWith("META-INF/", ignoreCase = true) && isEpubHtmlPath(name)
        }
        .sortedBy { it.key.lowercase(Locale.ROOT) }

    if (htmlEntries.isEmpty()) {
        throw IllegalArgumentException("EPUB sem OPF ou capítulos XHTML/HTML")
    }

    return htmlEntries
        .map { (_, content) -> importHtmlBytes(content) }
        .filter { it.isNotBlank() }
        .joinToString("\n\n---\n\n")
}

/**
 * Despacha importação por extensão de [fileName] (case-insensitive).
 * Retorna `null` em extensão desconhecida, ZIP corrompido ou estrutura obrigatória ausente.
 */
fun importDocumentBytes(fileName: String, bytes: ByteArray): String? {
    val ext = fileName.trim().substringAfterLast('.', "").lowercase(Locale.ROOT)
    return try {
        when (ext) {
            "html", "htm", "xhtml" -> importHtmlBytes(bytes)
            "docx" -> importDocxBytes(bytes)
            "epub" -> importEpubBytes(bytes)
            "md", "markdown", "mmd", "txt" -> bytes.toString(Charsets.UTF_8)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Sugere nome `.md` preservando o radical de [originalFileName], ou `"Sem Título.md"` se vazio.
 */
fun suggestedMarkdownTitle(originalFileName: String): String {
    val base = originalFileName
        .trim()
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()
    if (base.isEmpty() || base == "." || base == "..") return "Sem Título.md"
    val dot = base.lastIndexOf('.')
    val stem = if (dot > 0) base.substring(0, dot).trim() else base
    return if (stem.isEmpty()) "Sem Título.md" else "$stem.md"
}

private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
    val result = LinkedHashMap<String, ByteArray>()
    var totalRead = 0L
    val buffer = ByteArray(8192)

    ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
        while (true) {
            val entry = zis.nextEntry ?: break
            if (!entry.isDirectory) {
                val safePath = normalizeZipPath(entry.name)
                if (safePath != null) {
                    val out = ByteArrayOutputStream()
                    var entryRead = 0
                    while (true) {
                        val n = zis.read(buffer)
                        if (n <= 0) break
                        entryRead += n
                        totalRead += n
                        if (entryRead > MAX_ZIP_ENTRY_BYTES || totalRead > MAX_ZIP_TOTAL_BYTES) {
                            throw IllegalArgumentException("Arquivo ZIP excede limite de tamanho descomprimido")
                        }
                        out.write(buffer, 0, n)
                    }
                    result[safePath] = out.toByteArray()
                }
            }
            zis.closeEntry()
        }
    }
    return result
}

private fun normalizeZipPath(rawPath: String): String? {
    val unified = rawPath.replace('\\', '/').trimStart('/')
    if (unified.isEmpty()) return null
    val stack = ArrayList<String>()
    for (part in unified.split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> {
                if (stack.isEmpty()) return null
                stack.removeAt(stack.lastIndex)
            }
            else -> stack += part
        }
    }
    return if (stack.isEmpty()) null else stack.joinToString("/")
}

private fun parseSecureXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = true
    isXIncludeAware = false
    isExpandEntityReferences = false
    runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
    runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
}.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

private fun Element.localTagName(): String =
    localName ?: nodeName.substringAfter(':')

private fun Element.attr(localAttr: String): String? {
    if (hasAttribute(localAttr)) return getAttribute(localAttr)
    val attrs = attributes
    for (i in 0 until attrs.length) {
        val item = attrs.item(i)
        val name = item.localName ?: item.nodeName.substringAfter(':')
        if (name == localAttr) return item.nodeValue
    }
    return null
}

private fun childElements(parent: Element): List<Element> {
    val list = ArrayList<Element>()
    var cur: Node? = parent.firstChild
    while (cur != null) {
        if (cur is Element) list += cur
        cur = cur.nextSibling
    }
    return list
}

private fun findFirstChild(parent: Element, localName: String): Element? {
    var cur: Node? = parent.firstChild
    while (cur != null) {
        if (cur is Element && cur.localTagName() == localName) return cur
        cur = cur.nextSibling
    }
    return null
}

private fun findFirstDescendant(root: Element, localName: String): Element? {
    for (child in childElements(root)) {
        if (child.localTagName() == localName) return child
        findFirstDescendant(child, localName)?.let { return it }
    }
    return null
}

// --- DOCX ---

private fun parseDocxRels(bytes: ByteArray): Map<String, String> {
    val doc = parseSecureXml(bytes)
    val map = LinkedHashMap<String, String>()
    for (el in childElements(doc.documentElement)) {
        if (el.localTagName() == "Relationship") {
            val id = el.attr("Id")?.trim().orEmpty()
            val target = el.attr("Target")?.trim().orEmpty()
            if (id.isNotEmpty() && target.isNotEmpty()) {
                map[id] = target
            }
        }
    }
    return map
}

private enum class DocxBlockKind { NORMAL, LIST_ITEM }

private data class DocxRenderedBlock(
    val kind: DocxBlockKind,
    val markdown: String,
)

private fun renderDocxBody(body: Element, rels: Map<String, String>): String {
    val blocks = ArrayList<DocxRenderedBlock>()
    collectDocxBodyBlocks(body, rels, blocks)
    if (blocks.isEmpty()) return ""

    val sb = StringBuilder()
    for (i in blocks.indices) {
        val current = blocks[i]
        if (i > 0) {
            val prev = blocks[i - 1]
            if (prev.kind == DocxBlockKind.LIST_ITEM && current.kind == DocxBlockKind.LIST_ITEM) {
                sb.append('\n')
            } else {
                sb.append("\n\n")
            }
        }
        sb.append(current.markdown)
    }
    return sb.toString()
}

private fun collectDocxBodyBlocks(
    container: Element,
    rels: Map<String, String>,
    out: MutableList<DocxRenderedBlock>,
) {
    for (el in childElements(container)) {
        when (el.localTagName()) {
            "p" -> renderDocxParagraph(el, rels)?.let { out += it }
            "tbl" -> {
                val tableMd = renderDocxTable(el, rels)
                if (tableMd.isNotEmpty()) out += DocxRenderedBlock(DocxBlockKind.NORMAL, tableMd)
            }
            "sdt" -> findFirstChild(el, "sdtContent")?.let { collectDocxBodyBlocks(it, rels, out) }
        }
    }
}

private fun renderDocxParagraph(p: Element, rels: Map<String, String>): DocxRenderedBlock? {
    val pPr = findFirstChild(p, "pPr")
    val headingLevel = pPr?.let(::extractDocxHeadingLevel) ?: 0
    val listLevel = pPr?.let(::extractDocxListLevel)

    val content = renderDocxParagraphInlines(p, rels).trim()
    if (content.isEmpty()) return null

    if (headingLevel in 1..6) {
        return DocxRenderedBlock(DocxBlockKind.NORMAL, "${"#".repeat(headingLevel)} $content")
    }
    if (listLevel != null) {
        val indent = "  ".repeat(listLevel.coerceIn(0, 10))
        return DocxRenderedBlock(DocxBlockKind.LIST_ITEM, "$indent- $content")
    }
    return DocxRenderedBlock(DocxBlockKind.NORMAL, content)
}

private fun extractDocxHeadingLevel(pPr: Element): Int {
    val pStyle = findFirstChild(pPr, "pStyle")?.attr("val")?.trim()
    if (!pStyle.isNullOrEmpty()) {
        if (pStyle.equals("Title", ignoreCase = true)) return 1
        HEADING_STYLE_REGEX.matchEntire(pStyle)?.let { m ->
            return m.groupValues[1].toInt()
        }
    }
    val outlineVal = findFirstChild(pPr, "outlineLvl")?.attr("val")?.trim()?.toIntOrNull()
    if (outlineVal != null && outlineVal in 0..5) {
        return outlineVal + 1
    }
    return 0
}

private fun extractDocxListLevel(pPr: Element): Int? {
    val numPr = findFirstChild(pPr, "numPr") ?: return null
    val numId = findFirstChild(numPr, "numId")?.attr("val")?.trim()
    if (numId == "0") return null
    return findFirstChild(numPr, "ilvl")?.attr("val")?.trim()?.toIntOrNull() ?: 0
}

private data class RunStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val underline: Boolean = false,
    val code: Boolean = false,
)

private sealed interface InlinePiece {
    data class StyledText(val text: String, val style: RunStyle) : InlinePiece
    data class Hyperlink(val text: String, val target: String) : InlinePiece
}

private fun renderDocxParagraphInlines(container: Element, rels: Map<String, String>): String {
    val pieces = ArrayList<InlinePiece>()
    collectDocxInlinePieces(container, rels, pieces)
    return formatInlinePieces(pieces)
}

private fun collectDocxInlinePieces(
    container: Element,
    rels: Map<String, String>,
    out: MutableList<InlinePiece>,
) {
    for (el in childElements(container)) {
        when (el.localTagName()) {
            "r" -> {
                val style = extractRunStyle(el)
                val text = extractRunText(el)
                if (text.isNotEmpty()) {
                    val last = out.lastOrNull()
                    if (last is InlinePiece.StyledText && last.style == style) {
                        out[out.lastIndex] = InlinePiece.StyledText(last.text + text, style)
                    } else {
                        out += InlinePiece.StyledText(text, style)
                    }
                }
            }
            "hyperlink" -> {
                val rId = el.attr("id")
                val anchor = el.attr("anchor")?.trim()
                val target = when {
                    !rId.isNullOrBlank() -> rels[rId] ?: ""
                    !anchor.isNullOrBlank() -> "#$anchor"
                    else -> ""
                }
                val linkPieces = ArrayList<InlinePiece>()
                collectDocxInlinePieces(el, rels, linkPieces)
                val linkText = formatInlinePieces(linkPieces).trim()
                if (linkText.isNotEmpty()) {
                    if (target.isNotEmpty()) {
                        out += InlinePiece.Hyperlink(linkText, target)
                    } else {
                        out += InlinePiece.StyledText(linkText, RunStyle())
                    }
                }
            }
            "ins", "smartTag" -> collectDocxInlinePieces(el, rels, out)
            "sdt" -> findFirstChild(el, "sdtContent")?.let { collectDocxInlinePieces(it, rels, out) }
        }
    }
}

private fun extractRunStyle(r: Element): RunStyle {
    val rPr = findFirstChild(r, "rPr") ?: return RunStyle()
    val bold = isDocxToggleOn(findFirstChild(rPr, "b"))
    val italic = isDocxToggleOn(findFirstChild(rPr, "i"))
    val strike = isDocxToggleOn(findFirstChild(rPr, "strike")) ||
        isDocxToggleOn(findFirstChild(rPr, "dstrike"))
    val uEl = findFirstChild(rPr, "u")
    val underline = if (uEl == null) {
        false
    } else {
        val uVal = uEl.attr("val")?.trim()?.lowercase(Locale.ROOT)
        uVal == null || (uVal != "none" && uVal != "0" && uVal != "false")
    }
    val rStyleVal = findFirstChild(rPr, "rStyle")?.attr("val")?.lowercase(Locale.ROOT).orEmpty()
    val rFonts = findFirstChild(rPr, "rFonts")
    val fontNames = listOfNotNull(
        rFonts?.attr("ascii"),
        rFonts?.attr("hAnsi"),
        rFonts?.attr("cs"),
        rFonts?.attr("eastAsia"),
    ).joinToString(" ").lowercase(Locale.ROOT)

    val code = rStyleVal.contains("code") ||
        rStyleVal.contains("verbatim") ||
        rStyleVal.contains("monospace") ||
        MONOSPACE_FONTS.any { fontNames.contains(it) }

    return RunStyle(bold = bold, italic = italic, strike = strike, underline = underline, code = code)
}

private fun isDocxToggleOn(el: Element?): Boolean {
    if (el == null) return false
    val v = el.attr("val")?.trim()?.lowercase(Locale.ROOT) ?: return true
    return v != "0" && v != "false"
}

private fun extractRunText(r: Element): String {
    val sb = StringBuilder()
    for (child in childElements(r)) {
        when (child.localTagName()) {
            "t", "delText" -> sb.append(child.textContent)
            "br" -> {
                val type = child.attr("type")?.lowercase(Locale.ROOT)
                sb.append(if (type == "page") "\n" else "  \n")
            }
            "cr" -> sb.append('\n')
            "tab" -> sb.append('\t')
            "noBreakHyphen" -> sb.append('-')
        }
    }
    return sb.toString()
}

private fun formatInlinePieces(pieces: List<InlinePiece>): String {
    val sb = StringBuilder()
    for (piece in pieces) {
        when (piece) {
            is InlinePiece.StyledText -> sb.append(formatStyledText(piece.text, piece.style))
            is InlinePiece.Hyperlink -> {
                val safeTarget = if (piece.target.any { it.isWhitespace() } || '(' in piece.target || ')' in piece.target) {
                    "<${piece.target}>"
                } else {
                    piece.target
                }
                sb.append('[').append(piece.text).append("](").append(safeTarget).append(')')
            }
        }
    }
    return sb.toString()
}

private fun formatStyledText(raw: String, style: RunStyle): String {
    if (raw.isEmpty()) return ""
    if (style == RunStyle()) return raw

    val leadLen = raw.indexOfFirst { it != ' ' && it != '\t' && it != '\n' && it != '\r' }
    if (leadLen < 0) return raw
    val trailIdx = raw.indexOfLast { it != ' ' && it != '\t' && it != '\n' && it != '\r' }
    val lead = raw.substring(0, leadLen)
    val core = raw.substring(leadLen, trailIdx + 1)
    val trail = raw.substring(trailIdx + 1)

    var formatted = core
    if (style.code) {
        val ticks = chooseInlineBackticks(formatted)
        val padded = if (formatted.startsWith('`') || formatted.endsWith('`')) " $formatted " else formatted
        formatted = "$ticks$padded$ticks"
    }
    if (style.strike) formatted = "~~$formatted~~"
    if (style.underline) formatted = "<u>$formatted</u>"
    if (style.italic) formatted = "*$formatted*"
    if (style.bold) formatted = "**$formatted**"

    return "$lead$formatted$trail"
}

private fun chooseInlineBackticks(text: String): String {
    var maxRun = 0
    var run = 0
    for (ch in text) {
        if (ch == '`') {
            run++
            if (run > maxRun) maxRun = run
        } else {
            run = 0
        }
    }
    return "`".repeat(maxRun + 1)
}

private fun renderDocxTable(tbl: Element, rels: Map<String, String>): String {
    val rows = ArrayList<List<String>>()
    for (child in childElements(tbl)) {
        if (child.localTagName() == "tr") {
            val cells = ArrayList<String>()
            for (tc in childElements(child)) {
                if (tc.localTagName() == "tc") {
                    cells += renderDocxTableCell(tc, rels)
                }
            }
            if (cells.isNotEmpty()) rows += cells
        }
    }
    val colCount = rows.maxOfOrNull { it.size } ?: 0
    if (colCount == 0) return ""

    fun formatRow(cells: List<String>): String = buildString {
        append("| ")
        for (c in 0 until colCount) {
            if (c > 0) append(" | ")
            append(cells.getOrElse(c) { "" })
        }
        append(" |")
    }

    val sepRow = buildString {
        append("| ")
        for (c in 0 until colCount) {
            if (c > 0) append(" | ")
            append("---")
        }
        append(" |")
    }

    return buildString {
        append(formatRow(rows.first())).append('\n')
        append(sepRow)
        for (row in rows.drop(1)) {
            append('\n').append(formatRow(row))
        }
    }
}

private fun renderDocxTableCell(tc: Element, rels: Map<String, String>): String {
    val parts = ArrayList<String>()
    for (child in childElements(tc)) {
        if (child.localTagName() == "p") {
            val inline = renderDocxParagraphInlines(child, rels)
                .replace("  \n", " ")
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim()
            if (inline.isNotEmpty()) parts += inline
        }
    }
    return parts.joinToString(" ").replace("|", "\\|")
}

// --- EPUB ---

private fun resolveEpubOpfPath(entries: Map<String, ByteArray>): String? {
    val containerBytes = entries["META-INF/container.xml"]
        ?: entries.entries.firstOrNull { it.key.equals("META-INF/container.xml", ignoreCase = true) }?.value
    if (containerBytes != null) {
        val doc = parseSecureXml(containerBytes)
        val rootfile = findFirstDescendant(doc.documentElement, "rootfile")
        val fullPath = rootfile?.attr("full-path")?.trim()
        if (!fullPath.isNullOrEmpty()) {
            return normalizeZipPath(fullPath)
        }
    }
    return entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
}

private fun extractEpubSpineChapters(
    entries: Map<String, ByteArray>,
    opfPath: String,
    opfBytes: ByteArray,
): List<String>? {
    val doc = parseSecureXml(opfBytes)
    val manifestEl = findFirstDescendant(doc.documentElement, "manifest") ?: return null
    val spineEl = findFirstDescendant(doc.documentElement, "spine") ?: return null

    val manifest = LinkedHashMap<String, String>()
    for (item in childElements(manifestEl)) {
        if (item.localTagName() == "item") {
            val id = item.attr("id")?.trim().orEmpty()
            val href = item.attr("href")?.trim().orEmpty()
            if (id.isNotEmpty() && href.isNotEmpty()) {
                manifest[id] = href
            }
        }
    }

    val spineRefs = childElements(spineEl)
        .filter { it.localTagName() == "itemref" }
        .mapNotNull { it.attr("idref")?.trim()?.takeIf { ref -> ref.isNotEmpty() } }
    if (spineRefs.isEmpty()) return null

    val opfDir = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    val chapters = ArrayList<String>()

    for (idref in spineRefs) {
        val href = manifest[idref]?.substringBefore('#') ?: continue
        val decodedHref = decodePercentEncoding(href)
        val resolved = normalizeZipPath("$opfDir$decodedHref") ?: continue
        val chapterBytes = entries[resolved]
            ?: entries.entries.firstOrNull { it.key.equals(resolved, ignoreCase = true) }?.value
            ?: continue
        val md = importHtmlBytes(chapterBytes)
        if (md.isNotBlank()) chapters += md
    }
    return chapters
}

private fun isEpubHtmlPath(path: String): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return ext == "xhtml" || ext == "html" || ext == "htm"
}

private fun decodePercentEncoding(input: String): String {
    if ('%' !in input) return input
    val out = ByteArrayOutputStream(input.length)
    var i = 0
    while (i < input.length) {
        val ch = input[i]
        if (ch == '%' && i + 2 < input.length) {
            val hi = input[i + 1].digitToIntOrNull(16)
            val lo = input[i + 2].digitToIntOrNull(16)
            if (hi != null && lo != null) {
                out.write((hi shl 4) or lo)
                i += 3
                continue
            }
        }
        val utf8 = ch.toString().toByteArray(Charsets.UTF_8)
        out.write(utf8, 0, utf8.size)
        i++
    }
    return out.toString(Charsets.UTF_8)
}
