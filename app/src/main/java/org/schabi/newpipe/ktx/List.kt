package org.schabi.newpipe.ktx

fun <A> MutableList<A>.popFirst(filter: (A) -> Boolean): A? {
    val i = indexOfFirst(filter)
    if (i < 0) {
        return null
    }
    return removeAt(i)
}
