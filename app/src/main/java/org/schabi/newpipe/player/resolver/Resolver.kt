package org.schabi.newpipe.player.resolver

open interface Resolver<Source, Product> {
    fun resolve(source: Source): Product?
}
