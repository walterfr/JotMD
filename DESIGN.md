# DESIGN — referência visual e de comportamento

Extraído do Typora instalado (Windows, tema `github`, PT-BR) em 2026-08-08.
Fonte dos números: `C:\Program Files\Typora\resources\style\base.css`,
`base-control.css` e `%APPDATA%\Typora\themes\github.css`.

## Modelo de foco — como o Typora realmente funciona

Observado, não suposto:

- **Marcadores de bloco nunca aparecem.** Com o cursor dentro de um `# Título`,
  o `#` continua escondido. Idem `>` de citação e `-` de lista. O tipo do bloco
  é comunicado só pela renderização.
- **Marcadores inline aparecem por span, não por bloco.** Cursor dentro de
  `**negrito**` revela só aquele `**`, em cinza claro. O `*itálico*` do mesmo
  parágrafo continua escondido.
- Mecanismo: os marcadores são `<span class="md-meta">` com
  `display:none`/`width:0;opacity:0`. O Typora põe `.md-expand` no nó inline sob
  o cursor, e só aí `.md-meta` vira `display:inline`.
- A sintaxe crua completa só existe no **modo código-fonte** (botão `</>` no
  rodapé): CodeMirror com gutter de números de linha, linha atual destacada,
  marcadores coloridos (`##` magenta, `>` e `-` cinza, `[x]` magenta).

**Consequência para o JotMD:** o `AI_CONTEXT.md` descreve "bloco em foco =
`BasicTextField` com marcadores visíveis". Isso é mais grosseiro que o Typora —
expõe a sintaxe do bloco inteiro, não do span. É uma aproximação aceitável e
muito mais simples com IME Android, mas é uma diferença deliberada, não um
detalhe. Fidelidade total exigiria um `BasicTextField` com `VisualTransformation`
que esconde/revela por span conforme a posição do cursor — caro e instável com
IME (Armadilha 4 do contexto). Decisão: ficar na aproximação por bloco em F2/F3,
reavaliar em F6.

## Enter em listas e citações — observado no Typora

Método: caixa-preta e inspeção com `morluto/rea`. Originalmente observado
digitando sequências num arquivo vazio do Typora instalado, salvando e lendo
o `.md` gravado (fuses `RunAsNode` e `EnableNodeCliInspectArguments`
desligados no executável, conferidos com `rea inspect-electron-fuses`). O
uso de `rea` para inspecionar os artefatos instalados em
`C:\Program Files\Typora\resources` (CSS/temas, HTML, JS/Electron,
`app.asar`, `appsrc/`, `page-dist/`) e APKs Android de referência está
liberado para extrair comportamento, tokens e fluxos para reimplementação do
zero em Kotlin/Compose no JotMD.

| Situação | O Typora grava |
|---|---|
| Enter em item com texto | novo item, mesmo marcador e indentação, lista compacta (`- um` / `- dois`) |
| Enter no meio do item | o texto depois do cursor vai para o novo item |
| Enter em item vazio | sai da lista: linha em branco e parágrafo |
| Lista numerada | o próximo número (`1. a` / `2. b`) |
| Tarefa | novo item sempre desmarcado (`- [ ] `), mesmo depois de `[x]` no fim da linha; no meio de item marcado preserva `[x]`; no início (offset 0), o item vazio acima nasce desmarcado (`[ ]`) e o texto desce mantendo o estado original |
| Heading (início / meio / fim) | offset 0: linha acima vira `paragraph` vazio e o título desce mantendo `heading`; meio: divide em dois `heading` iguais; fim: cria `paragraph` abaixo |
| Citação | novo parágrafo **dentro** da citação: `> a` / `>` / `> b` |
| Citação vazia | sai, e o separador `>` vazio sai junto |
| YAML front matter (`meta_block`) | `Enter` insere `\n` interno; só sai para `paragraph` abaixo quando já há `\n\n` no fim (`/\n{2,}$/`) e `Enter` é pressionado no final sem modificadores |
| `---` na linha 1 (`paragraph`) | `Enter` no final converte em `meta_block` no topo + `paragraph` vazio abaixo e foca o `meta_block` |
| Célula de tabela (`table_cell`) | `Enter` vai para a célula da linha abaixo na mesma coluna; `Ctrl+Enter` (`Cmd+Enter` no Mac) adiciona nova linha abaixo; `Shift+Enter` insere `<br />` |
| Span inline (`smartSplit`) | dividir no meio de `**negrito**`, `*itálico*`, etc. fecha os delimitadores no bloco superior e os reabre no inferior, ajustando o cursor |
| `def_link` / `def_footnote` vazios | `Enter` no início/fim converte o bloco de volta para `paragraph` |
| `**`, `` ` `` | sem fechamento automático ao dar `Enter` |

Implementado em `editor/ops/SmartEnter.kt` (listas, listas numeradas, tarefas e citações; divisão e fusão de blocos em `editor/ops/BlockOps.kt` e `editor/EditorState.kt`).

## Backspace e Delete — extraído do Typora (`Mod 61`, `Mod 5f`, `Mod 60`)

Pipeline de `backspaceHandler` (`Mod 61`) quando o cursor está no offset 0 de um bloco folha (`x = [b, v, z, V, tryConvertListItemToUpperParagraph, w, y]`):

1. **`b` (`table_cell`):** se estiver na 1ª célula e a tabela inteira estiver vazia (`isTableEmpty`), apaga a tabela; caso contrário, move o cursor para o fim da célula anterior (`getSibling(t, "before")`).
2. **`v` (`hr` ou bloco vazio anterior):** se o irmão anterior for `hr` ou bloco vazio (enquanto o atual não é vazio), ou item vazio antes do item atual, apaga apenas o bloco/`hr` anterior sem mover o bloco atual.
3. **`z` (`tasklist`):** no 1º parágrafo de um item de tarefa, o **1º Backspace** remove apenas o checkbox (`unset("style")`, `unset("checked")`), convertendo `- [ ] texto` → `- texto` (ou `- [x] texto` → `- texto`).
4. **`V` (1º filho de `blockquote` ou 1º item de `list` no topo):** chama `Node.upStream` (`Mod 5f`), desenrolando um nível (`> texto` → `texto`; `- texto` → `texto`).
5. **`tryConvertListItemToUpperParagraph` (2º+ `list_item` de uma lista):** o **1º Backspace** remove o marcador do item e anexa o parágrafo como filho ao final do `list_item` anterior; o **2º Backspace** funde o texto no parágrafo anterior. *(Adaptação para o modelo plano do JotMD: 1º Backspace remove o marcador de lista/heading/citação virando parágrafo/desindentando; 2º Backspace funde com o bloco anterior).*
6. **`w` (`Node.backToParagraphBeforeDelete` = `heading`, `def_footnote`, `def_link`, `meta_block`, `fences`):** o **1º Backspace** converte o bloco em `paragraph` mantendo texto e cursor no início (`### Título` → `Título`); o **2º Backspace** funde com o bloco anterior.
7. **`y` (fallback em `paragraph`):** concatena o texto no final do bloco folha anterior e posiciona o cursor no ponto de junção.

