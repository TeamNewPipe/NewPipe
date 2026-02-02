package org.schabi.newpipe.util

import androidx.compose.runtime.Stable
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.reflect.safeCast

@Stable
data class Either<A : Any, B : Any>(
    val value: Any,
    val classA: KClass<A>,
    val classB: KClass<B>
) {
    inline fun <R> match(ifLeft: (A) -> R, ifRight: (B) -> R): R {
        return classA.safeCast(value)?.let { ifLeft(it) }
            ?: ifRight(classB.cast(value))
    }

    companion object {
        inline fun <reified A : Any, reified B : Any> left(a: A): Either<A, B> = Either(a, A::class, B::class)
        inline fun <reified A : Any, reified B : Any> right(b: B): Either<A, B> = Either(b, A::class, B::class)
    }
}
