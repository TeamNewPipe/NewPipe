package org.schabi.newpipe.extractor.services.youtube.youtube;

import org.junit.Before;
import org.junit.Test;
import org.schabi.newpipe.Downloader;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.FoundAdException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamUrlIdHandler;

import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;
import static org.junit.Assert.assertTrue;

/**
 * Test for {@link YoutubeStreamUrlIdHandler}
 */
public class YoutubeStreamUrlIdHandlerTest {
    private static String AD_URL = "https://googleads.g.doubleclick.net/aclk?sa=l&ai=C-2IPgeVTWPf4GcOStgfOnIOADf78n61GvKmmobYDrgIQASDj-5MDKAJg9ZXOgeAEoAGgy_T-A8gBAakC2gkpmquIsT6oAwGqBJMBT9BgD5kVgbN0dX602bFFaDw9vsxq-We-S8VkrXVBi6W_e7brZ36GCz1WO3EPEeklYuJjXLUowwCOKsd-8xr1UlS_tusuFJv9iX35xoBHKTRvs8-0aDbfEIm6in37QDfFuZjqgEMB8-tg0Jn_Pf1RU5OzbuU40B4Gy25NUTnOxhDKthOhKBUSZEksCEerUV8GMu10iAXCxquwApIFBggDEAEYAaAGGsgGlIjthrUDgAfItIsBqAemvhvYBwHSCAUIgGEQAbgT6AE&num=1&sig=AOD64_1DybDd4qAm5O7o9UAbTNRdqXXHFQ&ctype=21&video_id=dMO_IXYPZew&client=ca-pub-6219811747049371&adurl=http://www.youtube.com/watch%3Fv%3DdMO_IXYPZew";
    private YoutubeStreamUrlIdHandler urlIdHandler;

    @Before
    public void setUp() throws Exception {
        urlIdHandler = YoutubeStreamUrlIdHandler.getInstance();
        NewPipe.init(Downloader.getInstance());
    }

    @Test(expected = NullPointerException.class)
    public void getIdWithNullAsUrl() throws ParsingException {
        urlIdHandler.getId(null);
    }

    @Test(expected = FoundAdException.class)
    public void getIdForAd() throws ParsingException {
        urlIdHandler.getId(AD_URL);
    }

