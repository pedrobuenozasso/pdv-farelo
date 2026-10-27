#!/usr/bin/env bash
# Run on the Mac. Streams the PKCS#12 through SSH into systemd-creds on the VPS.
# No unencrypted copy is written on the VPS or into this repository.
set -euo pipefail
umask 077

if [[ $# -ne 1 || ! -f "$1" || ! -r "$1" ]]; then
  printf 'usage: %s /path/to/certificate.p12\n' "$0" >&2
  exit 2
fi

source_file="$1"
remote=farelo-vps
ssh_options=(-T -S none -o ControlMaster=no -o ClearAllForwardings=yes -o BatchMode=yes)

ssh "${ssh_options[@]}" "$remote" '
  set -euo pipefail
  umask 077
  ulimit -c 0
  [[ $(id -u) -eq 0 ]]
  dir=/opt/farelo-os/shared/fiscal
  destination="$dir/a1.p12.cred"
  install -d -m 700 "$dir"
  [[ ! -e "$destination" ]]
  temporary=$(mktemp "$dir/.a1-XXXXXXXX")
  trap '\''rm -f -- "$temporary"'\'' EXIT
  systemd-creds encrypt --with-key=host --name=a1.p12.cred - "$temporary" >/dev/null
  chmod 600 "$temporary"
  mv -T -- "$temporary" "$destination"
  trap - EXIT
  printf "a1_encrypted=OK\n"
' < "$source_file"

# Verify the sealed bytes without showing the certificate or writing plaintext.
if ! ssh "${ssh_options[@]}" "$remote" \
    'systemd-creds decrypt /opt/farelo-os/shared/fiscal/a1.p12.cred -' \
    | cmp -s -- "$source_file" -; then
  printf 'a1_roundtrip=FAIL; keep the original on the Mac and inspect the VPS credential\n' >&2
  exit 1
fi
printf 'a1_roundtrip=OK\n'
