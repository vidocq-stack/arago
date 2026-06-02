package io.vidocq.tools.arago.pins;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal, zero-dependency OpenGraph extractor for the URL-pin preview (§4.4). Scans the document's
 * {@code <meta property|name="og:title|og:image|og:description" content="…">} tags (attributes in any
 * order, single or double quotes) and falls back to {@code <title>} for the title. Best-effort: returns
 * whatever it finds, nulls otherwise. Bounded to the first 64 KB (only the head matters).
 */
public final class OpenGraph {

    private OpenGraph() {}

    /** Extracted preview; any field may be null. */
    public record Preview(String title, String image, String description) {
        public boolean isEmpty() {
            return title == null && image == null && description == null;
        }
    }

    private static final int SCAN_CAP = 64 * 1024;
    private static final Pattern META = Pattern.compile("(?is)<meta\\b[^>]*>");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern ATTR =
            Pattern.compile("(?is)([a-zA-Z:_-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");

    public static Preview parse(String html) {
        if (html == null) {
            return new Preview(null, null, null);
        }
        String head = html.length() > SCAN_CAP ? html.substring(0, SCAN_CAP) : html;

        String ogTitle = null;
        String ogImage = null;
        String ogDesc = null;
        Matcher meta = META.matcher(head);
        while (meta.find()) {
            String key = null;
            String content = null;
            Matcher attr = ATTR.matcher(meta.group());
            while (attr.find()) {
                String name = attr.group(1).toLowerCase();
                String value = attr.group(3) != null ? attr.group(3) : attr.group(4);
                if ("property".equals(name) || "name".equals(name)) {
                    key = value == null ? null : value.toLowerCase();
                } else if ("content".equals(name)) {
                    content = value;
                }
            }
            if (key == null || content == null) {
                continue;
            }
            switch (key) {
                case "og:title" -> ogTitle = ogTitle != null ? ogTitle : decode(content);
                case "og:image" -> ogImage = ogImage != null ? ogImage : decode(content);
                case "og:description" -> ogDesc = ogDesc != null ? ogDesc : decode(content);
                default -> { /* ignore other meta */ }
            }
        }
        if (ogTitle == null) {
            Matcher title = TITLE.matcher(head);
            if (title.find()) {
                ogTitle = decode(title.group(1));
            }
        }
        return new Preview(trimToNull(ogTitle), trimToNull(ogImage), trimToNull(ogDesc));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Decodes the handful of HTML entities common in meta content; {@code &amp;} last to avoid double-decoding. */
    private static String decode(String s) {
        return s.replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#x2F;", "/")
                .replace("&amp;", "&");
    }
}
