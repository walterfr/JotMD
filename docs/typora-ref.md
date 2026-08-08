# Título nível 1

## Título nível 2

### Título nível 3

Parágrafo normal com **negrito**, *itálico*, ~~riscado~~, `código inline` e um [link](https://example.com).

> Citação em bloco.
> Segunda linha da citação.

- Item de lista
- Outro item
  - Item aninhado
  - Outro aninhado

1. Primeiro
2. Segundo
3. Terceiro

- [ ] Tarefa pendente
- [x] Tarefa feita

```kotlin
fun splitAt(block: Block, offset: Int): Pair<Block, Block> {
    val before = block.source.substring(0, offset)
    val after = block.source.substring(offset)
    return Block(newId(), before, "\n") to Block(newId(), after, block.trailing)
}
```

| Coluna A | Coluna B | Coluna C |
|----------|:--------:|---------:|
| esquerda | centro   | direita  |
| valor    | valor    | valor    |

---

Texto após regra horizontal.

    bloco de código indentado

Último parágrafo para ver o espaçamento entre blocos.
