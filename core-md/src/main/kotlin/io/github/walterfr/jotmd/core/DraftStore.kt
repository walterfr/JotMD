package io.github.walterfr.jotmd.core

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

private const val DRAFT_EXTENSION = ".draft"

/**
 * Grava [text] em UTF-8 de forma atômica em [path] usando arquivo temporário no mesmo diretório.
 */
fun writeTextAtomic(path: Path, text: String) {
    val parent = path.toAbsolutePath().parent
    if (parent != null) Files.createDirectories(parent)

    val dir = parent ?: Paths.get(".")
    val baseName = path.fileName?.toString() ?: "file"
    val tid = Thread.currentThread().id
    val tmp = dir.resolve(".$baseName.tmp.$tid.${System.nanoTime()}")

    try {
        Files.write(tmp, text.toByteArray(Charsets.UTF_8))
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        runCatching { Files.deleteIfExists(tmp) }
    }
}

/**
 * Metadados e conteúdo recuperados de um rascunho persistido em disco.
 */
data class DraftSnapshot(
    val id: String,
    val title: String,
    val content: String,
    val updatedAtMs: Long,
)

/**
 * Repositório simples de rascunhos `.draft` em [dir] com escrita atômica e tolerância a arquivos corrompidos.
 */
class DraftRepository(private val dir: Path) {

    /**
     * Salva um rascunho se [id] sanitizado e [content] forem não vazios; remove o existente se [content] for em branco.
     */
    fun saveDraft(
        id: String,
        title: String,
        content: String,
        updatedAtMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val safeId = sanitizeId(id)
        if (safeId.isEmpty()) return false

        val target = draftPath(safeId)
        if (content.isBlank()) {
            runCatching { Files.deleteIfExists(target) }
            return false
        }

        val singleLineTitle = title
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim()
        val payload = buildString {
            append("title=").append(singleLineTitle).append('\n')
            append("updatedAtMs=").append(updatedAtMs).append('\n')
            append("---\n")
            append(content)
        }
        writeTextAtomic(target, payload)
        return true
    }

    /**
     * Carrega todos os rascunhos válidos ordenados do mais recente para o mais antigo.
     */
    fun loadDrafts(): List<DraftSnapshot> {
        if (!Files.isDirectory(dir)) return emptyList()
        val snapshots = ArrayList<DraftSnapshot>()

        Files.list(dir).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                val fileName = path.fileName?.toString() ?: continue
                if (!Files.isRegularFile(path) || !fileName.endsWith(DRAFT_EXTENSION)) continue
                val id = fileName.removeSuffix(DRAFT_EXTENSION)
                if (id.isEmpty() || sanitizeId(id) != id) continue

                val raw = runCatching { Files.readAllBytes(path).toString(Charsets.UTF_8) }.getOrNull() ?: continue
                parseDraftSnapshot(id, raw)?.let { snapshots += it }
            }
        }
        return snapshots.sortedByDescending { it.updatedAtMs }
    }

    /**
     * Exclui o rascunho identificado por [id], se existir.
     */
    fun deleteDraft(id: String): Boolean {
        val safeId = sanitizeId(id)
        if (safeId.isEmpty()) return false
        return runCatching { Files.deleteIfExists(draftPath(safeId)) }.getOrDefault(false)
    }

    /**
     * Mantém no máximo [maxKeep] rascunhos mais recentes, removendo os excedentes.
     */
    fun pruneOldDrafts(maxKeep: Int = 20) {
        val keep = maxKeep.coerceAtLeast(0)
        val drafts = loadDrafts()
        if (drafts.size <= keep) return
        for (old in drafts.drop(keep)) {
            deleteDraft(old.id)
        }
    }

    private fun draftPath(safeId: String): Path = dir.resolve("$safeId$DRAFT_EXTENSION")

    private fun sanitizeId(raw: String): String =
        raw.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }
            .take(120)

    private fun parseDraftSnapshot(id: String, raw: String): DraftSnapshot? {
        val nl1 = raw.indexOf('\n')
        if (nl1 < 0) return null
        val nl2 = raw.indexOf('\n', nl1 + 1)
        if (nl2 < 0) return null
        val nl3 = raw.indexOf('\n', nl2 + 1)
        if (nl3 < 0) return null

        val line1 = raw.substring(0, nl1).removeSuffix("\r")
        val line2 = raw.substring(nl1 + 1, nl2).removeSuffix("\r")
        val line3 = raw.substring(nl2 + 1, nl3).removeSuffix("\r")

        if (!line1.startsWith("title=") || !line2.startsWith("updatedAtMs=") || line3 != "---") {
            return null
        }

        val title = line1.removePrefix("title=")
        val updatedAtMs = line2.removePrefix("updatedAtMs=").trim().toLongOrNull() ?: return null
        val content = raw.substring(nl3 + 1)
        if (content.isBlank()) return null

        return DraftSnapshot(
            id = id,
            title = title,
            content = content,
            updatedAtMs = updatedAtMs,
        )
    }
}
