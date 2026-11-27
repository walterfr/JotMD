package io.github.walterfr.jotmd

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.DocumentsContract
import io.github.walterfr.jotmd.core.docIndex
import io.github.walterfr.jotmd.core.tagsOf
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Consulta FTS a partir do que o usuário digitou: cada palavra vira prefixo
 * entre aspas (`"plano"*`), todas obrigatórias. Aspas, `OR`, `NEAR`,
 * parênteses e `*` digitados nunca chegam crus ao `MATCH` — ou quebrariam a
 * consulta (exceção do SQLite) ou mudariam o sentido dela.
 * Null quando não sobra nenhuma palavra.
 */
fun ftsQuery(input: String): String? {
    val words = input.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    return words.joinToString(" ") { "\"$it\"*" }
}

data class SearchHit(val docId: String, val name: String, val snippet: String)

// Marcadores do termo encontrado no snippet(). Caracteres de controle, não
// `[`/`]`: colchetes aparecem no próprio markdown (links) e se confundiriam.
private const val MARK_START = '\u0002'
private const val MARK_END = '\u0003'

/**
 * Trecho pronto para exibir: espaços e quebras de linha viram um espaço só
 * (senão o termo cai fora das linhas visíveis) e os marcadores somem, virando
 * os intervalos a destacar.
 */
fun snippetMarks(raw: String): Pair<String, List<IntRange>> {
    val flat = raw.replace(Regex("\\s+"), " ").trim()
    val text = StringBuilder()
    val marks = ArrayList<IntRange>()
    var start = -1
    for (ch in flat) {
        when (ch) {
            MARK_START -> start = text.length
            MARK_END -> if (start >= 0) { marks += start until text.length; start = -1 }
            else -> text.append(ch)
        }
    }
    return text.toString() to marks
}

/**
 * Calcula pontuação de relevância a partir do blob binário de `matchinfo(docs, 'pcx')`
 * (inteiros little-endian de 32 bits: `phrases`, `cols`, seguidos de `phrases * cols * 3`
 * contendo `hitsThisRow`, `hitsAllRows`, `docsWithPhrase` por frase/coluna).
 * Pondera título (`name`, col 1), corpo (`body`, col 2) e tags (`tags`, col 3),
 * favorecendo termos mais raros e múltiplas ocorrências. Retorna `0.0` para blobs
 * nulos, curtos ou malformados.
 */
fun ftsMatchInfoScore(
    blob: ByteArray?,
    nameWeight: Double = 3.0,
    bodyWeight: Double = 1.0,
    tagsWeight: Double = 2.0,
): Double {
    if (blob == null || blob.size < 8 || blob.size % 4 != 0) return 0.0
    val buf = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN)
    val phrases = buf.int.toLong() and 0xFFFF_FFFFL
    val cols = buf.int.toLong() and 0xFFFF_FFFFL
    if (phrases <= 0L || cols <= 0L) return 0.0
    if (phrases > 10_000L || cols > 1_000L) return 0.0
    val expectedBytes = (2L + phrases * cols * 3L) * 4L
    if (blob.size.toLong() != expectedBytes) return 0.0

    var score = 0.0
    var matchedInName = 0L
    for (p in 0L until phrases) {
        var nameHitsForPhrase = 0L
        for (c in 0 until cols.toInt()) {
            val hitsThisRow = (buf.int.toLong() and 0xFFFF_FFFFL).toDouble()
            buf.int // hitsAllRows
            val docsWithPhrase = (buf.int.toLong() and 0xFFFF_FFFFL).toDouble()
            val weight = when (c) {
                1 -> nameWeight
                2 -> bodyWeight
                3 -> tagsWeight
                else -> 0.0
            }
            if (hitsThisRow > 0.0 && weight > 0.0) {
                score += hitsThisRow * weight / (1.0 + docsWithPhrase * 0.1)
                if (c == 1) nameHitsForPhrase += hitsThisRow.toLong()
            }
        }
        if (nameHitsForPhrase > 0L) matchedInName++
    }
    if (matchedInName == phrases) {
        score += 0.25 * nameWeight
    }
    return score
}

