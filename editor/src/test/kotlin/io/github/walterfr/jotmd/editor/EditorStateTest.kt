package io.github.walterfr.jotmd.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import io.github.walterfr.jotmd.core.BlockType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorStateTest {

    @Test
    fun `focar carrega o source cru, sem marcadores, com cursor no fim`() {
        val s = EditorState("# Título\n\nparágrafo\n")
        s.focus(s.doc.blocks[0])
        assertEquals("# Título", s.editValue.text)
        assertEquals(TextRange(8), s.editValue.selection)
    }

    @Test
    fun `editar propaga pro doc na hora, sem esperar perder o foco`() {
        val s = EditorState("texto\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto novo", TextRange(10)))
        assertEquals("texto novo", s.doc.blocks[0].source)
    }

    @Test
    fun `commit atrasado do IME no campo antigo não sobrescreve o bloco recém-focado`() {
        // Visto no tablet: SwiftKey compondo "Fim", toque na lista, a lista virou "Fim".
        val s = EditorState("- um\n\nFim\n")
        val (lista, fim) = s.doc.blocks
        s.focus(fim)
        s.focus(lista)
        s.edit(TextFieldValue("Fim", TextRange(3)), from = fim.id)
        assertEquals("- um\n\nFim\n", s.text())
    }

    @Test
    fun `trailing do bloco não muda com a edição`() {
        val s = EditorState("um\n\ndois\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("UM", TextRange(2)))
        assertEquals("\n\n", s.doc.blocks[0].trailing)
        assertEquals("dois", s.doc.blocks[1].source)
    }

    @Test
    fun `trocar de bloco preserva a edição do anterior`() {
        val s = EditorState("um\n\ndois\n")
        val (primeiro, segundo) = s.doc.blocks
        s.focus(primeiro)
        s.edit(TextFieldValue("UM", TextRange(2)))
        s.focus(segundo)
        assertEquals("UM", s.doc.blocks[0].source)
        assertEquals("dois", s.editValue.text)
    }

    @Test
    fun `wrapSelection envolve o trecho selecionado e reposiciona a seleção`() {
        val s = EditorState("um dois tres\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("um dois tres", TextRange(3, 7))) // "dois"
        s.wrapSelection("**")
        assertEquals("um **dois** tres", s.editValue.text)
        assertEquals(TextRange(5, 9), s.editValue.selection)
        assertEquals("um **dois** tres", s.doc.blocks[0].source)
    }

    @Test
    fun `wrapSelection sem cursor, sem seleção, insere os marcadores colados`() {
        val s = EditorState("texto\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto", TextRange(5)))
        s.wrapSelection("**")
        assertEquals("texto****", s.editValue.text)
        assertEquals(TextRange(7, 7), s.editValue.selection)
    }

    @Test
    fun `wrapSelection sem bloco focado não faz nada`() {
        val s = EditorState("um\n")
        s.wrapSelection("**")
        assertEquals("", s.editValue.text)
    }

    @Test
    fun `release com id diferente do focado não limpa o foco`() {
        val s = EditorState("um\n\ndois\n")
        val (primeiro, segundo) = s.doc.blocks
        s.focus(primeiro)
        s.release(segundo.id)
        assertEquals(primeiro.id, s.focusedId)
    }

    @Test
    fun `release com o id certo limpa o foco`() {
        val s = EditorState("um\n")
        val block = s.doc.blocks[0]
        s.focus(block)
        s.release(block.id)
        assertNull(s.focusedId)
    }

    @Test
    fun `load reseta foco, buffer e histórico de undo`() {
        val s = EditorState("um\n")
        s.focus(s.doc.blocks[0])
        s.load("novo\n")
        assertNull(s.focusedId)
        assertEquals("", s.editValue.text)
        assertEquals("novo", s.doc.blocks[0].source)
    }

    @Test
    fun `text devolve o documento carregado idêntico, sem edição`() {
        val original = "\n# T\n\num **b**\n\n\n- item\r\n"
        assertEquals(original, EditorState(original).text())
    }

    @Test
    fun `text reflete a edição e preserva todo o resto byte a byte`() {
        val s = EditorState("um\n\ndois\n\n\ntres\n")
        s.focus(s.doc.blocks[1])
        s.edit(TextFieldValue("DOIS!", TextRange(5)))
        assertEquals("um\n\nDOIS!\n\n\ntres\n", s.text())
    }

    @Test
    fun `enter em lista continua o item e enter no item vazio sai pra um parágrafo`() {
        val s = EditorState("- um\n\nfim\n")
        s.focus(s.doc.blocks[0])
        assertTrue(s.smartEnterAtCursor())
        assertEquals("- um\n- ", s.doc.blocks[0].source)

        assertTrue(s.smartEnterAtCursor()) // item vazio
        assertEquals(listOf("- um", "", "fim"), s.doc.blocks.map { it.source })
        assertEquals(BlockType.PARAGRAPH, s.doc.blocks[1].type)
        assertEquals(s.doc.blocks[1].id, s.focusedId)
        assertEquals("- um\n\n\n\nfim\n", s.text())
    }

    @Test
    fun `enter em parágrafo não é tratado como lista`() {
        val s = EditorState("texto\n")
        s.focus(s.doc.blocks[0])
        assertEquals(false, s.smartEnterAtCursor())
    }

    @Test
    fun `undo desfaz a saída da lista`() {
        val s = EditorState("- um\n")
        s.focus(s.doc.blocks[0])
        s.smartEnterAtCursor()
        Thread.sleep(600) // passa da janela de coalescência: dois checkpoints
        s.smartEnterAtCursor()
        s.undo()
        assertEquals(listOf("- um\n- "), s.doc.blocks.map { it.source })
    }

    // ---- splitAtCursor / mergeWithPreviousBlock ----

    @Test
    fun `splitAtCursor no meio do texto cria dois blocos e foca o segundo no início`() {
        val s = EditorState("um dois tres\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("um dois tres", TextRange(7))) // cursor logo antes de "tres"
        s.splitAtCursor()

        assertEquals(2, s.doc.blocks.size)
        assertEquals("um dois", s.doc.blocks[0].source)
        assertEquals(" tres", s.doc.blocks[1].source)
        assertEquals(s.doc.blocks[1].id, s.focusedId)
        assertEquals(TextRange(0), s.editValue.selection)
    }

    @Test
    fun `splitAtCursor no fim do texto cria bloco vazio, não pula pro próximo bloco existente`() {
        // Enter no fim de "um" — bug real pego no tablet: reparse descarta o
        // bloco "after" vazio (parse() nunca cria bloco de texto vazio), e sem
        // este caso especial o foco pulava direto pro "dois", abaixo.
        val s = EditorState("um\n\ndois\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("um", TextRange(2)))
        s.splitAtCursor()

        assertEquals(3, s.doc.blocks.size)
        assertEquals("um", s.doc.blocks[0].source)
        assertEquals("", s.doc.blocks[1].source)
        assertEquals("dois", s.doc.blocks[2].source)
        assertEquals(s.doc.blocks[1].id, s.focusedId)
        assertEquals("", s.editValue.text)
    }

    @Test
    fun `splitAtCursor sem bloco focado não faz nada`() {
        val s = EditorState("um\n")
        s.splitAtCursor()
        assertEquals(1, s.doc.blocks.size)
    }

    @Test
    fun `mergeWithPreviousBlock com blocos separados por linha em branco reposiciona o cursor, não junta texto`() {
        // dois blocos de topo vindos de parse() nunca colam em um parágrafo só:
        // CommonMark exige linha em branco pra separá-los, e essa linha em
        // branco sobrevive à fusão. O que muda de fato é o foco, que volta pro
        // fim do bloco anterior — é o "backspace só engole a linha em branco"
        // que edição de texto real também faz.
        val s = EditorState("primeiro\n\nsegundo\n")
        val segundo = s.doc.blocks[1]
        s.mergeWithPreviousBlock(segundo.id)

        assertEquals(2, s.doc.blocks.size)
        assertEquals("primeiro", s.doc.blocks[0].source)
        assertEquals("segundo", s.doc.blocks[1].source)
        assertEquals(s.doc.blocks[0].id, s.focusedId)
        assertEquals(TextRange(8), s.editValue.selection) // fim de "primeiro"
    }

    @Test
    fun `mergeWithPreviousBlock no primeiro bloco não faz nada`() {
        val s = EditorState("um\n\ndois\n")
        val primeiro = s.doc.blocks[0]
        s.mergeWithPreviousBlock(primeiro.id)
        assertEquals(2, s.doc.blocks.size)
    }

    @Test
    fun `splitAt seguido de reparse detecta cerca aberta e absorve o resto`() {
        val s = EditorState("texto\n\noutro\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("```kotlin", TextRange(9)))
        s.splitAtCursor()

        // "```kotlin" vira uma cerca não fechada — o reparse dentro de
        // splitAtCursor deve engolir o bloco seguinte ("outro") junto
        assertTrue(s.doc.blocks.any { it.source.contains("outro") && it.type == BlockType.CODE_FENCE })
    }

    // ---- navegação ----

    @Test
    fun `focusPrevious pula pro bloco anterior com cursor no fim`() {
        val s = EditorState("um\n\ndois\n")
        val (primeiro, segundo) = s.doc.blocks
        s.focusPrevious(segundo.id)
        assertEquals(primeiro.id, s.focusedId)
        assertEquals(TextRange(primeiro.source.length), s.editValue.selection)
    }

    @Test
    fun `focusPrevious no primeiro bloco não faz nada`() {
        val s = EditorState("um\n\ndois\n")
        s.focusPrevious(s.doc.blocks[0].id)
        assertNull(s.focusedId)
    }

    @Test
    fun `focusNext pula pro bloco seguinte com cursor no início`() {
        val s = EditorState("um\n\ndois\n")
        val (primeiro, segundo) = s.doc.blocks
        s.focusNext(primeiro.id)
        assertEquals(segundo.id, s.focusedId)
        assertEquals(TextRange(0), s.editValue.selection)
    }

    @Test
    fun `focusNext no último bloco não faz nada`() {
        val s = EditorState("um\n\ndois\n")
        s.focusNext(s.doc.blocks[1].id)
        assertNull(s.focusedId)
    }

    // ---- undo/redo ----

    @Test
    fun `undo desfaz uma edição`() {
        val s = EditorState("texto\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto novo", TextRange(10)))
        s.undo()
        assertEquals("texto", s.doc.blocks[0].source)
    }

    @Test
    fun `redo refaz o que o undo desfez`() {
        val s = EditorState("texto\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto novo", TextRange(10)))
        s.undo()
        s.redo()
        assertEquals("texto novo", s.doc.blocks[0].source)
    }

    @Test
    fun `undo sem histórico não quebra`() {
        val s = EditorState("texto\n")
        s.undo()
        assertEquals("texto", s.doc.blocks[0].source)
    }

    @Test
    fun `undo desfaz um split inteiro, não caractere por caractere`() {
        val s = EditorState("um dois tres\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("um dois tres", TextRange(7)))
        s.splitAtCursor()
        assertEquals(2, s.doc.blocks.size)

        s.undo()
        assertEquals(1, s.doc.blocks.size)
        assertEquals("um dois tres", s.doc.blocks[0].source)
    }

    @Test
    fun `toggleTask muda só o source do bloco, mantém trailing e vira um undo`() {
        val s = EditorState("- [ ] a\n- [x] b\n\nfim\n")
        s.toggleTask(s.doc.blocks[0].id, 0)
        assertEquals("- [x] a\n- [x] b", s.doc.blocks[0].source)
        assertEquals("\n\n", s.doc.blocks[0].trailing)
        assertEquals("- [x] a\n- [x] b\n\nfim\n", s.text())
        s.undo()
        assertEquals("- [ ] a\n- [x] b\n\nfim\n", s.text())
    }

    @Test
    fun `toggleTask em item sem checkbox não muda nada nem grava undo`() {
        val s = EditorState("- a\n- [ ] b\n")
        s.toggleTask(s.doc.blocks[0].id, 0)
        assertEquals("- a\n- [ ] b\n", s.text())
    }

    // ---- backspaceAtCursor ----

    @Test
    fun `backspaceAtCursor em cascata transforma tarefa em lista, depois em parágrafo e depois funde`() {
        val s = EditorState("antes\n\n- [x] a\n")
        s.focus(s.doc.blocks[1])
        s.edit(TextFieldValue("- [x] a", TextRange(6)))

        assertTrue(s.backspaceAtCursor())
        assertEquals("- a", s.editValue.text)
        assertEquals(TextRange(2), s.editValue.selection)
        assertEquals(BlockType.UNORDERED_LIST, s.doc.blocks[1].type)

        assertTrue(s.backspaceAtCursor())
        assertEquals("a", s.editValue.text)
        assertEquals(TextRange(0), s.editValue.selection)
        assertEquals(BlockType.PARAGRAPH, s.doc.blocks[1].type)

        assertTrue(s.backspaceAtCursor())
        assertEquals("antes", s.doc.blocks[0].source)
        assertEquals(s.doc.blocks[0].id, s.focusedId)
        assertEquals(TextRange(5), s.editValue.selection)
    }

    @Test
    fun `backspaceAtCursor em heading vira parágrafo e heading vazio vira parágrafo vazio`() {
        val s = EditorState("### Título\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("### Título", TextRange(4)))
        assertTrue(s.backspaceAtCursor())
        assertEquals("Título", s.editValue.text)
        assertEquals(TextRange(0), s.editValue.selection)
        assertEquals(BlockType.PARAGRAPH, s.doc.blocks[0].type)
        assertEquals("Título", s.doc.blocks[0].source)

        val s2 = EditorState("## Título\n")
        s2.focus(s2.doc.blocks[0])
        s2.edit(TextFieldValue("## ", TextRange(3)))
        assertTrue(s2.backspaceAtCursor())
        assertEquals("", s2.editValue.text)
        assertEquals(TextRange(0), s2.editValue.selection)
        assertEquals(BlockType.PARAGRAPH, s2.doc.blocks[0].type)
        assertEquals(1, s2.doc.blocks.size)
    }

    @Test
    fun `backspaceAtCursor remove par auto-fechado vazio`() {
        val s = EditorState("texto () fim\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto () fim", TextRange(7)))
        assertTrue(s.backspaceAtCursor())
        assertEquals("texto  fim", s.editValue.text)
        assertEquals(TextRange(6), s.editValue.selection)
        assertEquals("texto  fim", s.doc.blocks[0].source)
    }

    @Test
    fun `backspaceAtCursor no offset 0 remove linha horizontal anterior preservando foco e cursor`() {
        val s = EditorState("antes\n\n---\n\ndepois\n")
        assertEquals(BlockType.HORIZONTAL_RULE, s.doc.blocks[1].type)
        val depoisId = s.doc.blocks[2].id
        s.focus(s.doc.blocks[2])
        s.edit(TextFieldValue("depois", TextRange(0)))

        assertTrue(s.backspaceAtCursor())
        assertEquals(listOf("antes", "depois"), s.doc.blocks.map { it.source })
        assertEquals(depoisId, s.focusedId)
        assertEquals(TextRange(0), s.editValue.selection)
        assertEquals("antes\n\ndepois\n", s.text())
    }

    @Test
    fun `backspaceAtCursor no offset 0 após linha horizontal mantém parágrafo vazio focado`() {
        val s = EditorState("---\n\nx\n")
        val alvoId = s.doc.blocks[1].id
        s.focus(s.doc.blocks[1])
        s.edit(TextFieldValue("", TextRange(0)))

        assertTrue(s.backspaceAtCursor())
        assertEquals(1, s.doc.blocks.size)
        assertEquals("", s.doc.blocks[0].source)
        assertEquals(alvoId, s.focusedId)
        assertEquals(TextRange(0), s.editValue.selection)
    }

    @Test
    fun `backspaceAtCursor no offset 0 remove bloco em branco anterior quando o atual não é vazio`() {
        val s = EditorState("um\n\nfim\n")
        s.focus(s.doc.blocks[0])
        s.splitAtCursor() // ["um", "", "fim"]
        val fim = s.doc.blocks[2]
        s.focus(fim)
        s.edit(TextFieldValue("fim", TextRange(0)))

        assertTrue(s.backspaceAtCursor())
        assertEquals(listOf("um", "fim"), s.doc.blocks.map { it.source })
        assertEquals(fim.id, s.focusedId)
        assertEquals(TextRange(0), s.editValue.selection)
        assertEquals("um\n\nfim\n", s.text())
    }

    @Test
    fun `undo desfaz etapas de backspaceAtCursor e remoção de bloco anterior`() {
        val s = EditorState("- [x] a\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("- [x] a", TextRange(6)))
        Thread.sleep(600)
        assertTrue(s.backspaceAtCursor())
        assertEquals("- a", s.doc.blocks[0].source)
        Thread.sleep(600)
        assertTrue(s.backspaceAtCursor())
        assertEquals("a", s.doc.blocks[0].source)
        assertEquals(BlockType.PARAGRAPH, s.doc.blocks[0].type)

        s.undo()
        assertEquals("- a", s.doc.blocks[0].source)
        assertEquals(BlockType.UNORDERED_LIST, s.doc.blocks[0].type)

        s.undo()
        assertEquals("- [x] a", s.doc.blocks[0].source)

        val hr = EditorState("antes\n\n---\n\ndepois\n")
        hr.focus(hr.doc.blocks[2])
        hr.edit(TextFieldValue("depois", TextRange(0)))
        Thread.sleep(600)
        assertTrue(hr.backspaceAtCursor())
        assertEquals("antes\n\ndepois\n", hr.text())
        hr.undo()
        assertEquals("antes\n\n---\n\ndepois\n", hr.text())
    }

    @Test
    fun `backspaceAtCursor preserva front matter e devolve false quando não há ação especial`() {
        assertFalse(EditorState("texto\n").backspaceAtCursor())

        val s = EditorState("- a\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("- a", TextRange(0, 2)))
        assertFalse(s.backspaceAtCursor())

        s.edit(TextFieldValue("texto", TextRange(3)))
        assertFalse(s.backspaceAtCursor())

        val fm = EditorState("---\n# nota\n---\n")
        fm.focus(fm.doc.blocks[0])
        fm.edit(TextFieldValue("---\n# nota\n---", TextRange(6)))
        assertTrue(fm.backspaceAtCursor())
        assertEquals(BlockType.FRONT_MATTER, fm.doc.blocks[0].type)
        assertEquals("---\nnota\n---", fm.doc.blocks[0].source)
    }

    // ---- tabAtCursor / SmartPair / deleteAtCursor / frontMatter / replaceAll / 5k ----

    @Test
    fun `tabAtCursor indenta e shift-tab desindenta ou remove marcador`() {
        val s = EditorState("- item\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("- item", TextRange(6)))

        assertTrue(s.tabAtCursor(shift = false))
        assertEquals("  - item", s.doc.blocks[0].source)
        assertEquals(TextRange(8), s.editValue.selection)

        assertTrue(s.tabAtCursor(shift = true))
        assertEquals("- item", s.doc.blocks[0].source)
        assertEquals(TextRange(6), s.editValue.selection)

        assertTrue(s.tabAtCursor(shift = true))
        assertEquals("item", s.doc.blocks[0].source)
        assertEquals(BlockType.PARAGRAPH, s.doc.blocks[0].type)
    }

    @Test
    fun `edit aplica auto-pair e surroundPair quando autoPairEnabled ativo`() {
        val s = EditorState("texto\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("texto ", TextRange(6)))
        s.edit(TextFieldValue("texto (", TextRange(7)))
        assertEquals("texto ()", s.editValue.text)
        assertEquals(TextRange(7), s.editValue.selection)

        // surroundPair com seleção ativa
        s.edit(TextFieldValue("um dois tres", TextRange(3, 7)))
        s.edit(TextFieldValue("um * tres", TextRange(4)))
        assertEquals("um *dois* tres", s.editValue.text)
        assertEquals(TextRange(4, 8), s.editValue.selection)
    }

    @Test
    fun `deleteAtCursor remove bloco vazio, HR ou funde com próximo bloco`() {
        val hr = EditorState("antes\n\n---\n\ndepois\n")
        hr.focus(hr.doc.blocks[0])
        hr.edit(TextFieldValue("antes", TextRange(5)))
        assertTrue(hr.deleteAtCursor())
        assertEquals(listOf("antes", "depois"), hr.doc.blocks.map { it.source })
        assertEquals("antes\n\ndepois\n", hr.text())

        val empty = EditorState("um\n\n\nfim\n")
        empty.focus(empty.doc.blocks[0])
        empty.splitAtCursor() // ["um", "", "fim"]
        assertEquals(3, empty.doc.blocks.size)
        assertTrue(empty.deleteAtCursor())
        assertEquals(listOf("um", "fim"), empty.doc.blocks.map { it.source })
    }

    @Test
    fun `splitAtCursor com traços na linha 1 cria front matter e Enter após linha vazia sai`() {
        val s = EditorState("---\n")
        s.focus(s.doc.blocks[0])
        s.edit(TextFieldValue("---", TextRange(3)))
        s.splitAtCursor()

        assertEquals(BlockType.FRONT_MATTER, s.doc.blocks[0].type)
        assertEquals("---\n\n---", s.doc.blocks[0].source)
        assertEquals(s.doc.blocks[0].id, s.focusedId)
        assertEquals(TextRange(4), s.editValue.selection)

        // Digita campo e cria linha vazia antes do fechamento, então Enter sai para parágrafo.
        s.edit(TextFieldValue("---\ntitle: ok\n\n---", TextRange(14)))
        assertTrue(s.exitFrontMatterOnEnter())
        assertEquals(BlockType.FRONT_MATTER, s.doc.blocks[0].type)
        assertEquals("---\ntitle: ok\n---", s.doc.blocks[0].source)
        assertEquals(s.doc.blocks[1].id, s.focusedId)
        assertEquals(BlockType.PARAGRAPH, s.doc.blocks[1].type)
    }

    @Test
    fun `splitAtCursor fecha e reabre span inline entre parágrafos`() {
        val s = EditorState("olá **mundo bonito** fim\n")
        s.focus(s.doc.blocks[0])
        // Cursor entre "mundo" e " bonito" -> índice 11 ("olá **mundo")
        s.edit(TextFieldValue("olá **mundo bonito** fim", TextRange(11)))
        s.splitAtCursor()

        assertEquals(2, s.doc.blocks.size)
        assertEquals("olá **mundo**", s.doc.blocks[0].source)
        assertEquals("** bonito** fim", s.doc.blocks[1].source)
        assertEquals(TextRange(2), s.editValue.selection)
    }

    @Test
    fun `replaceAll substitui na nota inteira com undo único e modo código-fonte alterna`() {
        val s = EditorState("um alfa\n\ndois ALFA\n")
        s.toggleSourceMode()
        assertTrue(s.isSourceMode)

        assertEquals(2, s.replaceAll("alfa", "beta"))
        assertEquals("um beta\n\ndois beta\n", s.text())

        s.undo()
        assertEquals("um alfa\n\ndois ALFA\n", s.text())
    }

    @Test
    fun `documento com 5000 linhas mantém parse, split rápido e roundtrip íntegro`() {
        val bigText = buildString {
            for (i in 1..2500) {
                append("Parágrafo número $i com texto comum.\n\n")
            }
        }
        val s = EditorState(bigText)
        assertEquals(2500, s.doc.blocks.size)
        assertEquals(bigText, s.text())

        val first = s.doc.blocks[0]
        s.focus(first)
        s.edit(TextFieldValue(first.source, TextRange(9)))
        s.splitAtCursor()
        assertEquals(2501, s.doc.blocks.size)
        assertEquals(bigText.length + 2, s.text().length)
    }

    @Test
    fun `edit via IME virtual aplica smartBackspace e smartEnter ao apagar ou inserir quebra`() {
        val s = EditorState("- [x] um\n")
        s.focus(s.doc.blocks[0])
        // IME insere "\n" via InputConnection.commitText em vez de KeyEvent.Enter
        s.edit(TextFieldValue("- [x] um\n", TextRange(9)))
        assertEquals("- [x] um\n- [ ] ", s.editValue.text)

        // IME envia deleteSurroundingText(1, 0) removendo espaço após "- [ ] " -> cascata
        val cur = s.editValue.text
        val rawBs1 = cur.dropLast(1)
        s.edit(TextFieldValue(rawBs1, TextRange(cur.length - 1)))
        assertEquals("- [x] um\n- ", s.editValue.text)

        // Eco imediato do mesmo batchEdit do IME (como SwiftKey após rewrite) é descartado
        s.edit(TextFieldValue(rawBs1, TextRange(cur.length - 1)))
        assertEquals("- [x] um\n- ", s.editValue.text)

        // Segundo deleteSurroundingText(1, 0) remove marcador "- " -> linha vazia
        val cur2 = s.editValue.text
        s.edit(TextFieldValue(cur2.dropLast(1), TextRange(cur2.length - 1)))
        assertEquals("- [x] um\n", s.editValue.text)
    }
}
