package io.vidocq.tools.arago.persistence;

/**
 * Kind of pinned content a speaker can surface in a room (cf. arago-spec §4.4).
 *
 * <ul>
 *   <li>{@code TEXT}   — a short note.</li>
 *   <li>{@code URL}    — a link (front does best-effort preview).</li>
 *   <li>{@code CODE}   — a snippet, with a {@code lang} for highlighting.</li>
 *   <li>{@code SECRET} — a transient secret (demo key/token): copy button, <strong>auto-expires at
 *       room close</strong> and is <strong>never logged</strong> server-side.</li>
 * </ul>
 */
public enum PinType {
    TEXT,
    URL,
    CODE,
    SECRET,
    /** An image attachment: {@code content} is the attachment id, rendered inline for attendees. */
    IMAGE,
    /** A file attachment: {@code content} is the attachment id, offered as a download. */
    FILE,
    /** A QR code: {@code content} is the URL/text; attendees see a scannable QR generated client-side. */
    QR
}
