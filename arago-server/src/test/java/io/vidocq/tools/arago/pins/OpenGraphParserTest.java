package io.vidocq.tools.arago.pins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenGraphParserTest {

    @Test
    void extractsOgTagsRegardlessOfAttributeOrderAndDecodesEntities() {
        String html = """
                <html><head>
                  <meta content="Hello &amp; Welcome" property="og:title">
                  <meta property="og:image" content="https://x.test/img.png">
                  <meta name="og:description" content='A &quot;great&quot; talk'>
                  <title>ignored when og:title present</title>
                </head></html>""";

        OpenGraph.Preview p = OpenGraph.parse(html);

        assertEquals("Hello & Welcome", p.title());
        assertEquals("https://x.test/img.png", p.image());
        assertEquals("A \"great\" talk", p.description());
    }

    @Test
    void fallsBackToTitleTagWhenNoOgTitle() {
        OpenGraph.Preview p = OpenGraph.parse("<html><head><title>My Page</title></head></html>");
        assertEquals("My Page", p.title());
        assertNull(p.image());
        assertNull(p.description());
    }

    @Test
    void emptyWhenNoMetadata() {
        assertTrue(OpenGraph.parse("<html><body>nothing here</body></html>").isEmpty());
        assertTrue(OpenGraph.parse(null).isEmpty());
    }
}
