#!/usr/bin/env sh
# Envía un evento firmado a la API, como lo harían facturador-sri o nomina-ec.
# Uso: INTEGRATION_HMAC_SECRET=... ./docs/firmar-evento.sh invoices evento.json [http://localhost:8080]
# Firma: sha256=hex(HMAC_SHA256(secreto, timestamp + "." + cuerpo))
set -eu

tipo="${1:?Indica invoices o payrolls}"
archivo="${2:?Indica el archivo JSON del evento}"
base="${3:-http://localhost:8080}"
secreto="${INTEGRATION_HMAC_SECRET:?Define INTEGRATION_HMAC_SECRET}"

timestamp="$(date +%s)"
firma="sha256=$( { printf '%s.' "$timestamp"; cat "$archivo"; } | openssl dgst -sha256 -hmac "$secreto" -hex | sed 's/^.*= //')"

curl -sS -X POST "$base/api/v1/integrations/$tipo" \
  -H "Content-Type: application/json" \
  -H "X-Timestamp: $timestamp" \
  -H "X-Signature: $firma" \
  --data-binary "@$archivo"
echo
