# JotMD

Leitor e editor de Markdown nativo para Android, com UI/UX inspirada em Typora.
100% Jetpack Compose, sem WebView. Alvo de publicação: F-Droid.

## Estado

**F1 — Leitor.** Abre um `.md` por SAF e renderiza heading, parágrafo, lista,
citação, código e regra horizontal com a tipografia do Typora. Sem edição ainda.
Tabela renderiza como texto cru até a F4. O roadmap está em `AI_CONTEXT.md`.

## Módulos

| Módulo | O quê | Fase |
|---|---|---|
| `:core-md` | Parsing e serialização. Kotlin puro, zero Android, testável em JVM. | F0 ✅ |
| `:editor` | Renderização de blocos em Compose. Edição em F2/F3. | F1 ✅ |
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

- `AI_CONTEXT.md` — decisões de arquitetura e roadmap. Fonte única de verdade.
- `DESIGN.md` — tokens visuais e modelo de foco medidos no Typora real.
- `docs/typora-ref.md` — documento de referência; também é o corpus de teste.