/**
 * Índice de busca da pasta raiz, em FTS4 do SQLite nativo do Android.
 *
 * Não é Room, apesar do `AI_CONTEXT.md`: Room traria KSP e um acoplamento de
 * versões com o Kotlin só para uma tabela virtual e três queries.
 *
 * Tokenizer `unicode61`: tira acento e caixa na indexação E na consulta, então
 * "acao" acha "Ação". Se o SQLite do aparelho não tiver unicode61, cai no
 * tokenizer padrão — a busca continua funcionando, só passa a diferenciar acento.
 */
class SearchIndex(context: Context) : SQLiteOpenHelper(context, "search.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        val columns = "doc_id, name, body, tags, notindexed=doc_id"
        try {
            db.execSQL("CREATE VIRTUAL TABLE docs USING fts4($columns, tokenize=unicode61)")
        } catch (e: SQLiteException) {
            db.execSQL("CREATE VIRTUAL TABLE docs USING fts4($columns)")
        }
        // Data de modificação vista na última indexação: só relê o que mudou.
        db.execSQL("CREATE TABLE seen (doc_id TEXT PRIMARY KEY, modified INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /**
     * Sincroniza o índice com [nodes] (a listagem atual da raiz): relê só os
     * arquivos cuja data de modificação mudou e remove os que sumiram.
     * Bloqueante: chamar em Dispatchers.IO.
     */
    fun reindex(resolver: ContentResolver, treeUri: Uri, nodes: List<DocNode>) {
        val files = nodes.filter { !it.isDir }
        val db = writableDatabase
        val seen = HashMap<String, Long>()
        db.rawQuery("SELECT doc_id, modified FROM seen", null).use { c ->
            while (c.moveToNext()) seen[c.getString(0)] = c.getLong(1)
        }
        val changed = files.filter { seen[it.docId] != it.lastModified || it.lastModified == 0L }
        val gone = seen.keys - files.map { it.docId }.toSet()

        // Lê fora da transação: E/S de provider SAF pode ser lenta (nuvem) e não
        // deve segurar o banco travado.
        val texts = changed.mapNotNull { node ->
            val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, node.docId)
            runCatching {
                resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { node to it }
        }

        db.beginTransaction()
        try {
            for (id in gone + texts.map { it.first.docId }) {
                db.delete("docs", "doc_id = ?", arrayOf(id))
                db.delete("seen", "doc_id = ?", arrayOf(id))
            }
            for ((node, text) in texts) {
                db.insert("docs", null, ContentValues().apply {
                    put("doc_id", node.docId)
                    put("name", node.name)
                    put("body", text)
                    put("tags", tagsOf(docIndex(text)).joinToString(" "))
                })
                db.insert("seen", null, ContentValues().apply {
                    put("doc_id", node.docId)
                    put("modified", node.lastModified)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Arquivos que casam com [input], com trecho do corpo marcado (ver [snippetMarks]),
     * ranqueados via `matchinfo(docs, 'pcx')` (peso maior para título e tags) e
     * desempate alfabético por nome. Cai na consulta simples se o SQLite do
     * aparelho não suportar `matchinfo`.
     */
    fun search(input: String): List<SearchHit> {
        val query = ftsQuery(input) ?: return emptyList()
        val rankedSql = "SELECT doc_id, name, snippet(docs, '$MARK_START', '$MARK_END', '…', 2, 12), " +
            "matchinfo(docs, 'pcx') FROM docs WHERE docs MATCH ? LIMIT 100"
        return try {
            val scored = readableDatabase.rawQuery(rankedSql, arrayOf(query)).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            SearchHit(c.getString(0), c.getString(1), c.getString(2)) to
                                ftsMatchInfoScore(c.getBlob(3)),
                        )
                    }
                }
            }
            scored
                .sortedWith(
                    compareByDescending<Pair<SearchHit, Double>> { it.second }
                        .thenBy { it.first.name.lowercase() },
                )
                .take(50)
                .map { it.first }
        } catch (_: SQLiteException) {
            val fallbackSql = "SELECT doc_id, name, snippet(docs, '$MARK_START', '$MARK_END', '…', 2, 12) " +
                "FROM docs WHERE docs MATCH ? LIMIT 50"
            readableDatabase.rawQuery(fallbackSql, arrayOf(query)).use { c ->
                buildList { while (c.moveToNext()) add(SearchHit(c.getString(0), c.getString(1), c.getString(2))) }
            }
        }
    }
}
