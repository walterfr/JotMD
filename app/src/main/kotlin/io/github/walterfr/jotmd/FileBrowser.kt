package io.github.walterfr.jotmd

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/**
 * Item da lista achatada do painel. [depth] vira indentação; [docId] é o ID SAF.
 * [lastModified] (ms, 0 = desconhecido) decide se o índice de busca relê o arquivo.
 */
data class DocNode(
    val name: String,
    val docId: String,
    val isDir: Boolean,
    val depth: Int,
    val lastModified: Long = 0,
)

/** Só `.md`/`.markdown`; sem diferenciar maiúsculas (`README.MD` é markdown). */
fun isMarkdownName(name: String): Boolean {
    val n = name.lowercase()
    return n.endsWith(".md") || n.endsWith(".markdown")
}

/**
 * Arquivo apontado por `[[target]]`: ignora âncora `#seção`, normaliza `\` -> `/`
 * e resolve o caminho relativo reconstruído na árvore.
 * - Com `/`: prioriza caminho relativo exato (com ou sem `.md`/`.markdown`,
 *   sem diferenciar caixa), depois sufixo `/<target>` (menor profundidade) e,
 *   por fim, nome de arquivo.
 * - Sem `/`: casa o nome do arquivo (com ou sem `.md`/`.markdown`) e escolhe
 *   o resultado mais raso (`depth` menor, mantendo o primeiro em empate).
 */
fun resolveWikilink(target: String, nodes: List<DocNode>): DocNode? {
    val normalized = target.substringBefore('#')
        .trim()
        .replace('\\', '/')
        .trimStart('/')
    if (normalized.isEmpty()) return null

    data class FileEntry(val node: DocNode, val relativePath: String)

    val stack = ArrayList<String>()
    val files = ArrayList<FileEntry>()
    for (node in nodes) {
        val safeDepth = node.depth.coerceAtLeast(0)
        while (stack.size > safeDepth) {
            stack.removeAt(stack.lastIndex)
        }
        if (node.isDir) {
            stack.add(node.name)
        } else {
            val relPath = if (stack.isEmpty()) node.name else "${stack.joinToString("/")}/${node.name}"
            files += FileEntry(node, relPath)
        }
    }

    if (normalized.contains('/')) {
        files.firstOrNull {
            it.relativePath.equals(normalized, ignoreCase = true) ||
                stripMarkdownExtension(it.relativePath).equals(normalized, ignoreCase = true)
        }?.let { return it.node }

        val suffix = "/$normalized"
        files.filter {
            it.relativePath.endsWith(suffix, ignoreCase = true) ||
                stripMarkdownExtension(it.relativePath).endsWith(suffix, ignoreCase = true)
        }.minByOrNull { it.node.depth }?.let { return it.node }
    }

    val wantedName = normalized.substringAfterLast('/')
    return files.filter {
        it.node.name.equals(wantedName, ignoreCase = true) ||
            stripMarkdownExtension(it.node.name).equals(wantedName, ignoreCase = true)
    }.minByOrNull { it.node.depth }?.node
}

private fun stripMarkdownExtension(path: String): String {
    val lower = path.lowercase()
    return when {
        lower.endsWith(".md") -> path.dropLast(3)
        lower.endsWith(".markdown") -> path.dropLast(9)
        else -> {
            val slash = path.lastIndexOf('/')
            val dot = path.lastIndexOf('.')
            if (dot > slash) path.substring(0, dot) else path
        }
    }
}

private val INVALID_FILENAME_CHARS = Regex("[/\\\\:*?\"<>|\\x00-\\x1F]")

/**
 * Higieniza nomes digitados pelo usuário para uso no SAF: remove barras e
 * caracteres inválidos, faz `trim()`, cai em `"Sem Título"` se ficar vazio
 * (ou `.`/`..`) e anexa `.md` quando [ensureMarkdownExtension] está ativo.
 */
fun sanitizeFileName(rawName: String, ensureMarkdownExtension: Boolean): String {
    val cleaned = rawName.replace(INVALID_FILENAME_CHARS, "").trim()
    val base = if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") "Sem Título" else cleaned
    return if (ensureMarkdownExtension && !isMarkdownName(base)) "$base.md" else base
}

/** Filhos de uma pasta: só pastas e markdown, pastas primeiro, A-Z sem caixa. */
fun sortAndFilter(children: List<DocNode>): List<DocNode> =
    children
        .filter { it.isDir || isMarkdownName(it.name) }
        .sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))

/**
 * Lista a árvore SAF recursivamente em ordem de exibição (pasta, depois o
 * conteúdo dela). Usa query direta no provider: `DocumentFile.listFiles()` faz
 * uma query por atributo por arquivo e fica lento em árvores grandes.
 * Quando [includeEmptyDirs] é `false` (padrão), pastas sem nenhum markdown dentro
 * são omitidas; quando `true`, omite apenas pastas cujo nome começa com `.` e
 * mantém pastas normais vazias (ex.: recém-criadas via `+ Pasta`).
 * Bloqueante: chamar em Dispatchers.IO.
 */