Outros casos de remoção:
- **Backspace entre par auto-fechado vazio (`tryRemovePairStart`, `Mod 60`):** apagar logo após `(`, `[`, `{`, `"`, `'`, `*`, `` ` ``, `~`, `_`, `$`, `^` quando o caractere seguinte é o fechamento correspondente apaga ambos os caracteres de uma vez.
- **Delete no fim ou bloco vazio (`k`):** em `paragraph`/`heading` vazio com bloco seguinte, remove o bloco atual e move o cursor para o início do próximo; em `hr`/`toc`, `deleteSelectable` remove o bloco; em `CodeMirror` (`fences`/`math_block`) vazio, `deleteBlock` apaga o bloco inteiro.

Implementado no JotMD em `editor/ops/SmartBackspace.kt` e `editor/ops/SmartDelete.kt`.

## Tab / Shift+Tab (`Mod 5f`, `Mod 48`, `Mod 49`)

- **Lista (`list_item`):** `Tab` (`moreIndent`) aninha o item dentro do último filho do `list_item` anterior; `Shift+Tab` (`lessIndent`) sobe um nível de lista ou desenrola para `paragraph` se já estiver na raiz.
- **Citação (`blockquote`):** `Shift+Tab` desenrola um nível do `blockquote` (`paragraph`, `heading`, `def_link`, `def_footnote`).
- **Tabela (`table_cell`):** `Tab` / `Shift+Tab` avança/recua para a célula seguinte/anterior selecionando o conteúdo (passa de `th` para o 1º `td` e entre linhas); `Tab` na última célula cria nova linha (`addRow`).
- **Link/nota de rodapé (`def_link`, `def_footnote`):** `Tab` / `Shift+Tab` alterna o foco entre os campos `md-def-name`, `md-def-url`/`md-def-content` e `md-def-title`.
- **Parágrafo / Heading / Front matter:** `Shift+Tab` remove `\t` ou até 2 espaços no início; `Tab` insere `\t` literal (ou `indentSize` espaços dentro de `meta_block` quando no início da linha).
- **CodeMirror (`fences`, `math_block`, `html_block`, modo fonte):** delega para `indentMore` / `indentLess`.

Implementado no JotMD em `editor/ops/SmartTab.kt`.

## Arquitetura interna do Typora (`v1.13.7`)

Extraído de `C:\Program Files\Typora\resources` via `morluto/rea`:

- **`app.asar`** (`package.json` `version: "1.13.7"`, `releaseId: "cf170905"`):
  - `launch.dist.js` (1.383 B): loader de bytecode V8 (`.jsc`) via `vm.Script` com string dummy preenchida com `U+200B` do mesmo tamanho (`source_length = 235.173`).
  - `atom.compiled.dist.jsc` (379.912 B; `payload_length = 379.880`): processo principal Electron compilado; `rea inspect_v8_code_cache` extraiu 1.828 strings incluindo canais IPC (`export.printToPDF`, `export.genPrintView`, `export.prepareImageCapture`, `export.captureImage`, `dialog.showOpenDialog`, `dialog.showSaveDialog`, `theme.apply`, `theme.setThemeSource`, `nativeTheme`, `filesOp.*`). Nota: `app.asar` possui divergência de SHA-256 declarada vs observada em `atom.compiled.dist.jsc` (inspecionado com `integrity_policy: "record-and-continue"`).
- **`appsrc/window/frame.js`** (1.671.751 B, 92 módulos SystemJS): todo o motor do editor, parser, renderizador DOM, `UndoManager`, `Brush`, `Stylize`, `TableEdit`, `SearchPanel` e exportadores.
- **`appsrc/finder-worker.js`** (415 linhas): Web Worker para indexação e busca rápida de arquivos na pasta aberta.
- **`lib.asar`** (8.342.504 B): bibliotecas embarcadas (`MathJax4/`, `codemirror/mode.min.js`, `diagram/mermaid.min.js`, `jimp/`, `pdf/pdf-lib.min.js`, `pdf/pdf-designer.min.js`).

## Modelo de Nós (`Mod 13` — `Node` e `TYPE`)

- **15 tipos de bloco top-level:** `paragraph`, `heading`, `blockquote`, `fences`, `hr`, `def_link`, `def_footnote`, `table`, `raw_edit`, `meta_block`, `math_block`, `html_block`, `list`, `toc`, `iframe`.
- **4 blocos virtuais/estruturais:** `list_item`, `table_row`, `table_cell`, `alert_title`.
- **35 tipos inline:** `tab`, `plain`, `strong`, `em`, `code`, `underline`, `escape`, `autolink`, `reflink`, `refimg`, `link`, `url`, `br`, `tag`, `comment`, `atag`, `imgtag`, `del`, `todo`, `image`, `footnote`, `attr`, `emoji`, `inline_math`, `subscript`, `superscript`, `linebreak`, `highlight`, `html_entity`, `pants`, `emptyline`, `softbreak`, `html_inline`, `ruby`, `alert_text`.

## Auto-pair, Formatação Inline, Seleção e Atalhos (`Mod 60`, `Mod 2d`, `Mod 48`)

- **`SyntaxMap` (`Mod 2d`):** `strong: ["**","**"]`, `em: ["*","*"]`, `code: ["`","`"]`, `del: ["~~","~~"]`, `underline: ["<u>","</u>"]`, `footnote: ["[^"," ]"]`, `link: ["[","]()"]`, `image: ["![","]()"]`, `highlight: ["==","=="]`, `superscript: ["^","^"]`, `subscript: ["~","~"]`, `inline_math: ["$","$"]`, `comment: ["<!--","-->"]`.
- **Auto-pair (`handlePair` / `surroundPair`, `Mod 60`):** fecha pares `()`, `[]`, `{}`, `""`, `''` (e, com `autoPairExtendSymbol`, `*`, `` ` ``, `_`, `~`, `^`, `=`, `$`). Com seleção ativa, digitar o delimitador de abertura envolve o texto selecionado (`surroundPair`).
- **Atalhos `KeyMaster` (`Mod 48`):** `Ctrl+B` (negrito), `Ctrl+I` (itálico), `Ctrl+U` (sublinhado), `Ctrl+Z` / `Ctrl+Y` / `Ctrl+Shift+Z` (undo/redo), `Ctrl+F` (buscar), `Ctrl+H` (substituir no editor; delete char no modo não-Electron), `Ctrl+L` (selecionar linha), `Ctrl+D` (selecionar palavra), `Ctrl+E` (selecionar frase), `Ctrl+A` (selecionar tudo), `Ctrl+Home` / `Ctrl+End` (início/fim do doc), `CmdOrCtrl+/` (modo código-fonte).

