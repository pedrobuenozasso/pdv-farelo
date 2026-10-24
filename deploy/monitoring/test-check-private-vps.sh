#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf -- "$tmp"' EXIT
mkdir -p "$tmp/bin" "$tmp/site/current/deploy" "$tmp/site/shared/backups"
: > "$tmp/site/current/deploy/compose.yml"
: > "$tmp/site/shared/.env"
: > "$tmp/site/shared/backups/recent.dump"

cat > "$tmp/bin/docker" <<'MOCK'
#!/usr/bin/env bash
case " $* " in
  *' ps --status running --services '*) printf 'db\napi\nweb\n' ;;
  *' exec -T db sh -c pg_isready '*) exit 0 ;;
  *' exec -T api wget '*) printf '{"status":"UP"}' ;;
  *' exec -T db sh -c exec psql '*) printf '%s\n' "${MOCK_COUNTS:-0|0|0|0|0|0}" ;;
  *) printf 'unexpected docker call (secret must not appear)\n' >&2; exit 3 ;;
esac
MOCK
cat > "$tmp/bin/curl" <<'MOCK'
#!/usr/bin/env bash
[[ "${MOCK_WEB_FAIL:-0}" == 0 ]] || exit 7
exit 0
MOCK
chmod +x "$tmp/bin/docker" "$tmp/bin/curl"

run_check() {
  PATH="$tmp/bin:$PATH" FARELO_MONITOR_ROOT="$tmp/site" \
    FARELO_MONITOR_DISK_MAX_PERCENT=100 "$script_dir/check-private-vps.sh"
}

output="$(run_check)"
[[ "$output" == *'container_db=OK'* ]]
[[ "$output" == *'api_health=OK'* ]]
[[ "$output" == *'queue_query=FAIL'* ]] && exit 1
[[ "$output" == *'fiscal_uncertain=OK'* ]]
[[ "$output" != *secret* ]]

if output="$(MOCK_WEB_FAIL=1 run_check)"; then
  printf 'Unavailable Web should fail.\n' >&2
  exit 1
fi
[[ "$output" == *'web_login=FAIL'* ]]

touch -t 202001010000 "$tmp/site/shared/backups/recent.dump"
if output="$(run_check)"; then
  printf 'Stale local backup should fail.\n' >&2
  exit 1
fi
[[ "$output" == *'local_backup=FAIL'* ]]
touch "$tmp/site/shared/backups/recent.dump"

if output="$(MOCK_COUNTS='2|601|1|0|0|0' run_check)"; then
  printf 'Old outbox event should fail.\n' >&2
  exit 1
fi
[[ "$output" == *'outbox=FAIL'* ]]

if output="$(MOCK_COUNTS='0|0|0|1|0|1' run_check)"; then
  printf 'Failed print and uncertain fiscal attempt should fail.\n' >&2
  exit 1
fi
[[ "$output" == *'print_failures=FAIL'* ]]
[[ "$output" == *'fiscal_uncertain=FAIL'* ]]

if output="$(MOCK_COUNTS='bad|data' run_check)"; then
  printf 'Malformed database result should fail closed.\n' >&2
  exit 1
fi
[[ "$output" == *'queue_query=FAIL'* ]]
[[ "$output" != *bad* ]]

printf 'monitoring checks passed\n'
