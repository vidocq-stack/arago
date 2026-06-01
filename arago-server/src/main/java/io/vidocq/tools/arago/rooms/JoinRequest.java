package io.vidocq.tools.arago.rooms;

/**
 * Body of {@code POST /api/rooms/join} (cf. arago-spec §4.2). {@code pin} + {@code pseudo} are
 * mandatory; {@code email} is optional and, when present, requires {@code consentAccepted=true}
 * (the explicit GDPR checkbox, §4.7) and a {@code consentTextVersion}.
 */
public record JoinRequest(String pin, String pseudo, String email,
                          Boolean consentAccepted, String consentTextVersion) {}
