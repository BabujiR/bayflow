#!/usr/bin/env bash
# Dumps the database and copies the dump to Cloud Storage (gs://$BACKUP_BUCKET/postgres/).
# Run nightly by cron (scripts/setup-vm.sh); run it by hand before an update too.
# The bucket deletes dumps after 30 days (backup-lifecycle.json); 3 days are also kept on the VM.
set -euo pipefail
cd "$(dirname "$0")/.."

setting() { grep -E "^$1=" .env | tail -n 1 | cut -d= -f2-; }
BUCKET="$(setting BACKUP_BUCKET)"
DB="$(setting POSTGRES_DB)"; DB="${DB:-bayflow}"
DB_USER="$(setting POSTGRES_USER)"; DB_USER="${DB_USER:-bayflow}"
if [ -z "$BUCKET" ]; then
  echo "$(date -u +%FT%TZ) BACKUP_BUCKET is not set in .env; nothing backed up." >&2
  exit 1
fi

mkdir -p backups
FILE="backups/bayflow-$(date -u +%Y%m%dT%H%M%SZ).dump"
# Custom format (compressed; restore with pg_restore). The table owner sees every workshop.
docker compose exec -T postgres pg_dump -U "$DB_USER" -d "$DB" --format=custom > "$FILE.part"
mv "$FILE.part" "$FILE"
gcloud storage cp --quiet "$FILE" "gs://$BUCKET/postgres/"
find backups -name 'bayflow-*.dump' -mtime +3 -delete
echo "$(date -u +%FT%TZ) backed up $FILE to gs://$BUCKET/postgres/"
