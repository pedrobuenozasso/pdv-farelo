#!/usr/bin/env bash
set -euo pipefail

release_id=${1:?release id required}
expected_sha=${2:?SHA-256 required}
archive=/root/farelo-release.tar.gz
release_dir="/opt/farelo-os/releases/$release_id"
shared_dir=/opt/farelo-os/shared

actual_sha=$(sha256sum "$archive" | cut -d ' ' -f 1)
if [[ "$actual_sha" != "$expected_sha" ]]; then
  echo "Release archive checksum mismatch" >&2
  exit 1
fi
if [[ -e "$release_dir" ]]; then
  echo "Release directory already exists: $release_dir" >&2
  exit 1
fi

install -d -m 0755 /opt/farelo-os/releases "$release_dir"
install -d -m 0700 "$shared_dir"
tar -xzf "$archive" -C "$release_dir" --strip-components=1

if [[ ! -e "$shared_dir/.env" ]]; then
  postgres_password=$(openssl rand -hex 32)
  jwt_secret=$(openssl rand -hex 32)
  print_device_key=$(openssl rand -hex 32)
  (
    umask 077
    printf 'POSTGRES_USER=farelo\nPOSTGRES_DB=farelo\nPOSTGRES_PASSWORD=%s\nJWT_SECRET=%s\nFARELO_PRINT_DEVICE_KEY=%s\n' \
      "$postgres_password" "$jwt_secret" "$print_device_key" > "$shared_dir/.env"
  )
  unset postgres_password jwt_secret print_device_key
fi
chmod 0600 "$shared_dir/.env"
echo "Release verified and extracted; secrets stored at $shared_dir/.env"