Implementado no JotMD: auto-pair e `surroundPair` em `editor/ops/SmartPair.kt` (e remoção de par vazio em `editor/ops/SmartBackspace.kt`).

## Brush (`Mod 2b`), Undo/Redo (`Mod 1b`), Busca (`finder-worker.js` / `Mod 36`) e Export (`Mod 4d`, `Mod 5b`)

- **`Brush` (`Mod 2b`):** fila assíncrona executada a cada **200 ms** (`interval = 200`). `quickParse` (`Mod 19`) converte em tempo real gatilhos digitados no bloco (`# `, `> `, `- `, `1. `, `- [ ] `, `[!NOTE]`). `expand` gerencia `.md-expand` e `.md-focus`; a conversão opcional de cabeçalhos/defs para markdown cru no foco (`simpleExpand`) depende de `File.option.expandSimpleBlock` (desligado por padrão).
- **`UndoManager` (`Mod 1b`):** snapshots com `SnapFlag` (`NONE=0`, `ADD=1`, `REPLACE=2`, `DELETE=-1`). Coalescência na mesma `source` (`cid#type`) quando `new Date - r.date < 3000` (**3 s**), exceto `REPLACE`. Pilha limitada por peso a **160** comandos.
- **Workspace (`finder-worker.js`):** extensões suportadas: `md`, `markdown`, `mmd`, `mkd`, `mdwn`, `mdown`, `mdx`, `""` (sem extensão), `mdtxt`, `mdtext`, `apib`, `rmarkdown`, `rmd`, `qmd`, `txt`, `text`. Ignora arquivos/pastas começando com `.`, `node_modules`, `.asar` e arquivos **> 1000 KB** (1 MB). Cache inicial limitado a `_MAX_COUNT = 600` arquivos e `_MAX_ITER = 4000` iterações; `quickFind` limita a `_MAX_QUERY_HIT = 100` resultados.
- **Busca no documento (`SearchPanel`, `Mod 36`):** debounce de **50 ms** (**100 ms** em busy mode), suporta `caseSensitive`, `wholeWord` e `useRegexp`, teto `MAX_HITS_COUNT = 5000`, substituição única e `replaceAll` (agrupado em um único comando de undo). Implementado no JotMD em `:core-md` (`DocSearch.kt`: `findInDocument`, `replaceAllInText`).
- **Export HTML / PDF / Imagem (`Mod 4d`, `Mod 5b`, `base.css`):**
  - **HTML:** `<body class="typora-export">` com `<div class="typora-export-content">` e `<div id="write">` (quando `sidebar` ativo, adiciona `<div class="typora-export-sidebar"><div class="outline-content">...</div></div>` e classes `typora-export-show-outline` + `typora-export-collapse-outline`). CSS inlinado na ordem `base.css` + tema + overrides de impressão/margens (`padding-left/right: 16mm` por padrão, `preLinebreakOnExport`). Implementado no JotMD em `:core-md` (`HtmlExport.kt`: `exportToHtml`).
  - **PDF:** gera HTML temporário com `forPrint: true` (quebra opcional de página antes de `h1` via `data-breakpage`), chama `export.printToPDF` (tamanho padrão `A4`, margens `16mm`), pós-processa com `pdf-lib` para desenhar cabeçalho/rodapé (`12px`, variáveis `${title}`, `${author}`, `${pageno}`, `${totalpages}`) e `TPDFOutLineMaker` para bookmarks do PDF. Implementado em versão nativa Kotlin pura em `:core-md` (`PdfExport.kt`: `exportToPdf`, A4 `595x842 pt`, margens `45 pt`, `WinAnsiEncoding`, paginação e rodapé).
  - **Imagem:** `exportToImage` usa `imageWidth: 640`, `imageFontSize: "24px"`, captura fatiada via `export.captureImage` e montagem no Web Worker `image-worker.js` com `Jimp`.

