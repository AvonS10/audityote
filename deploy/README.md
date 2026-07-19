# Deployment

AuditYote runs as a small Docker stack on a Linux VPS. The React single-page app and the Spring Boot
API run in separate containers, with PostgreSQL in a third. A reverse proxy in front terminates TLS and
serves the app over HTTPS on its own subdomain. Only the reverse proxy is reachable from the internet;
the API and the database stay on the internal Docker network.

```
browser ──HTTPS──▶ reverse proxy (TLS)
                     └─ app subdomain ──▶ SPA container (loopback only)
                                            └─ /api ──▶ API container ──▶ PostgreSQL  (internal network)
```

## Images from CI

Container images are built by GitHub Actions and published to a container registry. The server pulls
the prebuilt images instead of compiling anything locally, so a deploy is a pull and a restart, and the
image CI tested is the image that runs.

## Compose files

- `docker-compose.yml` is the base stack, also used for local development.
- `docker-compose.deploy.yml` is the deploy override: it publishes only the SPA on the loopback
  interface for the reverse proxy to reach, and keeps PostgreSQL and the API off any public port.
- `docker-compose.registry.yml` runs the API and SPA from the prebuilt registry images rather than
  building on the server.
- `docker-compose.prod.yml` with the `Caddyfile` is an alternative that uses Caddy as a standalone
  reverse proxy with automatic TLS, for a server with no existing proxy.

## Configuration

All configuration and secrets live in a `.env` file on the server that is never committed: database
credentials, the session secret, seed logins, and the optional AI key. Secrets are generated on the
server and are not reused from development.

## Deploy and redeploy

```bash
git pull
docker compose ... pull
docker compose ... up -d
```

Hibernate validates the schema against the Flyway migrations on boot, and any new migrations apply
automatically. The PostgreSQL data lives in a named volume that survives redeploys, so an ordinary
deploy never touches the data. Rolling back is checking out a previous revision and bringing the stack
up again.

## Hardening

The server uses key-only SSH with root login and password authentication disabled, a host firewall
limited to SSH and HTTP/HTTPS, and fail2ban. TLS certificates come from Let's Encrypt and renew
automatically. The database volume is backed up on a schedule.
