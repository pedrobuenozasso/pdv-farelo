#!/usr/bin/env bash
# Run as root on the VPS after both encrypted credentials have been enrolled.
# systemd decrypts them only for this short-lived process in its credential area.
set -euo pipefail

dir=/opt/farelo-os/shared/fiscal
[[ $(id -u) -eq 0 ]]
[[ -f "$dir/a1.p12.cred" && -f "$dir/a1-password.cred" ]]
command -v openssl >/dev/null

if systemd-run --quiet --wait --collect \
    -p LimitCORE=0 \
    -p "LoadCredentialEncrypted=a1.p12.cred:$dir/a1.p12.cred" \
    -p "LoadCredentialEncrypted=a1-password.cred:$dir/a1-password.cred" \
    /bin/sh -c 'openssl pkcs12 -in "$CREDENTIALS_DIRECTORY/a1.p12.cred" \
      -passin "file:$CREDENTIALS_DIRECTORY/a1-password.cred" -noout \
      >/dev/null 2>&1' >/dev/null 2>&1; then
  printf 'a1_credentials=OK\n'
else
  printf 'a1_credentials=FAIL\n' >&2
  exit 1
fi
