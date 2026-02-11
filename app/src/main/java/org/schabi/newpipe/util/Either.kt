package org.schabi.newpipe.util

import androidx.compose.runtime.Stable
import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.reflect.safeCast

/**
 * Contains either an item of type [A] or an item of type [B]. If [A] is a subclass of [B] or
 * vice versa, [match] may not call the same left/right branch that the [Either] was constructed
 * with. This is because the point of this class is not to represent two possible options of an
 * enum, but to enforce type safety when an object can be of two known types.
 */
@Stable
data class Either<out A : Any, out B : Any>(
    val value: Any,
    val classA: KClass<out A>,
    val classB: KClass<out B>
) {
    /**
     * Calls either [ifLeft] or [ifRight] by casting the [value] this [Either] was built with to
     * either [A] or [B] (first tries [A], and if that fails uses [B] and asserts that the cast
     * succeeds). See [Either] for a possible pitfall of this function.
     */
    inline fun <R> match(ifLeft: (A) -> R, ifRight: (B) -> R): R {
        return classA.safeCast(value)?.let { ifLeft(it) }
            ?: ifRight(classB.cast(value))
    }

    companion object {
        /**
         * Builds an [Either] populated with a value of the left variant type [A].
         */
        inline fun <reified A : Any, reified B : Any> left(a: A): Either<A, B> = Either(a, A::class, B::class)

        /**
         * Builds an [Either] populated with a value of the right variant type [B].
         */
        inline fun <reified A : Any, reified B : Any> right(b: B): Either<A, B> = Either(b, A::class, B::class)
    }
}
