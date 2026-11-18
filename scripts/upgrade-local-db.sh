#!/bin/sh
# One-time upgrade of an existing local Bayflow database for SaaS phase 2 (row-level
# security): creates the bayflow_app login the services now use. New data volumes get it
# automatically from infra/postgres/init, so `docker compose down -v` (which deletes all
# local data) is the alternative. Safe to run more than once.
set -e
cd "$(dirname "$0")/.."

echo "Starting PostgreSQL (recreated if its settings changed; data is kept)..."
docker compose up -d --wait postgres

echo "Creating the bayflow_app role..."
docker compose exec -T postgres sh /docker-entrypoint-initdb.d/02-create-app-role.sh

echo "Done. Now run: docker compose up -d --build"
