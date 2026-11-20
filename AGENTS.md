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
- **Inspeção com `morluto/rea` liberada:** usar o `rea` (MCP e CLI) para
  inspecionar o Typora instalado (`C:\Program Files\Typora\resources`:
  CSS/temas, HTML, JS/Electron, `app.asar`, `appsrc/`, `page-dist/`) e
  APKs Android de referência, extraindo regras de comportamento, tokens
  visuais e fluxos de UI/UX para reimplementar do zero em Kotlin/Compose
  no JotMD.
- **App Windows em Compose Desktop (`:desktop`):** o app desktop Windows
  usa Kotlin/JVM + Compose Multiplatform Desktop compartilhando `:core-md` e
  os fontes comuns de `:editor` (`editor/src/main/kotlin`), isolando apenas
  `ImagePreview` por plataforma (`editor/src/androidMain` no Android e
  `desktop/src/main` no Windows).

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
| `:core-md` | Kotlin puro, zero Android. `Block`/`DocState`, `MdParser` (`parse`), `MdSerializer` (`serialize`), `FrontMatter`, `Wikilinks`, `Tags` (`tagSpansIn`), `DocIndex`, `Scan`, `Alerts` (`alertOf`), `DocSearch` (`findInDocument`, `replaceAllInText`), `HtmlExport` (`exportToHtml`), `PdfExport` (`exportToPdf`). |
| `:editor` | Compose. `EditorState` (estado e operações), `BlockEditor` (foco + teclas), `BlockRenderer`/`BlockParts`/`InlineText` (renderização, GitHub Alerts e `==highlight==`), `Toolbar`, `ImagePreview`, `MdTheme` (tema claro/escuro via `MdTokens.isDark` / `MdPalette`), modo código-fonte (`isSourceMode`). Operações puras em `editor/ops/`: `BlockOps` (`splitAt`, `mergeWithPrevious`, `reparse`), `UndoStack`, `TaskOps`, `SmartEnter`, `SmartBackspace`, `SmartTab`, `SmartPair`, `SmartDelete`. |
| `:app` | Shell Android. `MainActivity` (gaveta, multi-abas, tema escuro, busca na pasta e na nota, export HTML/PDF via SAF, links), `TabManager`, `FileSession` (abrir/gravar SAF, autosave), `FileBrowser` (árvore da pasta raiz, `resolveWikilink`), `SearchIndex` (FTS4). |
| `:desktop` | Shell Windows (Compose Desktop JVM). `Main` / `DesktopWorkspace` (abas, árvore `.md`, busca full-text na pasta e na nota, tema escuro, export HTML/PDF, wikilinks/tags, gravação em disco) + `ImagePreview.desktop` compartilhando `:core-md` e `editor/src/main/kotlin`. |

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

Implementado em código e testado em JVM neste ciclo:

- YAML front matter (`BlockType.FRONT_MATTER`), `SmartBackspace`, `SmartTab`,
  `SmartPair`, `SmartDelete`, `TabManager` (Android `:app`), app desktop
  Windows (`:desktop`), tema escuro `night` (`MdTheme.kt`), modo código-fonte
  (`isSourceMode`), GitHub Alerts (`Alerts.kt`), `==highlight==`, busca e
  substituição na nota (`DocSearch.kt`), exportação HTML (`HtmlExport.kt`),
  exportação PDF 1.4 nativa (`PdfExport.kt` em `:core-md`, plugado via SAF
  em `:app` e diálogo em `:desktop`), otimização de split local e `contentType`
  em `LazyColumn` para documentos de 5k+ linhas, e metadados/receita F-Droid
  (`dependenciesInfo` desligado em `app/build.gradle.kts`, Fastlane `pt-BR`/
  `en-US` em `fastlane/metadata/android/`, `fdroid/io.github.walterfr.jotmd.yml`).

Verificado em tablet Android (`TB361FU - 16`, Android 16 via `adb`):

