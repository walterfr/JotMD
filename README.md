# JotMD

[![Release](https://img.shields.io/github/v/release/walterfr/JotMD?label=Release)](https://github.com/walterfr/JotMD/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Pride Versioning](https://img.shields.io/badge/PrideVer-0.4.1-ff69b4)](https://pridever.org/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF.svg)](gradle/libs.versions.toml)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84.svg)](app/build.gradle.kts)
[![Windows](https://img.shields.io/badge/Windows-Compose%20Desktop-0078D4.svg)](desktop/build.gradle.kts)
[![Sponsor](https://img.shields.io/badge/sponsor-%E2%99%A5-ea4aaa?logo=githubsponsors)](https://github.com/sponsors/walterfr)
[![Ko-fi](https://img.shields.io/badge/Ko--fi-apoiar-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/walterfr)

Leitor e editor de Markdown nativo para **Android** e **Windows Desktop**, com UI/UX inspirada no [Typora](https://typora.io/). Construído **100% em Jetpack Compose / Compose Multiplatform**, sem WebView e sem telemetria.

- **Android:** armazenamento exclusivo via *Storage Access Framework* (SAF), pronto para o [F-Droid](fdroid/io.github.walterfr.jotmd.yml).
- **Windows:** shell desktop JVM compartilhando `:core-md` e os fontes de `:editor` com suporte a árvore de pasta em tempo real, atalhos e pacotes nativos (`.msi`, `.exe` e `.zip` portátil).

---

## Download

Baixe os instaladores e checksums (`SHA256SUMS.txt`) na página de [**GitHub Releases**](https://github.com/walterfr/JotMD/releases/latest):

| Plataforma | Artefato | Observação |
|---|---|---|
| Android 8.0+ | `jotmd-android-0.4.1.apk` | Assinado via release CI; sem permissões invasivas (`dependenciesInfo` desativado) |
| Windows (x64) | `jotmd-windows-0.4.1.msi` | Instalador Windows padrão (WiX) com associação `.md` |
| Windows (x64) | `jotmd-windows-0.4.1-setup.exe` | Instalador executável com associação `.md` |
| Windows (x64) | `jotmd-windows-0.4.1-portable.zip` | Versão portátil (extraia e execute `JotMD.exe`) |

---

## Funcionalidades

### Editor & Motor de Markdown ("Escolha C")
- **Edição por bloco sem WebView:** `LazyColumn` de blocos — o bloco focado abre em `BasicTextField` com Markdown cru enquanto os demais são renderizados formatados, mantendo a string Markdown como única fonte da verdade.
- **Edição inteligente estilo Typora:**
  - `SmartEnter`: continua listas (`-`, `*`, `+`, `1.`, `- [ ]`), citações (`>`) e sai da estrutura em item vazio ou ao fim de front matter YAML.
  - `SmartBackspace` em cascata: remove checkbox, marcador de lista/citação, reduz indentação antes de fundir com o bloco anterior (com proteção contra eco stale do teclado virtual Android/SwiftKey).
  - `SmartTab` (`Tab`/`Shift+Tab`), `SmartPair` (auto-fechamento e envolvimento de seleção) e `SmartDelete` nas fronteiras de bloco.
  - Undo/Redo por documento com coalescência temporal (~500 ms).
- **Invariante inegociável:** `serialize(parse(texto)) == texto`, **byte a byte** (preservando separadores originais e BOM UTF-8).

### Sintaxe Suportada
- Títulos ATX (`#`–`######`) e Setext (`===` / `---`)
- Parágrafos, regras horizontais (`---`), negrito, itálico, riscado (`~~texto~~`), sublinhado (`<u>texto</u>`) e marca-texto (`==highlight==`)
- Front matter YAML (`---` na linha 1)
- Citações e **GitHub Alerts** (`> [!NOTE]`, `> [!TIP]`, `> [!IMPORTANT]`, `> [!WARNING]`, `> [!CAUTION]`)
- Listas aninhadas, ordenadas e **listas de tarefas** (`- [ ]` / `- [x]`) clicáveis
- **Tabelas GFM** alinhadas (`:--`, `:-:`, `--:`): inserção `3x4` (`Ctrl+T` / botão `Tabela` / `⊞`), adição/remoção de linhas e colunas (até 40 colunas), reordenação (`↑Lin`, `↓Lin`, `←Col`, `→Col`) e ciclo de alinhamento
- **Blocos de código cercado e indentado** com realce sintático (Kotlin/Java, JS/TS, Python, Rust/Go, SQL, Shell, YAML, JSON)
- **Fórmulas LaTeX** inline (`$...$`, evitando falsos positivos com preços como `$5 e $10`) e em bloco (`$$...$$`) convertidas nativamente para Unicode
- **Notas de rodapé** (`[^id]` clicável com salto até `[^id]: definição`) e **links por referência** (`[texto][ref]`, `[ref][]`, `[ref]`)
- **Sumário (`[TOC]`, `{:toc}`, `[[_TOC_]]`):** widget clicável no editor, âncoras HTML e sumário estruturado no PDF
- **WikiLinks e Tags:** `[[wikilinks|alias]]` clicáveis para abrir notas da pasta e `#tags` (corpo e YAML) clicáveis para filtrar

### Modos de Leitura, Escrita & Temas
- **5 temas do Typora:** `GitHub`, `Newsprint`, `Pixyll`, `Whitey` e `Night` (escuro).
- **Modo Código Fonte (`</>` / `Ctrl+/`):** realce sintático em Markdown ao vivo via `VisualTransformation` (comprimento 1:1 preservando cursor) e gutter seletivo numerando apenas linha `1`, linha ativa, múltiplos de `10` e última linha.
- **Modo Foco (`Foco`):** reduz opacidade de blocos inativos durante a digitação.
- **Modo Máquina de Escrever (`Máquina`, Desktop):** mantém o bloco ativo centralizado na viewport.
- **Coluna centralizada:** largura máxima `860.dp` no modo visual e `800.dp` no modo código-fonte.
- **Painel de Índices & Contagem de Palavras:** aba `"Índices"` (Outline) na gaveta Android e sidebar Desktop, além de contagem de palavras (CJK-aware, `382` WPM para tempo estimado de leitura).

### Workspace, Busca, Colagem Rica & Exportação
- **Multi-abas e Autosave:** abas independentes (`EditorState` + histórico undo próprio), título padrão `"Sem Título"`, autosave com debounce de 1 s e flush ao sair.
- **Gerenciamento de pasta (CRUD):**
  - **Android (SAF):** criar nota (`+ Nota`), criar pasta (`+ Pasta`), renomear, excluir e navegar árvore via `DocumentsContract` (sem `MANAGE_EXTERNAL_STORAGE`).
  - **Windows Desktop:** CRUD seguro contra *path traversal*, atualização automática em tempo real via `DirectoryTreeWatcher` (`WatchService`) e **Quick Open (`Ctrl+P`)**.
- **Busca Full-Text e Busca na Nota:**
  - **Na pasta:** SQLite FTS4 incremental com dobra de acentos no Android (`"acao"` encontra `"ação"`), trecho destacado e busca NFD no Desktop.
  - **Na nota (`Ctrl+F`):** diferenciação `Aa` (maiúsculas/minúsculas), palavra inteira `[ab]`, expressão regular `.*`, substituição unitária / global e **destaque visual das ocorrências** no editor (diferenciando ocorrência ativa).
- **Colagem Rica (`Colar+` / `Ctrl+Shift+V`):**
  - Converte `text/html` da área de transferência para Markdown GFM via `HtmlToMarkdown` puro;
  - No **Desktop**, cola imagens do clipboard salvando PNG em `typora-user-images/` e inserindo `![imagem](...)` automático; no **Android**, aceita URIs de imagem e HTML/Markdown.
- **Exportação:**
  - **HTML** autocontido (`exportToHtml`) compatível com classes `.typora-export`, `[TOC]` com âncoras, tema claro/escuro, LaTeX, alertas e notas de rodapé;
  - **PDF 1.4 nativo** em folha A4 (`exportToPdf`) sem dependências externas, com `[TOC]`, paginação automática, alertas coloridos, tabelas, links/notas de rodapé e fallback ASCII para matemática em `WinAnsi`.
- **Importação, preferências e abertura externa (`v0.4.0`):**
  - Importação pura em Kotlin de **HTML, DOCX e EPUB** para Markdown (`DocumentImport`) e escrita atômica com recuperação automática de rascunhos (`DraftStore`);
  - Modal de **Preferências** persistidas (5 temas, fonte `12–24 sp`, largura de coluna `600–1200 dp`, autopareamento, indentação, idioma **EN/PT-BR** e restauração de sessão);
  - Abertura direta de `.md`/`.txt` via intents externos (`VIEW`/`EDIT`/`SEND`) no Android e CLI / associação `.md` nos instaladores Windows (`.msi`/`.exe`).

---

## Atalhos de Teclado

| Atalho | Ação | Contexto |
|---|---|---|
| `Ctrl+B` / `Ctrl+I` | Negrito (`**`) / Itálico (`*`) | Bloco focado |
| `Ctrl+U` / `Ctrl+K` | Sublinhado (`<u>`) / Link (`[]()`) | Bloco focado |
| `Ctrl+T` | Inserir tabela `3x4` | Editor / Desktop |
| `Ctrl+/` | Alternar Modo Código Fonte (`</>`) | Editor / Desktop |
| `Ctrl+Shift+V` | Colar+ (Imagem PNG / HTML → MD / Markdown) | Desktop |
| `Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y` | Desfazer / Refazer | Bloco focado |
| `Ctrl+F` / `Esc` | Buscar na nota / Fechar barra ou Quick Open | Desktop |
| `Ctrl+P` | Quick Open na pasta e abas abertas | Desktop |
| `Ctrl+S` | Salvar arquivo ativo (ou *Salvar como*) | Desktop |
| `Tab` / `Shift+Tab` | Indentar / desindentar lista e bloco | Bloco focado |
| `↑` / `↓` | Navegar para o bloco anterior / seguinte nas bordas | Bloco focado |

---

## Arquitetura e Módulos

```
core-md  ──▶  editor  ──▶  app (Android SAF + FTS4)
    │            │
    └────────────┴──────▶  desktop (Windows Compose Desktop)
```

| Módulo | Tecnologia | Responsabilidades |
|---|---|---|
| [`:core-md`](core-md/) | Kotlin puro (zero Android) | `Block`, `DocState`, `MdParser`, `MdSerializer`, `FrontMatter`, `Wikilinks`, `Tags`, `DocIndex`, `Scan`, `Alerts`, `DocOutline`, `DocStats`, `DocRefs`, `MathRender`, `HtmlToMarkdown`, `DocSearch`, `HtmlExport`, `PdfExport` |
| [`:editor`](editor/) | Compose + JVM comum | `EditorState`, `EditorScreen`, `DocumentView`, `BlockEditor`, `BlockRenderer`, `BlockParts`, `InlineText`, `Toolbar`, `ImagePreview` (`expect`/`actual`), `MdTheme`, `editor/ops/*` (`BlockOps`, `UndoStack`, `TaskOps`, `TableOps`, `SourceGutter`, `SyntaxHighlight`, `SmartEnter`, `SmartBackspace`, `SmartTab`, `SmartPair`, `SmartDelete`) |
| [`:app`](app/) | Android (`compileSdk 35`, `minSdk 26`) | `MainActivity`, `TabManager`, `FileSession`, `FileBrowser` (SAF CRUD + `DocumentsContract`), `SearchIndex` (SQLite FTS4) |
| [`:desktop`](desktop/) | Compose Desktop (JVM 17) | `Main`, `DesktopWorkspace` (`DesktopTabManager`, árvore + CRUD + `DirectoryTreeWatcher`, `filterQuickOpen`, PNG em `typora-user-images/`), `ImagePreview.desktop` |

### Invariante de Roundtrip

```kotlin
serialize(parse(texto)) == texto   // byte a byte, para qualquer entrada
```

`MdParser.parse` particiona a entrada em fatias contíguas sem sobreposição cobrindo `DocState.leading` + `(source + trailing)` de cada `Block`. A invariante é garantida por construção e protegida por [`RoundtripTest`](core-md/src/test/kotlin/io/github/walterfr/jotmd/core/RoundtripTest.kt).

---

## Build, Testes e Desenvolvimento

### Pré-requisitos
- **JDK 17**
- **Android SDK** (`compileSdk 35`, `minSdk 26`) para compilar `:app`

### Comandos

```bash
# Rodar todas as suítes de teste JVM/unitários (:core-md, :editor, :app, :desktop) e gerar APK debug
./gradlew test assembleDebug

# Instalar APK debug via adb no aparelho Android conectado
./gradlew :app:installDebug

# Executar o aplicativo Windows / Compose Desktop diretamente
./gradlew :desktop:run

# Empacotar distribuível e instaladores Windows (.msi / .exe / imagem portátil)
./gradlew :desktop:createDistributable :desktop:packageMsi :desktop:packageExe "-PdesktopPackageVersion=0.4.1"
```

*(No PowerShell do Windows, use `.\gradlew.bat` e mantenha parâmetros `-P...=...` entre aspas.)*

### Releases Automatizadas (CI/CD)
Tags `v*` acionam [`.github/workflows/release.yml`](.github/workflows/release.yml):
1. Roda todos os testes e gera o APK Release (`secrets.KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, ou assinatura de fallback `debug` fora de releases assinadas).
2. Empacota instaladores Windows `.msi`, `.exe` e `.zip` portátil em runner `windows-latest`.
3. Gera `SHA256SUMS.txt` e publica a Release no GitHub.
4. Quando construído pelo servidor do F-Droid (sem variáveis de assinatura no ambiente), `app/build.gradle.kts` mantém `dependenciesInfo` desligado e assinatura neutra para builds reprodutíveis.

---

## Versionamento — Pride Versioning 🏳️‍🌈

O JotMD adota o [**Pride Versioning**](https://pridever.org/) (`PROUD.DEFAULT.SHAME`):

- **PROUD (`X`):** incrementado quando sai algo de que realmente temos orgulho.
- **DEFAULT (`Y`):** entregas e marcos normais do projeto (como `0.3.0`, fechando a paridade de recursos com o Typora).
- **SHAME (`Z`):** correções apressadas de bugs embaraçosos que deixamos passar.

---

## Documentação Adicional

- [`AGENTS.md`](AGENTS.md) — estado atual do projeto, módulos, regras de trabalho e armadilhas já resolvidas.
- [`DESIGN.md`](DESIGN.md) — tokens visuais medidos no Typora, arquitetura de foco, CSS e matriz de paridade.
- [`AI_CONTEXT.md`](AI_CONTEXT.md) — decisões originais de arquitetura e histórico de roadmap.
- [`docs/typora-ref.md`](docs/typora-ref.md) — documento de referência usado nos testes visuais e de roundtrip.

---

## Apoie o projeto

O JotMD é gratuito e de código aberto. Se ele te ajudou, considere apoiar o desenvolvimento — qualquer contribuição ajuda a manter e melhorar o projeto:

- ❤️ [GitHub Sponsors](https://github.com/sponsors/walterfr)
- ☕ [Ko-fi](https://ko-fi.com/walterfr)
- ☕ [Buy Me a Coffee](https://buymeacoffee.com/walterfr)

### Pix (Brasil)

Escaneie o QR code no app do seu banco, ou use a chave **copia e cola** abaixo:

<img src="docs/pix-qr.png" alt="QR Code Pix" width="200" />

```
00020101021126400014br.gov.bcb.pix0118walterfr@gmail.com5204000053039865802BR5915WALTER REBOUCAS6009FORTALEZA62070503***63045603
```

---

## Licença

Distribuído sob a licença **MIT**. Veja [`LICENSE`](LICENSE) para detalhes.
