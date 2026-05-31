package io.vidocq.tools.arago.acceptance;

import io.cucumber.java.BeforeAll;

/**
 * Cucumber lifecycle: boots the Arago runtime once before any scenario. Teardown is handled by the
 * JVM shutdown hook registered in {@link AragoApp#start()}, so the single process-wide instance is
 * shared by every scenario (and by {@code BootSmokeTest}) without a stop/restart race.
 */
public final class Hooks {

    private Hooks() {}

    @BeforeAll
    public static void bootArago() {
        AragoApp.start();
    }
}
