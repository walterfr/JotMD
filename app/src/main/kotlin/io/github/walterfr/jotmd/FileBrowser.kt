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
 * Arquivo apontado por `[[target]]`: nome sem extensão igual ao alvo, sem
 * diferenciar caixa. `[[pasta/nota]]` usa só o último segmento e `[[nota.md]]`
 * também vale.
 *
 * ponytail: dois arquivos com o mesmo nome em pastas diferentes → vence o
 * primeiro da listagem. Upgrade: casar o caminho relativo quando o alvo tiver `/`.
 */
fun resolveWikilink(target: String, nodes: List<DocNode>): DocNode? {
    val wanted = target.substringAfterLast('/').trim()
    return nodes.firstOrNull { node ->
        !node.isDir && (
            node.name.equals(wanted, ignoreCase = true) ||
                node.name.substringBeforeLast('.').equals(wanted, ignoreCase = true)
            )
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
