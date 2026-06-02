package io.vidocq.tools.arago.history;

import io.vidocq.tools.arago.persistence.ChatMessage;
import io.vidocq.tools.arago.persistence.HelpRequest;

import java.time.Instant;
import java.util.List;

/**
 * Pure formatters for the past-event exports (arago-spec §11 Phase 5): the room chat as Markdown and
 * the help requests as CSV. No I/O, no entities mutated — unit-testable.
 */
public final class Exports {

    private Exports() {}

    /** Renders the chat history as a Markdown document (title + one bullet per message). */
    public static String chatMarkdown(String title, String pin, List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title == null ? "Room" : title)
                .append(" — chat (PIN ").append(pin).append(")\n\n");
        for (ChatMessage m : messages) {
            sb.append("- **").append(m.getAuthorPseudo()).append("** _(").append(m.getAt()).append(")_");
            if (m.isPersistent()) {
                sb.append(" [persistent]");
            }
            String body = m.getBody() == null ? "" : m.getBody().replace("\r", " ").replace("\n", " ");
            sb.append(" : ").append(body).append('\n');
        }
        return sb.toString();
    }

    /** Renders the help requests as CSV (header + one row each). */
    public static String helpCsv(List<HelpRequest> helps) {
        StringBuilder sb = new StringBuilder("id,attendee,position,status,createdAt,updatedAt,message\n");
        for (HelpRequest h : helps) {
            sb.append(csvCell(h.getId())).append(',')
                    .append(csvCell(h.getAttendeePseudo())).append(',')
                    .append(csvCell(h.getPosition())).append(',')
                    .append(csvCell(h.getStatus() == null ? null : h.getStatus().name())).append(',')
                    .append(csvCell(iso(h.getCreatedAt()))).append(',')
                    .append(csvCell(iso(h.getUpdatedAt()))).append(',')
                    .append(csvCell(h.getMessage())).append('\n');
        }
        return sb.toString();
    }

    /** RFC 4180 cell: always quoted, embedded quotes doubled (so commas/newlines/quotes are safe). */
    static String csvCell(String s) {
        if (s == null) {
            return "\"\"";
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
