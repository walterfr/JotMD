# JotMD

Leitor e editor de Markdown nativo para Android, com UI/UX inspirada em Typora.
100% Jetpack Compose, sem WebView. Alvo de publicação: F-Droid.

## Estado

**F0 — Fundação.** `:core-md` particiona e serializa Markdown com garantia de
roundtrip byte a byte. O restante do roadmap está em `AI_CONTEXT.md`.

## Módulos

| Módulo | O quê | Fase |
|---|---|---|
| `:core-md` | Parsing e serialização. Kotlin puro, zero Android, testável em JVM. | F0 ✅ |
| `:editor` | Motor de blocos em Compose, navegação, atalhos. | F1+ |
| `:app` | Shell: navegação, temas, SAF, preferências. | F1+ |

## A invariante

```kotlin
serialize(parse(texto)) == texto   // byte a byte, para todo texto
```

Quebrar isso corrompe o arquivo do usuário em silêncio. `parse` particiona o
texto original em fatias que não se sobrepõem e cobrem tudo, então a invariante
vale por construção — `RoundtripTest` a guarda, não a produz.

## Build

```bash
./gradlew :core-md:test
```

Requer JDK 17.

## Documentos

- `AI_CONTEXT.md` — decisões de arquitetura e roadmap. Fonte única de verdade.
- `DESIGN.md` — tokens visuais e modelo de foco medidos no Typora real.
- `docs/typora-ref.md` — documento de referência; também é o corpus de teste.
