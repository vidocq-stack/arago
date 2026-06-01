package io.vidocq.tools.arago.rooms;

import io.vidocq.tools.arago.persistence.HelpRequest;

/**
 * JSON projection of a {@link HelpRequest} (cf. arago-spec §4.5) for the speaker LAB panel.
 */
public record HelpView(String id, String attendee, String position, String message,
                       String status, String claimedBy,
                       Integer row, Integer block, Integer seat) {

    public static HelpView of(HelpRequest h) {
        return new HelpView(
                h.getId(),
                h.getAttendeePseudo(),
                h.getPosition(),
                h.getMessage(),
                h.getStatus() == null ? null : h.getStatus().name(),
                h.getClaimedBy(),
                h.getSeatRow(),
                h.getSeatBlockIndex(),
                h.getSeatInBlock());
    }
}
