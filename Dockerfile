# syntax=docker/dockerfile:1.7
#
# Arago runtime image.
# Build prerequisite on the host: `mvn -ntp -B install -DskipTests` (Vidocq SNAPSHOTs
# live in the local M2 / Central Snapshots; vauban 0.1.0 is a Central release). The
# Maven build also produces the Svelte front (frontend-maven-plugin) and copies every
# runtime dependency into arago-server/target/dependency/.
#
FROM bellsoft/liberica-openjre-debian:25 AS runtime

LABEL org.opencontainers.image.source="https://codeberg.org/VidocqTools/arago"
LABEL org.opencontainers.image.title="arago"
LABEL org.opencontainers.image.licenses="Apache-2.0"

# wget for the healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends wget ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# All runtime deps, then the application jar.
COPY arago-server/target/dependency/ /app/lib/
COPY arago-server/target/arago-server-*.jar /app/lib/

# Vidocq + Vauban + Cassini + Chappe + Mansart are JPMS modules — launch via module-path.
# Main class resolved through App.java -> Vidocq.main(args).
ENTRYPOINT ["java", \
    "--module-path", "/app/lib", \
    "--add-modules", "ALL-MODULE-PATH", \
    "-m", "io.vidocq.tools.arago.server/io.vidocq.tools.arago.server.App"]

EXPOSE 8080

# Knock health is mounted under the Cassini /api prefix in Phase 0.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -qO- http://localhost:8080/api/health || exit 1
