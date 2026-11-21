package io.github.walterfr.jotmd.desktop

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.walterfr.jotmd.editor.resolveLocalImageFile
import org.junit.jupiter.api.io.TempDir
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.image.BufferedImage
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
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
        assertEquals("Sem Título", fresh.title)
        assertNull(fresh.file)
        assertEquals("", fresh.editorState.text())
    }

    @Test
    fun `DesktopTabManager newEmptyTab usa titulo Sem Titulo`() {
        val manager = DesktopTabManager()
        val blank = manager.newEmptyTab()

        assertEquals("Sem Título", blank.title)
        assertNull(blank.file)
        assertEquals("", blank.editorState.text())
        assertEquals(blank.id, manager.activeTabId)
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

    @Test
    fun `filterQuickOpen dobra acentos, evita duplicar aba aberta, inclui rascunhos e prioriza titulo`(@TempDir root: Path) {
        Files.createDirectories(root.resolve("sub_acao"))
        Files.writeString(root.resolve("Ação.md"), "# Ação\n", StandardCharsets.UTF_8)
        Files.writeString(root.resolve("sub_acao/outro.md"), "# Outro\n", StandardCharsets.UTF_8)
        Files.writeString(root.resolve("plano.md"), "# Plano\n", StandardCharsets.UTF_8)

        val entries = listDesktopTree(root)
        val manager = DesktopTabManager()
        val openAcao = assertNotNull(manager.openOrFocus(root.resolve("Ação.md")))
        val blank = manager.newEmptyTab()

        val all = filterQuickOpen("", entries, manager.tabs)
        assertEquals(4, all.size)
        val acaoItems = all.filter { it.title == "Ação.md" }
        assertEquals(1, acaoItems.size)
        assertEquals(openAcao.id, acaoItems.single().tabId)
        assertTrue(all.any { it.tabId == blank.id && it.path == null && it.title == "Sem Título" })

        val acaoHits = filterQuickOpen("acao", entries, manager.tabs)
        assertEquals(listOf("Ação.md", "outro.md"), acaoHits.map { it.title })

        val blankHits = filterQuickOpen("sem", entries, manager.tabs)
        assertEquals(listOf("Sem Título"), blankHits.map { it.title })

        assertEquals(2, filterQuickOpen("", entries, manager.tabs, limit = 2).size)
    }

    @Test
    fun `DirectoryTreeWatcher detecta criacao de novo arquivo markdown`(@TempDir root: Path) {
        val latch = CountDownLatch(1)
        DirectoryTreeWatcher(root) { latch.countDown() }.use {
            Files.writeString(root.resolve("ignorado.txt"), "txt", StandardCharsets.UTF_8)
            Files.writeString(root.resolve(".oculto.md"), "oculto", StandardCharsets.UTF_8)
            Files.writeString(root.resolve("novo.md"), "# Novo\n", StandardCharsets.UTF_8)
            assertTrue(latch.await(5, TimeUnit.SECONDS), "DirectoryTreeWatcher deveria notificar criação de .md")
        }
    }

    @Test
    fun `listDesktopTree com includeEmptyDirs true inclui pastas vazias e mantem ocultas ignoradas`(@TempDir root: Path) {
        Files.createDirectories(root.resolve(".git/objects"))
        Files.createDirectories(root.resolve("vazia/aninhada"))
        Files.writeString(root.resolve("nota.md"), "# Nota\n", StandardCharsets.UTF_8)

        assertEquals(listOf("nota.md"), listDesktopTree(root).map { it.name })

        val withEmpty = listDesktopTree(root, includeEmptyDirs = true)
        assertEquals(
            listOf("vazia" to true, "aninhada" to true, "nota.md" to false),
            withEmpty.map { it.name to it.isDir },
        )
        assertEquals(listOf(0, 1, 0), withEmpty.map { it.depth })
    }

    @Test
    fun `sanitizeDesktopEntryName garante extensao, limpa caracteres invalidos e bloqueia traversal`() {
        assertEquals("Minha Nota.md", sanitizeDesktopEntryName("  Minha Nota  ", ensureMarkdownExtension = true))
        assertEquals("doc.markdown", sanitizeDesktopEntryName("doc.markdown", ensureMarkdownExtension = true))
        assertEquals("Pasta 01", sanitizeDesktopEntryName("  Pasta 01  ", ensureMarkdownExtension = false))
        assertEquals("nota 1.md", sanitizeDesktopEntryName("nota: 1?", ensureMarkdownExtension = true))

        assertEquals("", sanitizeDesktopEntryName("../escape", ensureMarkdownExtension = true))
        assertEquals("", sanitizeDesktopEntryName("..\\escape", ensureMarkdownExtension = false))
        assertEquals("", sanitizeDesktopEntryName("sub/nota", ensureMarkdownExtension = true))
        assertEquals("", sanitizeDesktopEntryName(".", ensureMarkdownExtension = true))
        assertEquals("", sanitizeDesktopEntryName("..", ensureMarkdownExtension = false))
        assertEquals("", sanitizeDesktopEntryName(".oculto", ensureMarkdownExtension = true))
        assertEquals("", sanitizeDesktopEntryName(".md", ensureMarkdownExtension = true))
        assertEquals("", sanitizeDesktopEntryName("   ", ensureMarkdownExtension = true))
    }

    @Test
    fun `CRUD de arquivos e pastas cria, renomeia, exclui e bloqueia traversal e sobrescrita`(@TempDir root: Path) {
        val sub = assertNotNull(createDesktopFolder(root, "SubPasta"))
        assertTrue(Files.isDirectory(sub))
        assertNull(createDesktopFolder(root, "SubPasta"))
        assertNull(createDesktopFolder(sub, "../fora"))

        val nota = assertNotNull(createDesktopMarkdownFile(sub, "Ideias"))
        assertEquals(sub.resolve("Ideias.md"), nota)
        assertTrue(Files.isRegularFile(nota))
        Files.writeString(nota, "conteúdo protegido", StandardCharsets.UTF_8)

        assertNull(createDesktopMarkdownFile(sub, "Ideias.md"))
        assertEquals("conteúdo protegido", Files.readString(nota, StandardCharsets.UTF_8))

        assertNull(createDesktopMarkdownFile(sub, "../escape"))
        assertFalse(Files.exists(root.resolve("escape.md")))

        val outra = assertNotNull(createDesktopMarkdownFile(sub, "Outra"))
        assertNull(renameDesktopEntry(nota, "Outra.md"))
        assertNull(renameDesktopEntry(nota, "../escape.md"))

        val renomeada = assertNotNull(renameDesktopEntry(nota, "Plano Final"))
        assertEquals(sub.resolve("Plano Final.md"), renomeada)
        assertFalse(Files.exists(nota))
        assertEquals("conteúdo protegido", Files.readString(renomeada, StandardCharsets.UTF_8))

        val subRenomeada = assertNotNull(renameDesktopEntry(sub, "Arquivos"))
        assertEquals(root.resolve("Arquivos"), subRenomeada)
        assertTrue(Files.isRegularFile(subRenomeada.resolve("Plano Final.md")))
        assertTrue(Files.isRegularFile(subRenomeada.resolve("Outra.md")))

        assertTrue(deleteDesktopEntry(subRenomeada))
        assertFalse(Files.exists(subRenomeada))
        assertFalse(deleteDesktopEntry(subRenomeada))
    }

    @Test
    fun `DesktopTabManager updateRenamedPath atualiza arquivo e pasta pai sem afetar prefixos parecidos`(@TempDir root: Path) {
        val pasta = Files.createDirectories(root.resolve("pasta"))
        val sub = Files.createDirectories(pasta.resolve("sub"))
        val pasta2 = Files.createDirectories(root.resolve("pasta2"))
        val a = pasta.resolve("a.md")
        val b = sub.resolve("b.md")
        val x = pasta2.resolve("x.md")
        Files.writeString(a, "A\n", StandardCharsets.UTF_8)
        Files.writeString(b, "B\n", StandardCharsets.UTF_8)
        Files.writeString(x, "X\n", StandardCharsets.UTF_8)

        val manager = DesktopTabManager()
        val tabA = assertNotNull(manager.openOrFocus(a))
        val tabB = assertNotNull(manager.openOrFocus(b))
        val tabX = assertNotNull(manager.openOrFocus(x))

        val aRenomeado = assertNotNull(renameDesktopEntry(a, "a2.md"))
        manager.updateRenamedPath(a, aRenomeado)
        assertEquals(aRenomeado.toAbsolutePath().normalize(), tabA.file)
        assertEquals("a2.md", tabA.title)

        val novaPasta = assertNotNull(renameDesktopEntry(pasta, "nova"))
        manager.updateRenamedPath(pasta, novaPasta)
        assertEquals(novaPasta.resolve("a2.md").toAbsolutePath().normalize(), tabA.file)
        assertEquals(novaPasta.resolve("sub/b.md").toAbsolutePath().normalize(), tabB.file)
        assertEquals("b.md", tabB.title)
        assertEquals(x.toAbsolutePath().normalize(), tabX.file)
    }

    @Test
    fun `DesktopTabManager closeRemovedPath fecha abas removidas sem regravar e abre Sem Titulo quando vazio`(@TempDir root: Path) {
        val pasta = Files.createDirectories(root.resolve("pasta"))
        val a = pasta.resolve("a.md")
        val outro = root.resolve("outro.md")
        Files.writeString(a, "A\n", StandardCharsets.UTF_8)
        Files.writeString(outro, "Outro\n", StandardCharsets.UTF_8)

        val manager = DesktopTabManager()
        val tabA = assertNotNull(manager.openOrFocus(a))
        tabA.editorState.focus(tabA.editorState.doc.blocks.first())
        tabA.editorState.edit(TextFieldValue("alterado sem salvar", TextRange(19)))
        val tabOutro = assertNotNull(manager.openOrFocus(outro))
        manager.select(tabA.id)

        assertTrue(deleteDesktopEntry(pasta))
        manager.closeRemovedPath(pasta)

        assertFalse(Files.exists(pasta))
        assertEquals(listOf(tabOutro.id), manager.tabs.map { it.id })
        assertEquals(tabOutro.id, manager.activeTabId)

        assertTrue(deleteDesktopEntry(outro))
        manager.closeRemovedPath(outro)

        assertEquals(1, manager.tabs.size)
        val fresh = assertNotNull(manager.activeTab)
        assertEquals("Sem Título", fresh.title)
        assertNull(fresh.file)
    }

    @Test
    fun `saveClipboardImagePng cria PNG em typora-user-images resolvivel pelo ImagePreview`(@TempDir root: Path) {
        val sub = Files.createDirectories(root.resolve("notas"))
        val activeFile = sub.resolve("nota.md")
        val img = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)

        val dest = assertNotNull(
            saveClipboardImagePng(
                image = img,
                activeFile = activeFile,
                rootDir = root,
                timestamp = "20261121-153000",
            ),
        )
        val expectedFile = sub.resolve("typora-user-images/image-20261121-153000.png")
        assertTrue(Files.isRegularFile(expectedFile))
        val decoded = assertNotNull(ImageIO.read(expectedFile.toFile()))
        assertEquals(8, decoded.width)
        assertEquals(8, decoded.height)
        assertNotNull(resolveLocalImageFile(dest))

        val second = assertNotNull(
            saveClipboardImagePng(
                image = img,
                activeFile = null,
                rootDir = root,
                timestamp = "20261121-153000",
            ),
        )
        assertTrue(Files.isRegularFile(root.resolve("typora-user-images/image-20261121-153000.png")))
        assertNotNull(resolveLocalImageFile(second))

        val third = assertNotNull(
            saveClipboardImagePng(
                image = img,
                activeFile = null,
                rootDir = root,
                timestamp = "20261121-153000",
            ),
        )
        assertTrue(Files.isRegularFile(root.resolve("typora-user-images/image-20261121-153000-1.png")))
        assertNotNull(resolveLocalImageFile(third))
        assertNull(saveClipboardImagePng(img, activeFile = null, rootDir = null, timestamp = "1"))
    }

    @Test
    fun `readClipboardPayload respeita prioridade imagem, HTML e texto`() {
        val clipboard = Clipboard("test-jotmd")

        clipboard.setContents(StringSelection("texto simples"), null)
        assertEquals(ClipboardPayload.Text("texto simples"), readClipboardPayload(clipboard))

        val htmlFlavor = DataFlavor("text/html;class=java.lang.String")
        clipboard.setContents(
            object : Transferable {
                override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(htmlFlavor, DataFlavor.stringFlavor)
                override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
                    flavor == htmlFlavor || flavor == DataFlavor.stringFlavor
                override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
                    htmlFlavor -> "<p><b>Olá</b></p>"
                    DataFlavor.stringFlavor -> "Olá"
                    else -> throw UnsupportedFlavorException(flavor)
                }
            },
            null,
        )
        assertEquals(ClipboardPayload.Html("<p><b>Olá</b></p>"), readClipboardPayload(clipboard))

        val img = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        clipboard.setContents(
            object : Transferable {
                override fun getTransferDataFlavors(): Array<DataFlavor> =
                    arrayOf(DataFlavor.imageFlavor, htmlFlavor, DataFlavor.stringFlavor)
                override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
                    flavor == DataFlavor.imageFlavor || flavor == htmlFlavor || flavor == DataFlavor.stringFlavor
                override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
                    DataFlavor.imageFlavor -> img
                    htmlFlavor -> "<p>ignored</p>"
                    DataFlavor.stringFlavor -> "ignored"
                    else -> throw UnsupportedFlavorException(flavor)
                }
            },
            null,
        )
        assertTrue(readClipboardPayload(clipboard) is ClipboardPayload.Image)
    }
}
