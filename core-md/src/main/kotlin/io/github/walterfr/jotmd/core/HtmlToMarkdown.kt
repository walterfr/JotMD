package io.github.walterfr.jotmd.core

import java.util.Locale

/**
 * Converte um trecho ou documento [html] para Markdown GFM compatível com o JotMD.
 * Tolerante a HTML vazio ou malformado, nunca lança exceção.
 */
fun htmlToMarkdown(html: String): String {
    if (html.isBlank()) return ""
    return try {
        val tokens = tokenizeHtml(html)
        val root = buildHtmlTree(tokens)
        renderBlockContainer(root.children, listDepth = 0).trim()
    } catch (_: Exception) {
        ""
    }
}

private sealed interface HtmlToken {
    data class Text(val raw: String) : HtmlToken
    data class StartTag(
        val name: String,
        val attrs: Map<String, String>,
        val selfClosing: Boolean,
    ) : HtmlToken
    data class EndTag(val name: String) : HtmlToken
}

private sealed interface HtmlNode {
    data class Text(val raw: String) : HtmlNode
    data class Element(
        val tag: String,
        val attrs: Map<String, String>,
        val children: MutableList<HtmlNode> = ArrayList(),
    ) : HtmlNode
}

private val VOID_TAGS = setOf(
    "area", "base", "br", "col", "embed", "hr", "img", "input",
    "link", "meta", "param", "source", "track", "wbr",
)

private val SKIP_SUBTREE_TAGS = setOf("head", "script", "style")

private val BLOCK_CONTAINER_TAGS = setOf(
    "html", "body", "main", "article", "section", "header", "footer",
    "aside", "nav", "div", "figure", "figcaption", "details", "summary",
)

private val INLINE_TAGS = setOf(
    "a", "strong", "b", "em", "i", "del", "s", "strike", "u", "mark",
    "code", "span", "sub", "sup", "br", "img", "input", "small",
    "abbr", "cite", "q", "kbd", "var", "samp", "time", "label", "font",
)

private val AUTO_CLOSE_P_TAGS = setOf(
    "h1", "h2", "h3", "h4", "h5", "h6", "p", "div", "ul", "ol",
    "blockquote", "pre", "table", "hr", "section", "article", "header", "footer", "aside", "nav",
)

