#!/usr/bin/env sh
# Generates an RSA key pair for signing Bayflow access tokens in local development.
# Output: keys/dev-private.pem (PKCS#8) and keys/dev-public.pem (X.509). keys/ is git-ignored.
set -eu
cd "$(dirname "$0")/.."
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/dev-private.pem
openssl pkey -in keys/dev-private.pem -pubout -out keys/dev-public.pem
chmod 600 keys/dev-private.pem
echo "Created keys/dev-private.pem and keys/dev-public.pem"
echo "Add to .env:"
echo "  BAYFLOW_JWT_PRIVATE_KEY_PATH=/run/keys/dev-private.pem"
echo "  BAYFLOW_JWT_PUBLIC_KEY_PATH=/run/keys/dev-public.pem"
