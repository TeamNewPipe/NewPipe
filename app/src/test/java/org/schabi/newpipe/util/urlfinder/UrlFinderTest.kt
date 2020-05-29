package org.schabi.newpipe.util.urlfinder

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Keep in mind that patterns from PatternsCompat are used, and they are already being extensively tested.
 */
class UrlFinderTest {
    @Test fun `first url from long text`() {
        val expected = "https://www.youtube.com/playlist?list=PLabcdefghij-ABCDEFGHIJ1234567890_"
        val result = UrlFinder.firstUrlFromInput("""
            |Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. 
            |Eu tincidunt tortor aliquam nulla. URL: https://www.youtube.com/playlist?list=PLabcdefghij-ABCDEFGHIJ1234567890_ Sed dictum consequat dui. 
            |Pharetra diam sit amet nisl suscipit adipiscing bibendum est. 
            |Volutpat sed cras ornare arcu dui vivamus. Nulla posuere sollicitudin aliquam ultrices sagittis. 
            |Amet nisl purus in mollis nunc sed id. Ut aliquam purus sit amet luctus. Sit amet nisl suscipit adipiscing. 
            |Dapibus ultrices in iaculis nunc sed augue lacus viverra. Nisl purus in mollis nunc. 
            |Viverra nibh cras pulvinar mattis. ####!@!@!@!#### Not this one: https://www.youtube.com/playlist?list=SHOULD_NOT Nunc sed blandit libero volutpat. 
            |Nisl tincidunt eget nullam non nisi est sit amet. Purus in massa tempor nec feugiat nisl pretium fusce id. 
            |Vulputate eu scelerisque felis imperdiet proin fermentum leo vel.""".trimMargin())

        assertEquals(expected, result)
    }

    @Test fun `no url from long text`() {
        val result = UrlFinder.firstUrlFromInput("""
            |Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. 
            |Eu tincidunt tortor aliquam nulla. Sed dictum consequat dui. Pharetra diam sit amet nisl suscipit adipiscing bibendum est. 
            |Volutpat sed cras ornare arcu dui vivamus. Nulla posuere sollicitudin aliquam ultrices sagittis. 
            |Amet nisl purus in mollis nunc sed id. Ut aliquam purus sit amet luctus. Sit amet nisl suscipit adipiscing. 
            |Dapibus ultrices in iaculis nunc sed augue lacus viverra. Nisl purus in mollis nunc. 
            |Viverra nibh cras pulvinar mattis. Not this one: sed blandit libero volutpat. 
            |Nisl tincidunt eget nullam non nisi est sit amet. Purus in massa tempor nec feugiat nisl pretium fusce id. 
            |Vulputate eu scelerisque felis imperdiet proin fermentum leo vel.""".trimMargin())

        assertEquals(null, result)
    }

    @Test fun `null and empty input`() {
        assertEquals(null, UrlFinder.firstUrlFromInput(null))
        assertEquals(null, UrlFinder.firstUrlFromInput(""))
        assertEquals(null, UrlFinder.firstUrlFromInput("            "))
    }

    @Test fun `normal urls`() {
        assertEquals("https://www.youtube.com/playlist?list=PLabcdefghij-ABCDEFGHIJ1234567890_",
                UrlFinder.firstUrlFromInput("https://www.youtube.com/playlist?list=PLabcdefghij-ABCDEFGHIJ1234567890_"))

        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                UrlFinder.firstUrlFromInput("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))

        assertEquals("http://www.youtube.com/watch?v=dQw4w9WgXcQ",
                UrlFinder.firstUrlFromInput("http://www.youtube.com/watch?v=dQw4w9WgXcQ"))

        assertEquals("https://www.google.com", UrlFinder.firstUrlFromInput("https://www.google.com"))
        assertEquals("http://www.google.com/test/", UrlFinder.firstUrlFromInput("http://www.google.com/test/"))
        assertEquals("https://www.google.com/test?x=yz#123", UrlFinder.firstUrlFromInput("https://www.google.com/test?x=yz#123"))
        assertEquals("https://208.67.222.222", UrlFinder.firstUrlFromInput("https://208.67.222.222"))
        assertEquals("https://208.67.222.222/", UrlFinder.firstUrlFromInput("https://208.67.222.222/"))
        assertEquals("http://208.67.222.222/", UrlFinder.firstUrlFromInput("http://208.67.222.222/"))
    }

    @Test fun `unknown protocols`() {
        assertEquals(null, UrlFinder.firstUrlFromInput("httpsS://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(null, UrlFinder.firstUrlFromInput("rtsp://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(null, UrlFinder.firstUrlFromInput("ftp://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(null, UrlFinder.firstUrlFromInput("ASDF://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(null, UrlFinder.firstUrlFromInput("https→://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(null, UrlFinder.firstUrlFromInput("file:///etc/fstab"))
        assertEquals(null, UrlFinder.firstUrlFromInput("://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(null, UrlFinder.firstUrlFromInput("www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(null, UrlFinder.firstUrlFromInput("youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun `no ipv6 urls`() {
        assertEquals(null, UrlFinder.firstUrlFromInput("http://[2620:119:35::35]/test"))
        assertEquals(null, UrlFinder.firstUrlFromInput("https://[2620:119:35::35]"))
    }

    @Test fun `random prefixes and suffixes`() {
        assertEquals("https://www.youtube.com/playlist?list=PLabcdefghij-ABCDEFGHIJ1234567890_",
                UrlFinder.firstUrlFromInput("$#!@#@!#https://www.youtube.com/playlist?list=PLabcdefghij-ABCDEFGHIJ1234567890_ @@@@@@@@@@@"))

        assertEquals("https://www.youtube.com/playlist?list=PLabcdefghij-ABCDEFGHIJ1234567890_",
                UrlFinder.firstUrlFromInput("(___\"https://www.youtube.com/playlist?list=PLabcdefghij-ABCDEFGHIJ1234567890_\")))_"))

        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                UrlFinder.firstUrlFromInput("              https://www.youtube.com/watch?v=dQw4w9WgXcQ           "))

        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                UrlFinder.firstUrlFromInput(" ------_---__-https://www.youtube.com/watch?v=dQw4w9WgXcQ !!!!!!"))

        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                UrlFinder.firstUrlFromInput("****https://www.youtube.com/watch?v=dQw4w9WgXcQ _"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                UrlFinder.firstUrlFromInput("https://www.youtube.com/watch?v=dQw4w9WgXcQ\"Not PartOfTheUrl"))
    }
}
