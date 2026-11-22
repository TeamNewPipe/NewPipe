package org.schabi.newpipe.views

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuTextFormatterTest {
    @Test
    fun usernameIsShownOnlyWhenEnabledAndPresent() {
        assertEquals("Hello", DanmakuTextFormatter.format("Alice", "Hello", false))
        assertEquals("Alice: Hello", DanmakuTextFormatter.format("Alice", "Hello", true))
        assertEquals("Hello", DanmakuTextFormatter.format(" ", "Hello", true))
    }

    @Test
    fun longAuthorNamesAreBoundedForOverlayDisplay() {
        val rendered = DanmakuTextFormatter.format("A".repeat(200), "Hi", true)
        assertEquals(80, rendered.substringBefore(':').length)
    }
}
