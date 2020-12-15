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
        assertTrue(Throwable().hasAssignableCause<Throwable>())
        assertTrue(Exception().hasAssignableCause<Exception>())
        assertTrue(IOException().hasAssignableCause<Exception>())

        assertTrue(IOException().hasAssignableCause<IOException>())
        assertTrue(Exception(SocketException()).hasAssignableCause<IOException>())
        assertTrue(Exception(IllegalStateException()).hasAssignableCause<RuntimeException>())
        assertTrue(Exception(Exception(IOException())).hasAssignableCause<IOException>())
        assertTrue(Exception(IllegalStateException(Exception(IOException()))).hasAssignableCause<IOException>())
        assertTrue(Exception(IllegalStateException(Exception(SocketException()))).hasAssignableCause<IOException>())
        assertTrue(Exception(IllegalStateException(Exception(SSLException("IO")))).hasAssignableCause<IOException>())
        assertTrue(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasAssignableCause<IOException>())
        assertTrue(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasAssignableCause<RuntimeException>())

        assertTrue(IllegalStateException().hasAssignableCause<Throwable>())
        assertTrue(IllegalStateException().hasAssignableCause<Exception>())
        assertTrue(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasAssignableCause<InterruptedIOException>())
    }

    @Test fun `no assignable causes`() {
        assertFalse(Throwable().hasAssignableCause<Exception>())
        assertFalse(Exception().hasAssignableCause<IOException>())
        assertFalse(Exception(IllegalStateException()).hasAssignableCause<IOException>())
        assertFalse(Exception(NullPointerException()).hasAssignableCause<IOException>())
        assertFalse(Exception(IllegalStateException(Exception(Exception()))).hasAssignableCause<IOException>())
        assertFalse(Exception(IllegalStateException(Exception(SocketException()))).hasAssignableCause<InterruptedIOException>())
        assertFalse(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasAssignableCause<InterruptedException>())
    }

    @Test fun `exact causes`() {
        assertTrue(Throwable().hasExactCause<Throwable>())
        assertTrue(Exception().hasExactCause<Exception>())

        assertTrue(IOException().hasExactCause<IOException>())
        assertTrue(Exception(SocketException()).hasExactCause<SocketException>())
        assertTrue(Exception(Exception(IOException())).hasExactCause<IOException>())
        assertTrue(Exception(IllegalStateException(Exception(IOException()))).hasExactCause<IOException>())
        assertTrue(Exception(IllegalStateException(Exception(SocketException()))).hasExactCause<SocketException>())
        assertTrue(Exception(IllegalStateException(Exception(SSLException("IO")))).hasExactCause<SSLException>())
        assertTrue(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasExactCause<InterruptedIOException>())
        assertTrue(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasExactCause<IllegalStateException>())
    }

    @Test fun `no exact causes`() {
        assertFalse(Throwable().hasExactCause<Exception>())
        assertFalse(Exception().hasExactCause<Throwable>())

        assertFalse(SocketException().hasExactCause<IOException>())
        assertFalse(IllegalStateException().hasExactCause<RuntimeException>())
        assertFalse(Exception(SocketException()).hasExactCause<IOException>())
        assertFalse(Exception(IllegalStateException(Exception(IOException()))).hasExactCause<RuntimeException>())
        assertFalse(Exception(IllegalStateException(Exception(SocketException()))).hasExactCause<IOException>())
        assertFalse(Exception(IllegalStateException(Exception(InterruptedIOException()))).hasExactCause<IOException>())
    }
}
