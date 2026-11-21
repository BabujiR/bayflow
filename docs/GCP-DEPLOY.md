# Ramplight on Google Cloud: one VM

This guide puts Ramplight on the internet at **https://getramplight.com**, with each workshop
at **https://&lt;slug&gt;.getramplight.com**. Everything runs on one Compute Engine VM with
Docker Compose; Caddy in front gets the HTTPS certificates (including the wildcard) from
Let's Encrypt through Cloudflare, where the domain's DNS lives.

```
browser ──HTTPS──▶ Caddy :443 ──▶ web (nginx) ──▶ auth / appointment / workshop / notification
  (Cloudflare DNS, grey cloud)                                         │
                                                                  PostgreSQL 16 (Docker volume)
                                    nightly pg_dump ──▶ Cloud Storage bucket (30 days)
                                    daily disk snapshot (7 days)
```

The files are in [`deploy/gcp/`](../deploy/gcp): `docker-compose.yml`, `caddy/` (Caddyfile and
the Caddy image with the Cloudflare module), `.env.example`, `scripts/setup-vm.sh` and
`scripts/backup.sh`.

**Cost:** roughly $55–60 a month in `us-east1`: an `e2-standard-2` VM (2 vCPU, 8 GB), a
30 GB disk, the static IP, snapshots and a few GB of backups. Check the
[pricing calculator](https://cloud.google.com/products/calculator) for current prices, and set
a budget alert (Billing → Budgets & alerts).

Commands marked **laptop** run where you have the `gcloud` CLI (or in Cloud Shell, in the
Google Cloud console); **VM** commands run on the VM over SSH.

---

## 1. Project and settings (laptop)

```bash
gcloud auth login
gcloud projects create ramplight-prod --name="Ramplight"     # or use an existing project
gcloud config set project ramplight-prod
# Link a billing account: console → Billing → Link a billing account.

gcloud services enable compute.googleapis.com storage.googleapis.com iap.googleapis.com

export PROJECT_ID=$(gcloud config get-value project)
export REGION=us-east1 ZONE=us-east1-b
export SA=ramplight-vm@$PROJECT_ID.iam.gserviceaccount.com
export BUCKET=$PROJECT_ID-backups
```

## 2. Backup bucket and the VM's account (laptop)

The VM runs as its own service account, which may only add and read backups (it cannot delete
them, so a compromised VM cannot wipe them).

```bash
gcloud iam service-accounts create ramplight-vm --display-name="Ramplight VM"

gcloud storage buckets create gs://$BUCKET --location=$REGION \
  --uniform-bucket-level-access --public-access-prevention
gcloud storage buckets update gs://$BUCKET --lifecycle-file=deploy/gcp/backup-lifecycle.json
gcloud storage buckets add-iam-policy-binding gs://$BUCKET \
  --member=serviceAccount:$SA --role=roles/storage.objectCreator
gcloud storage buckets add-iam-policy-binding gs://$BUCKET \
  --member=serviceAccount:$SA --role=roles/storage.objectViewer
```

(Run the `--lifecycle-file` line from the repository root, or give the file's full path.)

## 3. IP address, firewall and VM (laptop)

```bash
gcloud compute addresses create ramplight-ip --region=$REGION
gcloud compute addresses describe ramplight-ip --region=$REGION --format='value(address)'
#   → note this IP for step 4

# Web traffic to the VM; SSH only through Google's IAP tunnel, not from the whole internet.
gcloud compute firewall-rules create ramplight-web --network=default \
  --allow=tcp:80,tcp:443,udp:443 --target-tags=ramplight-web
gcloud compute firewall-rules create ramplight-ssh-iap --network=default \
  --allow=tcp:22 --source-ranges=35.235.240.0/20 --target-tags=ramplight-web
gcloud compute firewall-rules delete default-allow-ssh default-allow-rdp --quiet

gcloud compute instances create ramplight-vm --zone=$ZONE \
  --machine-type=e2-standard-2 \
  --image-family=debian-12 --image-project=debian-cloud \
  --boot-disk-size=30GB --boot-disk-type=pd-balanced \
  --address=ramplight-ip --tags=ramplight-web \
  --service-account=$SA --scopes=cloud-platform \
  --shielded-secure-boot --shielded-vtpm --shielded-integrity-monitoring

# A snapshot of the whole disk every day, kept for 7 days.
gcloud compute resource-policies create snapshot-schedule ramplight-daily --region=$REGION \
  --daily-schedule --start-time=07:00 --max-retention-days=7
gcloud compute disks add-resource-policies ramplight-vm --zone=$ZONE \
  --resource-policies=ramplight-daily
```

## 4. Cloudflare: DNS and API token (browser)

In the Cloudflare dashboard, open **getramplight.com → DNS → Records** and add:

| Type | Name | Content | Proxy status |
|---|---|---|---|
| A | `@` | the IP from step 3 | **DNS only** (grey cloud) |
| A | `*` | the IP from step 3 | **DNS only** (grey cloud) |

`*` covers every workshop address and `www`. Keep both **grey**: Caddy terminates HTTPS
itself, and Cloudflare's proxy would get in the way of the wildcard certificate and the
upload sizes.

Then create the token Caddy uses to prove it owns the domain: **My Profile → API Tokens →
Create Token → "Edit zone DNS" template → Zone Resources: Include · Specific zone ·
getramplight.com → Create**. Copy the token; it goes into `.env` in step 6.

## 5. GitHub: images with the domain built in (browser)

The web app has its domain built in at build time.

1. Repository **Settings → Secrets and variables → Actions → Variables → New repository
   variable**: `BAYFLOW_BASE_DOMAIN` = `getramplight.com`.
2. Push to `main` (or **Actions → CD → Re-run** on the last run). CD publishes
   `ghcr.io/<owner>/bayflow-auth`, `-appointment`, `-workshop`, `-notification` and `-web`.
3. If the packages are private (the default for a private repository), create a **classic**
   personal access token with only **read:packages** (GitHub → Settings → Developer settings
   → Personal access tokens → Tokens (classic)). The VM uses it to pull the images.

(No GitHub images yet? You can build on the VM instead: see "Build on the VM" below.)

## 6. Set up the VM (VM)

```bash
gcloud compute ssh ramplight-vm --zone=$ZONE --tunnel-through-iap      # from the laptop
```

On the VM, install git (the Debian image has none) and get the repository:

```bash
sudo apt-get update && sudo apt-get install -y git
sudo mkdir -p /opt/bayflow && sudo chown "$USER": /opt/bayflow
```

**Public repository:** `git clone https://github.com/<owner>/<repo>.git /opt/bayflow`

**Private repository:** use a read-only **deploy key**:

```bash
ssh-keygen -t ed25519 -N "" -f ~/.ssh/github_deploy
cat ~/.ssh/github_deploy.pub
# GitHub: repository Settings → Deploy keys → Add deploy key (read-only), paste the key.
printf 'Host github.com\n  IdentityFile ~/.ssh/github_deploy\n' >> ~/.ssh/config

git clone git@github.com:<owner>/<repo>.git /opt/bayflow
```

Then, either way:

```bash
bash /opt/bayflow/deploy/gcp/scripts/setup-vm.sh
```

`setup-vm.sh` installs Docker, adds 2 GB swap, turns on automatic security updates, creates
`deploy/gcp/.env` with random database passwords and service secrets, creates the token
signing key in `deploy/gcp/keys/` and schedules the nightly backup.

Fill in the rest of `.env`:

```bash
cd /opt/bayflow/deploy/gcp
nano .env
#   ACME_EMAIL=you@yourcompany.com            (certificate expiry warnings)
#   CLOUDFLARE_API_TOKEN=<token from step 4>
#   IMAGE_PREFIX=ghcr.io/<owner, lower case>
#   BAYFLOW_BOOTSTRAP_ADMIN_EMAIL=<an address you read>
#   BACKUP_BUCKET=<PROJECT_ID>-backups
```

**Save a copy of `.env` in your password manager**: restoring a backup needs its passwords.

Sign out and back in (so `docker` works without `sudo`), then start everything:

```bash
cd /opt/bayflow/deploy/gcp
echo <read:packages token> | docker login ghcr.io -u <github-user> --password-stdin   # private images only
docker compose pull
docker compose up -d
docker compose ps          # all "healthy" after a minute or two
docker compose logs caddy  # "certificate obtained successfully" for getramplight.com and *.getramplight.com
```

## 7. Check it

- **https://getramplight.com** shows the sign-up page, with a valid certificate.
- **https://www.getramplight.com** goes to the same page.
- Sign up a test workshop, say `test-garage`; you land on **https://test-garage.getramplight.com**.
- Sign in there, book an appointment, upload a logo (Branding): uploads and images work.
- `https://nope.getramplight.com` shows "workshop not found".
- The first admin (`BAYFLOW_BOOTSTRAP_ADMIN_USERNAME`, password in `.env`) belongs to the
  built-in workshop (slug `default`). Change its password after the first sign-in.
- Run a backup by hand and see it arrive: `bash scripts/backup.sh`, then
  `gcloud storage ls gs://<bucket>/postgres/`.

---

## Day to day (VM, in `/opt/bayflow/deploy/gcp`)

**Update** to the newest images (after CD has published them):

```bash
cd /opt/bayflow/deploy/gcp
bash scripts/backup.sh            # a fresh backup first
git -C /opt/bayflow pull          # picks up changes to the deploy files
docker compose pull && docker compose up -d
docker compose ps                 # wait until all show "healthy"
docker image prune -f             # then clean up the old versions
```

Database migrations run by themselves when the services start.

**Roll back:** set `IMAGE_TAG=<an earlier commit SHA>` in `.env`, then
`docker compose pull && docker compose up -d`. (Migrations are not undone: a release with a
new migration can only roll back to code that still works with it.)

**Logs:** `docker compose logs -f --tail=200 auth-service` (or any service). Logs rotate
automatically (5 × 10 MB per container).

**Restore a backup:**

```bash
gcloud storage ls gs://<bucket>/postgres/
gcloud storage cp gs://<bucket>/postgres/bayflow-<time>.dump /tmp/restore.dump
docker compose stop caddy web auth-service appointment-service workshop-service notification-service
docker compose exec -T postgres pg_restore -U bayflow -d bayflow --clean --if-exists < /tmp/restore.dump
docker compose up -d
```

Try a restore on a spare VM now and then; a backup you never restored is a hope, not a backup.

**Build on the VM** (no GHCR images, or to test a branch): `docker compose up -d --build`.
The Java builds take several minutes on this VM size; the result is the same.

## Security checklist

- [ ] SSH only through IAP (`default-allow-ssh` deleted); only 80/443 open to the internet.
- [ ] `.env` is `chmod 600`, never committed, and saved in your password manager.
- [ ] The Cloudflare token can only edit DNS of getramplight.com.
- [ ] The bootstrap admin's password was changed after the first sign-in.
- [ ] Two-factor sign-in on your Google, Cloudflare and GitHub accounts.
- [ ] A budget alert is set in Google Cloud billing.
- [ ] Demo users don't exist (the stack runs with `SPRING_PROFILES_ACTIVE=prod`, not `dev`).

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Browser certificate warning; Caddy logs mention DNS or `cloudflare` | Wrong or under-scoped `CLOUDFLARE_API_TOKEN`; `docker compose up -d caddy` after fixing `.env`. |
| Certificate for `getramplight.com` works, a workshop address doesn't | The `*` DNS record is missing or proxied (orange cloud). |
| Every workshop address says "workshop not found", or sign-up sends you to `localhost` | The web image was built without `BAYFLOW_BASE_DOMAIN` (step 5). Rebuild via CD, or `docker compose up -d --build web`. |
| 502 from Caddy | A service is not healthy yet: `docker compose ps`, then its logs. |
| A service restarts with "Killed" / exit 137 | Out of memory: raise its `mem_limit`, or move to `e2-standard-4`. |
| auth-service: "private-key-path and public-key-path must be set" | `keys/` is missing; run `scripts/setup-vm.sh` again. |
| Everyone was signed out after a restart | The signing key changed; keep `deploy/gcp/keys/` as it is. |
