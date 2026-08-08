# AI_CONTEXT — Markdown Editor Android

**Projeto:** Leitor e editor de Markdown nativo para Android, com UI/UX inspirada em Typora.  
**Criação:** agosto 2026  
**Motor:** Híbrido por blocos em Jetpack Compose  
**Alvo:** Publicação F-Droid (como Jotdown)

---

## Decisões Fundamentais

### Motor de Edição (Escolha C)
- **Modelo:** `LazyColumn` de blocos; bloco em foco = `BasicTextField` com marcadores visíveis; demais blocos renderizados formatados
- **Fonte da verdade:** string Markdown, nunca AST
- **Efeito:** Typora inline pseudo-WYSIWYG, 100% nativo Compose
- **Razão:** IME/teclado Android melhor que WebView; performance em docs longos; simplicidade de sincronização

### Recusa Temporária
- **Conversor estilo markitdown** (doc, docx, PDF, EPUB) — fica para Fase 5, após `:editor` funcional

---

## Stack Técnico

| Camada | Tecnologia | Função |
|--------|-----------|--------|
| **Parser** | `jetbrains/markdown` | AST incremental, fácil mapear para Compose |
| **Editor** | Jetpack Compose + `BasicTextField` | Motor híbrido de blocos |
| **Arquivos** | SAF / DocumentFile | Sem `MANAGE_EXTERNAL_STORAGE`; conformidade F-Droid |
| **Índice** | Room + FTS4 | Busca full-text, prefs em DataStore |
| **Syntax highlight** | Prism4j ou highlights-kt | No source mode |
| **Math** | KaTeX | Avaliar por último (risco) |
| **Export** | AST → HTML direto; `PrintedPdfDocument` | PDF |
| **VCS** | Git, GitHub (walterfr/markdown-android) | Público desde o início |

---

## Arquitetura de Módulos

```
markdown-android/
├── core-md/              # Motor de parsing e serialização (Kotlin puro, zero Android)
│   ├── Block.kt
│   ├── DocState.kt
│   ├── MdParser.kt
│   ├── MdSerializer.kt
│   └── test/             # JVM unit tests — invariantes de roundtrip
│
├── editor/               # Motor Compose, blocos, navegação, atalhos
│   ├── compose/
│   │   ├── BlockEditor.kt
│   │   ├── BlockRenderer.kt
│   │   └── EditorState.kt
│   ├── ops/              # Operações puras (split, merge, reparse)
│   │   ├── BlockOps.kt
│   │   └── UndoStack.kt
│   └── ui/
│       ├── EditorScreen.kt
│       └── Toolbar.kt
│
├── app/                  # Shell: navegação, temas, SAF, prefs
│   ├── ui/
│   │   ├── HomeScreen.kt
│   │   ├── FilePickerScreen.kt
│   │   └── SettingsScreen.kt
│   ├── data/
│   │   ├── FileRepository.kt
│   │   └── PreferenceManager.kt
│   ├── MainActivity.kt
│   └── AndroidManifest.xml
│
└── build.gradle.kts      # Agregador, com flavors
```

**Isolamento crítico:** `:core-md` é **100% testável em JVM**, sem dependências Android. Testes de `splitAt()`, `mergeWithPrevious()`, `reparse()`, `serialize()` rodam localmente em segundos, não no emulador.

---

## Modelo de Dados

```kotlin
@JvmInline value class BlockId(val v: Long)

data class Block(
    val id: BlockId,                 // estável, rehashing por posição
    val source: String,              // markdown cru, sem separadores
    val trailing: String,            // "\n\n", "\n" — preserva espaçamento
    val node: MdNode? = null,       // AST em cache; invalidada ao editar
    val isLazy: Boolean = false      // true = renderizar sem re-parsear até foco
)

data class DocState(
    val blocks: List<Block>,
    val focused: BlockId?,
    val selection: IntRange? = null // no bloco focado
)

// Undo stack — snapshots do documento
data class UndoFrame(
    val blocks: List<Block>,
    val focusedId: BlockId?,
    val timestamp: Long
)
```

**Granularidade:** cada item de lista é um bloco próprio (com nível de indentação e marcador nos metadados), não a lista inteira. Assim listas longas não travam o editor.

**IDs estáveis:** `LazyColumn(key = block.id)` depende que o ID permaneça quando re-parseamos. Reaproveite o ID da mesma posição — senão o foco perde a cada tecla.

---

## Operações Puras (Testáveis em JVM)

Quatro funções que sustentam tudo, **zero side-effects**, assinatura clara:

```kotlin
// core-md/ops/BlockOps.kt

fun splitAt(block: Block, offset: Int): Pair<Block, Block>
// Divide um bloco no offset, retorna (antes, depois) com novos IDs

fun mergeWithPrevious(blocks: List<Block>, targetId: BlockId): List<Block>
// Backspace no offset 0: funde com o anterior, retorna nova lista

fun reparse(blocks: List<Block>, fromIndex: Int): List<Block>
// Re-parseate a partir de fromIndex até estabilizar
// (mudança de tipo de blocos subsequentes denuncia necessidade de reparsear + longe)

fun serialize(blocks: List<Block>): String
// Documento completo, byte a byte compatível com `parse(serialize(doc)) == doc`

// Invariante crítica para teste:
// serialize(parse(texto)) == texto (identidade)
// Quebra isso = corrupção de arquivo silenciosa
```

---

## UI/UX — Do Typora ao Android

### Estado: Bloco sem foco (renderizado)
```
┌─────────────────────┐
│ # Título Grande     │  ← fonte grande, sem caixa de edição
│ Parágrafo normal    │  ← renderizado, azul de link ativo
│ > Citação em cor    │  ← fundo ligeiro, itálico
└─────────────────────┘
```

