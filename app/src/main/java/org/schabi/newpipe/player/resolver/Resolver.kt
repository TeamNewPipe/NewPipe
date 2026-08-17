package org.schabi.newpipe.player.resolver

interface Resolver<Source, Product> {
    fun resolve(source: Source): Product?
}
