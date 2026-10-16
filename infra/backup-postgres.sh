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

# A instalação privada atual usa deploy/compose.yml e serviço "db"; o
# compose de produção preparado em infra/ usa "postgres". A escolha é
# explícita no backup.env para evitar salvar o banco de outro projeto.
compose_file="${FARELO_COMPOSE_FILE:-$script_dir/compose.production.yml}"
db_service="${FARELO_DB_SERVICE:-postgres}"
if [[ ! -r "$compose_file" ]]; then
  printf 'Compose do backup não encontrado ou ilegível: %s\n' "$compose_file" >&2
  exit 1
fi
if [[ ! "$db_service" =~ ^[a-zA-Z][a-zA-Z0-9_-]*$ ]]; then
  printf 'Nome de serviço PostgreSQL inválido.\n' >&2
  exit 1
fi
compose=(docker compose --env-file "$production_env" -f "$compose_file")
# --stdin-from-command cancela o snapshot se pg_dump falhar. Um pipe para
# `restic backup --stdin` poderia registrar um dump truncado como válido.
restic backup --stdin-from-command --stdin-filename farelo-postgres.dump --tag farelo-db \
  -- "${compose[@]}" exec -T "$db_service" sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl'

# Confirma que o repositório externo recebeu um snapshot. A validação de
# recuperação exige um restore periódico em ambiente isolado (README).
restic snapshots --latest 1 --tag farelo-db
