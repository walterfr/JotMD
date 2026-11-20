package io.github.walterfr.jotmd.core

/** Tipos de alerta GitHub suportados em citações (`> [!NOTE]`, etc.). */
enum class AlertKind(val tag: String, val label: String) {
    NOTE("note", "Note"),
    TIP("tip", "Tip"),
    IMPORTANT("important", "Important"),
    WARNING("warning", "Warning"),
    CAUTION("caution", "Caution"),
}

/** Alerta GitHub extraído de uma citação: tipo e corpo Markdown já sem o nível `>`. */
data class AlertInfo(
    val kind: AlertKind,
    val body: String,
)

private val ALERT_HEADER = Regex(
    """^\s*>\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*(.*)$""",
    RegexOption.IGNORE_CASE,
)
private val QUOTE_PREFIX = Regex("""^\s*>\s?""")

/**
 * Extrai [AlertInfo] de [source] caso a 1ª linha comece com `> [!KIND]`, ou
 * devolve `null` para citações comuns.
 */
fun alertOf(source: String): AlertInfo? {
    val lines = source.lines()
    val first = lines.firstOrNull() ?: return null
    val match = ALERT_HEADER.matchEntire(first) ?: return null
    val rawKind = match.groupValues[1]
    val kind = AlertKind.entries.first { it.name.equals(rawKind, ignoreCase = true) }
    val firstRest = match.groupValues[2]

    val bodyLines = buildList {
        if (firstRest.isNotEmpty()) add(firstRest)
        for (line in lines.drop(1)) {
            add(line.replaceFirst(QUOTE_PREFIX, ""))
        }
    }
    return AlertInfo(kind, bodyLines.joinToString("\n").trim('\n'))
}
