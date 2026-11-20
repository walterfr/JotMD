package io.github.walterfr.jotmd.desktop

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.walterfr.jotmd.editor.resolveLocalImageFile
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopWorkspaceTest {

    @Test
    fun `listDesktopTree ordena pastas primeiro, omite vazias e ignora ocultos`(@TempDir root: Path) {
        Files.createDirectories(root.resolve(".git/objects"))
        Files.writeString(root.resolve(".git/HEAD.md"), "oculto")
        Files.writeString(root.resolve(".oculto.md"), "oculto")
        Files.createDirectories(root.resolve("vazia"))
        Files.writeString(root.resolve("vazia/ignorado.txt"), "txt")
        Files.createDirectories(root.resolve("Zeta"))
        Files.writeString(root.resolve("Zeta/sub.markdown"), "# sub")
        Files.createDirectories(root.resolve("alfa"))
        Files.writeString(root.resolve("alfa/nota.md"), "# alfa")
        Files.writeString(root.resolve("b.md"), "b")
        Files.writeString(root.resolve("A.md"), "a")

        val entries = listDesktopTree(root)

        assertEquals(
            listOf(
                "alfa" to true,
                "nota.md" to false,
                "Zeta" to true,
                "sub.markdown" to false,
                "A.md" to false,
                "b.md" to false,
            ),
            entries.map { it.name to it.isDir },
        )
        assertEquals(listOf(0, 1, 0, 1, 0, 0), entries.map { it.depth })
    }

    @Test
    fun `resolveDesktopWikilink resolve por nome sem extensao e por caminho relativo`(@TempDir root: Path) {
        Files.createDirectories(root.resolve("sub"))
        Files.writeString(root.resolve("Plano de Aula.md"), "# Plano")
        Files.writeString(root.resolve("sub/Plano de Aula.md"), "# Sub")
        Files.writeString(root.resolve("receita.markdown"), "# Receita")

        val entries = listDesktopTree(root)

        assertEquals(root.resolve("sub/Plano de Aula.md"), resolveDesktopWikilink("sub/Plano de Aula", entries)?.path)
        assertEquals(root.resolve("receita.markdown"), resolveDesktopWikilink("pasta/receita", entries)?.path)
        assertEquals(root.resolve("receita.markdown"), resolveDesktopWikilink("receita", entries)?.path)
        assertEquals(root.resolve("Plano de Aula.md"), resolveDesktopWikilink("plano de aula.md", entries)?.path)
        assertNull(resolveDesktopWikilink("sub", entries))
        assertNull(resolveDesktopWikilink("inexistente", entries))
    }

    @Test
    fun `searchDesktopFiles dobra acentos com NFD, encontra tags e marca snippet`(@TempDir root: Path) {
        Files.writeString(
            root.resolve("plano.md"),
            "# Plano de Aula\n\nRevisão de Ação importante para #projeto hoje.\n",
            StandardCharsets.UTF_8,
        )
        Files.writeString(
            root.resolve("meta.md"),
            "---\ntags: [projeto, estudo]\n---\n\nNotas sem cerquilha no corpo.\n",
            StandardCharsets.UTF_8,
        )
        Files.writeString(
            root.resolve("Música.md"),
            "Conteúdo simples.\n",
            StandardCharsets.UTF_8,
        )

        val accentHits = searchDesktopFiles(root, "acao")
        assertEquals(1, accentHits.size)
        val hit = accentHits.single()
        assertEquals("plano.md", hit.name)
        assertFalse(hit.snippet.contains('\n'))
        assertTrue(hit.marks.isNotEmpty())
        assertEquals("Ação", hit.snippet.substring(hit.marks.first()))

        val nameHits = searchDesktopFiles(root, "musica")
        assertEquals(listOf("Música.md"), nameHits.map { it.name })

        val tagHits = searchDesktopFiles(root, "#projeto")
        assertEquals(setOf("meta.md", "plano.md"), tagHits.map { it.name }.toSet())
    }

    @Test
    fun `DesktopTabManager abre sem duplicar e preserva EditorState ao alternar`(@TempDir root: Path) {
        val a = root.resolve("a.md")
        val b = root.resolve("b.md")
        Files.writeString(a, "original A\n")
        Files.writeString(b, "original B\n")

        val manager = DesktopTabManager()
        val tabA = assertNotNull(manager.openOrFocus(a))
        tabA.editorState.focus(tabA.editorState.doc.blocks.first())
        tabA.editorState.edit(TextFieldValue("editado A", TextRange(9)))

        val tabB = assertNotNull(manager.openOrFocus(b))
        assertEquals(2, manager.tabs.size)
        assertEquals(tabB.id, manager.activeTabId)

        val refocusedA = assertNotNull(manager.openOrFocus(a))
        assertEquals(2, manager.tabs.size)
        assertEquals(tabA.id, refocusedA.id)
        assertEquals(tabA.id, manager.activeTabId)
        assertEquals("editado A\n", refocusedA.editorState.text())
    }

    @Test
    fun `DesktopTabManager ao fechar ultima aba cria nova aba vazia`(@TempDir root: Path) {
        val a = root.resolve("a.md")
        Files.writeString(a, "conteudo\n")

        val manager = DesktopTabManager()
        val tab = assertNotNull(manager.openOrFocus(a))
        assertTrue(manager.close(tab.id))

        assertEquals(1, manager.tabs.size)
        val fresh = assertNotNull(manager.activeTab)
        assertNotEquals(tab.id, fresh.id)
        assertNull(fresh.file)
        assertEquals("", fresh.editorState.text())
    }

    @Test
    fun `DesktopTabManager close cancela quando gravacao falha`(@TempDir root: Path) {
        val a = root.resolve("a.md")
        Files.writeString(a, "inicial\n")

        val manager = DesktopTabManager()
        val tab = assertNotNull(manager.openOrFocus(a))
        tab.editorState.focus(tab.editorState.doc.blocks.first())
        tab.editorState.edit(TextFieldValue("alterado", TextRange(8)))

        // Substitui o arquivo por um diretório para forçar erro de gravação independente de ACL Windows.
        Files.delete(a)
        Files.createDirectory(a)

        assertFalse(manager.close(tab.id))
        assertTrue(tab.saveFailed)
        assertEquals(1, manager.tabs.size)
        assertEquals(tab.id, manager.activeTabId)
    }

    @Test
    fun `saveTabIfChanged so grava em disco quando o texto mudou`(@TempDir root: Path) {
        val file = root.resolve("nota.md")
        Files.writeString(file, "texto inicial\n")

        val manager = DesktopTabManager()
        val tab = assertNotNull(manager.openOrFocus(file))
        assertFalse(saveTabIfChanged(tab))

        tab.editorState.focus(tab.editorState.doc.blocks.first())
        tab.editorState.edit(TextFieldValue("texto alterado", TextRange(14)))
        assertTrue(saveTabIfChanged(tab))
        assertEquals("texto alterado\n", Files.readString(file))
        assertFalse(tab.saveFailed)

        assertFalse(saveTabIfChanged(tab))
    }

    @Test
    fun `resolveLocalImageFile bloqueia rede e UNC e aceita arquivo local existente`(@TempDir root: Path) {
        val img = root.resolve("pic.png")
        Files.write(img, byteArrayOf(1, 2, 3, 4))

        assertNull(resolveLocalImageFile("https://example.com/a.png"))
        assertNull(resolveLocalImageFile("http://localhost/a.png"))
        assertNull(resolveLocalImageFile("content://media/external/images/1"))
        assertNull(resolveLocalImageFile("\\\\servidor\\share\\a.png"))
        assertNull(resolveLocalImageFile("file://servidor/share/a.png"))
        assertNull(resolveLocalImageFile(root.resolve("ausente.png").toString()))

        assertNotNull(resolveLocalImageFile(img.toString()))
        assertNotNull(resolveLocalImageFile(img.toUri().toString()))
    }
}