## Duas armadilhas do `jetbrains/markdown`

Ambas custaram bug real na F1. Ficam aqui porque nenhuma aparece na API.

**1. Pontuação literal vira token de marcador.** Em `a (b) c`, o `(` e o `)` são
tokens dos tipos `(` e `)` — os mesmos que delimitam o destino de um link. Em
`2*3=6`, o `*` sem par vira um token `EMPH`. Logo, **descartar marcador por tipo
apaga pontuação do usuário**. O renderizador decide pela posição na árvore: cada
elemento conhecido descarta os próprios marcadores e todo o resto sai literal.
Construção desconhecida aparece como o texto cru dela; nunca some.

**2. Token e elemento compartilham nome sendo tipos diferentes.**
`MarkdownTokenTypes.BLOCK_QUOTE` é o `>` de cada linha;
`MarkdownElementTypes.BLOCK_QUOTE` é a citação inteira. Idem `SETEXT_1`. Trocar
um pelo outro compila e faz o marcador vazar para a tela.

## Tokens de tipografia (tema github)

Raiz: `html { font-size: 14px }`. `1rem = 14px`. `em` abaixo é relativo a isso.

| Elemento | Valor |
|---|---|
| Corpo | `line-height: 1.6`, cor `#333333`, fundo `#ffffff` |
| Largura do texto | `max-width: 860px`, `padding: 30px`, centralizado |
| H1 | `2.25em` bold, `line-height 1.2`, borda inferior `1px solid #eee` |
| H2 | `1.75em` bold, `line-height 1.225`, borda inferior `1px solid #eee` |
| H3 | `1.5em` bold, `line-height 1.43` |
| H4 | `1.25em` bold |
| H5 | `1em` bold |
| H6 | `1em` bold, cor `#777` |
| Margem de heading | `1rem` acima e abaixo |
| Espaço entre blocos | `margin: 0.8em 0` em `p`, `blockquote`, `ul`, `ol`, `table` |
| Link | `#4183C4` |
| Citação | borda esquerda `4px solid #dfe2e5`, `padding: 0 15px`, texto `#777777` |
| Código inline | fundo `#f3f4f4`, borda `1px solid #e7eaed`, raio `3px`, `0.9em` |
| Bloco de código | fundo `#f8f8f8`, mesma borda/raio, margem vertical `15px` |
| Tabela | borda `1px solid #dfe2e5`; header e linhas pares fundo `#f8f8f8`; célula `padding: 6px 13px` |
| Regra horizontal | `height: 2px`, fundo `#e7e7e7`, margem `16px 0` |
| Lista | `padding-left: 30px` |
| Seleção | `#B5D6FC` |
| Monoespaçada | `"Lucida Console", Consolas, Courier, monospace` |

