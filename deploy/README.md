# AuditYote — deployment runbook

AuditYote (the ControlMap GRC app) is deployed to a DigitalOcean droplet that **already hosts another
project** (a todo-list on `pasin.dev`, served by native **nginx + Certbot**). AuditYote runs as a
**Docker stack behind that existing nginx** on its own subdomain — the two apps never collide.

```
browser ──HTTPS──▶ host nginx (:80/:443, Certbot TLS)
                     ├─ pasin.dev            ──▶ 127.0.0.1:8082   (todo-list, unchanged)
                     └─ audityote.pasin.dev  ──▶ 127.0.0.1:8090   (AuditYote frontend container)
                                                      └─ /api ──▶ backend ──▶ postgres   (internal Docker network)
```

Only `127.0.0.1:8090` is published from Docker; Postgres and the backend stay on the internal compose
network. The host nginx owns 80/443 and terminates TLS for both sites.

Compose files: `docker-compose.yml` (base) + `docker-compose.deploy.yml` (this deployment). The
`docker-compose.prod.yml` + `Caddyfile` are an *alternative* standalone path (Caddy as its own front
door) — not used here.

---

## One-time setup

Run as the `deploy` user on the droplet unless a step says `sudo`. Steps marked **[sudo]** need the
deploy password; the rest need none once Docker is installed and `deploy` is in the `docker` group.

### 1. GitHub deploy key (read-only repo access)
```bash
ssh-keygen -t ed25519 -C "audityote-droplet" -f ~/.ssh/audityote_deploy -N ""
cat ~/.ssh/audityote_deploy.pub
```
Add that public key to the repo: **GitHub → repo → Settings → Deploy keys → Add deploy key**
(paste it, leave **Allow write access unchecked**). Then point SSH at it for github.com:
```bash
cat >> ~/.ssh/config <<'CFG'
Host github.com
  IdentityFile ~/.ssh/audityote_deploy
  IdentitiesOnly yes
CFG
chmod 600 ~/.ssh/config
ssh -T git@github.com   # expect: "Hi cs-muic/...: You've successfully authenticated"
```

### 2. Install Docker Engine + Compose plugin  **[sudo]**
```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker deploy
```
Log out and back in (or `newgrp docker`) so the group membership takes effect, then confirm:
```bash
docker ps           # should work WITHOUT sudo
docker compose version
```

### 3. Clone the repo
```bash
git clone git@github.com:cs-muic/ssc-y25t3-project-the-pawdit.git ~/audityote
cd ~/audityote
```

### 4. Create the production `.env` (gitignored — never committed)
Generate fresh secrets; do not reuse dev values:
```bash
cd ~/audityote
cat > .env <<ENV
# --- Postgres ---
POSTGRES_DB=controlmap
POSTGRES_USER=controlmap
POSTGRES_PASSWORD=$(openssl rand -base64 24)

# --- Seed users (you log in with these at the demo — record them somewhere safe) ---
SEED_ANALYST_NAME=Analyst
SEED_ANALYST_EMAIL=analyst@audityote.pasin.dev
SEED_ANALYST_PASSWORD=$(openssl rand -base64 18)
SEED_REVIEWER_NAME=Reviewer
SEED_REVIEWER_EMAIL=reviewer@audityote.pasin.dev
SEED_REVIEWER_PASSWORD=$(openssl rand -base64 18)

# --- App ---
APP_SESSION_SECRET=$(openssl rand -base64 32)
APP_DOMAIN=audityote.pasin.dev

# --- AI control-mapping (stretch — off) ---
AI_SUGGESTIONS_ENABLED=false
ENV
chmod 600 .env
echo "Seed logins (save these):"; grep -E 'SEED_(ANALYST|REVIEWER)_(EMAIL|PASSWORD)' .env
```

### 5. Build & start the stack
```bash
cd ~/audityote
docker compose -f docker-compose.yml -f docker-compose.deploy.yml --profile app up -d --build
```
Verify locally on the droplet (before nginx):
```bash
curl -s http://127.0.0.1:8090/api/health      # {"status":"up","db":"up",...}
curl -sI http://127.0.0.1:8090/ | head -1     # 200 OK (SPA)
```

### 6. nginx vhost for the subdomain  **[sudo]**
```bash
sudo cp ~/audityote/deploy/nginx/audityote.conf /etc/nginx/sites-available/audityote
sudo ln -s /etc/nginx/sites-available/audityote /etc/nginx/sites-enabled/audityote
sudo nginx -t && sudo systemctl reload nginx
```

### 7. TLS via Certbot  **[sudo]**
DNS `audityote.pasin.dev → 206.189.153.152` must already resolve (it does).
```bash
sudo certbot --nginx -d audityote.pasin.dev
```
Certbot rewrites the vhost to add `listen 443 ssl`, the cert lines, and the HTTP→HTTPS redirect, then
reloads nginx. Auto-renewal is already handled by the existing certbot timer.

### 8. Verify end-to-end
```bash
curl -sI https://audityote.pasin.dev/ | head -1          # 200
curl -s  https://audityote.pasin.dev/api/health          # db up
```
Then open `https://audityote.pasin.dev` and log in with the seeded analyst/reviewer.

---

## Redeploy (after any push to `main`)
```bash
cd ~/audityote
git pull
docker compose -f docker-compose.yml -f docker-compose.deploy.yml --profile app up -d --build
```
Hibernate validates the schema against Flyway migrations on boot; new migrations apply automatically.

## Operations
```bash
# logs
docker compose -f docker-compose.yml -f docker-compose.deploy.yml logs -f backend
# status
docker compose -f docker-compose.yml -f docker-compose.deploy.yml ps
# stop / start (keeps the pgdata volume)
docker compose -f docker-compose.yml -f docker-compose.deploy.yml --profile app down
docker compose -f docker-compose.yml -f docker-compose.deploy.yml --profile app up -d
```
The Postgres data lives in the `pgdata` Docker volume and survives `down`/redeploys. **Don't** run
`down -v` unless you intend to wipe the database.

## Rollback
```bash
cd ~/audityote
git checkout <previous-good-sha>
docker compose -f docker-compose.yml -f docker-compose.deploy.yml --profile app up -d --build
```
