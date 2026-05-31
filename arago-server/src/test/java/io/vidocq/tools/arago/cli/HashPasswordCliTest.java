package io.vidocq.tools.arago.cli;

import io.vidocq.tools.arago.auth.PasswordHasher;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashPasswordCliTest {

    @Test
    void prints_only_a_verifiable_phc_hash() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        HashPasswordCli.run("correct-horse".toCharArray(), new PrintStream(out, true, StandardCharsets.UTF_8));

        String printed = out.toString(StandardCharsets.UTF_8).trim();
        assertTrue(printed.startsWith("$pbkdf2-sha256$"), printed);
        // exactly one line: only the hash, never the clear-text password
        assertEquals(1, printed.lines().count());

        PasswordHasher hasher = new PasswordHasher();
        assertTrue(hasher.verify("correct-horse".toCharArray(), printed));
        assertFalse(hasher.verify("wrong".toCharArray(), printed));
    }
}