Notas de comportamento vistas na tela:
- Citação **não** é itálica e **não** tem fundo — só a barra esquerda e o texto
  cinza. O `AI_CONTEXT.md` diz "fundo ligeiro, itálico"; está errado.
- Lista ordenada respira mais que a não-ordenada (item vira parágrafo).
- Checkbox de tarefa é um checkbox nativo real, marcado em azul.
- Alinhamento de coluna de tabela (`:--`, `:-:`, `--:`) é respeitado.

Arquivo de referência usado no teste visual: `docs/typora-ref.md`.

## Tokens do Tema Escuro (`night.css`, `codeblock.dark.css`, `sourcemode.dark.css`) e GitHub Alerts (`base.css`)

Extraído de `C:\Program Files\Typora\resources\style\themes\night.css`, `night/codeblock.dark.css`, `night/sourcemode.dark.css` e `style/base.css`.

### Tema escuro (`night`)

Raiz: `html { font-size: 16px }`, `line-height: 1.625rem`. Largura máxima do texto (`#write`): `914px` (`1024px` em `>=1400px`, `1200px` em `>=1800px`).

| Token / Elemento | Valor |
|---|---|
| `--bg-color` (fundo do editor) | `#363B40` |
| `--side-bar-bg-color` (gaveta lateral) | `#2E3033` |
| `--text-color` | `#b8bfc6` |
| `--primary-color` | `#6dc1e7` |
| `--select-text-bg-color` (seleção) | `#4a89dc` (texto `#fff`) |
| `--active-file-bg-color` / borda / texto | `rgb(34, 34, 34)` (`#222222`) / `#8d8df0` / `#ffffff` |
| `--item-hover-bg-color` / texto | `#70717d` / `#ffffff` |
| `--control-text-color` / hover | `#b7b7b7` / `#eee` |
| `--search-select-bg-color` | `#428bca` |
| Busca hit (`.md-search-hit`) | `rgba(199, 140, 60, 0.81)`, texto `#eee` |
| Headings (`h1`–`h6`) | `#DEDEDE` (`h4`, `h6`: `white`); tamanhos `2.5 / 1.63 / 1.17 / 1.12 / 0.97 / 0.93rem` |
| Negrito (`strong` / `b` / `th`) | `#DEDEDE` |
| Link (`a`) | `#e0e0e0` sublinhado (hover `#fff`) |
| Citação (`blockquote`) | borda esquerda `2px solid #474d54`, texto `#9DA2A6` |
| Código inline (`code`) | fundo `rgba(0, 0, 0, 0.05)`, `0.875em` |
| Bloco de código (`pre.md-fences` / gutters) | fundo `#333` (`--rawblock-edit-panel-bd: #333`) |
| YAML front matter (`pre.md-meta-block`) | fundo `transparent`, borda inferior `1px dashed #ccc` |
| Tabela (`th`, `td`) | borda `1px solid #474d54` |
| Regra horizontal (`hr`) | altura `2px`, fundo `#474d54`, margem `24px 0` |
| Marca-texto (`mark`) | fundo `#D3D40E` |
| Focus mode (inativos vs foco) | `#686868` vs `#fff` |
| Codeblock syntax (`codeblock.dark.css`) | keyword `#C88FD0`, string `#D26B6B`, comment `#DA924A`, header/def `#8d8df0`, number `#64AB8F`, atom `#84B6CB`, builtin `#f3b3f8`, tag `#7DF46A`, attribute `#7575E4`, variable-2 `#9FBAD5`, variable-3 `#1cc685` |
| Source mode (`sourcemode.dark.css`) | header `#cebcca`, comment/code `#8aa1e1`, string `#A7A7D9`, atom/number `#848695` itálico, link `#95B94B`, linha ativa `rgba(51, 51, 51, 0.72)` |

Implementado no JotMD em `editor/MdTheme.kt` (`MdTokens.isDark` / `MdPalette`).

### GitHub Alerts (`> [!NOTE]` etc.)

Definido em `style/base.css` (borda esquerda `0.25em solid`, `padding: 0 1em`) com overrides de texto em `night.css`:

| Tipo | Claro (`base.css`) | Escuro (`night.css`) |
|---|---|---|
| `note` | `#0969da` | `rgb(47, 129, 247)` (`#2f81f7`) |
| `important` | `#8250df` | `rgb(163, 113, 247)` (`#a371f7`) |
| `warning` | `#9a6700` | `rgb(210, 153, 34)` (`#d29922`) |
| `tip` | `#1f883d` | `#1f883d` *(herda de `base.css`)* |
| `caution` | `#cf222e` | `#cf222e` *(herda de `base.css`)* |

