# AGENTS.md — instruções para agentes de código

Vale para qualquer agente (Claude Code, Antigravity, Copilot, Codex…). Leia
inteiro antes de mexer no código.

## O projeto

JotMD: leitor e editor de Markdown nativo para Android, clone de UI/UX do
Typora. 100% Jetpack Compose, sem WebView. Alvo: F-Droid. Repo público:
https://github.com/walterfr/JotMD. Idioma do projeto: português (código em
inglês, comentários, docs e commits em pt-BR).

Ordem de leitura:

1. **Este arquivo** — estado atual, regras, armadilhas.
2. `AI_CONTEXT.md` — decisões de arquitetura e roadmap original. As
   checkboxes do roadmap ali estão desatualizadas; o estado real está abaixo.
3. `DESIGN.md` — tokens visuais e comportamento medidos no Typora real.
4. `docs/typora-ref.md` — documento de referência e corpus de teste.

## Decisões travadas (não re-litigar sem pedido explícito do dono)

- **Motor "Escolha C":** `LazyColumn` de blocos. O bloco focado vira
  `BasicTextField` com o markdown cru; o resto é renderizado. Nunca WebView.
- **A fonte da verdade é a string Markdown**, nunca o AST.
- **Invariante inegociável:** `serialize(parse(texto)) == texto`, byte a byte.
  `RoundtripTest` guarda isso. Commit que quebra = arquivo do usuário
  corrompido em silêncio.
- **Armazenamento só por SAF** (`OpenDocument`, `OpenDocumentTree`,
  permissões persistidas). Nunca `MANAGE_EXTERNAL_STORAGE` (regra do F-Droid).
- **Foco por bloco, não por span:** o bloco focado expõe a sintaxe inteira.
  Diferença deliberada do Typora, ver `DESIGN.md` "Modelo de foco".
- **Busca:** FTS4 no SQLite nativo (`SQLiteOpenHelper`), não Room.
  Diverge do `AI_CONTEXT.md`, de propósito: uma tabela não justifica a
  dependência.
- **Typora é proprietário.** Comportamento é observado em caixa-preta
  (digitar, salvar, ler o `.md`) e CSS/temas não protegidos. Nunca
  decompilar, descriptografar ou copiar código do Typora.

## Stack

Kotlin 2.1.21, AGP 8.7.3, Gradle 8.10.2, JDK 17, compileSdk/targetSdk 35,
minSdk 26, Compose BOM 2025.06.01, `org.jetbrains:markdown` 0.7.3. Versões em
`gradle/libs.versions.toml`.

```bash
./gradlew test                 # testes JVM de todos os módulos
./gradlew :app:installDebug    # instala no aparelho conectado via adb
./gradlew test assembleDebug   # o que tem que passar antes de commit
```

## Módulos

| Módulo | Conteúdo |
|---|---|
| `:core-md` | Kotlin puro, zero Android. `Block`/`DocState`, `MdParser` (`parse`), `MdSerializer` (`serialize`), `FrontMatter`, `Wikilinks`, `Tags` (`tagSpansIn`), `DocIndex`, `Scan`. |
| `:editor` | Compose. `EditorState` (estado e operações), `BlockEditor` (foco + teclas), `BlockRenderer`/`BlockParts`/`InlineText` (renderização), `Toolbar`, `ImagePreview`, `MdTheme` (tokens). Operações puras em `editor/ops/`: `BlockOps` (`splitAt`, `mergeWithPrevious`, `reparse`), `UndoStack`, `TaskOps`, `SmartEnter`. |
| `:app` | Shell. `MainActivity` (gaveta, busca, links), `FileSession` (abrir/gravar SAF, autosave), `FileBrowser` (árvore da pasta raiz, `resolveWikilink`), `SearchIndex` (FTS4). |

## Estado (2026-11-20)

Tag `v0.1.0` publicada. Feito e verificado em tablet:

- F0–F3: parser/serializer com roundtrip, renderização estilo Typora, edição
  por bloco, Enter divide, Backspace no início funde, ↑/↓ entre blocos,
  undo/redo com coalescência (~500 ms).
- F4: tabelas GFM alinhadas, checkboxes clicáveis, preview de imagens SAF,
  cercas de código com linguagem.
- Autosave: debounce de 1 s + flush no `ON_STOP`; título do arquivo vem do
  `DISPLAY_NAME`.
