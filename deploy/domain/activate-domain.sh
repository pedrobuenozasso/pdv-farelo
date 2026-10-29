#!/usr/bin/env bash
# Run on the VPS only after the staff password has been enrolled and DNS is
# ready. Preserve the existing Caddy bind-mount inode so reload sees changes.
set -euo pipefail

if [[ $(id -u) -ne 0 ]]; then
  echo 'Execute como root na VPS.' >&2
  exit 1
fi

source_file=/root/sucao-estoque/Caddyfile
template=/opt/farelo-os/current/deploy/domain/Caddyfile.farelo.template
hash_file=/opt/farelo-os/shared/domain/staff.hash
work_dir=$(mktemp -d /tmp/farelo-domain.XXXXXXXX)
trap 'rm -rf "$work_dir"' EXIT

if [[ ! -s "$hash_file" ]]; then
  echo 'Hash da senha extra ausente; execute set-staff-password.sh antes.' >&2
  exit 1
fi
if grep -q 'cardapio.farelodebolo.com.br' "$source_file"; then
  echo 'Bloco Farelo já presente; inspecione antes de executar novamente.' >&2
  exit 1
fi

cp -p "$source_file" "$work_dir/original"
python3 - "$source_file" "$template" "$hash_file" "$work_dir/candidate" <<'PY'
from pathlib import Path
import sys

source, template, hash_file, output = map(Path, sys.argv[1:])
hash_value = hash_file.read_text().strip()
if not hash_value.startswith('$argon2id$'):
    raise SystemExit('Hash Argon2id inválido.')
output.write_text(source.read_text().rstrip() + '\n\n' + template.read_text().replace('__STAFF_HASH__', hash_value))
PY

docker cp "$work_dir/candidate" sucao-estoque-caddy-1:/tmp/farelo-caddy-candidate
if ! docker exec sucao-estoque-caddy-1 caddy validate --config /tmp/farelo-caddy-candidate --adapter caddyfile; then
  docker exec sucao-estoque-caddy-1 rm -f /tmp/farelo-caddy-candidate
  exit 1
fi
docker exec sucao-estoque-caddy-1 rm -f /tmp/farelo-caddy-candidate

backup="${source_file}.pre-farelo-$(date -u +%Y%m%dT%H%M%SZ)"
cp -p "$source_file" "$backup"
cat "$work_dir/candidate" > "$source_file"
if ! docker exec sucao-estoque-caddy-1 caddy reload --config /etc/caddy/Caddyfile; then
  cat "$backup" > "$source_file"
  docker exec sucao-estoque-caddy-1 caddy reload --config /etc/caddy/Caddyfile || true
  echo "Reload falhou; Caddyfile anterior restaurado. Backup: $backup" >&2
  exit 1
fi
echo "Caddy recarregado. Backup para rollback: $backup"
