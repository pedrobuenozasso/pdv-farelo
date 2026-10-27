#!/usr/bin/env bash
# Run interactively on the Mac; the password is never passed as an argument.
set -euo pipefail
umask 077

if [[ ! -t 0 ]]; then
  printf 'Run this script in an interactive Terminal on the Mac.\n' >&2
  exit 2
fi

IFS= read -r -s -p 'Senha do A1 (invisível): ' password
printf '\n'
IFS= read -r -s -p 'Repita a senha do A1: ' confirmation
printf '\n'
trap 'unset password confirmation' EXIT
if [[ -z "$password" || "$password" != "$confirmation" ]]; then
  printf 'As senhas não coincidem ou estão vazias. Nada foi enviado.\n' >&2
  exit 1
fi
unset confirmation

remote=farelo-vps
ssh_options=(-T -S none -o ControlMaster=no -o ClearAllForwardings=yes -o BatchMode=yes)
printf '%s' "$password" | ssh "${ssh_options[@]}" "$remote" '
  set -euo pipefail
  umask 077
  ulimit -c 0
  [[ $(id -u) -eq 0 ]]
  dir=/opt/farelo-os/shared/fiscal
  destination="$dir/a1-password.cred"
  install -d -m 700 "$dir"
  [[ ! -e "$destination" ]]
  temporary=$(mktemp "$dir/.a1-password-XXXXXXXX")
  trap '\''rm -f -- "$temporary"'\'' EXIT
  systemd-creds encrypt --with-key=host --name=a1-password.cred - "$temporary" >/dev/null
  chmod 600 "$temporary"
  mv -T -- "$temporary" "$destination"
  trap - EXIT
  printf "a1_password_encrypted=OK\n"
'

if ! ssh "${ssh_options[@]}" "$remote" \
    'systemd-creds decrypt /opt/farelo-os/shared/fiscal/a1-password.cred -' \
    | cmp -s - <(printf '%s' "$password"); then
  printf 'password_roundtrip=FAIL; inspect the encrypted credential\n' >&2
  exit 1
fi
printf 'password_roundtrip=OK\n'
