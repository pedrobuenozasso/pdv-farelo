#!/usr/bin/env bash
# Teste isolado de backup-offsite.sh com age e rclone reais e um remoto rclone
# do tipo "local" num diretório temporário. Não usa rede nem credenciais.
# Requer Linux (GNU date), bash, age, age-keygen, rclone, flock e sha256sum.
# Sem esses comandos, rodar num contêiner descartável, por exemplo:
#   docker run --rm -v "$PWD/deploy:/deploy:ro" alpine:3.20 sh -c \
#     'apk add -q bash coreutils age rclone util-linux && bash /deploy/test-backup-offsite.sh'
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
for tool in age age-keygen rclone flock sha256sum; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    printf 'test-backup-offsite: comando ausente: %s (ver cabeçalho do teste).\n' "$tool" >&2
    exit 2
  fi
done

fixture="$(mktemp -d)"
trap 'rm -rf -- "$fixture"' EXIT
site="$fixture/site"
remote_dir="$fixture/remote"
dest="$remote_dir/farelo/pg"
mkdir -p "$site/shared/backups" "$remote_dir" "$fixture/owner"
chmod 700 "$site/shared/backups"

run_offsite() { FARELO_BACKUP_ROOT="$site" bash "$script_dir/backup-offsite.sh"; }
expect_fail() {
  local label="$1" key="$2" output
  if output="$(run_offsite 2>&1)"; then
    printf '%s: deveria falhar.\n' "$label" >&2
    exit 1
  fi
  [[ "$output" == *"$key=FAIL"* ]] || { printf '%s: esperado %s=FAIL.\n' "$label" "$key" >&2; exit 1; }
  [[ "$output" != *AGE-SECRET-KEY* ]] || { printf '%s: chave privada vazou.\n' "$label" >&2; exit 1; }
}

# 1. Sem offsite.env: não configurado, saída 0, nada é criado.
output="$(run_offsite)"
[[ "$output" == *'offsite_backup=NOT_CONFIGURED'* ]]
[[ ! -e "$site/shared/offsite-state" ]]

# Chave do "proprietário" fora do site; só a pública vai para a VPS.
age-keygen -o "$fixture/owner/key.txt" 2>/dev/null
age-keygen -y "$fixture/owner/key.txt" > "$site/shared/offsite-age-recipients.txt"
printf '[testremote]\ntype = local\n' > "$site/shared/rclone.conf"
chmod 600 "$site/shared/rclone.conf"
write_env() {
  cat > "$site/shared/offsite.env" <<EOF
# comentário
OFFSITE_REMOTE=testremote:$remote_dir
OFFSITE_PREFIX="farelo/pg"
OFFSITE_AGE_RECIPIENTS_FILE=$site/shared/offsite-age-recipients.txt
OFFSITE_RCLONE_CONFIG=$site/shared/rclone.conf
OFFSITE_KEEP_DAILY=${1:-14}
OFFSITE_KEEP_WEEKLY=${2:-8}
OFFSITE_KEEP_MONTHLY=${3:-12}
EOF
}
write_env

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
dump="$site/shared/backups/daily-$stamp.dump"
{ printf 'PGDMP'; head -c 4096 /dev/urandom; } > "$dump"
chmod 600 "$dump"
older="$site/shared/backups/daily-20200101T050000Z.dump"
printf 'PGDMP old' > "$older"
: > "$site/shared/backups/.daily-$stamp-tmp.dump"
local_before="$(sha256sum "$site/shared/backups"/* "$site/shared/backups"/.daily-* | sha256sum)"

# 2. Envio normal: cifrado, verificado, publicado com .sha256 e marcador.
output="$(run_offsite)"
[[ "$output" == *'offsite_upload=OK'* && "$output" == *'offsite_backup=OK'* ]]
[[ "$output" == *"offsite_object=daily-$stamp.dump.age"* ]]
object="$dest/daily-$stamp.dump.age"
[[ -f "$object" && -f "$object.sha256" ]]
[[ "$(head -c 21 "$object")" == 'age-encryption.org/v1' ]]
if grep -q PGDMP "$object"; then printf 'Objeto remoto não está cifrado.\n' >&2; exit 1; fi
(cd "$dest" && sha256sum -c --quiet "daily-$stamp.dump.age.sha256")
age -d -i "$fixture/owner/key.txt" "$object" | cmp -s - "$dump"
[[ -z "$(find "$dest" -name '*.partial')" ]]
[[ "$(find "$dest" -name 'daily-*.dump.age' | wc -l | tr -d ' ')" == 1 ]]
grep -qx "object=daily-$stamp.dump.age" "$site/shared/offsite-state/last-success"
[[ -z "$(find "$site/shared/offsite-state" -name '.work-*')" ]]
[[ "$(stat -c %a "$site/shared/offsite-state")" == 700 ]]
[[ "$output" != *AGE-SECRET-KEY* ]]
# O backup local não muda.
[[ "$(sha256sum "$site/shared/backups"/* "$site/shared/backups"/.daily-* | sha256sum)" == "$local_before" ]]

# 3. Segunda execução: nada é reenviado.
before="$(sha256sum "$object")"
output="$(run_offsite)"
[[ "$output" == *'offsite_upload=SKIPPED_ALREADY_PRESENT'* && "$output" == *'offsite_backup=OK'* ]]
[[ "$(sha256sum "$object")" == "$before" ]]

