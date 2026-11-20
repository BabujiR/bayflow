#!/usr/bin/env bash
# One-time setup of the Ramplight VM (Debian 12 on Google Compute Engine). Safe to run again.
# Run it as your normal user (not root), from the cloned repository:
#   /opt/bayflow/deploy/gcp/scripts/setup-vm.sh
# It installs Docker, adds swap, turns on automatic security updates, creates .env with random
# passwords and secrets, creates the token signing key and schedules the nightly backup.
set -euo pipefail

if [ "$(id -u)" -eq 0 ]; then
  echo "Run this as your normal user (it uses sudo where needed), not as root." >&2
  exit 1
fi

GCP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
REPO_DIR="$(cd "$GCP_DIR/../.." && pwd)"
ME="$(id -un)"

echo "== Docker"
if ! command -v docker >/dev/null 2>&1; then
  sudo apt-get update
  sudo apt-get install -y ca-certificates curl
  sudo install -m 0755 -d /etc/apt/keyrings
  sudo curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
  sudo chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
sudo usermod -aG docker "$ME"

echo "== Swap (2 GB), so a memory peak slows the VM down instead of stopping a service"
if ! swapon --show | grep -q .; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo "== Automatic security updates"
sudo apt-get install -y unattended-upgrades openssl
sudo dpkg-reconfigure -f noninteractive unattended-upgrades

echo "== Settings (.env)"
cd "$GCP_DIR"
if [ ! -f .env ]; then
  cp .env.example .env
fi
chmod 600 .env
# Every empty password or secret gets a random value; values already set are kept.
for key in POSTGRES_PASSWORD BAYFLOW_APP_DB_PASSWORD BAYFLOW_SYSTEM_CLIENT_SECRET \
           BAYFLOW_WORKSHOP_CLIENT_SECRET BAYFLOW_NOTIFICATION_CLIENT_SECRET \
           BAYFLOW_BOOTSTRAP_ADMIN_PASSWORD; do
  if grep -qE "^${key}=$" .env; then
    sed -i "s|^${key}=$|${key}=$(openssl rand -hex 24)|" .env
  fi
done

echo "== Token signing key (keys/)"
# The services verify sign-ins with this key, so it must outlast restarts. The container's
# user must be able to read it; the folders above it keep other users of the VM out.
mkdir -p keys
if [ ! -f keys/jwt-private.pem ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/jwt-private.pem
  openssl pkey -in keys/jwt-private.pem -pubout -out keys/jwt-public.pem
fi
chmod 755 keys
chmod 644 keys/jwt-private.pem keys/jwt-public.pem
chmod 750 "$REPO_DIR" "$GCP_DIR"

echo "== Nightly database backup (03:17 UTC, scripts/backup.sh)"
mkdir -p backups
echo "17 3 * * * $ME bash $GCP_DIR/scripts/backup.sh >> $GCP_DIR/backups/backup.log 2>&1" \
  | sudo tee /etc/cron.d/bayflow-backup >/dev/null
sudo chmod 644 /etc/cron.d/bayflow-backup

cat <<MSG

Done. Next:
  1. Fill in the empty values in $GCP_DIR/.env:
       ACME_EMAIL, CLOUDFLARE_API_TOKEN, IMAGE_PREFIX, BAYFLOW_BOOTSTRAP_ADMIN_EMAIL, BACKUP_BUCKET
     The first admin's password is BAYFLOW_BOOTSTRAP_ADMIN_PASSWORD in that file.
  2. Save a copy of .env in your password manager.
  3. Sign out and back in (so "docker" works without sudo), then:
       cd $GCP_DIR && docker compose pull && docker compose up -d
MSG