### Estado: Bloco com foco (edição)
```
┌─────────────────────┐
│ ┆ # Título Grande ┆ │  ← marcadores `, visível
│ ┆ Parágrafo normal  │
│ ┆ > Citação        │
└─────────────────────┘
    ↑ cursor aqui
```

### Atalhos de teclado
- `↑` / `↓` nas bordas → pula para bloco anterior/próximo
- `Ctrl+Z` / `Ctrl+Shift+Z` → undo/redo (coalescência por pausa ~500ms)
- `Ctrl+B` / `Ctrl+I` → envolvimento de seleção
- `Ctrl+K` → diálogo de link
- `Ctrl+L` → nova lista
- `Ctrl+H` → heading (prompt de nível)

### Toolbar (embaixo)
```
[ # ][ ** ][ __ ][ [] ][ {} ][ > ][ ``` ]
 H   Bold   Italic  Link Código Cite CodeBlk
```

---

## Roadmap

### F0 — Fundação (2 semanas)
- [ ] Estrutura Gradle, 3 módulos
- [ ] `Block`, `DocState`, modelos de dados
- [ ] Parser básico (jetbrains/markdown)
- [ ] `serialize()` com testes JVM (invariante de roundtrip)
- [ ] SAF file picker + open/save

### F1 — Leitor (1 semana)
- [ ] `BlockRenderer` (read-only, sem edição)
- [ ] Renderização de heading, parágrafo, lista, citação, código
- [ ] Typora-like styling (tipografia, cores)

### F2 — Source mode (1 semana)
- [ ] `BasicTextField` em cada bloco
- [ ] Syntax highlight (Prism4j)
- [ ] Barra de ferramentas básica

### F3 — Motor híbrido (2 semanas) ⭐ O coração
- [ ] `splitAt()`, `mergeWithPrevious()`, `reparse()`
- [ ] Undo stack com coalescência
- [ ] Navegação com ↑/↓ entre blocos
- [ ] Foco e cursor sincronizados
- [ ] Testes JVM completos das 4 operações

### F4 — Elementos avançados (2 semanas)
- [ ] Tabelas (parsing GFM, renderização simples)
- [ ] Imagens inline (SAF picker, preview)
- [ ] Checkboxes (listas de tarefa)
- [ ] Code fences com linguagem

### F5 — Workspace e descoberta (2 semanas)
- [ ] Pasta raiz, navegação de arquivos
- [ ] Busca full-text (FTS4)
- [ ] `[[wikilinks]]` (parsing, navegação)
- [ ] `#tags` (coleta, filtro)
- [ ] YAML front matter (exibição em sidebar)

### F6 — Polish (1 semana)
- [ ] Temas Typora-like (claro/escuro)
- [ ] Export para HTML e PDF
- [ ] Performance em documentos 5k+ linhas
- [ ] Publicação F-Droid

---

## Armadilhas Conhecidas

1. **Vazamento de contexto no re-parse**
   - Um backtick ` ``` ` digitado no meio pode absorver blocos seguintes
   - Solução: `reparse()` continua enquanto `node.type` dos blocos seguintes mudar

2. **Undo/Redo com TextFieldState**
   - O `TextFieldState` nativo não é suficiente
   - Mantenha `UndoStack<UndoFrame>` no nível do documento
   - Coalescência: agrupe edições dentro de ~500ms como um único frame

3. **LazyColumn em documentos longos**
   - Recomposição inteira causa lag visível em 5k+ blocos
   - Use `key = block.id` e `rememberLazyListState()` para preservar scroll

4. **IME em texto estilizado**
   - `BasicTextField` + `AnnotatedString` é instável
   - Mantenha o source em `String` pura, renderize via `AnnotatedString` só na leitura

5. **Tentação de começar pela Fase 3**
   - O motor é o trabalho mais duro; não comece por ele
   - F0 → F1 → F2 primeiro. Assim F3 tem testes claros.

---

## Referências de Design

- **Typora** — UI inline WYSIWYG, tipografia elegante, sem ruído
- **MarkText** (marktext/marktext) — estrutura em `packages/`, isolamento do motor
- **Zettlr** — workspace, busca, wikilinks (roadmap futuro, F5+)
- **Jotdown** (seu projeto) — stack Compose, Room, SAF, publicação F-Droid

---

## Invariante Crítica

```kotlin
// Em TODA a suite JVM de testes:
for (doc in testDocs) {
    val serialized = serialize(parse(doc))
    assertEquals(doc, serialized, 
        "Roundtrip falhou: $doc")
}
```

Quebra isso = arquivo do usuário silenciosamente corrompido. **Nunca, nunca deixe um commit sem isso passar.**

---

## Checklist de Partida (Android Studio)

- [ ] Criar novo projeto Kotlin, Compose, API 26+ (seu tablet é 12", suporta)
- [ ] `settings.gradle.kts`: incluir `:core-md`, `:editor`, `:app`
- [ ] `core-md/build.gradle.kts`: `kotlin { jvm() }` + `jetbrains/markdown`
- [ ] `editor/build.gradle.kts`: `compose`, `BasicTextField`, Room
- [ ] `app/build.gradle.kts`: agregador, SAF, DataStore
- [ ] Criar `core-md/src/test/kotlin/` com primeiros testes de roundtrip
- [ ] GitHub repo público desde o início (como Jotdown)

---

## Contato com Anteriores

Se precisar revisar arquitetura em breve:
- Veja `/topics/dev-workflow.md` para padrão de handoff com `.md`
- `/areas/jotdown.md` tem stack análogo (Compose, Room, SAF, F-Droid)
- Este arquivo é a fonte única de verdade para decisões de design
