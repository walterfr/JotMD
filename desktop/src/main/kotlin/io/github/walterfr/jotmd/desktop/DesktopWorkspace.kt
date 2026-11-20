package io.github.walterfr.jotmd.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.walterfr.jotmd.core.docIndex
import io.github.walterfr.jotmd.core.tagsOf
import io.github.walterfr.jotmd.editor.EditorState
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.text.Normalizer

/** Aba aberta no workspace desktop. */
class DesktopTab(
    val id: Long,
    file: Path?,
    title: String,
    val editorState: EditorState,
    lastSaved: String? = null,
    saveFailed: Boolean = false,
) {
    var file: Path? by mutableStateOf(file)
    var title: String by mutableStateOf(title)
    var lastSaved: String? by mutableStateOf(lastSaved)
    var saveFailed: Boolean by mutableStateOf(saveFailed)

    val isDirty: Boolean
        get() = file != null && editorState.text() != lastSaved
}

/**
 * Grava o texto atual de [tab] em UTF-8 somente se diferir de [DesktopTab.lastSaved].
 * Devolve `true` quando escreveu em disco; marca [DesktopTab.saveFailed] em erro de E/S.
 */
fun saveTabIfChanged(tab: DesktopTab): Boolean {
    val path = tab.file ?: run {
        tab.saveFailed = false
        return false
    }
    val current = tab.editorState.text()
    if (current == tab.lastSaved) {
        tab.saveFailed = false
        return false
    }
    return try {
        path.parent?.let { Files.createDirectories(it) }
        Files.writeString(path, current, StandardCharsets.UTF_8)
        tab.lastSaved = current
        tab.saveFailed = false
        true
    } catch (_: Exception) {
        tab.saveFailed = true
        false
    }
}

/** Gerencia abas abertas sem duplicar o mesmo arquivo e preservando cada [EditorState]. */
class DesktopTabManager {
    var tabs: List<DesktopTab> by mutableStateOf(emptyList())
        private set
    var activeTabId: Long? by mutableStateOf(null)
        private set

    val activeTab: DesktopTab?
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()

    private var nextId = 1L

    fun openInitial(title: String, content: String): DesktopTab {
        val tab = DesktopTab(
            id = nextId++,
            file = null,
            title = title,
            editorState = EditorState(content),
            lastSaved = null,
        )
        tabs = tabs + tab
        activeTabId = tab.id
        return tab
    }

    fun openOrFocus(path: Path): DesktopTab? {
        val normalized = path.toAbsolutePath().normalize()
        tabs.firstOrNull { it.file?.toAbsolutePath()?.normalize() == normalized }?.let { existing ->
            activeTabId = existing.id
            return existing
        }
        val text = try {
            Files.readString(normalized, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            return null
        }
        val tab = DesktopTab(
            id = nextId++,
            file = normalized,
            title = normalized.fileName?.toString() ?: normalized.toString(),
            editorState = EditorState(text),
            lastSaved = text,
        )
        tabs = tabs + tab
        activeTabId = tab.id
        return tab
    }

    fun newEmptyTab(title: String = "Sem título"): DesktopTab {
        val tab = DesktopTab(
            id = nextId++,
            file = null,
            title = title,
            editorState = EditorState(""),
            lastSaved = null,
        )
        tabs = tabs + tab
        activeTabId = tab.id
        return tab
    }

    fun select(id: Long): DesktopTab? {
        val tab = tabs.firstOrNull { it.id == id } ?: return null
        activeTabId = tab.id
        return tab
    }

    /**
     * Faz flush da aba antes de fechar; se a gravação falhar, cancela o fechamento e devolve `false`.
     * Ao fechar a última aba, abre uma nova aba vazia para manter o editor utilizável.
     */
    fun close(id: Long): Boolean {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return false
        val tab = tabs[index]
        if (tab.file != null) {
            saveTabIfChanged(tab)
            if (tab.saveFailed) return false
        }
        val remaining = tabs.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            tabs = emptyList()
            activeTabId = null
            newEmptyTab()
            return true
        }
        tabs = remaining
        if (activeTabId == id) {
            activeTabId = remaining[minOf(index, remaining.lastIndex)].id
        }
        return true
    }

