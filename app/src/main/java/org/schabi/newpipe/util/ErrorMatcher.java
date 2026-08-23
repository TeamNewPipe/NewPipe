package org.schabi.newpipe.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ErrorMatcher {

    Element body;

    public static String BASE_URL = "https://github.com/InfinityLoop1308/PipePlay/wiki/FAQ";
    public ErrorMatcher(String html) {
        this.body = Jsoup.parse(html).select("div.markdown-body").first();
    }

    public String getMatch(String kind, String error){
        Elements h2Elements = body.select("h2");

        for (Element h2 : h2Elements) {
            if (h2.text().equals(kind)) {
                Element current = h2.parent().nextElementSibling();
                String h3Path = null;

                // Iterate through next siblings until the next h2 or the end of siblings
                while (current != null && current.select("h2").isEmpty()) {
                    // If an h3 is found, remember it as it might be the one related to the pattern we find later
                    if (!current.select("h3").isEmpty()) {
                        h3Path = current.select("a").first().attr("href");
                    }

                    // If a details tag with the matching pattern is found, output the related h3
                    if (current.tagName().equals("details")){
                        Pattern p = Pattern.compile(current.select("code").text());
                        Matcher m = p.matcher(error);
                        if (m.find()) {
                            if (h3Path != null) {
                                return BASE_URL + h3Path;
                            } else {
                                return null;
                            }
                        }
                    }
                    current = current.nextElementSibling();
                }
            }
        }
        return null;
    }
}