- Renderização e fluxo visual de `typora-ref.md`, barra multi-abas (`+`, troca,
  fechamento), barra `Buscar`/`Subst. tudo` (`1 substituído(s)`), tema escuro/claro,
  abertura SAF de arquivo em `/sdcard/Documents/jotmd-ws/`, `FRONT_MATTER`,
  GitHub Alerts (`[!NOTE]`, `[!WARNING]`), `==highlight==`, tabela GFM,
  toggle de checkbox salvo em disco e `SmartEnter` pelo Enter virtual do
  Microsoft SwiftKey gravado em disco.
- 3 bugs reais pegos no tablet e corrigidos com testes de regressão:
  (1) contraste do título/status bar no tema claro/escuro (`Surface` +
  `onBackground` + `SystemBarStyle` reativo ao `isDark` no `MainActivity.kt`,
  re-verificado em screenshot); (2) BOM UTF-8 (`\uFEFF`) antes de `---` no
  `MdParser.kt` + `RoundtripTest.kt` (re-verificado na tela); (3) `Backspace`/
  `Enter` do IME virtual chegando por `InputConnection` -> `EditorState.edit()`
  com filtro contra eco stale de `batchEdit` do SwiftKey (`EditorState.kt` +
  `EditorStateTest.kt`; re-teste final em aparelho pausado antes de rodar
  porque outro app tomou foco).

Pendente:

- Re-validar interativamente no tablet o ajuste final de eco stale do
  `batchEdit` no `SmartBackspace` via SwiftKey, e testar geração de pacote
  release/tag atualizada antes da submissão ao `fdroiddata`.

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
8. **Front matter só na linha 1 do arquivo.** `parse(text, ids, frontMatter)`:
   o default `true` vale só para o documento inteiro. Quem parseia um trecho
   (`reparse` com `fromIndex > 0`, `mergeWithPrevious` fora do topo,
   `splitAt`) passa `false`, senão um `---` no meio vira metadado.
9. **Scripts Python via heredoc no Git Bash do Windows** trocaram `"\\n"` por
   newline real ao editar `.kt`. Para editar código, use a ferramenta de
   edição do agente, não `python - <<EOF`.
10. **Corpus de teste em `docs/*.md` é `-text`** no `.gitattributes`: bytes
   exatos, sem normalização de fim de linha.
11. **IME Android (SwiftKey) faz `deleteSurroundingText` + eco de `batchEdit`:**
    teclados virtuais disparam `Backspace` via `InputConnection -> onValueChange`
    (`EditorState.edit`) em vez de `onPreviewKeyEvent`. Além disso, quando `edit`
    reescreve `editValue` (como `SmartBackspace`/`SmartPair`), o `batchEdit` do
    IME pode reenviar o texto cru anterior ~4 ms depois antes da recomposição
    atualizar o `BasicTextField`, exigindo o filtro `isStaleImeEcho` em `EditorState.kt`.
12. **BOM UTF-8 (`\uFEFF`) na linha 1:** arquivos criados no Windows podem começar
    com `\uFEFF` antes de `---`; `MdParser.parse` preserva o `\uFEFF` em
    `DocState.leading` para não quebrar `FRONT_MATTER` nem o roundtrip byte a byte.

## Regras de trabalho

- Mudança de comportamento = teste JVM primeiro (`core-md/src/test`,
  `editor/src/test`). Bug corrigido deixa teste de regressão.
- Diff mínimo; reusar o que existe; sem dependência nova para o que poucas
  linhas resolvem.
- Commits: Conventional Commits em pt-BR (`feat(editor): …`, `fix(app): …`).
  Um commit por fase/feature, `test` + `assembleDebug` passando.
- **O dono também commita em paralelo.** Sempre `git fetch` antes de
  `git push`; se divergiu, `git pull --rebase`, rebuild, testes, push.
- Comportamento novo "como o Typora" deve ser observado ou inspecionado via
  `rea` no Typora antes e registrado no `DESIGN.md`.
- **Execução em paralelo com múltiplos subagentes:** tarefas ou features
  independentes devem ser sempre paralelizadas usando múltiplos subagentes
  (`invoke_subagent`) com conjuntos disjuntos de arquivos, consolidando o
  build/testes na thread principal.

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
