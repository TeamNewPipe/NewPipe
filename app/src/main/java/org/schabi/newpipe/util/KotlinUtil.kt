package org.schabi.newpipe.util

/**
 * Especially useful to apply some Compose Modifiers only if some condition is met. E.g.
 * ```kt
 * Modifier
 *     .padding(left = 4.dp)
 *     .letIf(someCondition) { padding(right = 4.dp) }
 * ```
 */
inline fun <T> T.letIf(condition: Boolean, block: T.() -> T): T = if (condition) block(this) else this
