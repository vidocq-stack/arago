/**
 * Arago server — runtime Vidocq : REST Cassini + Mansart Data (PostgreSQL) + bootstrap.
 *
 * <p>Phase 0 : démarre Chappe, expose {@code /health} (knock), applique les migrations
 * Flyway au boot puis sert le front Svelte statique. Le modèle métier complet (rooms,
 * chat, pins, help requests…) arrive aux phases suivantes (cf. arago-spec §11).
 */
module io.vidocq.tools.arago.server {
    requires java.logging;
    requires java.sql;
    // JDK HttpClient — used by the pins OpenGraph preview fetcher (io.vidocq.tools.arago.pins).
    requires java.net.http;
    // APT-generated repository impl / CDI proxies importent @Generated (SOURCE-retention).
    requires static java.compiler;

    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.annotation;
    requires jakarta.ws.rs;
    requires jakarta.json.bind;
    // JSON-P (champollion) — used to build/parse Arago-signed HS256 JWT payloads (io.vidocq.tools.arago.auth).
    requires jakarta.json;
    requires jakarta.persistence;
    requires jakarta.data;
    requires jakarta.transaction;

    requires io.vidocq.runtime.core;
    requires io.vidocq.runtime.spi;
    requires io.vidocq.runtime.extensions.jakartaee.core.cassini;
    // Requis par la sortie APT cassini-processor ($$CassiniAdapter).
    requires io.vidocq.cassini.api;
    requires io.vidocq.runtime.extensions.jakartaee.web.mansart.pool;
    requires io.vidocq.runtime.extensions.jakartaee.web.mansart.data;
    requires io.vidocq.runtime.extensions.jakartaee.web.mansart.transactions;
    requires io.vidocq.runtime.extensions.microprofile.ravel;
    // MP Config API — @ConfigProperty injection of superadmin/attendee settings (resolved by ravel).
    requires org.eclipse.microprofile.config;
    requires io.vidocq.runtime.extensions.microprofile.knock;
    // MicroProfile Metrics (dirac) — @Gauge for the active-rooms metric exposed at /metrics (§12).
    requires microprofile.metrics.api;

    requires io.vidocq.chappe.api;
    requires io.vidocq.vauban.core;
    requires io.vidocq.mansart.data.core;

    // Schema migrations via the Vidocq Flyway extension (the runner + the Flyway backend). The
    // extension pulls flyway.core / flyway.database.postgresql transitively; the migration runs at
    // boot from vidocq.pool.* — no FlywayMigrator class needed any more.
    requires io.vidocq.runtime.extensions.essentials.migration;
    requires io.vidocq.runtime.extensions.essentials.migration.flyway;

    // JAX-RS et CDI réfléchissent sur ressources, beans et entités ; JSON-B sur les records/entités.
    opens io.vidocq.tools.arago.server;
    opens io.vidocq.tools.arago.persistence;
    opens io.vidocq.tools.arago.rest;
    opens io.vidocq.tools.arago.rooms;
    opens io.vidocq.tools.arago.ws;
    opens io.vidocq.tools.arago.metrics;
    opens io.vidocq.tools.arago.admin;
    opens io.vidocq.tools.arago.speaker;
    opens io.vidocq.tools.arago.profile;
    opens io.vidocq.tools.arago.mail;
    opens io.vidocq.tools.arago.pins;
    opens io.vidocq.tools.arago.attachments;

    // Flyway (automatic module) scans the classpath for migrations; resources in a named module's
    // packages are encapsulated, so open the migration "package" to make them discoverable.
    opens db.migration;
}