- F5: pasta raiz com navegação; busca full-text FTS4 (tokenizer `unicode61`,
  acentos dobrados, "acao" acha "ação"; reindexação incremental por
  `COLUMN_LAST_MODIFIED`; trecho com termo em negrito); `[[wikilinks]]`
  renderizados sem colchetes e clicáveis (abrem a nota; ausente mostra
  "Nota não encontrada"); `#tags` clicáveis (abrem a busca pela tag).
- Enter inteligente (`ops/SmartEnter.kt`): continua lista, numerada, tarefa
  e citação; item vazio sai da estrutura. Igual ao que o Typora grava.

Pendente:

- F5: exibir YAML front matter (função pura existe em `:core-md`, sem UI).
- Backspace no início de item/heading no estilo Typora: ainda não observado.
- F6: tema escuro, export HTML/PDF, desempenho em 5k+ linhas, publicação
  F-Droid.
- `README.md` ainda descreve o estado da F4; atualizar quando fechar a F5.

## Armadilhas já pagas (não repetir)

1. **Pontuação vira token no `jetbrains/markdown`.** `(`, `)`, `*` soltos são
   tokens de marcador. Descartar por tipo apaga texto do usuário. Decidir pela
   posição na árvore. Ver `DESIGN.md`.
2. **Token e elemento com o mesmo nome** (`MarkdownTokenTypes.BLOCK_QUOTE` ≠
   `MarkdownElementTypes.BLOCK_QUOTE`). Trocar compila e vaza marcador.
3. **IDs de bloco:** uma única `BlockIdSource` por documento carregado, dona
   do `EditorState`. Chamar `parse()` com fonte nova no meio da edição colide
   IDs e quebra o `LazyColumn(key = id)`.
4. **Commit atrasado do IME.** O teclado (SwiftKey) entrega o texto em
   composição ao campo antigo DEPOIS que um toque já trocou o foco. Por isso
   `EditorState.edit(value, from)` descarta valores de bloco não focado.
   Sem isso, tocar noutro bloco sobrescreve ele inteiro.
5. **Toques em toolbar/checkbox/link:** usar `pointerInput(Unit)` +
   `detectTapGestures` + `rememberUpdatedState`. `clickable` ou
   `pointerInput(lambda)` roubam o foco do `BasicTextField` ou reiniciam o
   detector a cada recomposição.
6. **Enter no fim do bloco não pode reparsear** um `after` vazio (o parser
   não cria bloco para texto vazio e o foco pularia). Exceto cerca de código
   aberta, que precisa cascatear. Ver comentário em `splitAtCursor`.
7. **Wikilinks/tags na renderização** são "specials" por posição no source
   (`Ctx` em `InlineText.kt`), viram URIs internas `jotmd:wiki:` /
   `jotmd:tag:` e são interceptados por `LocalUriHandler` no `MainActivity`.
8. **Corpus de teste em `docs/*.md` é `-text`** no `.gitattributes`: bytes
   exatos, sem normalização de fim de linha.

## Regras de trabalho

- Mudança de comportamento = teste JVM primeiro (`core-md/src/test`,
  `editor/src/test`). Bug corrigido deixa teste de regressão.
- Diff mínimo; reusar o que existe; sem dependência nova para o que poucas
  linhas resolvem.
- Commits: Conventional Commits em pt-BR (`feat(editor): …`, `fix(app): …`).
  Um commit por fase/feature, `test` + `assembleDebug` passando.
- **O dono também commita em paralelo.** Sempre `git fetch` antes de
  `git push`; se divergiu, `git pull --rebase`, rebuild, testes, push.
- Comportamento novo "como o Typora" deve ser observado no Typora antes e
  registrado no `DESIGN.md` (tabela do que o Typora grava).

## Teste em aparelho (tablet Android via adb wireless)

- O aparelho é compartilhado com outros apps. Antes de qualquer toque:
  `adb shell dumpsys activity activities | grep topResumedActivity` e
  confirmar que o JotMD está na frente. Nunca tocar em outro app.
- Não contornar tela bloqueada: pedir ao dono para desbloquear.
- Durante o teste: `adb shell settings put system screen_off_timeout 1800000`;
  ao fim, restaurar `60000`.
- Arquivos de teste só em `/sdcard/Documents/jotmd-ws/` (criados pelo agente).
  No seletor SAF, navegar só para o armazenamento local.
- Ler o resultado pelo arquivo gravado (`adb exec-out cat …`), não só pela
  tela. Teclas via `adb shell input keyevent` são de hardware; testar também
  o Enter do teclado virtual.
- Limpeza ao fim: `adb shell rm -r /sdcard/Documents/jotmd-ws`,
  `adb shell pm clear io.github.walterfr.jotmd`.
