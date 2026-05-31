package io.vidocq.tools.arago.cli;

import io.vidocq.tools.arago.auth.PasswordHasher;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * {@code arago hash-password} — generates the PBKDF2 hash for the superadmin password (cf. arago-spec
 * §4.8/§10.2). Reads the password without echoing it (masked console prompt, or the first stdin line
 * when piped) and prints <em>only</em> the self-describing PHC hash; the clear-text password is never
 * echoed nor logged. Put the printed value in {@code ARAGO_SUPERADMIN_PASSWORD_HASH}.
 *
 * <pre>
 * java -m io.vidocq.tools.arago.server/io.vidocq.tools.arago.cli.HashPasswordCli
 * echo -n 's3cret' | java -m io.vidocq.tools.arago.server/io.vidocq.tools.arago.cli.HashPasswordCli
 * </pre>
 */
public final class HashPasswordCli {

    private HashPasswordCli() {}

    public static void main(String[] args) throws IOException {
        char[] password = readPassword();
        try {
            run(password, System.out);
        } finally {
            Arrays.fill(password, '\0'); // do not keep the clear-text around
        }
    }

    /** Hashes {@code password} and prints the PHC string (and nothing else) to {@code out}. */
    static void run(char[] password, PrintStream out) {
        out.println(new PasswordHasher().hash(password));
    }

    private static char[] readPassword() throws IOException {
        Console console = System.console();
        if (console != null) {
            char[] pw = console.readPassword("Superadmin password: ");
            if (pw == null || pw.length == 0) {
                throw new IllegalArgumentException("empty password");
            }
            return pw;
        }
        // Non-interactive (piped) fallback — read the first stdin line; no masking is possible.
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line = reader.readLine();
        if (line == null || line.isEmpty()) {
            throw new IllegalArgumentException("empty password");
        }
        return line.toCharArray();
    }
}
