#!/usr/bin/env bash
# Teste de restauração do backup externo (OPS-01). Roda na máquina do
# proprietário, que guarda a chave PRIVADA age; nunca na VPS.
#
# Baixa o objeto mais recente (ou --object), confere o SHA-256, decifra,
# confere o catálogo com pg_restore --list e restaura num PostgreSQL 16
# descartável, sem rede. Imprime só contagens e estados, e apaga o contêiner
# e os arquivos temporários ao final, inclusive em caso de erro.
#
# Requer: bash, docker, rclone e age (no Mac: `brew install rclone age`).
# Compatível com o bash 3.2 do macOS.
#
# Uso:
#   bash deploy/restore-offsite-check.sh \
#     --remote farelo-offsite:nome-do-bucket/farelo-os/postgres \
#     --identity ~/caminho/farelo-backup-age.key \
#     [--rclone-config ~/.config/rclone/rclone.conf] [--object daily-...dump.age] \
#     [--min-products 1]
set -euo pipefail
umask 077

remote="${FARELO_RESTORE_REMOTE:-}"
identity="${FARELO_RESTORE_IDENTITY:-}"
rclone_config="${FARELO_RESTORE_RCLONE_CONFIG:-${RCLONE_CONFIG:-}}"
object="${FARELO_RESTORE_OBJECT:-}"
min_products="${FARELO_RESTORE_MIN_PRODUCTS:-1}"
pg_image="${FARELO_RESTORE_PG_IMAGE:-postgres:16-alpine}"
object_pattern='^daily-[0-9]{8}T[0-9]{6}Z\.dump\.age$'

usage() { sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; }
fail() {
  printf '%s=FAIL\n' "$1" >&2
  if [[ -n "${2:-}" ]]; then printf '%s\n' "$2" >&2; fi
  exit 1
}

while (( $# > 0 )); do
  case "$1" in
    --remote) remote="${2:-}"; shift 2 ;;
    --identity) identity="${2:-}"; shift 2 ;;
    --rclone-config) rclone_config="${2:-}"; shift 2 ;;
    --object) object="${2:-}"; shift 2 ;;
    --min-products) min_products="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

[[ -n "$remote" ]] || fail restore_configuration 'Informe --remote remoto:caminho.'
[[ -n "$identity" && -r "$identity" ]] || fail restore_configuration 'Informe --identity com a chave privada age legível.'
[[ "$min_products" =~ ^[0-9]+$ ]] || fail restore_configuration '--min-products deve ser inteiro.'
if [[ -n "$object" && ! "$object" =~ $object_pattern ]]; then
  fail restore_configuration '--object deve ter o formato daily-AAAAMMDDTHHMMSSZ.dump.age.'
fi
for tool in docker rclone age; do
  command -v "$tool" >/dev/null 2>&1 || fail restore_dependency "Comando necessário não instalado: $tool"
done
if command -v sha256sum >/dev/null 2>&1; then
  sha256() { sha256sum -- "$1" | awk '{ print $1 }'; }
elif command -v shasum >/dev/null 2>&1; then
  sha256() { shasum -a 256 -- "$1" | awk '{ print $1 }'; }
else
  fail restore_dependency 'Comando necessário não instalado: sha256sum ou shasum'
fi
docker info >/dev/null 2>&1 || fail restore_dependency 'Docker não está respondendo.'

rclone_args=(-q)
if [[ -n "$rclone_config" ]]; then rclone_args+=(--config "$rclone_config"); fi
remote="${remote%/}"

work="$(mktemp -d "${TMPDIR:-/tmp}/farelo-restore-XXXXXX")"
container="farelo-restore-check-$$-$RANDOM"
cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
  rm -rf -- "$work"
}
trap cleanup EXIT
err="$work/stderr"
debug_tail() { if [[ "${FARELO_RESTORE_DEBUG:-0}" == 1 && -s "$err" ]]; then cat -- "$err" >&2; fi; }

listing="$(rclone "${rclone_args[@]}" lsf --files-only --max-depth 1 "$remote" 2>>"$err")" \
  || { debug_tail; fail restore_remote 'Não foi possível listar o destino externo.'; }
if [[ -z "$object" ]]; then
  object="$(printf '%s\n' "$listing" | grep -E "$object_pattern" | sort -r | while IFS= read -r name; do
    if printf '%s\n' "$listing" | grep -Fxq "$name.sha256"; then printf '%s\n' "$name"; break; fi
  done)" || true
  [[ -n "$object" ]] || fail restore_remote 'Nenhum objeto daily-*.dump.age com .sha256 no destino.'
