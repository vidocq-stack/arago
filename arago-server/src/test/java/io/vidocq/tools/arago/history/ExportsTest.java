package io.vidocq.tools.arago.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import io.vidocq.tools.arago.persistence.ChatMessage;
import io.vidocq.tools.arago.persistence.HelpRequest;
import io.vidocq.tools.arago.persistence.HelpStatus;

import org.junit.jupiter.api.Test;

class ExportsTest {

    @Test
    void csvCellQuotesAndEscapes() {
        assertEquals("\"\"", Exports.csvCell(null));
        assertEquals("\"plain\"", Exports.csvCell("plain"));
        assertEquals("\"a,b\"", Exports.csvCell("a,b"));
        assertEquals("\"he said \"\"hi\"\"\"", Exports.csvCell("he said \"hi\""));
    }

    @Test
    void helpCsvHasHeaderAndRow() {
        HelpRequest h = new HelpRequest("h1", "room", "Ada", "Row 1", "stuck",
                HelpStatus.RESOLVED, Instant.parse("2026-06-02T10:00:00Z"));
        h.setStatus(HelpStatus.RESOLVED);
        h.setUpdatedAt(Instant.parse("2026-06-02T10:05:00Z"));

        String csv = Exports.helpCsv(List.of(h));
        String[] lines = csv.split("\n");
        assertEquals("id,attendee,position,status,createdAt,updatedAt,message", lines[0]);
        assertTrue(lines[1].contains("\"Ada\""));
        assertTrue(lines[1].contains("\"RESOLVED\""));
        assertTrue(lines[1].contains("\"stuck\""));
    }

    @Test
    void chatMarkdownIncludesBodyAndAuthor() {
        ChatMessage m = new ChatMessage("m1", "room", null, "Bob", false, true, "hello world",
                Instant.parse("2026-06-02T10:00:00Z"), null, true);
        String md = Exports.chatMarkdown("My Talk", "123456", List.of(m));
        assertTrue(md.startsWith("# My Talk"));
        assertTrue(md.contains("**Bob**"));
        assertTrue(md.contains("hello world"));
        assertTrue(md.contains("[persistent]"));
    }
}
