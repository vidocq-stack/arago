package io.vidocq.tools.arago.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.util.logging.Logger;

/**
 * Applies Flyway migrations at startup. The {@code @Observes @Initialized(ApplicationScoped.class)}
 * trigger fires once the Vauban CDI container is fully started — after the mansart-pool extension
 * has published the pooled {@link DataSource} — so migrations run before the first HTTP request can
 * hit a Mansart repository.
 *
 * <p>Vidocq ships no Flyway extension; this is the deliberate manual wiring (mirrors the
 * {@code SchemaInitializer} pattern of the official Mansart example, swapping ad-hoc DDL for Flyway).
 * Migration scripts live under {@code src/main/resources/db/migration} (Flyway default classpath
 * location). Only migration counts are logged — never SQL payloads.
 */
@ApplicationScoped
public class FlywayMigrator {

    private static final Logger LOG = Logger.getLogger(FlywayMigrator.class.getName());

    // Indirect via Instance<> so the Vauban compile-time index does not reject the @Inject:
    // the DataSource bean is contributed by mansart-pool-extension at runtime.
    @Inject
    Instance<DataSource> dataSourceInstance;

    void onStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
        DataSource dataSource = dataSourceInstance.get();
        var result = Flyway.configure(getClass().getClassLoader())
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        LOG.info(() -> "Flyway: schema up to date (applied "
                + result.migrationsExecuted + " migration(s), target "
                + result.targetSchemaVersion + ")");
    }
}