fi
printf 'restore_object=%s\n' "$object"

rclone "${rclone_args[@]}" copyto "$remote/$object" "$work/$object" 2>>"$err" \
  || { debug_tail; fail restore_download 'Falha ao baixar o objeto cifrado.'; }
rclone "${rclone_args[@]}" copyto "$remote/$object.sha256" "$work/$object.sha256" 2>>"$err" \
  || { debug_tail; fail restore_download 'Falha ao baixar o arquivo .sha256.'; }
expected="$(awk 'NR == 1 { print $1 }' "$work/$object.sha256")"
actual="$(sha256 "$work/$object")"
[[ "$expected" =~ ^[0-9a-f]{64}$ && "$expected" == "$actual" ]] || fail restore_sha256 'SHA-256 do objeto baixado não confere.'
printf 'restore_sha256=OK\n'

dump="$work/restore.dump"
age --decrypt -i "$identity" -o "$dump" "$work/$object" 2>>"$err" \
  || { debug_tail; fail restore_decrypt 'Falha ao decifrar (chave errada ou objeto corrompido).'; }
rm -f -- "$work/$object"
[[ "$(head -c 5 "$dump")" == PGDMP ]] || fail restore_decrypt 'O conteúdo decifrado não é um pg_dump -Fc.'
printf 'restore_decrypt=OK\n'

# Sem rede e com autenticação local "trust": nada fica exposto fora do contêiner.
docker run -d --rm --name "$container" --network none \
  -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_DB=restore_check \
  "$pg_image" >/dev/null 2>>"$err" || { debug_tail; fail restore_container 'Falha ao iniciar o PostgreSQL descartável.'; }
ready=0
for _ in $(seq 1 60); do
  # O servidor temporário da inicialização não escuta em TCP; esperar o definitivo.
  if docker exec "$container" pg_isready -q -h 127.0.0.1 -U postgres -d restore_check >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 1
done
(( ready == 1 )) || fail restore_container 'PostgreSQL descartável não ficou pronto em 60 s.'

docker exec -i "$container" pg_restore --list < "$dump" > "$work/list" 2>>"$err" \
  || { debug_tail; fail restore_list 'pg_restore --list recusou o dump.'; }
list_entries="$(grep -cv '^;' "$work/list" || true)"
table_data="$(grep -c ' TABLE DATA ' "$work/list" || true)"
printf 'restore_list_entries=%s\nrestore_list_table_data=%s\n' "$list_entries" "$table_data"

docker exec -i "$container" pg_restore --exit-on-error --no-owner --no-acl \
  -U postgres -d restore_check < "$dump" > /dev/null 2>>"$err" \
  || { debug_tail; fail restore_pg_restore 'pg_restore --exit-on-error falhou no banco descartável.'; }
rm -f -- "$dump"

query() {
  docker exec "$container" psql -X -qAt -v ON_ERROR_STOP=1 -U postgres -d restore_check -c "$1" 2>>"$err"
}
tables="$(query "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")" \
  || fail restore_sanity 'Falha ao contar tabelas.'
[[ "$tables" =~ ^[0-9]+$ ]] || fail restore_sanity 'Contagem de tabelas inválida.'
printf 'restore_tables=%s\n' "$tables"
(( tables > 0 )) || fail restore_sanity 'Nenhuma tabela restaurada.'

products="$(query 'SELECT count(*) FROM product')" || fail restore_sanity 'Tabela product ausente ou ilegível.'
[[ "$products" =~ ^[0-9]+$ ]] || fail restore_sanity 'Contagem de produtos inválida.'
printf 'restore_products=%s\n' "$products"
(( products >= min_products && products > 0 )) || fail restore_sanity 'Menos produtos que o mínimo esperado.'

has_flyway="$(query "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'")" || has_flyway=0
if [[ "$has_flyway" == 1 ]]; then
  flyway="$(query 'SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1')" || flyway=""
  failed_migrations="$(query 'SELECT count(*) FROM flyway_schema_history WHERE NOT success')" || failed_migrations=""
  printf 'restore_flyway_version=%s\nrestore_flyway_failed=%s\n' "${flyway:-?}" "${failed_migrations:-?}"
  [[ "$failed_migrations" == 0 ]] || fail restore_sanity 'Histórico Flyway com migração falha ou ilegível.'
fi

printf 'restore_checked_at=%s\nrestore_test=OK\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