private fun Char.isAsciiTagStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
private fun Char.isAsciiTagChar(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '-' || this == '_' || this == ':'

private fun tokenizeHtml(html: String): List<HtmlToken> {
    val tokens = ArrayList<HtmlToken>()
    var pos = 0
    val len = html.length

    while (pos < len) {
        if (html[pos] != '<') {
            val start = pos
            while (pos < len && html[pos] != '<') pos++
            tokens += HtmlToken.Text(html.substring(start, pos))
            continue
        }

        // Comentário HTML
        if (html.startsWith("<!--", pos)) {
            val end = html.indexOf("-->", pos + 4)
            pos = if (end >= 0) end + 3 else len
            continue
        }

        // CDATA
        if (html.startsWith("<![CDATA[", pos, ignoreCase = true)) {
            val end = html.indexOf("]]>", pos + 9)
            if (end >= 0) {
                tokens += HtmlToken.Text(html.substring(pos + 9, end))
                pos = end + 3
            } else {
                tokens += HtmlToken.Text(html.substring(pos + 9))
                pos = len
            }
            continue
        }

        // DOCTYPE ou instrução de processamento <?...?>
        if (pos + 1 < len && (html[pos + 1] == '!' || html[pos + 1] == '?')) {
            val end = html.indexOf('>', pos + 2)
            pos = if (end >= 0) end + 1 else len
            continue
        }

        // Tag de fechamento </tag>
        if (pos + 1 < len && html[pos + 1] == '/') {
            if (pos + 2 < len && html[pos + 2].isAsciiTagStart()) {
                var nameEnd = pos + 2
                while (nameEnd < len && html[nameEnd].isAsciiTagChar()) nameEnd++
                val name = html.substring(pos + 2, nameEnd).lowercase(Locale.ROOT)
                val closeGt = html.indexOf('>', nameEnd)
                pos = if (closeGt >= 0) closeGt + 1 else len
                tokens += HtmlToken.EndTag(name)
            } else {
                tokens += HtmlToken.Text("<")
                pos++
            }
            continue
        }

        // Tag de abertura <tag ...>
        if (pos + 1 < len && html[pos + 1].isAsciiTagStart()) {
            var cursor = pos + 1
            while (cursor < len && html[cursor].isAsciiTagChar()) cursor++
            val name = html.substring(pos + 1, cursor).lowercase(Locale.ROOT)
            val attrs = LinkedHashMap<String, String>()
            var selfClosing = false

            while (cursor < len) {
                while (cursor < len && html[cursor].isWhitespace()) cursor++
                if (cursor >= len) break
                if (html[cursor] == '>') {
                    cursor++
                    break
                }
                if (html[cursor] == '/' && cursor + 1 < len && html[cursor + 1] == '>') {
                    selfClosing = true
                    cursor += 2
                    break
                }
                if (html[cursor] == '/') {
                    cursor++
                    continue
                }

                // Nome do atributo
                val attrStart = cursor
                while (cursor < len && !html[cursor].isWhitespace() &&
                    html[cursor] != '=' && html[cursor] != '>' && html[cursor] != '/'
                ) {
                    cursor++
                }
                if (cursor == attrStart) {
                    cursor++
                    continue
                }
                val attrName = html.substring(attrStart, cursor).lowercase(Locale.ROOT)
                while (cursor < len && html[cursor].isWhitespace()) cursor++
                var attrValue = ""
                if (cursor < len && html[cursor] == '=') {
                    cursor++
                    while (cursor < len && html[cursor].isWhitespace()) cursor++
                    if (cursor < len && (html[cursor] == '"' || html[cursor] == '\'')) {
                        val quote = html[cursor++]
                        val valStart = cursor
                        while (cursor < len && html[cursor] != quote) cursor++
                        attrValue = decodeHtmlEntities(html.substring(valStart, cursor))
                        if (cursor < len && html[cursor] == quote) cursor++
                    } else {
                        val valStart = cursor
                        while (cursor < len && !html[cursor].isWhitespace() && html[cursor] != '>') {
                            if (html[cursor] == '/' && cursor + 1 < len && html[cursor + 1] == '>') break
                            cursor++
                        }
                        attrValue = decodeHtmlEntities(html.substring(valStart, cursor))
                    }
                }
                attrs[attrName] = attrValue
            }

            pos = cursor
            tokens += HtmlToken.StartTag(name, attrs, selfClosing)

            // Conteúdo cru de <script> ou <style> não deve ser tokenizado como HTML.
            if (!selfClosing && (name == "script" || name == "style")) {
                val closeIdx = findRawEndTag(html, pos, name)
                if (closeIdx >= 0) {
                    val gt = html.indexOf('>', closeIdx + 2 + name.length)
                    pos = if (gt >= 0) gt + 1 else len
                    tokens += HtmlToken.EndTag(name)
                } else {
                    pos = len
                }
            }
            continue
        }

        // '<' literal isolado no texto
        tokens += HtmlToken.Text("<")
        pos++
    }
    return tokens
}

private fun findRawEndTag(html: String, from: Int, tag: String): Int {
    val needle = "</$tag"
    var idx = html.indexOf(needle, from, ignoreCase = true)
    while (idx >= 0) {
        val after = idx + needle.length
        if (after >= html.length || html[after] == '>' || html[after].isWhitespace()) {
            return idx
        }
        idx = html.indexOf(needle, after, ignoreCase = true)
    }
    return -1
}

private fun buildHtmlTree(tokens: List<HtmlToken>): HtmlNode.Element {
    val root = HtmlNode.Element("document", emptyMap())
    val stack = ArrayList<HtmlNode.Element>()
    stack += root

    fun popIfOnTop(tag: String) {
        if (stack.size > 1 && stack.last().tag == tag) {
            stack.removeAt(stack.lastIndex)
        }
    }

    fun popUpToWithin(target: String, barrierTags: Set<String>) {
        for (i in stack.lastIndex downTo 1) {
            val t = stack[i].tag
            if (t == target) {
                while (stack.size > i) stack.removeAt(stack.lastIndex)
                return
            }
            if (t in barrierTags) return
        }
    }

    for (token in tokens) {
        when (token) {
            is HtmlToken.Text -> stack.last().children += HtmlNode.Text(token.raw)

            is HtmlToken.StartTag -> {
                val tag = token.name
                when (tag) {
                    "li" -> popUpToWithin("li", setOf("ul", "ol"))
                    "tr" -> popUpToWithin("tr", setOf("table", "thead", "tbody", "tfoot"))
                    "td", "th" -> {
                        popUpToWithin("td", setOf("tr", "table"))
                        popUpToWithin("th", setOf("tr", "table"))
                    }
                    in AUTO_CLOSE_P_TAGS -> popIfOnTop("p")
                }
                val el = HtmlNode.Element(tag, token.attrs)
                stack.last().children += el
                if (!token.selfClosing && tag !in VOID_TAGS) {
                    stack += el
                }
            }

            is HtmlToken.EndTag -> {
                val matchIdx = (stack.lastIndex downTo 1).firstOrNull { stack[it].tag == token.name }
                if (matchIdx != null) {
                    while (stack.size > matchIdx) stack.removeAt(stack.lastIndex)
                }
            }
        }
    }
    return root
}

private fun renderBlockContainer(nodes: List<HtmlNode>, listDepth: Int): String {
    val blocks = ArrayList<String>()
    val inlineRun = ArrayList<HtmlNode>()

    fun flushInline() {
        if (inlineRun.isEmpty()) return
        val rendered = renderInlineNodes(inlineRun).trim()
        inlineRun.clear()
        if (rendered.isNotEmpty()) blocks += rendered
    }

    for (node in nodes) {
        if (isInlineNode(node)) {
            inlineRun += node
        } else {
            flushInline()
            val el = node as HtmlNode.Element
            val block = renderBlockElement(el, listDepth)
            if (block.isNotEmpty()) blocks += block
        }
    }
    flushInline()
    return blocks.joinToString("\n\n")
}

private fun isInlineNode(node: HtmlNode): Boolean = when (node) {
    is HtmlNode.Text -> true
    is HtmlNode.Element -> node.tag in INLINE_TAGS
}

private fun renderBlockElement(el: HtmlNode.Element, listDepth: Int): String = when (el.tag) {
    in SKIP_SUBTREE_TAGS -> ""

    "h1", "h2", "h3", "h4", "h5", "h6" -> {
        val level = el.tag[1].digitToInt()
        val text = renderInlineNodes(el.children).trim()
        if (text.isEmpty()) "" else "${"#".repeat(level)} $text"
    }

    "hr" -> "---"

    "p" -> renderParagraphElement(el, listDepth)

    "pre" -> renderPreBlock(el)

    "blockquote" -> renderBlockquote(el, listDepth)

    "ul", "ol" -> renderList(el, listDepth)

    "table" -> renderTable(el)

    in BLOCK_CONTAINER_TAGS -> renderBlockContainer(el.children, listDepth)

    else -> {
        if (el.children.all { isInlineNode(it) }) {
            renderInlineNodes(el.children).trim()
        } else {
            renderBlockContainer(el.children, listDepth)
        }
    }
}

private fun renderParagraphElement(el: HtmlNode.Element, listDepth: Int): String {
    if (el.children.all { isInlineNode(it) }) {
        return renderInlineNodes(el.children).trim()
    }
    return renderBlockContainer(el.children, listDepth)
}

private fun renderPreBlock(el: HtmlNode.Element): String {
    val nonWs = el.children.filterNot { it is HtmlNode.Text && it.raw.isBlank() }
    val codeEl = nonWs.singleOrNull() as? HtmlNode.Element
    val target = if (codeEl != null && codeEl.tag == "code") codeEl else el
    val lang = extractCodeLanguage(codeEl?.attrs?.get("class"))
        ?: extractCodeLanguage(el.attrs["class"])
        ?: ""
    val rawCode = collectPreRawText(target)
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .removeSuffix("\n")
    val fence = chooseBacktickFence(rawCode)
    return if (rawCode.isEmpty()) {
        "$fence$lang\n$fence"
    } else {
        "$fence$lang\n$rawCode\n$fence"
    }
}

private fun extractCodeLanguage(classAttr: String?): String? {
    if (classAttr.isNullOrBlank()) return null
    for (token in classAttr.trim().split(Regex("""\s+"""))) {
        when {
            token.startsWith("language-") && token.length > 9 -> return token.substring(9)
            token.startsWith("lang-") && token.length > 5 -> return token.substring(5)
        }
    }
    return null
}

private fun collectPreRawText(node: HtmlNode): String = when (node) {
    is HtmlNode.Text -> decodeHtmlEntities(node.raw)
    is HtmlNode.Element -> when (node.tag) {
        in SKIP_SUBTREE_TAGS -> ""
        "br" -> "\n"
        else -> buildString {
            for (child in node.children) append(collectPreRawText(child))
        }
    }
}

private fun chooseBacktickFence(code: String): String {
    var maxRun = 2
    var run = 0
    for (ch in code) {
        if (ch == '`') {
            run++
            if (run > maxRun) maxRun = run
        } else {
            run = 0
        }
    }
    return "`".repeat(maxRun + 1)
}

private fun renderBlockquote(el: HtmlNode.Element, listDepth: Int): String {
    val inner = renderBlockContainer(el.children, listDepth).trim()
    if (inner.isEmpty()) return ""
    return inner.lines().joinToString("\n") { line ->
        if (line.isEmpty()) ">" else "> $line"
    }
}

private fun renderList(listEl: HtmlNode.Element, depth: Int): String {
    val ordered = listEl.tag == "ol"
    var index = listEl.attrs["start"]?.trim()?.toIntOrNull() ?: 1
    val indent = "  ".repeat(depth)
    val lines = ArrayList<String>()

    for (child in listEl.children) {
        val item = child as? HtmlNode.Element ?: continue
        if (item.tag != "li") continue
        val bullet = if (ordered) "${index++}. " else "- "
        val renderedItem = renderListItem(item, indent, bullet, depth)
        if (renderedItem.isNotEmpty()) lines += renderedItem
    }
    return lines.joinToString("\n")
}

private fun renderListItem(
    item: HtmlNode.Element,
    indent: String,
    bullet: String,
    depth: Int,
): String {
    val (checkboxPrefix, contentNodes) = extractLeadingTaskCheckbox(item.children)
    val prefix = "$indent$bullet$checkboxPrefix"
    val contIndent = indent + " ".repeat(bullet.length)

    val out = StringBuilder()
    val inlineRun = ArrayList<HtmlNode>()
    var firstBlockEmitted = false

    fun appendFirstOrCont(blockText: String, isParagraph: Boolean) {
        val cleaned = blockText.trimEnd()
        if (cleaned.isEmpty()) return
        val blockLines = cleaned.lines()
        if (!firstBlockEmitted) {
            out.append(prefix).append(blockLines.first())
            for (extra in blockLines.drop(1)) {
                out.append('\n')
                if (extra.isNotEmpty()) out.append(contIndent).append(extra)
            }
            firstBlockEmitted = true
        } else {
            if (isParagraph) out.append('\n')
            for (line in blockLines) {
                out.append('\n')
                if (line.isNotEmpty()) out.append(contIndent).append(line)
            }
        }
    }

    fun flushInline() {
        if (inlineRun.isEmpty()) return
        val text = renderInlineNodes(inlineRun).trim()
        inlineRun.clear()
        if (text.isNotEmpty()) appendFirstOrCont(text, isParagraph = false)
    }

    for (child in contentNodes) {
        if (isInlineNode(child)) {
            inlineRun += child
        } else {
            flushInline()
            val el = child as HtmlNode.Element
            when (el.tag) {
                "ul", "ol" -> {
                    val sub = renderList(el, depth + 1)
                    if (sub.isNotEmpty()) {
                        if (!firstBlockEmitted) {
                            out.append(prefix.trimEnd()).append('\n').append(sub)
                            firstBlockEmitted = true
                        } else {
                            out.append('\n').append(sub)
                        }
                    }
                }
                "p" -> appendFirstOrCont(renderParagraphElement(el, depth), isParagraph = true)
                else -> {
                    val b = renderBlockElement(el, depth)
                    if (b.isNotEmpty()) appendFirstOrCont(b, isParagraph = true)
                }
            }
        }
    }
    flushInline()

    if (!firstBlockEmitted) {
        return prefix.trimEnd()
    }
    return out.toString()
}

private fun extractLeadingTaskCheckbox(children: List<HtmlNode>): Pair<String, List<HtmlNode>> {
    val firstSignificantIdx = children.indexOfFirst {
        !(it is HtmlNode.Text && it.raw.isBlank())
    }
    if (firstSignificantIdx < 0) return "" to children

    val firstNode = children[firstSignificantIdx] as? HtmlNode.Element ?: return "" to children
    if (firstNode.tag == "input" && firstNode.attrs["type"]?.equals("checkbox", ignoreCase = true) == true) {
        val box = if ("checked" in firstNode.attrs) "[x] " else "[ ] "
        val remaining = children.filterIndexed { i, _ -> i != firstSignificantIdx }
        return box to remaining
    }
    if (firstNode.tag == "p") {
        val (box, innerRemaining) = extractLeadingTaskCheckbox(firstNode.children)
        if (box.isNotEmpty()) {
            val replaced = HtmlNode.Element("p", firstNode.attrs, innerRemaining.toMutableList())
            val remaining = children.mapIndexed { i, n -> if (i == firstSignificantIdx) replaced else n }
            return box to remaining
        }
    }
    return "" to children
}

private enum class MdTableAlign { NONE, LEFT, CENTER, RIGHT }

private fun renderTable(tableEl: HtmlNode.Element): String {
    val headRows = ArrayList<HtmlNode.Element>()
    val bodyRows = ArrayList<HtmlNode.Element>()

    fun collectRows(node: HtmlNode.Element, inThead: Boolean) {
        for (child in node.children) {
            val el = child as? HtmlNode.Element ?: continue
            when (el.tag) {
                "table" -> Unit // Não absorve tabela aninhada como linhas da externa.
                "thead" -> collectRows(el, inThead = true)
                "tbody", "tfoot" -> collectRows(el, inThead = false)
                "tr" -> if (inThead) headRows += el else bodyRows += el
                else -> collectRows(el, inThead)
            }
        }
    }
    collectRows(tableEl, inThead = false)

    val allRows = headRows + bodyRows
    if (allRows.isEmpty()) return ""

    val headerTr = headRows.firstOrNull() ?: allRows.first()
    val dataTrs = if (headRows.isNotEmpty()) {
        headRows.drop(1) + bodyRows
    } else {
        allRows.drop(1)
    }

    fun extractCells(tr: HtmlNode.Element): List<HtmlNode.Element> =
        tr.children.filterIsInstance<HtmlNode.Element>().filter { it.tag == "th" || it.tag == "td" }

    val headerCells = extractCells(headerTr)
    val dataCellRows = dataTrs.map { extractCells(it) }
    val colCount = maxOf(headerCells.size, dataCellRows.maxOfOrNull { it.size } ?: 0)
    if (colCount == 0) return ""

    val alignments = (0 until colCount).map { col ->
        headerCells.getOrNull(col)?.let(::extractTableAlignment) ?: MdTableAlign.NONE
    }

    fun formatRow(cells: List<HtmlNode.Element>): String = buildString {
        append("| ")
        for (c in 0 until colCount) {
            if (c > 0) append(" | ")
            val cell = cells.getOrNull(c)
            val text = if (cell != null) {
                renderInlineNodes(cell.children)
                    .trim()
                    .replace("  \n", " ")
                    .replace('\n', ' ')
                    .replace("|", "\\|")
            } else {
                ""
            }
            append(text)
        }
        append(" |")
    }

    val sepRow = buildString {
        append("| ")
        for (c in 0 until colCount) {
            if (c > 0) append(" | ")
            append(
                when (alignments[c]) {
                    MdTableAlign.NONE -> "---"
                    MdTableAlign.LEFT -> ":---"
                    MdTableAlign.CENTER -> ":---:"
                    MdTableAlign.RIGHT -> "---:"
                },
            )
        }
        append(" |")
    }

    return buildString {
        append(formatRow(headerCells)).append('\n')
        append(sepRow)
        for (row in dataCellRows) {
            append('\n').append(formatRow(row))
        }
    }
}

private fun extractTableAlignment(cell: HtmlNode.Element): MdTableAlign {
    val style = cell.attrs["style"]
    if (style != null) {
        val m = Regex("""text-align\s*:\s*(left|center|right)""", RegexOption.IGNORE_CASE).find(style)
        if (m != null) {
            return when (m.groupValues[1].lowercase(Locale.ROOT)) {
                "center" -> MdTableAlign.CENTER
                "right" -> MdTableAlign.RIGHT
                "left" -> MdTableAlign.LEFT
                else -> MdTableAlign.NONE
            }
        }
    }
    return when (cell.attrs["align"]?.trim()?.lowercase(Locale.ROOT)) {
        "center" -> MdTableAlign.CENTER
        "right" -> MdTableAlign.RIGHT
        "left" -> MdTableAlign.LEFT
        else -> MdTableAlign.NONE
    }
}

private class InlineWriter {
    private val sb = StringBuilder()
    private var pendingSpace = false
    private var atLineStart = true

    fun appendCollapsedText(raw: String) {
        val decoded = decodeHtmlEntities(raw)
        for (ch in decoded) {
            if (ch.isWhitespace()) {
                if (!atLineStart) pendingSpace = true
            } else {
                if (pendingSpace) {
                    sb.append(' ')
                    pendingSpace = false
                }
                sb.append(ch)
                atLineStart = false
            }
        }
    }

    fun appendSpaceIfNotLineStart() {
        if (!atLineStart) pendingSpace = true
    }

    fun appendLiteralToken(token: String) {
        if (token.isEmpty()) return
        if (pendingSpace) {
            sb.append(' ')
            pendingSpace = false
        }
        sb.append(token)
        atLineStart = false
    }

    fun appendHardBreak() {
        pendingSpace = false
        sb.append("  \n")
        atLineStart = true
    }

    override fun toString(): String = sb.toString()
}

private fun renderInlineNodes(nodes: List<HtmlNode>): String {
    val writer = InlineWriter()
    for (node in nodes) {
        appendInlineNode(node, writer)
    }
    return writer.toString()
}

private fun appendInlineNode(node: HtmlNode, writer: InlineWriter) {
    when (node) {
        is HtmlNode.Text -> writer.appendCollapsedText(node.raw)

        is HtmlNode.Element -> when (node.tag) {
            in SKIP_SUBTREE_TAGS -> Unit

            "br" -> writer.appendHardBreak()

            "img" -> {
                val src = node.attrs["src"]?.trim().orEmpty()
                if (src.isNotEmpty()) {
                    val alt = (node.attrs["alt"] ?: "").replace('\r', ' ').replace('\n', ' ').trim()
                    val safeSrc = if (src.any { it.isWhitespace() }) "<$src>" else src
                    writer.appendLiteralToken("![$alt]($safeSrc)")
                }
            }

            "input" -> {
                if (node.attrs["type"]?.equals("checkbox", ignoreCase = true) == true) {
                    writer.appendLiteralToken(if ("checked" in node.attrs) "[x]" else "[ ]")
                }
            }

            "code" -> {
                val raw = collectPreRawText(node)
                    .replace("\r\n", " ")
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                if (raw.isNotEmpty()) {
                    val fence = chooseInlineCodeTicks(raw)
                    val padded = if (raw.startsWith('`') || raw.endsWith('`')) " $raw " else raw
                    writer.appendLiteralToken("$fence$padded$fence")
                }
            }

            "strong", "b" -> appendWrappedInline(node, "**", "**", writer)
            "em", "i" -> appendWrappedInline(node, "*", "*", writer)
            "del", "s", "strike" -> appendWrappedInline(node, "~~", "~~", writer)
            "u" -> appendWrappedInline(node, "<u>", "</u>", writer)
            "mark" -> appendWrappedInline(node, "==", "==", writer)

            "a" -> {
                val href = node.attrs["href"]?.trim().orEmpty()
                val leadSpace = hasLeadingHtmlSpace(node.children)
                val trailSpace = hasTrailingHtmlSpace(node.children)
                val trimmed = renderInlineNodes(node.children).trim()
                if (trimmed.isEmpty()) {
                    if (leadSpace || trailSpace) writer.appendSpaceIfNotLineStart()
                } else {
                    if (leadSpace) writer.appendSpaceIfNotLineStart()
                    if (href.isEmpty()) {
                        writer.appendLiteralToken(trimmed)
                    } else {
                        val safeHref = if (href.any { it.isWhitespace() } || '(' in href || ')' in href) {
                            "<$href>"
                        } else {
                            href
                        }
                        writer.appendLiteralToken("[$trimmed]($safeHref)")
                    }
                    if (trailSpace) writer.appendSpaceIfNotLineStart()
                }
            }

            else -> {
                for (child in node.children) appendInlineNode(child, writer)
            }
        }
    }
}

private fun appendWrappedInline(
    el: HtmlNode.Element,
    open: String,
    close: String,
    writer: InlineWriter,
) {
    val leadSpace = hasLeadingHtmlSpace(el.children)
    val trailSpace = hasTrailingHtmlSpace(el.children)
    val inner = renderInlineNodes(el.children).trim()
    if (inner.isEmpty()) {
        if (leadSpace || trailSpace) writer.appendSpaceIfNotLineStart()
        return
    }
    if (leadSpace) writer.appendSpaceIfNotLineStart()
    writer.appendLiteralToken("$open$inner$close")
    if (trailSpace) writer.appendSpaceIfNotLineStart()
}

private fun hasLeadingHtmlSpace(nodes: List<HtmlNode>): Boolean {
    val first = nodes.firstOrNull() ?: return false
    return when (first) {
        is HtmlNode.Text -> decodeHtmlEntities(first.raw).firstOrNull()?.isWhitespace() == true
        is HtmlNode.Element -> if (first.tag in INLINE_TAGS && first.tag != "br" && first.tag != "img") {
            hasLeadingHtmlSpace(first.children)
        } else {
            false
        }
    }
}

private fun hasTrailingHtmlSpace(nodes: List<HtmlNode>): Boolean {
    val last = nodes.lastOrNull() ?: return false
    return when (last) {
        is HtmlNode.Text -> decodeHtmlEntities(last.raw).lastOrNull()?.isWhitespace() == true
        is HtmlNode.Element -> if (last.tag in INLINE_TAGS && last.tag != "br" && last.tag != "img") {
            hasTrailingHtmlSpace(last.children)
        } else {
            false
        }
    }
}

private fun chooseInlineCodeTicks(code: String): String {
    var maxRun = 0
    var run = 0
    for (ch in code) {
        if (ch == '`') {
            run++
            if (run > maxRun) maxRun = run
        } else {
            run = 0
        }
    }
    return "`".repeat(maxRun + 1)
}

private val HTML_ENTITY_REGEX = Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z][a-zA-Z0-9]*);""")

private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " ",
    "mdash" to "—",
    "ndash" to "–",
    "hellip" to "…",
    "lsquo" to "‘",
    "rsquo" to "’",
    "ldquo" to "“",
    "rdquo" to "”",
    "copy" to "©",
    "reg" to "®",
    "trade" to "™",
    "times" to "×",
    "divide" to "÷",
    "plusmn" to "±",
)

private fun decodeHtmlEntities(text: String): String {
    if ('&' !in text) return text
    return HTML_ENTITY_REGEX.replace(text) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x", ignoreCase = true) -> {
                val cp = body.substring(2).toIntOrNull(16)
                decodeCodePoint(cp) ?: match.value
            }
            body.startsWith("#") -> {
                val cp = body.substring(1).toIntOrNull(10)
                decodeCodePoint(cp) ?: match.value
            }
            else -> NAMED_ENTITIES[body.lowercase(Locale.ROOT)] ?: match.value
        }
    }
}

private fun decodeCodePoint(cp: Int?): String? {
    if (cp == null || cp <= 0 || !Character.isValidCodePoint(cp)) return null
    if (cp in Character.MIN_SURROGATE.code..Character.MAX_SURROGATE.code) return null
    return String(Character.toChars(cp))
}
