package `in`.innovaticshub.notepad.core.domain.util

import java.util.UUID

/**
 * Simple UUID generator for element IDs.
 */
object UuidGenerator {
    fun generate(): String = UUID.randomUUID().toString()
}

/**
 * Extension for generating timestamps for element versioning.
 */
fun timestampNow(): Long = System.currentTimeMillis()

/**
 * Generate a unique ID with optional prefix.
 */
fun generateId(prefix: String = ""): String {
    val uuid = UUID.randomUUID().toString()
    return if (prefix.isNotEmpty()) "$prefix-$uuid" else uuid
}
