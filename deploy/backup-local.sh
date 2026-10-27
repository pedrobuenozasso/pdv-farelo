#!/usr/bin/env bash
# Interim private-VPS database backup. This is not an offsite backup.
set -euo pipefail
umask 077

root="${FARELO_BACKUP_ROOT:-/opt/farelo-os}"
compose_file="$root/current/deploy/compose.yml"
env_file="$root/shared/.env"
backup_dir="$root/shared/backups"

if [[ ! -r "$compose_file" || ! -r "$env_file" ]]; then
  printf 'backup_configuration=FAIL\n' >&2
  exit 1
fi
for tool in docker mktemp; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'backup_dependency=FAIL\n' >&2
    exit 1
  fi
done
install -d -m 700 "$backup_dir"

compose=(docker compose --env-file "$env_file" -f "$compose_file")
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
temporary="$(mktemp "$backup_dir/.daily-$stamp-XXXXXX.dump")"
trap 'rm -f -- "$temporary"' EXIT

if ! "${compose[@]}" exec -T db sh -c \
    'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-acl' \
    > "$temporary" 2>/dev/null; then
  printf 'backup_dump=FAIL\n' >&2
  exit 1
fi
if [[ ! -s "$temporary" ]] || ! "${compose[@]}" exec -T db pg_restore --list \
    < "$temporary" >/dev/null 2>&1; then
  printf 'backup_validation=FAIL\n' >&2
  exit 1
fi

destination="$backup_dir/daily-$stamp.dump"
if [[ -e "$destination" ]]; then
  printf 'backup_destination=FAIL\n' >&2
  exit 1
fi
mv -- "$temporary" "$destination"
trap - EXIT
printf 'local_backup=OK\n'
