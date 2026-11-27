package io.github.walterfr.jotmd

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TabManagerTest {

    @Test
    fun `criacao inicial tem uma aba ativa`() {
        val tm = TabManager(
            initialTitle = "typora-ref.md",
            initialText = "# Ola\n",
            initialKey = "asset://typora-ref.md",
        )
        assertEquals(1, tm.tabs.size)
        assertEquals(tm.tabs[0].id, tm.activeTabId)
        assertEquals("typora-ref.md", tm.activeTab.title)
        assertEquals("asset://typora-ref.md", tm.activeTab.key)
        assertEquals("# Ola\n", tm.activeTab.editorState.text())
    }

    @Test
    fun `openOrFocus com chave nova cria segunda aba e preserva edicoes da primeira`() {
        val tm = TabManager(initialTitle = "a.md", initialText = "um\n", initialKey = "k1")
        val first = tm.activeTab
        first.editorState.focus(first.editorState.doc.blocks[0])
        first.editorState.edit(TextFieldValue("UM", TextRange(2)))

        val second = tm.openOrFocus(key = "k2", title = "b.md", text = "dois\n")

        assertEquals(2, tm.tabs.size)
        assertNotEquals(first.id, second.id)
        assertEquals(second.id, tm.activeTabId)
        assertEquals("b.md", tm.activeTab.title)
        assertEquals("UM\n", first.editorState.text())
        assertEquals("dois\n", second.editorState.text())
    }

    @Test
    fun `openOrFocus com chave ja aberta foca aba existente sem perder edicao nem undo`() {
        val tm = TabManager(initialTitle = "a.md", initialText = "inicial\n", initialKey = "k1")
        val tab1 = tm.activeTab
        tab1.editorState.focus(tab1.editorState.doc.blocks[0])
        tab1.editorState.edit(TextFieldValue("editado", TextRange(7)))

        tm.openOrFocus(key = "k2", title = "b.md", text = "outro\n")
        val refocused = tm.openOrFocus(key = "k1", title = "a.md", text = "conteudo do disco\n")

        assertEquals(2, tm.tabs.size)
        assertEquals(tab1.id, refocused.id)
        assertEquals(tab1.id, tm.activeTabId)
        assertEquals("editado\n", tm.activeTab.editorState.text())

        tm.activeTab.editorState.undo()
        assertEquals("inicial\n", tm.activeTab.editorState.text())
    }

    @Test
    fun `select alterna entre abas preservando foco, conteudo e undo de cada EditorState`() {
        val tm = TabManager(initialTitle = "a.md", initialText = "a1\n\na2\n", initialKey = "k1")
        val tab1 = tm.activeTab
        val block1 = tab1.editorState.doc.blocks[1]
        tab1.editorState.focus(block1)
        tab1.editorState.edit(TextFieldValue("A2", TextRange(2)))

        val tab2 = tm.openOrFocus(key = "k2", title = "b.md", text = "b1\n")
        val block2 = tab2.editorState.doc.blocks[0]
        tab2.editorState.focus(block2)
        tab2.editorState.edit(TextFieldValue("B1", TextRange(2)))

        tm.select(tab1.id)
        assertEquals(tab1.id, tm.activeTabId)
        assertEquals(block1.id, tm.activeTab.editorState.focusedId)
        assertEquals("a1\n\nA2\n", tm.activeTab.editorState.text())
        tm.activeTab.editorState.undo()
        assertEquals("a1\n\na2\n", tm.activeTab.editorState.text())

        tm.select(tab2.id)
        assertEquals(tab2.id, tm.activeTabId)
        assertEquals(block2.id, tm.activeTab.editorState.focusedId)
        assertEquals("B1\n", tm.activeTab.editorState.text())
        tm.activeTab.editorState.undo()
        assertEquals("b1\n", tm.activeTab.editorState.text())

        tm.select(9999L)
        assertEquals(tab2.id, tm.activeTabId)
    }

    @Test
    fun `close em aba inativa remove sem mudar aba ativa`() {
        val tm = TabManager(initialTitle = "a.md", initialText = "a\n", initialKey = "k1")
        val t1 = tm.tabs[0]
        val t2 = tm.openOrFocus("k2", "b.md", "b\n")
        val t3 = tm.openOrFocus("k3", "c.md", "c\n")
        tm.select(t2.id)

        assertTrue(tm.close(t1.id))
        assertEquals(listOf(t2.id, t3.id), tm.tabs.map { it.id })
        assertEquals(t2.id, tm.activeTabId)
    }

    @Test
    fun `close na aba ativa seleciona aba vizinha`() {
        val tm = TabManager(initialTitle = "a.md", initialText = "a\n", initialKey = "k1")
        val t1 = tm.tabs[0]
        val t2 = tm.openOrFocus("k2", "b.md", "b\n")
        val t3 = tm.openOrFocus("k3", "c.md", "c\n")

        tm.select(t2.id)
        assertTrue(tm.close(t2.id))
        assertEquals(listOf(t1.id, t3.id), tm.tabs.map { it.id })
        assertEquals(t3.id, tm.activeTabId)

        // Fechar a última da direita recua para a anterior.
        assertTrue(tm.close(t3.id))
        assertEquals(listOf(t1.id), tm.tabs.map { it.id })
        assertEquals(t1.id, tm.activeTabId)
    }

    @Test
    fun `close na ultima aba cria nova aba Sem Titulo vazia`() {
        val tm = TabManager(initialTitle = "a.md", initialText = "conteudo\n", initialKey = "k1")
        val originalId = tm.activeTabId

        assertTrue(tm.close(originalId))
        assertEquals(1, tm.tabs.size)
        val blank = tm.activeTab
        assertNotEquals(originalId, blank.id)
        assertEquals("Sem Título", blank.title)
        assertNull(blank.key)
        assertNull(blank.session)
        assertEquals("", blank.editorState.text())
    }

    @Test
    fun `close cancela quando onBeforeClose retorna false e marca saveFailed`() {
        val tm = TabManager(initialTitle = "a.md", initialText = "a\n", initialKey = "k1")
        val t2 = tm.openOrFocus("k2", "b.md", "b\n")
        t2.editorState.focus(t2.editorState.doc.blocks[0])
        t2.editorState.edit(TextFieldValue("modificado", TextRange(10)))

        assertFalse(tm.close(t2.id) { false })
        assertEquals(2, tm.tabs.size)
        assertEquals(t2.id, tm.activeTabId)
        assertTrue(tm.activeTab.saveFailed)
        assertEquals("modificado\n", tm.activeTab.editorState.text())

        assertFalse(tm.close(9999L))
    }

    @Test
    fun `newEmptyTab, updateSaveState e replaceSession atualizam estado da aba`() {
        val tm = TabManager(initialTitle = "a.md", initialText = "a\n", initialKey = "k1")
        val blank = tm.newEmptyTab()

        assertEquals(2, tm.tabs.size)
        assertEquals(blank.id, tm.activeTabId)
        assertEquals("Sem Título", blank.title)
        assertNull(blank.key)
        assertEquals("", blank.editorState.text())

        tm.updateSaveState(blank.id, saveFailed = true)
        assertTrue(tm.activeTab.saveFailed)
        tm.updateSaveState(blank.id, saveFailed = false)
        assertFalse(tm.activeTab.saveFailed)

        tm.replaceSession(blank.id, session = null, title = "renomeada.md")
        assertEquals("renomeada.md", tm.activeTab.title)
    }

    @Test
    fun `openOrFocus, newEmptyTab e updateDraftId preservam e atualizam draftId`() {
        val tm = TabManager(initialTitle = "a.md", initialText = "a\n", initialKey = "k1")
        assertNull(tm.activeTab.draftId)

        val recovered = tm.openOrFocus(
            key = null,
            title = "Rascunho.md",
            text = "conteudo",
            draftId = "draft-1",
        )
        assertEquals("draft-1", recovered.draftId)

        val blank = tm.newEmptyTab(title = "Untitled", text = "abc", draftId = "draft-2")
        assertEquals("draft-2", blank.draftId)
        assertEquals("Untitled", blank.title)

        tm.updateDraftId(blank.id, "draft-2-updated")
        assertEquals("draft-2-updated", tm.tabs.first { it.id == blank.id }.draftId)

        tm.updateDraftId(blank.id, null)
        assertNull(tm.tabs.first { it.id == blank.id }.draftId)

        // ID inexistente não gera exceção nem altera abas existentes.
        tm.updateDraftId(9999L, "ignored")
        assertEquals("draft-1", tm.tabs.first { it.id == recovered.id }.draftId)
    }

    @Test
    fun `default inicial e Sem Titulo vazia e openOrFocus substitui quando intacta`() {
        val tm = TabManager()
        assertEquals(1, tm.tabs.size)
        assertEquals("Sem Título", tm.activeTab.title)
        assertNull(tm.activeTab.key)
        assertEquals("", tm.activeTab.editorState.text())
        assertTrue(tm.isUntouchedBlankTab(tm.activeTab))

        val opened = tm.openOrFocus(
            key = "content://doc/1",
            title = "nota.md",
            text = "# Nota\n",
            replaceInitialBlank = true,
        )
        assertEquals(1, tm.tabs.size)
        assertEquals(opened.id, tm.activeTabId)
        assertEquals("nota.md", tm.activeTab.title)
        assertFalse(tm.isUntouchedBlankTab(opened))
    }

    @Test
    fun `openOrFocus com replaceInitialBlank preserva aba inicial se ja editada`() {
        val tm = TabManager()
        val first = tm.activeTab
        first.editorState.load("rascunho")
        assertFalse(tm.isUntouchedBlankTab(first))

        val opened = tm.openOrFocus(
            key = "content://doc/2",
            title = "outra.md",
            text = "dois\n",
            replaceInitialBlank = true,
        )
        assertEquals(2, tm.tabs.size)
        assertEquals(first.id, tm.tabs[0].id)
        assertEquals(opened.id, tm.tabs[1].id)
        assertEquals(opened.id, tm.activeTabId)
    }

    @Test
    fun `bindFile limpa draftId e saveFailed, preserva EditorState e undo e retorna null se id faltar`() {
        val tm = TabManager(initialTitle = "Sem Título", initialText = "inicial\n", initialKey = null)
        val draftTab = tm.activeTab
        tm.updateDraftId(draftTab.id, "draft-42")
        tm.updateSaveState(draftTab.id, saveFailed = true)
        draftTab.editorState.focus(draftTab.editorState.doc.blocks[0])
        draftTab.editorState.edit(TextFieldValue("editado", TextRange(7)))

        // Abre outra aba para garantir que bindFile reativa a aba vinculada
        val other = tm.openOrFocus(key = "k-other", title = "outra.md", text = "outra\n")
        assertEquals(other.id, tm.activeTabId)

        val bound = tm.bindFile(
            id = draftTab.id,
            key = "content://doc/saved",
            title = "Salva.md",
            session = null,
        )

        assertSame(bound, tm.activeTab)
        assertEquals(draftTab.id, tm.activeTabId)
        assertEquals("content://doc/saved", bound?.key)
        assertEquals("Salva.md", bound?.title)
        assertNull(bound?.session)
        assertNull(bound?.draftId)
        assertFalse(bound?.saveFailed ?: true)
        assertSame(draftTab.editorState, bound?.editorState)
        assertEquals("editado\n", bound?.editorState?.text())

        // Histórico de undo permanece intacto
        bound?.editorState?.undo()
        assertEquals("inicial\n", bound?.editorState?.text())

        // ID inexistente retorna null sem alterar aba ativa
        assertNull(tm.bindFile(9999L, key = "k-x", title = "x.md", session = null))
        assertEquals(draftTab.id, tm.activeTabId)
    }
}
