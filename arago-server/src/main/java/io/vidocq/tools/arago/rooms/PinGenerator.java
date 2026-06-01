package io.vidocq.tools.arago.rooms;

import io.vidocq.tools.arago.persistence.RoomRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.SecureRandom;

/**
 * Mints the 6-digit room PIN attendees type to join (cf. arago-spec §4.1). The PIN must be unique
 * among joinable rooms (a partial unique index covers {@code DRAFT + ACTIVE}); we check against the
 * live set and retry on the rare collision. {@link SecureRandom} avoids guessable sequences.
 */
@ApplicationScoped
public class PinGenerator {

    private static final int PIN_DIGITS = 6;
    private static final int BOUND = 1_000_000; // 000000..999999
    private static final int MAX_ATTEMPTS = 50;

    private final SecureRandom random = new SecureRandom();

    @Inject
    RoomRepository rooms;

    /** Returns a fresh 6-digit PIN not currently held by a live room. */
    public String next() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String pin = String.format("%0" + PIN_DIGITS + "d", random.nextInt(BOUND));
            if (rooms.findByPin(pin).isEmpty()) {
                return pin;
            }
        }
        throw new IllegalStateException("Could not allocate a free room PIN after " + MAX_ATTEMPTS
                + " attempts — the live room set is unexpectedly dense");
    }
}