    fun saveTabAs(tab: DesktopTab, targetPath: Path): Boolean {
        val normalized = targetPath.toAbsolutePath().normalize()
        val current = tab.editorState.text()
        return try {
            normalized.parent?.let { Files.createDirectories(it) }
            Files.writeString(normalized, current, StandardCharsets.UTF_8)
            tab.file = normalized
            tab.title = normalized.fileName?.toString() ?: normalized.toString()
            tab.lastSaved = current
            tab.saveFailed = false
            true
        } catch (_: Exception) {
            tab.saveFailed = true
            false
        }
    }

    fun saveAll(): Boolean {
        for (tab in tabs) {
            if (tab.file != null) saveTabIfChanged(tab)
        }
        return tabs.none { it.saveFailed }
    }
}

/** Entrada achatada da árvore de documentos da pasta raiz no desktop. */
data class DesktopDocEntry(
    val name: String,
    val path: Path,
    val isDir: Boolean,
    val depth: Int,
    val relativePath: String = name,
)

/** Só `.md`/`.markdown`; sem diferenciar maiúsculas (`README.MD` é markdown). */
fun isMarkdownName(name: String): Boolean {
    val lower = name.lowercase()
    return lower.endsWith(".md") || lower.endsWith(".markdown")
}

private class DirNode(val path: Path, val relativePath: String) {
    val dirs = LinkedHashMap<String, DirNode>()
    val files = ArrayList<Pair<String, Path>>()
}

/**
 * Lista pastas e arquivos `.md`/`.markdown` em [root], ordenados com pastas primeiro
 * e depois arquivos em ordem alfabética sem caixa. Ignora entradas ocultas começando com `.`
 * (ex.: `.git`) e omite pastas sem nenhum markdown dentro.
 */
