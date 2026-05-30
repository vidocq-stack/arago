package io.vidocq.tools.arago.server;

import io.vidocq.runtime.core.Vidocq;

import java.io.IOException;
import java.util.logging.LogManager;

/**
 * Arago entry point. Delegates to {@link Vidocq#main(String[])}, which discovers and
 * orchestrates every extension on the module path (Mansart pool/data, Cassini REST,
 * Knock health, Chappe HTTP). Schema migrations run via {@link FlywayMigrator} once the
 * CDI container — and the pooled {@code DataSource} — are up.
 */
public final class App {

    private App() {}

    public static void main(String[] args) throws IOException {
        try (var in = App.class.getResourceAsStream("/logging.properties")) {
            if (in != null) {
                LogManager.getLogManager().readConfiguration(in);
            }
        }
        Vidocq.main(args);
    }
}
