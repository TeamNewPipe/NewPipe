package org.schabi.newpipe.util

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class EitherTest {
    @Test
    fun testMatchLeft() {
        var leftCalledTimes = 0
        Either.left<String, Int>("A").match(
            ifLeft = { e ->
                assertEquals("A", e)
                leftCalledTimes += 1
            },
            ifRight = { fail() }
        )
        assert(leftCalledTimes == 1)
    }

    @Test
    fun testMatchRight() {
        var rightCalledTimes = 0
        Either.right<String, Int>(5).match(
            ifLeft = { fail() },
            ifRight = { e ->
                assertEquals(5, e)
                rightCalledTimes += 1
            }
        )
        assert(rightCalledTimes == 1)
    }

    @Test
    fun testCovariance() {
        // since values can only be read from an Either, you can e.g. assign Either<String, Int>
        // to Either<CharSequence, Number> because String is a subclass of Object
        val e1: Either<CharSequence, Number> = Either.left<String, Int>("Hello")
        assertEquals("Hello", e1.value)
        val e2: Either<CharSequence, Number> = Either.right<String, Int>(5)
        assertEquals(5, e2.value)
    }
}
