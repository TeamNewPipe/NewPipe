package org.schabi.newpipe.util

import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import javax.net.ssl.SSLException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.util.ExceptionUtils.Companion.hasAssignableCause
import org.schabi.newpipe.util.ExceptionUtils.Companion.hasExactCause

class ExceptionUtilsTest {
    @Test fun `assignable causes`() {
        assertTrue(hasAssignableCause(Throwable(), Throwable::class.java))
        assertTrue(hasAssignableCause(Exception(), Exception::class.java))
        assertTrue(hasAssignableCause(IOException(), Exception::class.java))

        assertTrue(hasAssignableCause(IOException(), IOException::class.java))
        assertTrue(hasAssignableCause(Exception(SocketException()), IOException::class.java))
        assertTrue(hasAssignableCause(Exception(IllegalStateException()), RuntimeException::class.java))
        assertTrue(hasAssignableCause(Exception(Exception(IOException())), IOException::class.java))
        assertTrue(hasAssignableCause(Exception(IllegalStateException(Exception(IOException()))), IOException::class.java))
        assertTrue(hasAssignableCause(Exception(IllegalStateException(Exception(SocketException()))), IOException::class.java))
        assertTrue(hasAssignableCause(Exception(IllegalStateException(Exception(SSLException("IO")))), IOException::class.java))
        assertTrue(hasAssignableCause(Exception(IllegalStateException(Exception(InterruptedIOException()))), IOException::class.java))
        assertTrue(hasAssignableCause(Exception(IllegalStateException(Exception(InterruptedIOException()))), RuntimeException::class.java))

        assertTrue(hasAssignableCause(IllegalStateException(), Throwable::class.java))
        assertTrue(hasAssignableCause(IllegalStateException(), Exception::class.java))
        assertTrue(hasAssignableCause(Exception(IllegalStateException(Exception(InterruptedIOException()))), InterruptedIOException::class.java))
    }

    @Test fun `no assignable causes`() {
        assertFalse(hasAssignableCause(Throwable(), Exception::class.java))
        assertFalse(hasAssignableCause(Exception(), IOException::class.java))
        assertFalse(hasAssignableCause(Exception(IllegalStateException()), IOException::class.java))
        assertFalse(hasAssignableCause(Exception(NullPointerException()), IOException::class.java))
        assertFalse(hasAssignableCause(Exception(IllegalStateException(Exception(Exception()))), IOException::class.java))
        assertFalse(hasAssignableCause(Exception(IllegalStateException(Exception(SocketException()))), InterruptedIOException::class.java))
        assertFalse(hasAssignableCause(Exception(IllegalStateException(Exception(InterruptedIOException()))), InterruptedException::class.java))
    }

    @Test fun `exact causes`() {
        assertTrue(hasExactCause(Throwable(), Throwable::class.java))
        assertTrue(hasExactCause(Exception(), Exception::class.java))

        assertTrue(hasExactCause(IOException(), IOException::class.java))
        assertTrue(hasExactCause(Exception(SocketException()), SocketException::class.java))
        assertTrue(hasExactCause(Exception(Exception(IOException())), IOException::class.java))
        assertTrue(hasExactCause(Exception(IllegalStateException(Exception(IOException()))), IOException::class.java))
        assertTrue(hasExactCause(Exception(IllegalStateException(Exception(SocketException()))), SocketException::class.java))
        assertTrue(hasExactCause(Exception(IllegalStateException(Exception(SSLException("IO")))), SSLException::class.java))
        assertTrue(hasExactCause(Exception(IllegalStateException(Exception(InterruptedIOException()))), InterruptedIOException::class.java))
        assertTrue(hasExactCause(Exception(IllegalStateException(Exception(InterruptedIOException()))), IllegalStateException::class.java))
    }

    @Test fun `no exact causes`() {
        assertFalse(hasExactCause(Throwable(), Exception::class.java))
        assertFalse(hasExactCause(Exception(), Throwable::class.java))

        assertFalse(hasExactCause(SocketException(), IOException::class.java))
        assertFalse(hasExactCause(IllegalStateException(), RuntimeException::class.java))
        assertFalse(hasExactCause(Exception(SocketException()), IOException::class.java))
        assertFalse(hasExactCause(Exception(IllegalStateException(Exception(IOException()))), RuntimeException::class.java))
        assertFalse(hasExactCause(Exception(IllegalStateException(Exception(SocketException()))), IOException::class.java))
        assertFalse(hasExactCause(Exception(IllegalStateException(Exception(InterruptedIOException()))), IOException::class.java))
    }
}