Implementado no JotMD em `:core-md` (`Alerts.kt`, `alertOf`) e `editor/BlockRenderer.kt`.

---

## Inspeção Profunda via `storytold/craft-anything` (`v0.6.1`, modo `--deep`) — Typora `1.13.7`

### 1. Binários nativos, Electron Fuses e `.asar`

- **Executável (`Typora.exe`):** `201,489,536 B` (SHA-256 `b651d55fbac7bc53a20cfe9352d90cb839bb7b8e5f9d8e64da010edbca8a8196`), PE32+ x86_64 GUI sem símbolos, com `nx`, `pie`, `high_entropy_va` e `cfg`. Electron `35.6.0` (arquivo `version`), Typora `1.13.7`, `releaseId: cf170905`, `family: abnerworks`. O recurso PE `INTEGRITY/ELECTRONASAR` guarda o SHA-256 `fe9b0c1ef4e995df5d4e094fe0df9524fdcfed90ff6bc315d6c4353d7fc2f447` que corresponde ao cabeçalho JSON de `app.asar`.
- **Electron Fuses (`0x9d00420`, v1 — 3/7 hardening habilitados):**
  - Habilitados: `RunAsNode=disabled`, `EnableNodeCliInspectArguments=disabled`, `OnlyLoadAppFromAsar=enabled`.
  - Desabilitados: `EnableCookieEncryption=disabled`, `EnableNodeOptionsEnvironmentVariable=enabled`, `EnableEmbeddedAsarIntegrityValidation=disabled`, `GrantFileProtocolExtraPrivileges=enabled`.
- **Desmistificação de `main.node` (`1,055,872 B`, SHA-256 `82e288468b71bd6fe3e167afd05afe3b550e9fb7fcf643ffe6cbc52e2d73c0e2`):**
  - **Não** contém decriptação de `.jsc` nem lógica de licenciamento. É um addon nativo C/C++ MSVC (`linker_version: 14.16`, `is_rust: false`; caminho PDB `C:\Users\leech\Workspace\TypeMark64\launch\build\Release\main.pdb`) que exporta apenas N-API v8 (`napi_register_module_v1`, `node_api_module_get_api_version_v1`).
  - Importa `KERNEL32.dll` (91), `SHELL32.dll` (`SHOpenWithDialog`, `#178`), `ole32.dll` (`CoInitializeEx`, `CoCreateInstance`, `CoTaskMemFree`, `CoUninitialize`) e `node.exe` (40 delay-loaded).
  - `sub_180009a70` verifica `global.navigator` antes de expor bindings ao renderer: `Spellchecker` (`isHunspell`, `isMisspelled`, `setDictionary`, `add`, `remove`, `getAvailableDictionaries`, `getCorrectionsForMisspelling` — usando COM `SpellCheckerFactory` do Windows GUIDs `7ab36653-1796-484b-bdfa-e74f1db7c1dc` / `8e018a9d-2415-4677-bf08-794ea61f94bb`, com fallback Hunspell embutido) e helpers Win32 Shell (`openAs`, `openProperties`).
- **Demais módulos nativos e utilitários:**
  - `fswin.node` (`v3.18.918`, `564,736 B`, exporta `version`, `getAttributes[Sync]`, `setAttributes[Sync]`), `cld.node` (`v2.10.1`, `2,906,752 B`, detecção de idioma Google CLD2), `reg.node` (`native-reg v0.3.6`, `615,424 B`), `spellchecker` (`v4.0.0`, delega para `main.node` + `cld`) e `vscode-ripgrep` (`v1.13.2`, binário Rust/MSVC `rg.exe 13.0.0`, `4,731,520 B`).
- **Pacotes `.asar`:**
  - `app.asar` (`382,346 B`, 3 arquivos: `package.json`, `launch.dist.js` estilo Bytenode e `atom.compiled.dist.jsc` de `379,912 B` com divergência de SHA-256 no cabeçalho ASAR tolerada porque `EnableEmbeddedAsarIntegrityValidation=disabled`).
  - `node_modules.asar` (`9,969,978 B`, 2.943 arquivos, 22 pacotes: `adm-zip 0.5.10`, `chokidar 3.5.3`, `cld 2.10.1`, `electron-dl 1.12.0`, `electron-fetch 1.9.1`, `electron-progressbar 1.2.0`, `extract-zip 1.6.7`, `fs-extra 3.0.1`, `fs-plus 3.1.1`, `fswin 3.18.918`, `getos 3.2.1`, `graceful-fs 4.2.8`, `hjson 1.8.4`, `iconv-lite 0.6.3`, `jschardet 3.1.4`, `lowdb 1.0.0`, `native-reg 0.3.6`, `node-machine-id 1.1.12`, `raven 2.6.4`, `spellchecker 4.0.0`, `sudo-prompt 9.2.1`, `vscode-ripgrep 1.13.2`).
  - `lib.asar` (`8,342,504 B`, 15 arquivos): MathJax `4.1.0` (`tex-svg-full.js` + `xypic.js`), CodeMirror `5.26.0` (core em `frame.js` `Mod 20` + 111 modos em `codemirror/mode.min.js`), Mermaid `11.13.0` + `mermaid-zenuml`, `diagram.min.js` (`lodash 4.14.1`, `Raphael 2.2.0`, `flowchart.js`, `js-sequence-diagrams 0.4.2`), `Jimp v0.3.2` + `image-worker.js`, `pdf-lib` + `pdf-designer.min.js`, `aws-sdk 2.663.0`, `Bootstrap v3.2.0`. `DOMPurify 2.0.7` vive dentro de `frame.js` (`Mod 16`). O Typora **não** usa `turndown` nem `marked` — utiliza parser próprio (`Mod 19`), serializer próprio (`Mod 12`), conversor HTML→Markdown próprio (`Mod 55`) e exportador HTML próprio (`Mod 17`).

