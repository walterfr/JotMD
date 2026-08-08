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
