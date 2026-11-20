package io.github.walterfr.jotmd

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/** Item da lista achatada do painel. [depth] vira indentação; [docId] é o ID SAF. */
data class DocNode(val name: String, val docId: String, val isDir: Boolean, val depth: Int)

/** Só `.md`/`.markdown`; sem diferenciar maiúsculas (`README.MD` é markdown). */
fun isMarkdownName(name: String): Boolean {
    val n = name.lowercase()
    return n.endsWith(".md") || n.endsWith(".markdown")
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
 * Pastas sem nenhum markdown dentro são omitidas, senão `.git` etc. poluem.
 * Bloqueante: chamar em Dispatchers.IO.
 */
fun listMarkdownTree(resolver: ContentResolver, treeUri: Uri): List<DocNode> {
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
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                raw += DocNode(
                    name = c.getString(1) ?: continue,
                    docId = c.getString(0),
                    isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    depth = depth,
                )
            }
        }
        for (node in sortAndFilter(raw)) {
            val at = out.size
            out += node
            if (node.isDir) {
                walk(node.docId, depth + 1)
                if (out.size == at + 1) out.removeAt(at)
            }
        }
    }
    walk(DocumentsContract.getTreeDocumentId(treeUri), 0)
    return out
}
