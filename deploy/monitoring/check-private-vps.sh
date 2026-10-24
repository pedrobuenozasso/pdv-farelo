#!/usr/bin/env bash
# Read-only, local health check for the private deploy/compose.yml installation.
# Output is limited to states, counts and ages; never print command stderr.
set -euo pipefail

root="${FARELO_MONITOR_ROOT:-/opt/farelo-os}"
compose_file="$root/current/deploy/compose.yml"
env_file="$root/shared/.env"
backup_dir="$root/shared/backups"
backup_max_hours="${FARELO_MONITOR_BACKUP_MAX_HOURS:-26}"
disk_max_percent="${FARELO_MONITOR_DISK_MAX_PERCENT:-85}"
outbox_max_seconds="${FARELO_MONITOR_OUTBOX_MAX_SECONDS:-600}"
failed=0

fail() { printf '%s=FAIL\n' "$1"; failed=1; }
pass() { printf '%s=OK\n' "$1"; }

if [[ ! "$backup_max_hours" =~ ^[0-9]+$ || ! "$disk_max_percent" =~ ^[0-9]+$ || ! "$outbox_max_seconds" =~ ^[0-9]+$ ]]; then
  fail configuration
  exit 1
fi
if [[ ! -r "$compose_file" || ! -r "$env_file" ]]; then
  fail configuration
  exit 1
fi
if ! command -v docker >/dev/null 2>&1 || ! command -v curl >/dev/null 2>&1; then
  fail dependencies
  exit 1
fi

compose=(docker compose --env-file "$env_file" -f "$compose_file")
running="$("${compose[@]}" ps --status running --services 2>/dev/null)" || running=""
for service in db api web; do
  if printf '%s\n' "$running" | grep -Fxq "$service"; then
    pass "container_$service"
  else
    fail "container_$service"
  fi
done

if "${compose[@]}" exec -T db sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null 2>&1; then
  pass database
else
  fail database
fi
api_health="$("${compose[@]}" exec -T api wget -q -O - http://127.0.0.1:8080/actuator/health 2>/dev/null)" || api_health=""
if [[ "$api_health" == *'"status":"UP"'* ]]; then
  pass api_health
else
  fail api_health
fi
if curl --noproxy '*' --max-time 5 -fsS -o /dev/null http://127.0.0.1:3000/login 2>/dev/null; then
  pass web_login
else
  fail web_login
fi

disk_percent="$(df -P "$root" 2>/dev/null | awk 'NR == 2 { gsub(/%/, "", $5); print $5 }')" || disk_percent=""
if [[ "$disk_percent" =~ ^[0-9]+$ ]]; then
  printf 'disk_used_percent=%s\n' "$disk_percent"
  if (( disk_percent < disk_max_percent )); then pass disk; else fail disk; fi
else
  fail disk
fi

# A recent local dump is useful evidence, but does not prove an external backup.
newest_backup=""
backup_epoch=""
shopt -s nullglob
for file in "$backup_dir"/*.dump; do
  [[ -f "$file" ]] || continue
  file_epoch="$(stat -c %Y -- "$file" 2>/dev/null || stat -f %m -- "$file" 2>/dev/null)" || continue
  [[ "$file_epoch" =~ ^[0-9]+$ ]] || continue
  if [[ -z "$backup_epoch" || "$file_epoch" -gt "$backup_epoch" ]]; then
    newest_backup="$file"
    backup_epoch="$file_epoch"
  fi
done
shopt -u nullglob
if [[ -n "$newest_backup" ]]; then
  now_epoch="$(date +%s)"
  if [[ "$backup_epoch" =~ ^[0-9]+$ && "$now_epoch" =~ ^[0-9]+$ && "$now_epoch" -ge "$backup_epoch" ]]; then
    backup_age_hours=$(((now_epoch - backup_epoch) / 3600))
    printf 'local_backup_age_hours=%s\n' "$backup_age_hours"
    if (( backup_age_hours <= backup_max_hours )); then pass local_backup; else fail local_backup; fi
  else
    fail local_backup
  fi
else
  fail local_backup
fi

# One aggregate row; no customer, document, ticket or secret fields leave psql.
sql="SELECT
  (SELECT count(*) FROM outbox_event WHERE status = 'PENDING'),
  COALESCE((SELECT extract(epoch FROM now() - min(created_at))::bigint FROM outbox_event WHERE status = 'PENDING'), 0),
  (SELECT count(*) FROM print_job p LEFT JOIN orders o ON o.id = p.order_id
    WHERE p.status = 'PENDING' AND (p.order_id IS NULL OR o.status <> 'CANCELLED')),
  (SELECT count(*) FROM print_job WHERE status = 'FAILED'),
  (SELECT count(*) FROM fiscal_document WHERE status IN ('PENDING', 'PROCESSING', 'REJECTED', 'CONTINGENCY')),
  (SELECT count(*) FROM fiscal_submission_attempt WHERE status IN ('DISPATCHING', 'UNCERTAIN'));"
counts="$("${compose[@]}" exec -T db sh -c 'exec psql -X -qAt -v ON_ERROR_STOP=1 -F "|" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "$1"' sh "$sql" 2>/dev/null)" || counts=""
if [[ "$counts" =~ ^([0-9]+)\|([0-9]+)\|([0-9]+)\|([0-9]+)\|([0-9]+)\|([0-9]+)$ ]]; then
  printf 'outbox_pending=%s\noutbox_oldest_seconds=%s\nprint_pending_eligible=%s\nprint_failed=%s\nfiscal_attention=%s\nfiscal_submission_uncertain=%s\n' \
    "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}" "${BASH_REMATCH[4]}" "${BASH_REMATCH[5]}" "${BASH_REMATCH[6]}"
  if (( ${BASH_REMATCH[2]} <= outbox_max_seconds )); then pass outbox; else fail outbox; fi
  if (( ${BASH_REMATCH[4]} == 0 )); then pass print_failures; else fail print_failures; fi
  if (( ${BASH_REMATCH[6]} == 0 )); then pass fiscal_uncertain; else fail fiscal_uncertain; fi
else
  fail queue_query
fi

exit "$failed"