fun listDesktopTree(root: Path): List<DesktopDocEntry> {
    if (!Files.isDirectory(root)) return emptyList()
    val normRoot = root.toAbsolutePath().normalize()
    val mdFiles = ArrayList<Path>()

    Files.walkFileTree(
        normRoot,
        object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (dir != normRoot && dir.fileName.toString().startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val name = file.fileName.toString()
                if (!name.startsWith(".") && isMarkdownName(name)) {
                    mdFiles.add(file)
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult =
                FileVisitResult.CONTINUE
        },
    )

    val rootNode = DirNode(normRoot, "")
    for (file in mdFiles) {
        val rel = normRoot.relativize(file)
        var node = rootNode
        for (i in 0 until rel.nameCount - 1) {
            val seg = rel.getName(i).toString()
            val childRel = if (node.relativePath.isEmpty()) seg else "${node.relativePath}/$seg"
            node = node.dirs.getOrPut(seg) { DirNode(node.path.resolve(seg), childRel) }
        }
        node.files += rel.fileName.toString() to file
    }

    val out = ArrayList<DesktopDocEntry>()
    fun emit(dir: DirNode, depth: Int) {
        for ((name, child) in dir.dirs.entries.sortedBy { it.key.lowercase() }) {
            out += DesktopDocEntry(
                name = name,
                path = child.path,
                isDir = true,
                depth = depth,
                relativePath = child.relativePath,
            )
            emit(child, depth + 1)
        }
        for ((name, filePath) in dir.files.sortedBy { it.first.lowercase() }) {
            val relPath = if (dir.relativePath.isEmpty()) name else "${dir.relativePath}/$name"
            out += DesktopDocEntry(
                name = name,
                path = filePath,
                isDir = false,
                depth = depth,
                relativePath = relPath,
            )
        }
    }
    emit(rootNode, 0)
    return out
}

/**
 * Resolve `[[target]]` nos arquivos listados: tenta o caminho relativo quando houver `/`,
 * depois casa pelo último segmento com ou sem extensão, sem diferenciar maiúsculas.
 */
fun resolveDesktopWikilink(target: String, entries: List<DesktopDocEntry>): DesktopDocEntry? {
    val normalized = target.trim().replace('\\', '/')
    if (normalized.isEmpty()) return null
    if ('/' in normalized) {
        entries.firstOrNull { entry ->
            !entry.isDir && (
                entry.relativePath.equals(normalized, ignoreCase = true) ||
                    entry.relativePath.substringBeforeLast('.').equals(normalized, ignoreCase = true)
                )
        }?.let { return it }
    }
    val wanted = normalized.substringAfterLast('/').trim()
    if (wanted.isEmpty()) return null
    return entries
        .filter { entry ->
            !entry.isDir && (
                entry.name.equals(wanted, ignoreCase = true) ||
                    entry.name.substringBeforeLast('.').equals(wanted, ignoreCase = true)
                )
        }
        .minByOrNull { it.depth }
}

/** Resultado da busca em arquivos locais, com trecho achatado e intervalos para negrito. */
data class DesktopSearchHit(
    val path: Path,
    val name: String,
    val relativePath: String,
    val snippet: String,
    val marks: List<IntRange>,
)

private val COMBINING_MARKS = Regex("\\p{M}+")
private const val MAX_SEARCH_FILE_BYTES = 2L * 1024L * 1024L

internal fun foldAccents(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()

private data class FoldedText(val folded: String, val origIndices: IntArray)

private fun foldWithIndexMap(text: String): FoldedText {
    val sb = StringBuilder(text.length)
    var map = IntArray(text.length + 16)
    var count = 0
    for (i in text.indices) {
        val ch = text[i]
        if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt() ||
            Character.getType(ch) == Character.COMBINING_SPACING_MARK.toInt() ||
            Character.getType(ch) == Character.ENCLOSING_MARK.toInt()
        ) {
            continue
        }
        val norm = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase()
        for (j in norm.indices) {
            sb.append(norm[j])
            if (count >= map.size) map = map.copyOf(map.size * 2)
            map[count++] = i
        }
    }
    return FoldedText(sb.toString(), map.copyOf(count))
}

private fun originalRange(text: String, map: IntArray, foldedStart: Int, foldedLen: Int): IntRange {
    val origStart = map[foldedStart]
    val lastOrig = map[(foldedStart + foldedLen - 1).coerceAtMost(map.lastIndex)]
    var origEnd = lastOrig + 1
    while (origEnd < text.length && Character.getType(text[origEnd]) in intArrayOf(
            Character.NON_SPACING_MARK.toInt(),
            Character.COMBINING_SPACING_MARK.toInt(),
            Character.ENCLOSING_MARK.toInt(),
        )
    ) {
        origEnd++
    }
    return origStart until origEnd
}

/**
 * Busca case-insensitive e sem acentos (via `Normalizer.Form.NFD`) nos nomes, tags
 * (corpo e front matter) e conteúdos dos `.md`/`.markdown` sob [root].
 */
fun searchDesktopFiles(root: Path, query: String, limit: Int = 50): List<DesktopSearchHit> {
    val rawTerms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (rawTerms.isEmpty() || !Files.isDirectory(root)) return emptyList()

    val terms = rawTerms.map { term ->
        if (term.startsWith("#") && term.length > 1) {
            Term.Tag(foldAccents(term.removePrefix("#")))
        } else {
            Term.Word(foldAccents(term))
        }
    }.filter { it.value.isNotEmpty() }
    if (terms.isEmpty()) return emptyList()

    val entries = listDesktopTree(root).filter { !it.isDir }
    val hits = ArrayList<DesktopSearchHit>()

    for (entry in entries) {
        if (hits.size >= limit) break
        val size = runCatching { Files.size(entry.path) }.getOrNull() ?: continue
        if (size > MAX_SEARCH_FILE_BYTES) continue
        val content = runCatching { Files.readString(entry.path, StandardCharsets.UTF_8) }.getOrNull() ?: continue

        val foldedName = foldAccents(entry.name)
        val foldedRel = foldAccents(entry.relativePath)
        val foldedBody = foldWithIndexMap(content)
        val docTags by lazy { tagsOf(docIndex(content)).map(::foldAccents) }

        val matchesAll = terms.all { term ->
            when (term) {
                is Term.Tag ->
                    docTags.any { it.startsWith(term.value) } ||
                        foldedBody.folded.contains("#${term.value}")
                is Term.Word ->
                    foldedName.contains(term.value) ||
                        foldedRel.contains(term.value) ||
                        foldedBody.folded.contains(term.value)
            }
        }
        if (!matchesAll) continue

        val highlightTerms = terms.map { term ->
            when (term) {
                is Term.Tag -> if (foldedBody.folded.contains("#${term.value}")) "#${term.value}" else term.value
                is Term.Word -> term.value
            }
        }

        val (snippet, marks) = buildSnippet(content, foldedBody, highlightTerms)
        hits += DesktopSearchHit(
            path = entry.path,
            name = entry.name,
            relativePath = entry.relativePath,
            snippet = snippet,
            marks = marks,
        )
    }
    return hits
}

private sealed interface Term {
    val value: String
    data class Word(override val value: String) : Term
    data class Tag(override val value: String) : Term
}

private fun buildSnippet(
    content: String,
    foldedBody: FoldedText,
    highlightTerms: List<String>,
): Pair<String, List<IntRange>> {
    if (content.isBlank()) return "" to emptyList()

    var firstMatch: IntRange? = null
    for (term in highlightTerms) {
        val idx = foldedBody.folded.indexOf(term)
        if (idx >= 0) {
            val range = originalRange(content, foldedBody.origIndices, idx, term.length)
            if (firstMatch == null || range.first < firstMatch.first) firstMatch = range
        }
    }

    val winStart = if (firstMatch != null) (firstMatch.first - 45).coerceAtLeast(0) else 0
    val winEnd = if (firstMatch != null) (firstMatch.last + 1 + 75).coerceAtMost(content.length) else minOf(120, content.length)
    val prefix = if (winStart > 0) "…" else ""
    val suffix = if (winEnd < content.length) "…" else ""
    val slice = content.substring(winStart, winEnd)

    val foldedSlice = foldWithIndexMap(slice)
    val sliceRanges = ArrayList<IntRange>()
    for (term in highlightTerms) {
        var from = 0
        while (true) {
            val idx = foldedSlice.folded.indexOf(term, from)
            if (idx < 0) break
            sliceRanges += originalRange(slice, foldedSlice.origIndices, idx, term.length)
            from = idx + term.length.coerceAtLeast(1)
        }
    }

    // Achata quebras de linha e espaços múltiplos mantendo o mapa exato de offsets para os destaques.
    val flat = StringBuilder(slice.length)
    val origToFlat = IntArray(slice.length + 1)
    for (i in slice.indices) {
        origToFlat[i] = flat.length
        if (slice[i].isWhitespace()) {
            if (flat.isNotEmpty() && flat.last() != ' ') flat.append(' ')
        } else {
            flat.append(slice[i])
        }
    }
    origToFlat[slice.length] = flat.length

    val trimmedFlat = flat.toString().trim()
    val leadingSpaces = flat.length - flat.toString().trimStart().length
    val finalText = prefix + trimmedFlat + suffix
    val shift = prefix.length - leadingSpaces

    val marks = sliceRanges
        .mapNotNull { r ->
            val start = (origToFlat[r.first] + shift).coerceIn(prefix.length, prefix.length + trimmedFlat.length)
            val end = (origToFlat[r.last + 1] + shift).coerceIn(start, prefix.length + trimmedFlat.length)
            if (end > start) start until end else null
        }
        .sortedBy { it.first }
        .distinct()

    return finalText to marks
}
