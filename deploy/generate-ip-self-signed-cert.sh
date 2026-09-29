#!/usr/bin/env sh
set -eu

IP_ADDRESS="${1:-60.205.211.104}"
CERT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/https"

command -v openssl >/dev/null 2>&1 || {
  echo "openssl is required to generate the certificate." >&2
  exit 1
}

mkdir -p "$CERT_DIR"
openssl req -x509 -newkey rsa:2048 -sha256 -nodes -days 825 \
  -keyout "$CERT_DIR/weatherhub-ip.key" \
  -out "$CERT_DIR/weatherhub-ip.crt" \
  -subj "/CN=$IP_ADDRESS" \
  -addext "subjectAltName=IP:$IP_ADDRESS"

echo "Created $CERT_DIR/weatherhub-ip.crt and weatherhub-ip.key"
