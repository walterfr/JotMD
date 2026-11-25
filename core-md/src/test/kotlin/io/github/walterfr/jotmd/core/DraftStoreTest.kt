package io.github.walterfr.jotmd.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftStoreTest {

    private fun withTempDir(block: (Path) -> Unit) {
        val dir = Files.createTempDirectory("jotmd-draft-test-")
        try {
            block(dir)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `writeTextAtomic cria diretório pai, grava UTF-8 e não deixa arquivo temporário`() = withTempDir { root ->
        val target = root.resolve("sub/dir/nota.md")
        writeTextAtomic(target, "# Título\nConteúdo com ação 🚀")

        assertTrue(Files.isRegularFile(target))
        assertEquals("# Título\nConteúdo com ação 🚀", Files.readString(target, Charsets.UTF_8))

        val filesInParent = Files.list(target.parent).use { it.toList() }
        assertEquals(listOf(target), filesInParent)
    }

    @Test
    fun `saveDraft e loadDrafts ordenam por updatedAtMs decrescente e sanitizam id`() = withTempDir { dir ->
        val repo = DraftRepository(dir)
        assertTrue(repo.saveDraft("tab-1", "Primeira\nLinha", "Corpo 1\n---\nFim", updatedAtMs = 100L))
        assertTrue(repo.saveDraft("tab_2/evil", "Segunda", "Corpo 2", updatedAtMs = 300L))
        assertTrue(repo.saveDraft("tab-3", "Terceira", "Corpo 3", updatedAtMs = 200L))
        assertFalse(repo.saveDraft("../..", "Inválido", "Corpo"))

        val drafts = repo.loadDrafts()
        assertEquals(3, drafts.size)
        assertEquals(listOf("tab_2evil", "tab-3", "tab-1"), drafts.map { it.id })
        assertEquals("Segunda", drafts[0].title)
        assertEquals("Primeira Linha", drafts[2].title)
        assertEquals("Corpo 1\n---\nFim", drafts[2].content)
    }

    @Test
    fun `saveDraft com conteúdo em branco ignora e apaga rascunho existente`() = withTempDir { dir ->
        val repo = DraftRepository(dir)
        assertTrue(repo.saveDraft("doc1", "Nota", "Texto inicial", updatedAtMs = 100L))
        assertEquals(1, repo.loadDrafts().size)

        assertFalse(repo.saveDraft("doc1", "Nota", "   \n  ", updatedAtMs = 200L))
        assertTrue(repo.loadDrafts().isEmpty())
    }

    @Test
    fun `loadDrafts tolera arquivos corrompidos ou inválidos`() = withTempDir { dir ->
        val repo = DraftRepository(dir)
        repo.saveDraft("valido", "Ok", "Conteúdo válido", updatedAtMs = 500L)

        Files.writeString(dir.resolve("corrompido1.draft"), "sem cabeçalho", Charsets.UTF_8)
        Files.writeString(dir.resolve("corrompido2.draft"), "title=X\nupdatedAtMs=nan\n---\nOi", Charsets.UTF_8)
        Files.writeString(dir.resolve("corrompido3.draft"), "title=X\nupdatedAtMs=10\nsem-separador\nOi", Charsets.UTF_8)
        Files.writeString(dir.resolve("outro.txt"), "ignorar", Charsets.UTF_8)

        val drafts = repo.loadDrafts()
        assertEquals(1, drafts.size)
        assertEquals("valido", drafts.single().id)
    }

    @Test
    fun `deleteDraft remove rascunho existente e retorna false se ausente`() = withTempDir { dir ->
        val repo = DraftRepository(dir)
        repo.saveDraft("d1", "T", "C", updatedAtMs = 1L)
        assertTrue(repo.deleteDraft("d1"))
        assertFalse(repo.deleteDraft("d1"))
        assertTrue(repo.loadDrafts().isEmpty())
    }

    @Test
    fun `pruneOldDrafts mantém apenas os maxKeep mais recentes`() = withTempDir { dir ->
        val repo = DraftRepository(dir)
        for (i in 1..5) {
            repo.saveDraft("d$i", "T$i", "C$i", updatedAtMs = i * 100L)
        }

        repo.pruneOldDrafts(maxKeep = 2)
        val remaining = repo.loadDrafts()
        assertEquals(listOf("d5", "d4"), remaining.map { it.id })
    }
}
