package io.github.walterfr.jotmd

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.walterfr.jotmd.editor.EditorState

/**
 * Uma aba aberta no editor. Cada aba carrega seu próprio [EditorState] (bloco
 * focado, undo/redo) e opcionalmente uma [FileSession] (nula em documentos só
 * de leitura como o asset inicial ou abas em branco "Sem Título").
 *
 * Imutável de propósito: alterações em [session], [title] ou [saveFailed]
 * substituem a entrada na [SnapshotStateList] do [TabManager], garantindo
 * recomposição automática no Compose sem depender de estados mutáveis soltos.
 */
data class EditorTab(
    val id: Long,
    val key: String?,
    val title: String,
    val editorState: EditorState,
    val session: FileSession? = null,
    val saveFailed: Boolean = false,
)

/**
 * Gerenciador de abas abertas. Sem dependências de `android.*` (além da
 * referência opaca a [FileSession] nula nos testes) para rodar em JVM puro.
 */
class TabManager(
    initialTitle: String = "typora-ref.md",
    initialText: String = "",
    initialKey: String? = null,
) {
    private var nextId = 1L

    private val _tabs = mutableStateListOf(
        EditorTab(
            id = nextId++,
            key = initialKey,
            title = initialTitle,
            editorState = EditorState(initialText),
        ),
    )

    val tabs: List<EditorTab> get() = _tabs

    var activeTabId: Long by mutableStateOf(_tabs.first().id)
        private set

    val activeTab: EditorTab
        get() = _tabs.first { it.id == activeTabId }

    /**
     * Foca a aba existente com [key] (preservando [EditorState], edição e undo)
     * ou abre uma nova aba no fim e a torna ativa.
     */
    fun openOrFocus(
        key: String?,
        title: String,
        text: String,
        session: FileSession? = null,
    ): EditorTab {
        if (key != null) {
            val idx = _tabs.indexOfFirst { it.key == key }
            if (idx >= 0) {
                val existing = _tabs[idx]
                val updated = if (existing.session == null && session != null) {
                    existing.copy(session = session)
                } else {
                    existing
                }
                if (updated !== existing) {
                    _tabs[idx] = updated
                }
                activeTabId = updated.id
                return updated
            }
        }
        val created = EditorTab(
            id = nextId++,
            key = key,
            title = title,
            editorState = EditorState(text),
            session = session,
        )
        _tabs.add(created)
        activeTabId = created.id
        return created
    }

    fun newEmptyTab(title: String = "Sem Título", text: String = ""): EditorTab =
        openOrFocus(key = null, title = title, text = text, session = null)

    fun select(id: Long) {
        if (_tabs.any { it.id == id }) {
            activeTabId = id
        }
    }

    fun updateSaveState(id: Long, saveFailed: Boolean) {
        val idx = _tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        val current = _tabs[idx]
        if (current.saveFailed != saveFailed) {
            _tabs[idx] = current.copy(saveFailed = saveFailed)
        }
    }

    fun replaceSession(id: Long, session: FileSession?, title: String? = null) {
        val idx = _tabs.indexOfFirst { it.id == id }
        if (idx < 0) return
        val current = _tabs[idx]
        _tabs[idx] = current.copy(
            session = session,
            title = title ?: current.title,
        )
    }

    /**
     * Fecha a aba [id] após consultar [onBeforeClose]. Se [onBeforeClose]
     * retornar `false`, marca `saveFailed = true` na aba e mantém ela aberta
     * para não perder edições não gravadas. Ao fechar a última aba restante,
     * abre uma aba vazia `"Sem Título"`.
     */
    fun close(id: Long, onBeforeClose: (EditorTab) -> Boolean = { true }): Boolean {
        val idx = _tabs.indexOfFirst { it.id == id }
        if (idx < 0) return false
        val tab = _tabs[idx]
        if (!onBeforeClose(tab)) {
            updateSaveState(id, saveFailed = true)
            return false
        }
        val wasActive = tab.id == activeTabId
        _tabs.removeAt(idx)
        if (_tabs.isEmpty()) {
            newEmptyTab()
        } else if (wasActive) {
            activeTabId = _tabs[idx.coerceAtMost(_tabs.lastIndex)].id
        }
        return true
    }
}
