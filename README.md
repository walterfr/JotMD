# JotMD

Leitor e editor de Markdown nativo para Android (com shell desktop Windows em
Compose Desktop), com UI/UX inspirada em Typora. 100% Compose, sem WebView.
Alvo de publicação no Android: F-Droid.

## Estado

**F0–F6 em evolução.** Abre arquivos e pastas de notas (via SAF no Android ou
diretório local no Windows) e edita blocos em Compose com motor "Escolha C",
edição inteligente estilo Typora, multi-abas, busca na pasta e na nota, temas
claro (`github`) e escuro (`night`) e exportação HTML. Em aberto para fechar
F6: validação visual em tablet Android, exportação PDF nativa, desempenho em
documentos longos (5k+ linhas) e empacotamento F-Droid. O roadmap original está
em `AI_CONTEXT.md` e o estado detalhado em `AGENTS.md`.

## Funcionalidades

- **Motor Escolha C:** `LazyColumn` de blocos onde apenas o bloco focado abre
  em `BasicTextField` com Markdown cru e o restante renderiza formatado
  (títulos, listas, citações, tabelas GFM alinhadas, checkboxes clicáveis,
  cercas de código com linguagem, preview de imagens e YAML front matter inline).
- **Edição inteligente estilo Typora:** `SmartEnter`, `SmartBackspace` em
  cascata, `SmartTab` (`Tab`/`Shift+Tab`), `SmartPair` (auto-pair e
  `surroundPair` em seleção) e `SmartDelete`, além de undo/redo com
  coalescência e modo código-fonte (`isSourceMode`).
- **Multi-abas e navegação:** abas simultâneas com estado preservado,
  navegador de pasta raiz via SAF (`:app`) e árvore `.md` (`:desktop`),
  `[[wikilinks]]` e `#tags` clicáveis.
- **Busca:** busca full-text na pasta com SQLite FTS4 no Android (e busca na
  pasta no desktop), além de busca e substituição local na nota (`DocSearch`).
- **Temas e blocos estendidos:** tema claro `github` e tema escuro `night`,
  GitHub Alerts (`> [!NOTE]` etc.) e marca-texto `==highlight==`.
- **Exportação:** geração de HTML standalone (`HtmlExport`), gravado via SAF
  no Android e em disco no desktop.

## Módulos

| Módulo | O quê | Fase |
|---|---|---|
| `:core-md` | Parsing, serialização, `FrontMatter`, `Wikilinks`, `Tags`, `DocIndex`, `Alerts`, `DocSearch` e `HtmlExport`. Kotlin puro, zero Android, testável em JVM. | F0, F5–F6 ✅ |
| `:editor` | Renderização e edição de blocos em Compose (`SmartEnter`, `SmartBackspace`, `SmartTab`, `SmartPair`, `SmartDelete`), temas claro/escuro (`github`/`night`), GitHub Alerts, `==highlight==` e `isSourceMode`. | F1–F6 ✅ |
| `:app` | Shell Android: SAF (arquivo e pasta raiz), multi-abas (`TabManager`), autosave, busca FTS4, busca na nota, tema escuro e export HTML. | F1–F6 (validação em tablet pendente) |
| `:desktop` | Shell Windows (Compose Desktop JVM) compartilhando `:core-md` e fontes comuns de `:editor`: abas, árvore `.md`, busca na pasta e na nota, tema escuro e export HTML. | F5–F6 ✅ |

## A invariante

```kotlin
serialize(parse(texto)) == texto   // byte a byte, para todo texto
```

Quebrar isso corrompe o arquivo do usuário em silêncio. `parse` particiona o
texto original em fatias que não se sobrepõem e cobrem tudo, então a invariante
vale por construção — `RoundtripTest` a guarda, não a produz.

## Build

```bash
./gradlew test assembleDebug
./gradlew :desktop:run
```

Requer JDK 17 e o Android SDK (`compileSdk 35`, `minSdk 26`).

## Documentos

- `AGENTS.md` — estado atual, regras e armadilhas para agentes de código.
- `AI_CONTEXT.md` — decisões de arquitetura e roadmap original.
- `DESIGN.md` — tokens visuais e modelo de foco medidos no Typora real.
- `docs/typora-ref.md` — documento de referência; também é o corpus de teste.
