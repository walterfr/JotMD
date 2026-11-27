package io.github.walterfr.jotmd

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileBrowserTest {
    private fun n(name: String, dir: Boolean = false) = DocNode(name, name, dir, 0)

    @Test fun markdownNames() {
        assertTrue(isMarkdownName("a.md"))
        assertTrue(isMarkdownName("B.MARKDOWN"))
        assertFalse(isMarkdownName("a.txt"))
        assertFalse(isMarkdownName("md"))
    }

    @Test fun foldersFirstThenCaseInsensitive() {
        val r = sortAndFilter(
            listOf(n("b.md"), n("Zeta", true), n("A.md"), n("x.png"), n("alfa", true)),
        )
        assertEquals(listOf("alfa", "Zeta", "A.md", "b.md"), r.map { it.name })
    }

    @Test fun wikilinkAchaArquivoPeloNomeSemExtensao() {
        val nodes = listOf(n("notas", true), n("Plano de Aula.md"), n("receita.markdown"))
        assertEquals("Plano de Aula.md", resolveWikilink("plano de aula", nodes)?.name)
        assertEquals("receita.markdown", resolveWikilink("receita", nodes)?.name)
        assertEquals("receita.markdown", resolveWikilink("pasta/receita", nodes)?.name)
        assertEquals("Plano de Aula.md", resolveWikilink("Plano de Aula.md", nodes)?.name)
        assertEquals(null, resolveWikilink("notas", nodes)) // pasta não é nota
        assertEquals(null, resolveWikilink("inexistente", nodes))
    }

    @Test fun wikilinkDesambiguaHomonimosPorProfundidadeECaminhoRelativo() {
        val deepFirst = DocNode("nota.md", "id-deep", isDir = false, depth = 2)
        val shallowLater = DocNode("nota.md", "id-shallow", isDir = false, depth = 1)
        val rootNote = DocNode("nota.md", "id-root", isDir = false, depth = 0)
        val nodes = listOf(
            DocNode("alfa", "d-alfa", isDir = true, depth = 0),
            DocNode("sub", "d-sub", isDir = true, depth = 1),
            deepFirst,
            DocNode("beta", "d-beta", isDir = true, depth = 0),
            shallowLater,
            rootNote,
        )

        // Sem barra: escolhe menor depth (raiz ganha de depth 1 e 2)
        assertEquals("id-root", resolveWikilink("nota", nodes)?.docId)

        // Sem raiz: menor depth entre subpastas (depth 1 antes de depth 2, mesmo listado depois)
        assertEquals("id-shallow", resolveWikilink("nota", nodes.dropLast(1))?.docId)

        // Com barra exata: resolve subpasta específica mesmo após homônimo anterior
        assertEquals("id-shallow", resolveWikilink("beta/nota", nodes)?.docId)
        assertEquals("id-deep", resolveWikilink("alfa/sub/nota.md", nodes)?.docId)

        // Sufixo relativo quando omite pasta pai
        assertEquals("id-deep", resolveWikilink("sub/nota", nodes)?.docId)
    }

    @Test fun wikilinkIgnoraAncoraENormalizaBarras() {
        val nodes = listOf(
            DocNode("pasta", "d1", isDir = true, depth = 0),
            DocNode("guia.md", "id-guia", isDir = false, depth = 1),
        )
        assertEquals("id-guia", resolveWikilink("guia#secao-2", nodes)?.docId)
        assertEquals("id-guia", resolveWikilink("pasta\\guia.md#topo", nodes)?.docId)
        assertEquals(null, resolveWikilink("#apenas-ancora", nodes))
        assertEquals(null, resolveWikilink("   ", nodes))
    }

    @Test fun sanitizeFileNameRemoveInvalidosEGaranteExtensaoMarkdown() {
        assertEquals("Minha Nota.md", sanitizeFileName("  Minha Nota  ", ensureMarkdownExtension = true))
        assertEquals("nota.md", sanitizeFileName("nota.md", ensureMarkdownExtension = true))
        assertEquals("README.MARKDOWN", sanitizeFileName("README.MARKDOWN", ensureMarkdownExtension = true))
        assertEquals("rascunho.txt.md", sanitizeFileName("rascunho.txt", ensureMarkdownExtension = true))
        assertEquals("abcdefghij.md", sanitizeFileName("  a/b\\c:d*e?f\"g<h>i|j  ", ensureMarkdownExtension = true))
        assertEquals("Sem Título.md", sanitizeFileName("   ", ensureMarkdownExtension = true))
        assertEquals("Sem Título.md", sanitizeFileName("///:*?\"<>|\\", ensureMarkdownExtension = true))
        assertEquals("Sem Título.md", sanitizeFileName("..", ensureMarkdownExtension = true))
    }

    @Test fun sanitizeFileNameParaPastaNaoAdicionaExtensao() {
        assertEquals("Projetos", sanitizeFileName("  Projetos  ", ensureMarkdownExtension = false))
        assertEquals("subpasta", sanitizeFileName("sub/pasta\\:*?", ensureMarkdownExtension = false))
        assertEquals("Sem Título", sanitizeFileName("", ensureMarkdownExtension = false))
        assertEquals("Sem Título", sanitizeFileName(".", ensureMarkdownExtension = false))
    }
}
