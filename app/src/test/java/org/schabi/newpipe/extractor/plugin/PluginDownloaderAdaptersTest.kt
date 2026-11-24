/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.extractor.plugin

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response

class PluginDownloaderAdaptersTest {
    /** Models an interface after its inherited Android default has been desugared away. */
    interface MissingErrorDefault : Downloader.AsyncCallback {
        override fun onError(error: Exception)
    }

    @Test
    fun hostCallbacksRemainUntouched() {
        val callback = object : Downloader.AsyncCallback {
            override fun onSuccess(response: Response) = Unit
        }
        assertSame(callback, PluginDownloaderAdapters.asyncCallback(callback))
    }

    @Test
    fun foreignCallbacksReceiveTheOriginalSuccessAndFailure() {
        var receivedResponse: Response? = null
        var receivedError: Exception? = null
        val callback = foreign { name, argument ->
            when (name) {
                "onSuccess" -> receivedResponse = argument as Response
                "onError" -> receivedError = argument as Exception
                else -> error("Unexpected callback method $name")
            }
        }
        val adapted = PluginDownloaderAdapters.asyncCallback(callback)
        assertNotSame(callback, adapted)
        val response = Response(200, "OK", emptyMap(), "{}", "{}".toByteArray(), "https://fixture.example/")
        val error = IOException("Expected request failure")
        adapted.onSuccess(response)
        adapted.onError(error)
        assertSame(response, receivedResponse)
        assertSame(error, receivedError)
    }

    @Test
    fun failuresInsideAConcretePluginCallbackAreNotMistakenForAMissingDefault() {
        val failure = AbstractMethodError("A dependency called by the callback is missing")
        val callback = foreign { _, _ -> throw failure }
        val actual = assertThrows(AbstractMethodError::class.java) {
            PluginDownloaderAdapters.asyncCallback(callback).onError(IOException("Request failed"))
        }
        assertSame(failure, actual)
    }

    @Test
    fun anActuallyMissingForeignDefaultInvokesTheHostsOriginalErrorHandler() {
        val loader = object : ClassLoader(Downloader.AsyncCallback::class.java.classLoader) {
            fun instantiate(bytes: ByteArray): Downloader.AsyncCallback = defineClass(null, bytes, 0, bytes.size).getDeclaredConstructor().newInstance() as Downloader.AsyncCallback
        }
        val callback = loader.instantiate(callbackWithoutErrorMethod())
        var printed = 0
        val failure = object : Exception("Expected callback failure") {
            override fun printStackTrace() {
                printed++
            }
        }
        // A concrete class compiled before an interface change may legitimately lack a
        // now-abstract method. JVM linkage fails just like the separately built Android DEX.
        assertThrows(AbstractMethodError::class.java) { callback.onError(failure) }
        assertEquals(0, printed)
        PluginDownloaderAdapters.asyncCallback(callback).onError(failure)
        assertEquals(1, printed)
    }

    private fun foreign(invoke: (String, Any?) -> Unit): Downloader.AsyncCallback {
        val loader = object : ClassLoader(Downloader.AsyncCallback::class.java.classLoader) {}
        return Proxy.newProxyInstance(loader, arrayOf(Downloader.AsyncCallback::class.java)) { _, method, arguments ->
            invoke(method.name, arguments?.single())
            null
        } as Downloader.AsyncCallback
    }

    /** A tiny test-owned Java 8 class: public constructor + onSuccess, no onError bridge. */
    private fun callbackWithoutErrorMethod(): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(0xcafebabe.toInt())
            output.writeShort(0)
            output.writeShort(52)
            output.writeShort(14)
            fun utf8(value: String) {
                output.writeByte(1)
                output.writeUTF(value)
            }
            fun classReference(name: Int) {
                output.writeByte(7)
                output.writeShort(name)
            }
            utf8("org/schabi/newpipe/extractor/plugin/fixture/CallbackWithoutError") // 1
            classReference(1) // 2
            utf8("java/lang/Object") // 3
            classReference(3) // 4
            utf8(MissingErrorDefault::class.java.name.replace('.', '/')) // 5
            classReference(5) // 6
            utf8("<init>") // 7
            utf8("()V") // 8
            output.writeByte(12) // 9: constructor name/type
            output.writeShort(7)
            output.writeShort(8)
            output.writeByte(10) // 10: Object constructor
            output.writeShort(4)
            output.writeShort(9)
            utf8("Code") // 11
            utf8("onSuccess") // 12
            utf8("(Lorg/schabi/newpipe/extractor/downloader/Response;)V") // 13
            output.writeShort(0x21) // public, super
            output.writeShort(2)
            output.writeShort(4)
            output.writeShort(1)
            output.writeShort(6)
            output.writeShort(0) // no fields
            output.writeShort(2)
            fun method(name: Int, descriptor: Int, stack: Int, locals: Int, code: ByteArray) {
                output.writeShort(1) // public
                output.writeShort(name)
                output.writeShort(descriptor)
                output.writeShort(1)
                output.writeShort(11) // Code attribute
                output.writeInt(12 + code.size)
                output.writeShort(stack)
                output.writeShort(locals)
                output.writeInt(code.size)
                output.write(code)
                output.writeShort(0) // exceptions
                output.writeShort(0) // code attributes
            }
            method(7, 8, 1, 1, byteArrayOf(0x2a, 0xb7.toByte(), 0, 10, 0xb1.toByte()))
            method(12, 13, 0, 2, byteArrayOf(0xb1.toByte()))
            output.writeShort(0) // class attributes
        }
    }.toByteArray()
}
