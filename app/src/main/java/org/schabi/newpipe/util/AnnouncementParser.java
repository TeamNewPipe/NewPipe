package org.schabi.newpipe.util;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class AnnouncementParser {

    public static class ParsedResult {
        public final String contents;
        public final String latestId;

        public ParsedResult(String contents, String latestId) {
            this.contents = contents;
            this.latestId = latestId;
        }
    }


    /**
     * Parses HTML content and returns all text before a certain heading ID
     *
     * @param html The HTML content to parse
     * @param beforeId The ID to stop before (exclusive)
     * @return ParsedResult containing combined contents and latest ID
     */
    public static ParsedResult parseContentsBeforeId(String html, String beforeId) {
        StringBuilder contents = new StringBuilder();
        String latestId = null;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd");

        Document doc = Jsoup.parse(html);
        Elements headings = doc.select("div.markdown-heading");

        for (Element heading : headings) {
            String currentId = heading.selectFirst("h2.heading-element").text();

            // Stop if we reach the target ID
            if (currentId.equals(beforeId) || (beforeId != null && LocalDate.parse(currentId, formatter).isBefore(LocalDate.parse(beforeId, formatter)))) {
                break;
            }

            // Add the section ID in Markdown format
            if (contents.length() > 0) {
                contents.append("\n");
            }
            contents.append("## ").append(currentId).append("\n");

            // Get all content between this heading and the next one
            Element nextElement = heading.nextElementSibling();
            while (nextElement != null && !nextElement.hasClass("markdown-heading")) {
                if (!nextElement.tagName().equals("hr")) {
                    contents.append(nextElement.text()).append("\n");
                }
                nextElement = nextElement.nextElementSibling();
            }
            if (latestId == null) {
                latestId = currentId;
            }
        }

        return new ParsedResult(contents.toString(), latestId);
    }
}