fun listMarkdownTree(
    resolver: ContentResolver,
    treeUri: Uri,
    includeEmptyDirs: Boolean = false,
): List<DocNode> {
    val out = ArrayList<DocNode>()
    fun walk(parentDocId: String, depth: Int) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val raw = ArrayList<DocNode>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                raw += DocNode(
                    name = name,
                    docId = docId,
                    isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    depth = depth,
                    lastModified = if (c.isNull(3)) 0 else c.getLong(3),
                )
            }
        }
        for (node in sortAndFilter(raw)) {
            if (node.isDir) {
                if (includeEmptyDirs && node.name.startsWith(".")) continue
                val at = out.size
                out += node
                walk(node.docId, depth + 1)
                if (!includeEmptyDirs && out.size == at + 1) out.removeAt(at)
            } else {
                out += node
            }
        }
    }
    walk(DocumentsContract.getTreeDocumentId(treeUri), 0)
    return out
}

/**
 * Cria um arquivo Markdown dentro de [parentDocId] via SAF (`text/markdown`,
 * com fallback para `text/plain` se o provedor rejeitar).
 * Bloqueante: chamar em Dispatchers.IO.
 */
fun createMarkdownFile(
    resolver: ContentResolver,
    treeUri: Uri,
    parentDocId: String,
    rawName: String,
): DocNode? {
    val fileName = sanitizeFileName(rawName, ensureMarkdownExtension = true)
    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
    val createdUri = runCatching {
        DocumentsContract.createDocument(resolver, parentUri, "text/markdown", fileName)
    }.getOrNull() ?: runCatching {
        DocumentsContract.createDocument(resolver, parentUri, "text/plain", fileName)
    }.getOrNull() ?: return null
    return queryDocNode(resolver, createdUri)
        ?: fallbackDocNode(createdUri, fileName, isDir = false)
}

/**
 * Cria uma subpasta dentro de [parentDocId] via SAF.
 * Bloqueante: chamar em Dispatchers.IO.
 */
fun createSubFolder(
    resolver: ContentResolver,
    treeUri: Uri,
    parentDocId: String,
    rawName: String,
): DocNode? {
    val dirName = sanitizeFileName(rawName, ensureMarkdownExtension = false)
    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
    val createdUri = runCatching {
        DocumentsContract.createDocument(
            resolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            dirName,
        )
    }.getOrNull() ?: return null
    return queryDocNode(resolver, createdUri)
        ?: fallbackDocNode(createdUri, dirName, isDir = true)
}

/**
 * Renomeia o documento ou pasta [docId] via SAF e consulta `COLUMN_DOCUMENT_ID` e
 * `COLUMN_DISPLAY_NAME` resultantes (SAF pode devolver uma nova [Uri] com novo `docId`).
 * Bloqueante: chamar em Dispatchers.IO.
 */
fun renameDocument(
    resolver: ContentResolver,
    treeUri: Uri,
    docId: String,
    rawNewName: String,
    isDir: Boolean,
): DocNode? {
    val cleanName = sanitizeFileName(rawNewName, ensureMarkdownExtension = !isDir)
    val targetUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    val renamedUri = runCatching {
        DocumentsContract.renameDocument(resolver, targetUri, cleanName)
    }.getOrNull() ?: return null
    return queryDocNode(resolver, renamedUri)
        ?: fallbackDocNode(renamedUri, cleanName, isDir = isDir)
}

/**
 * Exclui o documento ou pasta [docId] via SAF.
 * Bloqueante: chamar em Dispatchers.IO.
 */
fun deleteDocument(
    resolver: ContentResolver,
    treeUri: Uri,
    docId: String,
): Boolean {
    val targetUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    return runCatching {
        DocumentsContract.deleteDocument(resolver, targetUri)
    }.getOrDefault(false)
}

private fun queryDocNode(resolver: ContentResolver, uri: Uri): DocNode? =
    runCatching {
        resolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            ),
            null,
            null,
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val docId = c.getString(0) ?: return@use null
            val name = c.getString(1) ?: return@use null
            val isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
            val lastModified = if (c.isNull(3)) 0L else c.getLong(3)
            DocNode(
                name = name,
                docId = docId,
                isDir = isDir,
                depth = 0,
                lastModified = lastModified,
            )
        }
    }.getOrNull()

private fun fallbackDocNode(uri: Uri, name: String, isDir: Boolean): DocNode? =
    runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()?.let { docId ->
        DocNode(name = name, docId = docId, isDir = isDir, depth = 0)
    }
