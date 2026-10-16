#!/usr/bin/env bash
# Exercita o comando selecionado e a falha do dump sem Docker/restic reais.
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir "$fixture/bin"
touch "$fixture/compose.yml" "$fixture/restic-password"
printf 'POSTGRES_USER=test\nPOSTGRES_DB=test\n' > "$fixture/production.env"
cat > "$fixture/backup.env" <<EOF
RESTIC_REPOSITORY=s3:https://example.invalid/test
RESTIC_PASSWORD_FILE=$fixture/restic-password
FARELO_COMPOSE_FILE=$fixture/compose.yml
FARELO_DB_SERVICE=db
EOF
cat > "$fixture/bin/docker" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FARELO_TEST_LOG"
if [[ "${FARELO_TEST_DUMP_FAIL:-0}" == 1 ]]; then exit 42; fi
printf 'mock dump'
EOF
cat > "$fixture/bin/restic" <<'EOF'
#!/usr/bin/env bash
printf 'restic %s\n' "$1" >> "$FARELO_TEST_LOG"
if [[ "$1" == backup ]]; then
  while [[ "$1" != -- ]]; do shift; done
  shift
  "$@" >/dev/null
fi
EOF
chmod +x "$fixture/bin/docker" "$fixture/bin/restic"
export PATH="$fixture/bin:$PATH" FARELO_TEST_LOG="$fixture/calls"

"$script_dir/backup-postgres.sh" "$fixture/production.env" "$fixture/backup.env"
grep -q -- "-f $fixture/compose.yml exec -T db" "$fixture/calls"
grep -q '^restic snapshots$' "$fixture/calls"

: > "$fixture/calls"
export FARELO_TEST_DUMP_FAIL=1
if "$script_dir/backup-postgres.sh" "$fixture/production.env" "$fixture/backup.env"; then
  printf 'Backup aceitou um pg_dump com falha.\n' >&2
  exit 1
fi
if grep -q '^restic snapshots$' "$fixture/calls"; then
  printf 'Backup confirmou snapshot após falha do pg_dump.\n' >&2
  exit 1
fi
printf 'Backup: seleção de serviço e falha do dump validadas.\n'
