# JotMD

Leitor e editor de Markdown nativo para Android, com UI/UX inspirada em Typora.
100% Jetpack Compose, sem WebView. Alvo de publicação: F-Droid.

## Estado

**F4 — Elementos avançados.** Abre um `.md` por SAF e edita blocos em Compose,
com tabelas GFM alinhadas, checkboxes de tarefas, preview de imagens por URI
SAF e cercas de código com linguagem. O roadmap está em `AI_CONTEXT.md`.

## Módulos

| Módulo | O quê | Fase |
|---|---|---|
| `:core-md` | Parsing e serialização. Kotlin puro, zero Android, testável em JVM. | F0 ✅ |
| `:editor` | Renderização e edição de blocos em Compose. | F1–F4 ✅ |
| `:app` | Shell: abrir arquivo por SAF, tela do leitor. | F1 ✅ |

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
```

Requer JDK 17 e o Android SDK (`compileSdk 35`, `minSdk 26`).

## Documentos

- `AGENTS.md` — estado atual, regras e armadilhas para agentes de código.
- `AI_CONTEXT.md` — decisões de arquitetura e roadmap. Fonte única de verdade.
- `DESIGN.md` — tokens visuais e modelo de foco medidos no Typora real.
- `docs/typora-ref.md` — documento de referência; também é o corpus de teste.
