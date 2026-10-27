#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf -- "$fixture"' EXIT
mkdir -p "$fixture/bin" "$fixture/site/current/deploy" "$fixture/site/shared"
: > "$fixture/site/current/deploy/compose.yml"
: > "$fixture/site/shared/.env"

cat > "$fixture/bin/docker" <<'MOCK'
#!/usr/bin/env bash
if [[ " $* " == *' pg_dump '* ]]; then
  [[ "${MOCK_DUMP_FAIL:-0}" == 0 ]] || exit 42
  printf 'PGDMP mock'
elif [[ " $* " == *' pg_restore --list '* ]]; then
  [[ "${MOCK_RESTORE_FAIL:-0}" == 0 ]] || exit 43
  [[ "$(cat)" == 'PGDMP mock' ]]
else
  exit 44
fi
MOCK
chmod +x "$fixture/bin/docker"
run_backup() { PATH="$fixture/bin:$PATH" FARELO_BACKUP_ROOT="$fixture/site" bash "$script_dir/backup-local.sh"; }

if output="$(run_backup)"; then
  [[ "$output" == 'local_backup=OK' ]] || exit 1
else
  printf 'Valid dump failed.\n' >&2
  exit 1
fi
[[ "$(find "$fixture/site/shared/backups" -name 'daily-*.dump' -type f | wc -l | tr -d ' ')" == 1 ]]
mode="$(stat -f %Lp "$fixture/site/shared/backups"/daily-*.dump 2>/dev/null || stat -c %a "$fixture/site/shared/backups"/daily-*.dump)"
[[ "$mode" == 600 ]]

if MOCK_DUMP_FAIL=1 run_backup >/dev/null 2>&1; then
  printf 'Failed pg_dump unexpectedly succeeded.\n' >&2
  exit 1
fi
if MOCK_RESTORE_FAIL=1 run_backup >/dev/null 2>&1; then
  printf 'Invalid dump unexpectedly succeeded.\n' >&2
  exit 1
fi
[[ "$(find "$fixture/site/shared/backups" -name 'daily-*.dump' -type f | wc -l | tr -d ' ')" == 1 ]]
[[ "$(find "$fixture/site/shared/backups" -name '.daily-*.dump' -type f | wc -l | tr -d ' ')" == 0 ]]
printf 'Local backup checks passed.\n'
