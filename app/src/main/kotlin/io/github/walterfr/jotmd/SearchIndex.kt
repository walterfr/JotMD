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
     * Arquivos que casam com [input], com trecho do corpo marcado (ver [snippetMarks]).
     *
     * ponytail: sem ranking — FTS4 não tem bm25 embutido, a ordem é a do índice.
     * Upgrade: ordenar por `matchinfo()` se a lista crescer a ponto de importar.
     */
    fun search(input: String): List<SearchHit> {
        val query = ftsQuery(input) ?: return emptyList()
        val sql = "SELECT doc_id, name, snippet(docs, '$MARK_START', '$MARK_END', '…', 2, 12) " +
            "FROM docs WHERE docs MATCH ? LIMIT 50"
        return readableDatabase.rawQuery(sql, arrayOf(query)).use { c ->
            buildList { while (c.moveToNext()) add(SearchHit(c.getString(0), c.getString(1), c.getString(2))) }
        }
    }
}