### 2. Configurações padrão (`conf.default.json` / `Mod 52`), DOM (`window.html`) e Webviews

- **`conf.default.json`:** chaves `defaultFontFamily`, `autoHideMenuBar: false`, `searchService`, `keyBinding: {}`, `monocolorEmoji: false`, `maxFetchCountOnFileList: 500`, `flags: []`.
- **`FileOption.DEFAULT_OPTIONS` (`Mod 52` em `frame.js`):** `indentSize: 2`, `codeIndentSize: 4`, `wordsPerMinute: 382`, `wordCountDelimiter: 0`, `enableAlert: true`, `enableInlineMath: true`, `gitlabMath: true`, `enableHighlight: false`, `enableAutoSave: false`, `expandSimpleBlock: false`, `applyImageMoveForLocal: true`, `applyImageMoveForWeb: false`.
- **Arquitetura DOM (`window.html`, `91,786 B`, que embute `html/content.html`, além de `html/preview.html`):** `#toc-dropmenu`, `#typora-sidebar` (`#outline-content`, `#file-library`, `#file-info-content`, `#ty-sidebar-footer`), `#top-titlebar` (`24px`), `#megamenu-menu-sidebar` + `#megamenu-content`, `#md-searchpanel`, `#typora-quick-open` (`420px`), `#typora-source` (CodeMirror), `#write` (`role="textbox"`) e `footer.ty-footer` (`30px`, `#outline-btn`, `#toggle-sourceview-btn`, `#footer-word-count`, `#footer-spell-check`).
- **`page-dist/*`:** `setting.html`, `local-setting.html`, `welcome.html` e `license.html` são SPAs React/Webpack carregadas dentro de `<webview nodeintegration="true">`.
- **Canais IPC no JS não compilado (12 canais):** `addBackup`, `execForAll`, `export`, `getPath`, `license.show`, `menu.updateMenu`, `ondragstart`, `setting.put`, `updater.cancelUpdate`, `updater.downloadUpdate`, `updater.skipUpdate`, `window.close`.

### 3. Subsistemas do editor (`appsrc/window/frame.js`)

| Subsistema | Módulos JS | Regras e números observados |
|---|---|---|
| **Edição de Tabelas (`TableEdit`)** | `Mod 49`, `Mod 5e`, `Mod 12` | Barra flutuante `.ty-table-edit` (popover em grade `10x6`, `width 1..40`, `height 1..99`, padrão `3x4`; segundo clique no mesmo alinhamento limpa o alinhamento). Trackers no hover permitem reordenar linhas/colunas por drag-and-drop. Atalhos: `Tab`/`Shift+Tab`, `Enter`, `Ctrl+Enter` (adiciona linha), `Shift+Enter` (`<br />`), `Alt+Setas` (move linha/coluna), `Ctrl+Shift+Backspace`, `Ctrl+T`. `Mod 12` (`toMark`) limita largura de coluna entre `4..60`. |
| **Outline & `[TOC]`** | `Mod 43`, `Mod 44`, `Mod 14`, `Mod 19` | `nodeMap.toc` atualizado com debounce de `300 ms`, sem limite de profundidade (`H1`–`H6`). Scroll spy com busca binária (`100 ms`/`200 ms`). `[TOC]`, `{:toc}` e `[[_TOC_]]` viram widget não editável. |
| **Colagem, Imagens & Drag-Drop** | `Mod 62`, `Mod 55`, `Mod 2f`, `Mod 4b`, `Mod 10` | Prioridade no paste: texto puro/código → `text/markhtml` → `text/html` (`Mod 55` HTML→MD + heurística Markdown de 4 linhas `D`) → `e.files` → bitmap de clipboard salvo como PNG em `typora-user-images/image-YYYYMMDD...png` → `text/plain` (inclui paste de tabela TSV/pipe). Suporte a uploaders (`picgo-app` em `127.0.0.1:36677/upload`, `piclist-app`, `ipic` etc.) e `<video>`/`<audio>` (cópia de asset `<30 MB` em `.textbundle`). |
| **Contagem de Palavras & Read Time** | `Mod 37`, `Mod 29` | Cada caractere CJK (`/[\u3040-\uABFF\uD7A4-\uFAFF]/`) vale 1 palavra; contrações contam como 1 palavra; `quickUpdate` em O(1) faz cache dos blocos excluídos + `requestIdleCallback`; tempo de leitura usa `Math.round(words / 382)` (piso `<= 1 Minute`); contagem da seleção usa debounce de `50 ms`. |
| **Focus, Typewriter & Auto-save/Recovery** | `Mod 48`, `Mod 2b`, `Mod 32`, `Mod 3b`, `Mod 4a`, `Mod 35`, `Mod 56`, `Mod 58` | Focus Mode esmaece blocos inativos via `--blur-text-color` (`#C8C8C8` claro / `#686868` escuro) e imagens com opacidade `0.3` / `0.2`. Typewriter Mode injeta dinamicamente `#write { padding-top }` e `#write:after { height }` em `<style id="write-style">` centralizando a linha ativa (`120 ms` scroll). Backup na janela com debounce `1s..10s`; backup periódico em `draftsRecover/` a cada `10 min` (poda entre `10..40`, limpeza após `4h`); escrita atômica via `.<dir>/.~<basename>` + `Buffer.compare` + `fsync`; divergência DOM/`nodeMap` aciona `tryRecoveryFromDom()` para a área de transferência. |

