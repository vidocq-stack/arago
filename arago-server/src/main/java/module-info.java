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
    // APT-generated repository impl / CDI proxies importent @Generated (SOURCE-retention).
    requires static java.compiler;

    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.annotation;
    requires jakarta.ws.rs;
    requires jakarta.json.bind;
    requires jakarta.persistence;
    requires jakarta.data;
    requires jakarta.transaction;

    requires io.vidocq.runtime.core;
    requires io.vidocq.runtime.spi;
    requires io.vidocq.runtime.ext.rest.cassini;
    // Requis par la sortie APT cassini-processor ($$CassiniAdapter).
    requires io.vidocq.cassini.api;
    requires io.vidocq.runtime.ext.mansart.pool;
    requires io.vidocq.runtime.ext.mansart.data;
    requires io.vidocq.runtime.ext.mansart.transactions;
    requires io.vidocq.runtime.ext.ravel;
    requires io.vidocq.runtime.ext.knock;

    requires io.vidocq.chappe.api;
    requires io.vidocq.vauban.core;
    requires io.vidocq.mansart.data.core;

    // Migrations DB. Flyway ne fixe pas d'Automatic-Module-Name : nom dérivé du jar.
    requires flyway.core;

    // JAX-RS et CDI réfléchissent sur ressources, beans et entités ; JSON-B sur les records/entités.
    opens io.vidocq.tools.arago.server;
    opens io.vidocq.tools.arago.persistence;
    opens io.vidocq.tools.arago.rest;

    // Flyway (automatic module) scans the classpath for migrations; resources in a named module's
    // packages are encapsulated, so open the migration "package" to make them discoverable.
    opens db.migration;
}
