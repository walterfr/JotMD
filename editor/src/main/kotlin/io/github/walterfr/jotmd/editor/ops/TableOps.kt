package io.github.walterfr.jotmd.editor.ops

import io.github.walterfr.jotmd.editor.TableAlignment
import io.github.walterfr.jotmd.editor.TableModel
import io.github.walterfr.jotmd.editor.tableOf

private const val MIN_COLUMN_WIDTH = 4
private const val MAX_COLUMN_WIDTH = 60
private const val MAX_TABLE_COLUMNS = 40

/**
 * Formata [model] em tabela GFM com largura de coluna limitada a 4..60 caracteres
 * (igual ao `toMark` do `Mod 12` do Typora).
 */
fun formatTable(model: TableModel): String {
    if (model.headers.isEmpty()) return ""
    val widths = model.headers.indices.map { c ->
        val maxCell = maxOf(
            model.headers[c].length,
            model.rows.maxOfOrNull { it.getOrElse(c) { "" }.length } ?: 0,
        )
        maxCell.coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH)
    }

    fun formatRow(cells: List<String>): String =
        model.headers.indices.joinToString(" | ", prefix = "| ", postfix = " |") { c ->
            cells.getOrElse(c) { "" }.padEnd(widths[c])
        }

    val separator = model.headers.indices.joinToString(" | ", prefix = "| ", postfix = " |") { c ->
        val w = widths[c]
        when (model.alignments.getOrNull(c)) {
            TableAlignment.LEFT -> ":" + "-".repeat(w - 1)
            TableAlignment.CENTER -> ":" + "-".repeat(w - 2) + ":"
            TableAlignment.RIGHT -> "-".repeat(w - 1) + ":"
            null -> "-".repeat(w)
        }
    }

    return buildList(2 + model.rows.size) {
        add(formatRow(model.headers))
        add(separator)
        for (row in model.rows) add(formatRow(row))
    }.joinToString("\n")
}

/**
 * Insere uma linha vazia no fim (quando [afterRowIndex] é null) ou logo após
 * [afterRowIndex], devolvendo `null` se a tabela ou o índice forem inválidos.
 */
fun addTableRow(source: String, afterRowIndex: Int? = null): String? {
    val table = tableOf(source) ?: return null
    val insertAt = when {
        afterRowIndex == null -> table.rows.size
        afterRowIndex in table.rows.indices -> afterRowIndex + 1
        else -> return null
    }
    val emptyRow = List(table.headers.size) { "" }
    val updatedRows = table.rows.toMutableList().apply { add(insertAt, emptyRow) }
    return formatTable(table.copy(rows = updatedRows))
}

/** Remove a linha [rowIndex] ou a última linha de dados; `null` se não houver linhas de dados. */
fun removeTableRow(source: String, rowIndex: Int? = null): String? {
    val table = tableOf(source) ?: return null
    if (table.rows.isEmpty()) return null
    val target = rowIndex ?: table.rows.lastIndex
    if (target !in table.rows.indices) return null
    val updatedRows = table.rows.toMutableList().apply { removeAt(target) }
    return formatTable(table.copy(rows = updatedRows))
}

/** Adiciona coluna `"Col"` vazia respeitando o teto de 40 colunas do `TableEdit` do Typora. */
fun addTableColumn(source: String, afterColIndex: Int? = null): String? {
    val table = tableOf(source) ?: return null
    if (table.headers.size >= MAX_TABLE_COLUMNS) return null
    val insertAt = when {
        afterColIndex == null -> table.headers.size
        afterColIndex in table.headers.indices -> afterColIndex + 1
        else -> return null
    }
    val headers = table.headers.toMutableList().apply { add(insertAt, "Col") }
    val alignments = normalizedAlignments(table).apply { add(insertAt, null) }
    val rows = table.rows.map { row ->
        normalizedRow(row, table.headers.size).apply { add(insertAt, "") }
    }
    return formatTable(TableModel(headers, alignments, rows))
}

/** Remove a coluna [colIndex] ou a última; `null` se restar apenas 1 coluna. */
fun removeTableColumn(source: String, colIndex: Int? = null): String? {
    val table = tableOf(source) ?: return null
    if (table.headers.size <= 1) return null
    val target = colIndex ?: table.headers.lastIndex
    if (target !in table.headers.indices) return null
    val headers = table.headers.toMutableList().apply { removeAt(target) }
    val alignments = normalizedAlignments(table).apply { removeAt(target) }
    val rows = table.rows.map { row ->
        normalizedRow(row, table.headers.size).apply { removeAt(target) }
    }
    return formatTable(TableModel(headers, alignments, rows))
}

/** Move a linha de dados [rowIndex] para `rowIndex + delta`, ou `null` se fora de faixa. */
fun moveTableRow(source: String, rowIndex: Int, delta: Int): String? {
    val table = tableOf(source) ?: return null
    if (delta == 0 || rowIndex !in table.rows.indices) return null
    val target = rowIndex + delta
    if (target !in table.rows.indices) return null
    val rows = table.rows.toMutableList().apply {
        val moved = removeAt(rowIndex)
        add(target, moved)
    }
    return formatTable(table.copy(rows = rows))
}

/** Cicla o alinhamento da coluna [colIndex]: `null -> LEFT -> CENTER -> RIGHT -> null`. */
fun cycleTableColumnAlignment(source: String, colIndex: Int): String? {
    val table = tableOf(source) ?: return null
    if (colIndex !in table.headers.indices) return null
    val next = when (table.alignments.getOrNull(colIndex)) {
        null -> TableAlignment.LEFT
        TableAlignment.LEFT -> TableAlignment.CENTER
        TableAlignment.CENTER -> TableAlignment.RIGHT
        TableAlignment.RIGHT -> null
    }
    val alignments = normalizedAlignments(table).apply { set(colIndex, next) }
    return formatTable(table.copy(alignments = alignments))
}

private fun normalizedAlignments(table: TableModel): MutableList<TableAlignment?> =
    MutableList(table.headers.size) { table.alignments.getOrNull(it) }

private fun normalizedRow(row: List<String>, colCount: Int): MutableList<String> =
    MutableList(colCount) { row.getOrElse(it) { "" } }
