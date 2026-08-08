package io.github.walterfr.jotmd.core

/**
 * Documento completo, byte a byte. Contrapartida de [parse]:
 * `serialize(parse(t)) == t` para todo `t`.
 */
fun serialize(doc: DocState): String = buildString {
    append(doc.leading)
    for (block in doc.blocks) {
        append(block.source)
        append(block.trailing)
    }
}
