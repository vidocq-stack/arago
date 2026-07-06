---
name: verify
description: Lancer Arago localement et vérifier un changement de bout en bout (REST + WebSocket + front)
---

# Vérifier Arago en local

## Lancer l'app (mode dev, sans docker compose)

```bash
mvn -ntp -q install -DskipTests        # front Vite inclus ; requis si arago-web a changé
mvn -ntp -pl arago-server vidocq:dev   # Postgres jetable (Testcontainers, port hôte 65123), app sur :8080
```

- Credentials dev seedés : speakers `speakera@oidc.test` / `speakerb@oidc.test` (mdp `pw`),
  superadmin `root` / `arago-dev` sur `/admin`.
- Prêt quand `curl -s localhost:8080/health` répond 200 (~60-90 s à froid, pull d'images inclus).
- **Boot flaky connu** : le child JVM peut crasher au démarrage avec
  `UnsatisfiedResolutionException: No bean found for type …` (aléa de découverte de beans Vauban).
  Le watcher dev reste vivant : `touch` un fichier de `arago-server/src/main/java` pour forcer un
  redémarrage, qui passe en général du premier coup.

## Piloter la surface

- **REST** : `POST /api/speaker/login {email,password}` → `{token}` ; `POST /api/rooms {title,mode:"CONF"}`
  (Bearer speaker) → `{pin}` ; `POST /api/rooms/join {pin,pseudo}` → `{token}` attendee.
- **WebSocket** : `ws://localhost:8080/ws/rooms/{pin}?token=…`. Un client JDK single-file
  (`java Probe.java`, `java.net.http.WebSocket.Listener`) suffit pour observer frames texte et PING
  (keepalive serveur : 1 PING / 25 s, clé `arago.ws.ping-seconds`).
- **Front sans extension Chrome** : Chrome headless piloté en CDP pur Node (zéro dépendance) —
  `--headless=new --remote-debugging-port=9222`, `PUT /json/new?url=…`, puis `Runtime.evaluate`.
  Les inputs Svelte 5 se remplissent par `el.value = …; el.dispatchEvent(new Event('input',{bubbles:true}))`.
  Sélecteurs stables : `data-testid` (`join-pin`, `join-pseudo`, `join-submit`, `room`, `notice`,
  `my-pseudo`, `join-error`).
- **Simuler une coupure proxy/réseau** : petit proxy TCP Node entre le navigateur et :8080 qui
  `destroy()` toutes ses connexions sur SIGUSR1 (listener conservé). Écrire le PID dans un pidfile —
  `pgrep -f` attrape le wrapper zsh du harness avant le process node.

## Pièges

- Les hooks du harness redirigent `mvn` hors du tool Bash : lancer le serveur dev via un script
  wrapper en background, pas `mvn` inline.
- Ne jamais laisser tourner : `pkill -f vidocq:dev` puis `pkill -f io.vidocq.tools.arago.server`
  (Ryuk/Testcontainers nettoie le conteneur Postgres tout seul).
