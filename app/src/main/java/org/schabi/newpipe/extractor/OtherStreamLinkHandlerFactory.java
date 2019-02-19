package org.schabi.newpipe.extractor;


import android.util.Patterns;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;

public class OtherStreamLinkHandlerFactory extends LinkHandlerFactory {

    private static final OtherStreamLinkHandlerFactory instance = new OtherStreamLinkHandlerFactory();

    private OtherStreamLinkHandlerFactory() {
    }

    public static OtherStreamLinkHandlerFactory getInstance() {
        return instance;
    }

    @Override
    public String getId(String url) throws ParsingException {
        return url;
    }

    @Override
    public String getUrl(String id) throws ParsingException {
        return id;
    }

    @Override
    public boolean onAcceptUrl(String url) throws ParsingException {
        return Patterns.WEB_URL.matcher(url).matches();
    }
}
