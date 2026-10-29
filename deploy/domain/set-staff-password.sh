#!/usr/bin/env bash
# Run on the VPS in an interactive SSH terminal. The plaintext is read only
# from the terminal and passed to Caddy over stdin, never as a process argument.
set -euo pipefail

if [[ ! -t 0 ]]; then
  echo 'Execute em um terminal SSH interativo.' >&2
  exit 1
fi

target=/opt/farelo-os/shared/domain
install -d -m 700 "$target"
read -r -s -p 'Nova senha extra da equipe (invisível): ' password
printf '\n'
if [[ ${#password} -lt 16 ]]; then
  unset password
  echo 'Use pelo menos 16 caracteres.' >&2
  exit 1
fi
hash=$(printf '%s\n' "$password" | docker exec -i sucao-estoque-caddy-1 caddy hash-password --algorithm argon2id)
unset password
umask 077
printf '%s\n' "$hash" > "$target/staff.hash"
unset hash
echo 'Hash da senha extra salvo somente na VPS. Nenhuma rota foi publicada.'
