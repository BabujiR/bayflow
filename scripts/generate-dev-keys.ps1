# Generates an RSA key pair for signing Bayflow access tokens in local development.
# Needs openssl on PATH (Git for Windows includes it: C:\Program Files\Git\usr\bin).
# Output: keys\dev-private.pem (PKCS#8) and keys\dev-public.pem (X.509). keys\ is git-ignored.
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
New-Item -ItemType Directory -Force -Path keys | Out-Null
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/dev-private.pem
openssl pkey -in keys/dev-private.pem -pubout -out keys/dev-public.pem
Write-Host "Created keys\dev-private.pem and keys\dev-public.pem"
Write-Host "Add to .env:"
Write-Host "  BAYFLOW_JWT_PRIVATE_KEY_PATH=/run/keys/dev-private.pem"
Write-Host "  BAYFLOW_JWT_PUBLIC_KEY_PATH=/run/keys/dev-public.pem"
