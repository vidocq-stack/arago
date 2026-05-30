package io.vidocq.tools.arago.rest;

/** Liveness payload returned by {@link HealthResource}. Top-level record so the Cassini adapter
 *  and JSON-B resolve it cleanly (nested records tripped serialization). */
public record Health(String status) {}