# 4. Retenção: 3 diários + 3 semanais + 3 mensais sobre objetos antigos.
for old in 20260101T050000Z 20260102T040000Z 20260102T050000Z 20260115T050000Z \
           20260201T050000Z 20260210T050000Z 20260211T050000Z; do
  printf 'x' > "$dest/daily-$old.dump.age"
  printf 'h  daily-%s.dump.age\n' "$old" > "$dest/daily-$old.dump.age.sha256"
done
printf 'x' > "$dest/daily-20250505T050000Z.dump.age.partial"
printf 'h\n' > "$dest/daily-20250101T000000Z.dump.age.sha256"
printf 'manter\n' > "$dest/notes.txt"
write_env 3 3 3
output="$(run_offsite)"
[[ "$output" == *'offsite_pruned=3'* && "$output" == *'offsite_retained=5'* ]]
remaining="$(find "$dest" -mindepth 1 -maxdepth 1 -printf '%f\n' | sort | tr '\n' ' ')"
expected="daily-20260115T050000Z.dump.age daily-20260115T050000Z.dump.age.sha256 \
daily-20260201T050000Z.dump.age daily-20260201T050000Z.dump.age.sha256 \
daily-20260210T050000Z.dump.age daily-20260210T050000Z.dump.age.sha256 \
daily-20260211T050000Z.dump.age daily-20260211T050000Z.dump.age.sha256 \
daily-$stamp.dump.age daily-$stamp.dump.age.sha256 notes.txt "
[[ "$remaining" == "$expected" ]] || { printf 'Retenção inesperada: %s\n' "$remaining" >&2; exit 1; }

# OFFSITE_PRUNE=0 não apaga nada.
printf 'x' > "$dest/daily-20240101T050000Z.dump.age"
printf 'OFFSITE_PRUNE=0\n' >> "$site/shared/offsite.env"
output="$(run_offsite)"
[[ "$output" == *'offsite_retention=SKIPPED_PROVIDER_MANAGED'* ]]
[[ -f "$dest/daily-20240101T050000Z.dump.age" ]]
write_env

# 5. Configurações inválidas falham fechadas e não atualizam o marcador.
touch -t 202001010000 "$site/shared/offsite-state/last-success"
marker_before="$(stat -c %Y "$site/shared/offsite-state/last-success")"

printf 'RCLONE_S3_SECRET_ACCESS_KEY=x\n' >> "$site/shared/offsite.env"
expect_fail 'chave desconhecida' offsite_configuration
write_env
sed -i "s|^OFFSITE_REMOTE=.*|OFFSITE_REMOTE=:s3,secret_access_key=x:bucket|" "$site/shared/offsite.env"
expect_fail 'remoto inline' offsite_configuration
write_env 0
expect_fail 'retenção diária zero' offsite_configuration
write_env

chmod 644 "$site/shared/rclone.conf"
expect_fail 'rclone.conf legível por outros' offsite_rclone_config
chmod 600 "$site/shared/rclone.conf"

cp "$site/shared/offsite-age-recipients.txt" "$fixture/recipients.bak"
cat "$fixture/owner/key.txt" >> "$site/shared/offsite-age-recipients.txt"
expect_fail 'chave privada na VPS' offsite_age_recipients
printf 'nao-e-chave\n' > "$site/shared/offsite-age-recipients.txt"
expect_fail 'destinatário inválido' offsite_age_recipients
cp "$fixture/recipients.bak" "$site/shared/offsite-age-recipients.txt"

touch -t 202001010000 "$dump"
expect_fail 'dump local velho' offsite_source
touch "$dump"

sed -i "s|^OFFSITE_REMOTE=.*|OFFSITE_REMOTE=naoexiste:$remote_dir|" "$site/shared/offsite.env"
expect_fail 'remoto inexistente' offsite_remote
write_env

# Destino que devolve SHA-256 diferente: nada é publicado e o provisório sai.
mkdir -p "$fixture/shim"
real_rclone="$(command -v rclone)"
cat > "$fixture/shim/rclone" <<EOF
#!/usr/bin/env bash
for arg in "\$@"; do
  if [[ "\$arg" == hashsum ]]; then printf '%064d  x\n' 0; exit 0; fi
done
exec "$real_rclone" "\$@"
EOF
chmod +x "$fixture/shim/rclone"
stamp2="$(date -u -d '+1 minute' +%Y%m%dT%H%M%SZ)"
printf 'PGDMP novo' > "$site/shared/backups/daily-$stamp2.dump"
local_before="$(sha256sum "$site/shared/backups"/* "$site/shared/backups"/.daily-* | sha256sum)"
if output="$(PATH="$fixture/shim:$PATH" run_offsite 2>&1)"; then
  printf 'Hash divergente deveria falhar.\n' >&2
  exit 1
fi
[[ "$output" == *'offsite_verify=FAIL'* ]]
[[ ! -e "$dest/daily-$stamp2.dump.age" && ! -e "$dest/daily-$stamp2.dump.age.partial" ]]

[[ "$(stat -c %Y "$site/shared/offsite-state/last-success")" == "$marker_before" ]]
[[ "$(sha256sum "$site/shared/backups"/* "$site/shared/backups"/.daily-* | sha256sum)" == "$local_before" ]]

# 6. Depois de tudo, uma execução válida volta a passar.
output="$(run_offsite)"
[[ "$output" == *'offsite_backup=OK'* && "$output" == *"offsite_object=daily-$stamp2.dump.age"* ]]
[[ -f "$dest/daily-$stamp2.dump.age" && -f "$dest/daily-$stamp2.dump.age.sha256" ]]
grep -qx "object=daily-$stamp2.dump.age" "$site/shared/offsite-state/last-success"
printf 'Offsite backup checks passed.\n'
