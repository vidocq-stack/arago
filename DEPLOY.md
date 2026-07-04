# Déploiement — arago.vidocq.io (Portainer FUX + Nginx Proxy Manager)

Cible : hôte **fux.durand-blazart.fr** (Portainer EE, endpoint `local` id=2). `arago.vidocq.io`
est un CNAME vers `fux.durand-blazart.fr`. Modèle identique au sibling `forge-dashboard`.

## Architecture

```
Internet ──> Nginx Proxy Manager (reverseproxy-app-1)
                 │  proxy host: arago.vidocq.io ──> http://arago:8080  (+ WebSocket)
                 ▼  réseau docker partagé : reverseproxy_default
            ┌────────────────────────────┐
            │ stack "arago"              │
            │   arago (expose 8080)      │  ── réseau privé arago-net ──┐
            │                            │                              ▼
            │   postgres:16 (arago-net)  │                       PostgreSQL
            └────────────────────────────┘
```

- L'image vient du registry CI : `registry.vidocq.dev/vidocq-tools/arago:0.1.0-SNAPSHOT`
  (poussée par `.forgejo/workflows/build-deploy.yml` à chaque push sur `main`).
- Aucun port publié : NPM joint `arago` par son nom de conteneur sur `reverseproxy_default`.
- PostgreSQL est isolé sur `arago-net` (réseau interne) — jamais exposé.

## Stack Portainer

Fichier : [`docker-compose.prod.yml`](./docker-compose.prod.yml). **Aucun secret dans le fichier** —
tout passe par les variables d'environnement du stack (onglet *Environment* de Portainer) :

| Variable | Rôle | Exemple / valeur |
|---|---|---|
| `POSTGRES_DB` | base | `arago` |
| `POSTGRES_USER` | user DB | `arago` |
| `POSTGRES_PASSWORD` | **secret** mot de passe DB | (généré, 24 car.) |
| `DOCKER_REG_URL` | registry | `registry.vidocq.dev` |
| `ARAGO_IMAGE_TAG` | tag image | `0.1.0-SNAPSHOT` |
| `ARAGO_PUBLIC_URL` | URL publique (liens RGPD, partage) | `https://arago.vidocq.io` |
| `ARAGO_ATTENDEE_HMAC_SECRET` | **secret** signature JWT (speaker/admin/attendee) | (généré, 48 octets b64) |
| `ARAGO_SUPERADMIN_USERNAME` | login superadmin | `root` |
| `ARAGO_SUPERADMIN_PASSWORD_HASH` | **secret** hash PBKDF2 du mdp superadmin | `$pbkdf2-sha256$i=600000$…` |
| `ARAGO_MAIL_DEV_EXPOSE_LINK` | expose le lien magique RGPD dans la réponse (pas de SMTP) | `true` (phase de test) |
| `ARAGO_DEV_SEED_SPEAKER` | seed speakers au boot (vide = aucun) | *(vide)* |
| `ARAGO_DEV_SEED_SPEAKER_PASSWORD` | mdp initial des speakers seedés | *(vide)* |

> **Phase de test** : pas de SMTP câblé (seul `LoggingMailer`). `ARAGO_MAIL_DEV_EXPOSE_LINK=true`
> permet d'exercer le flux RGPD (le lien magique est renvoyé par `POST /api/profile/magic-link`).
> Repasser à `false` dès qu'un `SmtpMailer` est en place.

### Générer le hash superadmin

Le `…_PASSWORD_HASH` se calcule hors-bande (le mot de passe en clair ne quitte jamais votre poste) :

```bash
node -e 'const c=require("crypto");const pw=process.argv[1];const s=c.randomBytes(16);const h=c.pbkdf2Sync(Buffer.from(pw,"utf8"),s,600000,32,"sha256");const b=x=>Buffer.from(x).toString("base64").replace(/=+$/,"");console.log(`$pbkdf2-sha256$i=600000$${b(s)}$${b(h)}`)' 'MON_MOT_DE_PASSE'
```

> ⚠️ **Échapper les `$` dans Portainer** : compose ré-interpole les valeurs des variables du stack.
> Dans l'onglet *Environment* (ou via l'API), chaque `$` du hash PHC doit être doublé (`$$`),
> sinon `$pbkdf2`, `$i`, le sel et le hash sont substitués par du vide et le login renvoie 401.
> Constaté au premier déploiement (stack id=49, 2026-07-05). Les autres secrets (base64) ne
> contiennent jamais de `$` et passent tels quels.

## Nginx Proxy Manager — proxy host

Dans NPM → **Hosts → Proxy Hosts → Add Proxy Host** :

- **Details**
  - Domain Names : `arago.vidocq.io`
  - Scheme : `http`
  - Forward Hostname / IP : `arago`  *(nom du conteneur)*
  - Forward Port : `8080`
  - **Websockets Support : ON** ← requis pour le chat `/ws/rooms/{pin}`
  - Block Common Exploits : ON
- **SSL**
  - SSL Certificate : *Request a new SSL Certificate* (Let's Encrypt)
  - Force SSL : ON · HTTP/2 : ON · HSTS : optionnel
  - Accepter les CGU Let's Encrypt

> NPM et `arago` partagent le réseau `reverseproxy_default` ; NPM résout donc `arago:8080`
> directement par DNS Docker. Le challenge Let's Encrypt HTTP-01 passe par la même chaîne que
> les autres domaines `*.vidocq.io` déjà servis par cet NPM.

## Redéploiement continu

La CI (`build-deploy.yml`) reconstruit et pousse l'image à chaque `main`, puis appelle un webhook
Portainer de redéploiement. Pour l'activer : récupérer l'URL du webhook du stack (Portainer →
stack `arago` → *Webhooks*) et la mettre dans le secret Forgejo/Codeberg `PORTAINER_WEBHOOK_URL`.
