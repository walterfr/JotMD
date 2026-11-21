package io.github.walterfr.jotmd.editor.ops

import io.github.walterfr.jotmd.editor.TableAlignment
import io.github.walterfr.jotmd.editor.TableModel
import io.github.walterfr.jotmd.editor.tableOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableOpsTest {

    private val sample = """
        | Nome | Valor | Nota |
        | :--- | :---: | ---: |
        | Um | 10 | ok |
        | Dois | 20 | fim |
    """.trimIndent()

    @Test
    fun `formatTable respeita largura mínima 4, máxima 60 e gera separadores por alinhamento`() {
        val model = TableModel(
            headers = listOf("A", "B", "Valor", "D"),
            alignments = listOf(null, TableAlignment.LEFT, TableAlignment.CENTER, TableAlignment.RIGHT),
            rows = listOf(listOf("1", "2", "3", "4")),
        )
        val formatted = formatTable(model)
        assertEquals(
            "| A    | B    | Valor | D    |\n" +
                "| ---- | :--- | :---: | ---: |\n" +
                "| 1    | 2    | 3     | 4    |",
            formatted,
        )
        assertEquals(model, tableOf(formatted))

        val wide = "x".repeat(75)
        val clamped = formatTable(TableModel(listOf(wide), listOf(null), emptyList()))
        val sep = clamped.lines()[1]
        assertEquals("| ${"-".repeat(60)} |", sep)
        assertTrue(clamped.lines()[0].contains(wide))
    }

    @Test
    fun `addTableRow insere no fim ou após índice válido e rejeita inválidos`() {
        val atEnd = assertNotNull(addTableRow(sample))
        val parsedEnd = assertNotNull(tableOf(atEnd))
        assertEquals(3, parsedEnd.rows.size)
        assertEquals(listOf("", "", ""), parsedEnd.rows.last())

        val afterFirst = assertNotNull(addTableRow(sample, afterRowIndex = 0))
        val parsedAfter = assertNotNull(tableOf(afterFirst))
        assertEquals(listOf("Um", "10", "ok"), parsedAfter.rows[0])
        assertEquals(listOf("", "", ""), parsedAfter.rows[1])
        assertEquals(listOf("Dois", "20", "fim"), parsedAfter.rows[2])

        assertNull(addTableRow(sample, afterRowIndex = -1))
        assertNull(addTableRow(sample, afterRowIndex = 2))
        assertNull(addTableRow("não é tabela"))
    }

    @Test
    fun `removeTableRow remove última ou índice pedido e devolve null sem dados`() {
        val lastRemoved = assertNotNull(removeTableRow(sample))
        assertEquals(listOf(listOf("Um", "10", "ok")), tableOf(lastRemoved)?.rows)

        val firstRemoved = assertNotNull(removeTableRow(sample, rowIndex = 0))
        assertEquals(listOf(listOf("Dois", "20", "fim")), tableOf(firstRemoved)?.rows)

        val noData = "| A | B |\n| --- | --- |"
        assertNull(removeTableRow(noData))
        assertNull(removeTableRow(sample, rowIndex = 5))
    }

    @Test
    fun `addTableColumn e removeTableColumn respeitam teto de 40 colunas e piso de 1 coluna`() {
        val added = assertNotNull(addTableColumn(sample, afterColIndex = 0))
        val parsed = assertNotNull(tableOf(added))
        assertEquals(listOf("Nome", "Col", "Valor", "Nota"), parsed.headers)
        assertEquals(listOf("Um", "", "10", "ok"), parsed.rows[0])

        val headers40 = (1..40).map { "C$it" }
        val maxTable = formatTable(TableModel(headers40, List(40) { null }, emptyList()))
        assertNull(addTableColumn(maxTable))

        val removed = assertNotNull(removeTableColumn(sample, colIndex = 1))
        assertEquals(listOf("Nome", "Nota"), tableOf(removed)?.headers)

        val singleCol = "| Só |\n| ---- |\n| x |"
        assertNull(removeTableColumn(singleCol))
    }

    @Test
    fun `moveTableRow reordena linhas dentro da faixa e rejeita fora de faixa`() {
        val movedDown = assertNotNull(moveTableRow(sample, rowIndex = 0, delta = 1))
        assertEquals(
            listOf(listOf("Dois", "20", "fim"), listOf("Um", "10", "ok")),
            tableOf(movedDown)?.rows,
        )

        val movedUp = assertNotNull(moveTableRow(movedDown, rowIndex = 1, delta = -1))
        assertEquals(
            listOf(listOf("Um", "10", "ok"), listOf("Dois", "20", "fim")),
            tableOf(movedUp)?.rows,
        )

        assertNull(moveTableRow(sample, rowIndex = 0, delta = -1))
        assertNull(moveTableRow(sample, rowIndex = 1, delta = 1))
        assertNull(moveTableRow(sample, rowIndex = 0, delta = 0))
        assertNull(moveTableRow(sample, rowIndex = 4, delta = -1))
    }

    @Test
    fun `cycleTableColumnAlignment cicla null LEFT CENTER RIGHT null`() {
        val step1 = assertNotNull(cycleTableColumnAlignment("| A | B |\n| ---- | ---- |\n| 1 | 2 |", 0))
        assertEquals(TableAlignment.LEFT, tableOf(step1)?.alignments?.get(0))

        val step2 = assertNotNull(cycleTableColumnAlignment(step1, 0))
        assertEquals(TableAlignment.CENTER, tableOf(step2)?.alignments?.get(0))

        val step3 = assertNotNull(cycleTableColumnAlignment(step2, 0))
        assertEquals(TableAlignment.RIGHT, tableOf(step3)?.alignments?.get(0))

        val step4 = assertNotNull(cycleTableColumnAlignment(step3, 0))
        assertNull(tableOf(step4)?.alignments?.get(0))

        assertNull(cycleTableColumnAlignment(step1, 5))
    }

    @Test
    fun `newTableMarkdown gera tabela formatada com limites de colunas e linhas`() {
        val def = newTableMarkdown()
        val parsedDef = assertNotNull(tableOf(def))
        assertEquals(listOf("Col 1", "Col 2", "Col 3"), parsedDef.headers)
        assertEquals(3, parsedDef.rows.size)
        assertEquals(listOf("", "", ""), parsedDef.rows[0])

        val clampedLow = assertNotNull(tableOf(newTableMarkdown(cols = 0, dataRows = 0)))
        assertEquals(listOf("Col 1"), clampedLow.headers)
        assertEquals(1, clampedLow.rows.size)

        val clampedHigh = assertNotNull(tableOf(newTableMarkdown(cols = 99, dataRows = 200)))
        assertEquals(40, clampedHigh.headers.size)
        assertEquals(99, clampedHigh.rows.size)
    }

    @Test
    fun `moveTableColumn troca colunas e rejeita índices ou deltas inválidos`() {
        val movedRight = assertNotNull(moveTableColumn(sample, colIndex = 0, delta = 1))
        val parsedRight = assertNotNull(tableOf(movedRight))
        assertEquals(listOf("Valor", "Nome", "Nota"), parsedRight.headers)
        assertEquals(listOf(TableAlignment.CENTER, TableAlignment.LEFT, TableAlignment.RIGHT), parsedRight.alignments)
        assertEquals(listOf("10", "Um", "ok"), parsedRight.rows[0])
        assertEquals(listOf("20", "Dois", "fim"), parsedRight.rows[1])

        val movedLeft = assertNotNull(moveTableColumn(movedRight, colIndex = 2, delta = -1))
        val parsedLeft = assertNotNull(tableOf(movedLeft))
        assertEquals(listOf("Valor", "Nota", "Nome"), parsedLeft.headers)

        assertNull(moveTableColumn(sample, colIndex = 0, delta = -1))
        assertNull(moveTableColumn(sample, colIndex = 2, delta = 1))
        assertNull(moveTableColumn(sample, colIndex = 1, delta = 0))
        assertNull(moveTableColumn(sample, colIndex = 5, delta = -1))
        assertNull(moveTableColumn("não é tabela", colIndex = 0, delta = 1))
    }
}