### 4. Temas adicionais, Tokens UI CSS e Localização PT-BR

- **Temas adicionais empacotados em `style/themes/`:**
  - `newsprint.css`: fundo `#f3f2ee`, texto `#1f0909`, fonte `PT Serif`, acento `#065588`.
  - `pixyll.css`: fundo `#ffffff`, fontes `Merriweather` / `Lato`, acento `#463F5C`.
  - `whitey.css`: fundo `#fefefe`, fonte `Vollkorn`, acento `#2484c1`, `h1`–`h3` centralizados.
  - *(Observação: `gothic.css` não acompanha esta instalação Windows `1.13.7`.)*
- **Tokens UI CSS (`window.css`, `megamenu.css`, `codemirror.css`, `base-control.css`):** `#top-titlebar: 24px`, `header: 28px`, `ty-footer: 30px`, `--sidebar-width: 270px`, `#md-searchpanel` com 3 alturas (`39px` / `74px` / `104px`), `#typora-quick-open: 420px`, hits de busca `rgba(248,192,116,.3)` (claro) / `rgba(199,140,60,0.81)` (escuro) + ocorrência ativa `#000`/`#428bca`; no modo código-fonte (`#typora-source`), `max-width: 800px` e números de linha exibidos apenas na linha ativa, primeira, última e múltiplos de `10n`.
- **Terminologia oficial PT-BR (`locales/pt-BR.lproj/{Front,Menu,Panel,Welcome}.json`):** `"Índices"` (Outline), `"Visualizar em Tópicos"`, `"Contagem de Palavras"`, `"Modo Foco"`, `"Modo Código Fonte"`, `"Buscar e Substituir"`, `"Sem Título"`, `"Tema Claro"` / `"Tema Escuro"`, `"Exportar para PDF"`. *(Alinhado no JotMD: `TabManager` / `DesktopWorkspace` usam `"Sem Título"` e gaveta/sidebar usam `"Índices"`.)*

### 5. Matriz de Paridade Typora vs JotMD (Atualizada)

- **Paridade implementada e verificada em JVM (✅):** invariante de roundtrip + BOM UTF-8; blocos principais + GitHub Alerts + Front Matter + `==highlight==` + `<u>` + wikilinks/tags; `DocOutline` + widget/export `[TOC]` com âncoras HTML (`DocOutline.kt`, `HtmlExport.kt`, `BlockRenderer.kt`); `DocStats` CJK-aware a `382 WPM` (`DocStats.kt`); ops inteligentes (`SmartEnter`, `SmartBackspace`, `SmartTab`, `SmartPair`, `SmartDelete`, `TableOps`, `SourceGutter`) com guarda de eco stale IME; multi-abas (`"Sem Título"`) + autosave; árvore de pasta + FTS4 NFD + `DirectoryTreeWatcher` e Quick Open `Ctrl+P` (`420dp`) no Desktop; busca/substituição com `Aa`, `[ab]` e substituição unitária; 5 presets de tema (`GitHub`, `Newsprint`, `Pixyll`, `Whitey`, `Night`); coluna centralizada `860dp`/`800dp`, Focus/Typewriter Mode e rodapé Desktop `30dp`; exportação HTML e PDF nativo.
- **Paridade parcial deliberada ou pendente (🟡/❌):** foco por bloco (decisão deliberada do JotMD) vs por span; realce sintático avançado de código/source mode e regex na busca; TOC automático no PDF; sintaxes extras (notas de rodapé, links por referência, fórmulas LaTeX); CRUD completo de arquivos/pastas na árvore e barra flutuante rica/drag-and-drop de células em tabela.
