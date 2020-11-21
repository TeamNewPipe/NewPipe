package org.schabi.newpipe.ktx

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import javax.net.ssl.SSLException

class ThrowableExtensionsTest {
    @Test fun `assignable causes`() {
        assertTrue(Throwable().hasAssignableCause(Throwable::class.java))
        assertTrue(Exception().hasAssignableCause(Exception::class.java))
        assertTrue(IOException().hasAssignableCause(Exception::class.java))

        assertTrue(IOException().hasAssignableCause(IOException::class.java))
        assertTrue(Exception(SocketException()).hasAssignableCause(IOException::class.java))
        assertTrue(Exception(IllegalStateException()).hasAssignableCause(RuntimeException::class.java))
        assertTrue(Exception(Exception(IOException())).hasAssignableCause(IOException::class.java))
        assertTrue(Exception(IllegalStateException(Exception(IOException()))).hasAssignableCause(IOException::class.java))
        assertTrue(Exception(IllegalStateException(Exception(SocketException()))).hasAssignableCause(IOException::class.java))
        assertTrue(Exception(IllegalStateException(Exception(SSLException("IO")))).hasAssignableCause(IOException::class.java))
        assertTrue(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasAssignableCause(IOException::class.java))
        assertTrue(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasAssignableCause(RuntimeException::class.java))

        assertTrue(IllegalStateException().hasAssignableCause(Throwable::class.java))
        assertTrue(IllegalStateException().hasAssignableCause(Exception::class.java))
        assertTrue(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasAssignableCause(InterruptedIOException::class.java))
    }

    @Test fun `no assignable causes`() {
        assertFalse(Throwable().hasAssignableCause(Exception::class.java))
        assertFalse(Exception().hasAssignableCause(IOException::class.java))
        assertFalse(Exception(IllegalStateException()).hasAssignableCause(IOException::class.java))
        assertFalse(Exception(NullPointerException()).hasAssignableCause(IOException::class.java))
        assertFalse(Exception(IllegalStateException(Exception(Exception()))).hasAssignableCause(IOException::class.java))
        assertFalse(Exception(IllegalStateException(Exception(SocketException()))).hasAssignableCause(InterruptedIOException::class.java))
        assertFalse(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasAssignableCause(InterruptedException::class.java))
    }

    @Test fun `exact causes`() {
        assertTrue(Throwable().hasExactCause(Throwable::class.java))
        assertTrue(Exception().hasExactCause(Exception::class.java))

        assertTrue(IOException().hasExactCause(IOException::class.java))
        assertTrue(Exception(SocketException()).hasExactCause(SocketException::class.java))
        assertTrue(Exception(Exception(IOException())).hasExactCause(IOException::class.java))
        assertTrue(Exception(IllegalStateException(Exception(IOException()))).hasExactCause(IOException::class.java))
        assertTrue(Exception(IllegalStateException(Exception(SocketException()))).hasExactCause(SocketException::class.java))
        assertTrue(Exception(IllegalStateException(Exception(SSLException("IO")))).hasExactCause(SSLException::class.java))
        assertTrue(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasExactCause(InterruptedIOException::class.java))
        assertTrue(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasExactCause(IllegalStateException::class.java))
    }

    @Test fun `no exact causes`() {
        assertFalse(Throwable().hasExactCause(Exception::class.java))
        assertFalse(Exception().hasExactCause(Throwable::class.java))

        assertFalse(SocketException().hasExactCause(IOException::class.java))
        assertFalse(IllegalStateException().hasExactCause(RuntimeException::class.java))
        assertFalse(Exception(SocketException()).hasExactCause(IOException::class.java))
        assertFalse(Exception(IllegalStateException(Exception(IOException()))).hasExactCause(RuntimeException::class.java))
        assertFalse(Exception(IllegalStateException(Exception(SocketException()))).hasExactCause(IOException::class.java))
        assertFalse(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasExactCause(IOException::class.java))
    }
}