    @Test
    public void getIdForInvalidUrls() throws ParsingException {
        List<String> invalidUrls = new ArrayList<>(50);
        invalidUrls.add("https://www.youtube.com/watch?v=jZViOEv90d");
        invalidUrls.add("https://www.youtube.com/watchjZViOEv90d");
        invalidUrls.add("https://www.youtube.com/");
        for(String invalidUrl: invalidUrls) {
            Throwable exception = null;
            try {
                urlIdHandler.getId(invalidUrl);
            } catch (ParsingException e) {
                exception = e;
            }
            if(exception == null) {
                fail("Expected ParsingException for url: " + invalidUrl);
            }
        }
    }
    @Test
    public void getId() throws Exception {
        assertEquals("jZViOEv90dI", urlIdHandler.getId("https://www.youtube.com/watch?v=jZViOEv90dI"));
        assertEquals("W-fFHeTX70Q", urlIdHandler.getId("https://www.youtube.com/watch?v=W-fFHeTX70Q"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("https://www.youtube.com/watch?v=jZViOEv90dI?t=100"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("https://WWW.YouTube.com/watch?v=jZViOEv90dI?t=100"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("HTTPS://www.youtube.com/watch?v=jZViOEv90dI?t=100"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("https://youtu.be/jZViOEv90dI?t=9s"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("HTTPS://Youtu.be/jZViOEv90dI?t=9s"));
        assertEquals("uEJuoEs1UxY", urlIdHandler.getId("http://www.youtube.com/watch_popup?v=uEJuoEs1UxY"));
        assertEquals("uEJuoEs1UxY", urlIdHandler.getId("http://www.Youtube.com/watch_popup?v=uEJuoEs1UxY"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("https://www.youtube.com/embed/jZViOEv90dI"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("https://www.youtube-nocookie.com/embed/jZViOEv90dI"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("http://www.youtube.com/watch?v=jZViOEv90dI"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("http://youtube.com/watch?v=jZViOEv90dI"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("http://youtu.be/jZViOEv90dI?t=9s"));
        assertEquals("7_WWz2DSnT8", urlIdHandler.getId("https://youtu.be/7_WWz2DSnT8"));
        assertEquals("oy6NvWeVruY", urlIdHandler.getId("https://m.youtube.com/watch?v=oy6NvWeVruY"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("http://www.youtube.com/embed/jZViOEv90dI"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("http://www.Youtube.com/embed/jZViOEv90dI"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("http://www.youtube-nocookie.com/embed/jZViOEv90dI"));
        assertEquals("EhxJLojIE_o", urlIdHandler.getId("http://www.youtube.com/attribution_link?a=JdfC0C9V6ZI&u=%2Fwatch%3Fv%3DEhxJLojIE_o%26feature%3Dshare"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("vnd.youtube://www.youtube.com/watch?v=jZViOEv90dI"));
        assertEquals("jZViOEv90dI", urlIdHandler.getId("vnd.youtube:jZViOEv90dI"));

        // Shared links
        String sharedId = "7JIArTByb3E";
        String realId = "Q7JsK50NGaA";
        assertEquals(realId, urlIdHandler.getId("vnd.youtube://www.YouTube.com/shared?ci=" + sharedId + "&feature=twitter-deep-link"));
        assertEquals(realId, urlIdHandler.getId("vnd.youtube://www.youtube.com/shared?ci=" + sharedId ));
        assertEquals(realId, urlIdHandler.getId("https://www.youtube.com/shared?ci=7JIArTByb3E"));

    }


    @Test
    public void testAcceptUrl() {
        assertTrue(urlIdHandler.acceptUrl("https://www.youtube.com/watch?v=jZViOEv90dI"));
        assertTrue(urlIdHandler.acceptUrl("https://www.youtube.com/watch?v=jZViOEv90dI?t=100"));
        assertTrue(urlIdHandler.acceptUrl("https://WWW.YouTube.com/watch?v=jZViOEv90dI?t=100"));
        assertTrue(urlIdHandler.acceptUrl("HTTPS://www.youtube.com/watch?v=jZViOEv90dI?t=100"));
        assertTrue(urlIdHandler.acceptUrl("https://youtu.be/jZViOEv90dI?t=9s"));
        //assertTrue(urlIdHandler.acceptUrl("https://www.youtube.com/watch/jZViOEv90dI"));
        assertTrue(urlIdHandler.acceptUrl("https://www.youtube.com/embed/jZViOEv90dI"));
        assertTrue(urlIdHandler.acceptUrl("https://www.youtube-nocookie.com/embed/jZViOEv90dI"));
        assertTrue(urlIdHandler.acceptUrl("http://www.youtube.com/watch?v=jZViOEv90dI"));
        assertTrue(urlIdHandler.acceptUrl("http://youtu.be/jZViOEv90dI?t=9s"));
        assertTrue(urlIdHandler.acceptUrl("http://www.youtube.com/embed/jZViOEv90dI"));
        assertTrue(urlIdHandler.acceptUrl("http://www.youtube-nocookie.com/embed/jZViOEv90dI"));
        assertTrue(urlIdHandler.acceptUrl("http://www.youtube.com/attribution_link?a=JdfC0C9V6ZI&u=%2Fwatch%3Fv%3DEhxJLojIE_o%26feature%3Dshare"));
        assertTrue(urlIdHandler.acceptUrl("vnd.youtube://www.youtube.com/watch?v=jZViOEv90dI"));
        assertTrue(urlIdHandler.acceptUrl("vnd.youtube:jZViOEv90dI"));

        assertTrue(urlIdHandler.acceptUrl("vnd.youtube:jZViOEv90dI"));

        String sharedId = "8A940MXKFmQ";
        assertTrue(urlIdHandler.acceptUrl("vnd.youtube://www.youtube.com/shared?ci=" + sharedId + "&feature=twitter-deep-link"));
        assertTrue(urlIdHandler.acceptUrl("vnd.youtube://www.youtube.com/shared?ci=" + sharedId ));
        assertTrue(urlIdHandler.acceptUrl("https://www.youtube.com/shared?ci=" + sharedId));
    }
}