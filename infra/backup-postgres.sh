#!/usr/bin/env bash
# Backup lógico criptografado no destino externo configurado pelo operador.
# Exemplo: infra/backup-postgres.sh /etc/farelo/production.env /etc/farelo/backup.env
set -euo pipefail
umask 077

production_env="${1:-/etc/farelo/production.env}"
backup_env="${2:-/etc/farelo/backup.env}"
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

for file in "$production_env" "$backup_env"; do
  if [[ ! -r "$file" ]]; then
    printf 'Arquivo de configuração não encontrado ou ilegível: %s\n' "$file" >&2
    exit 1
  fi
done
for tool in docker restic; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'Comando necessário não instalado: %s\n' "$tool" >&2
    exit 1
  fi
done

# Ambos os arquivos pertencem ao operador e ficam fora do Git, com chmod 600.
set -a
# shellcheck disable=SC1090
source "$production_env"
# shellcheck disable=SC1090
source "$backup_env"
set +a
: "${RESTIC_REPOSITORY:?Configure RESTIC_REPOSITORY no backup.env}"
: "${RESTIC_PASSWORD_FILE:?Configure RESTIC_PASSWORD_FILE no backup.env}"
if [[ ! -r "$RESTIC_PASSWORD_FILE" ]]; then
  printf 'Arquivo da senha do restic não encontrado ou ilegível.\n' >&2
  exit 1
fi

compose=(docker compose --env-file "$production_env" -f "$script_dir/compose.production.yml")
# --stdin-from-command cancela o snapshot se pg_dump falhar. Um pipe para
# `restic backup --stdin` poderia registrar um dump truncado como válido.
restic backup --stdin-from-command --stdin-filename farelo-postgres.dump --tag farelo-db \
  -- "${compose[@]}" exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl'

# Confirma que o repositório externo recebeu um snapshot. A validação de
# recuperação exige um restore periódico em ambiente isolado (README).
restic snapshots --latest 1 --tag farelo-db
