#!/usr/bin/env bash
# Envia ao destino externo (OPS-01) uma cópia cifrada do dump local mais
# recente já validado por backup-local.sh. A cifra usa somente chaves
# públicas age: a VPS consegue gravar a cópia, mas não consegue lê-la.
# A saída tem apenas estados, nomes de objeto e contagens; stderr do rclone
# é suprimido (OFFSITE_DEBUG=1 o mostra numa execução manual).
set -euo pipefail
umask 077

root="${FARELO_BACKUP_ROOT:-/opt/farelo-os}"
config_file="${FARELO_OFFSITE_ENV:-$root/shared/offsite.env}"
backup_dir="$root/shared/backups"
state_dir="$root/shared/offsite-state"
object_pattern='^daily-([0-9]{8})T[0-9]{6}Z\.dump\.age$'

fail() {
  printf '%s=FAIL\n' "$1" >&2
  if [[ -n "${2:-}" ]]; then printf '%s\n' "$2" >&2; fi
  exit 1
}

if [[ ! -e "$config_file" ]]; then
  printf 'offsite_backup=NOT_CONFIGURED\n'
  printf 'Backup externo desativado: %s não existe. O backup local não é afetado.\n' "$config_file"
  exit 0
fi
if [[ ! -r "$config_file" ]]; then
  fail offsite_configuration 'offsite.env existe, mas não pode ser lido.'
fi

declare -A cfg=(
  [OFFSITE_REMOTE]=''
  [OFFSITE_PREFIX]=''
  [OFFSITE_AGE_RECIPIENTS_FILE]="$root/shared/offsite-age-recipients.txt"
  [OFFSITE_RCLONE_CONFIG]="$root/shared/rclone.conf"
  [OFFSITE_KEEP_DAILY]='14'
  [OFFSITE_KEEP_WEEKLY]='8'
  [OFFSITE_KEEP_MONTHLY]='12'
  [OFFSITE_PRUNE]='1'
  [OFFSITE_MAX_LOCAL_AGE_HOURS]='26'
)
# Leitura KEY=valor sem `source`: o arquivo não executa código.
line_number=0
while IFS= read -r line || [[ -n "$line" ]]; do
  line_number=$((line_number + 1))
  line="${line%$'\r'}"
  [[ "$line" =~ ^[[:space:]]*(#.*)?$ ]] && continue
  if [[ ! "$line" =~ ^([A-Z][A-Z0-9_]*)=(.*)$ ]]; then
    fail offsite_configuration "Linha $line_number de offsite.env não está no formato CHAVE=valor."
  fi
  key="${BASH_REMATCH[1]}"
  value="${BASH_REMATCH[2]}"
  if [[ "$value" =~ ^\"(.*)\"$ || "$value" =~ ^\'(.*)\'$ ]]; then value="${BASH_REMATCH[1]}"; fi
  if [[ -z "${cfg[$key]+set}" ]]; then
    fail offsite_configuration "Chave desconhecida em offsite.env (linha $line_number)."
  fi
  cfg[$key]="$value"
done < "$config_file"

remote="${cfg[OFFSITE_REMOTE]}"
prefix="${cfg[OFFSITE_PREFIX]}"
recipients_file="${cfg[OFFSITE_AGE_RECIPIENTS_FILE]}"
rclone_config="${cfg[OFFSITE_RCLONE_CONFIG]}"
keep_daily="${cfg[OFFSITE_KEEP_DAILY]}"
keep_weekly="${cfg[OFFSITE_KEEP_WEEKLY]}"
keep_monthly="${cfg[OFFSITE_KEEP_MONTHLY]}"
prune="${cfg[OFFSITE_PRUNE]}"
max_local_age_hours="${cfg[OFFSITE_MAX_LOCAL_AGE_HOURS]}"

# Só "nome-do-remoto:caminho". Vírgula e "=" ficam proibidos para que nenhuma
# credencial possa ser escrita na sintaxe inline do rclone.
if [[ ! "$remote" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]*:[A-Za-z0-9_./-]*$ ]]; then
  fail offsite_configuration 'OFFSITE_REMOTE deve ser "remoto:caminho", com o remoto definido no rclone.conf.'
fi
if [[ ! "$prefix" =~ ^[A-Za-z0-9_./-]*$ || "$prefix" == *..* || "$prefix" == /* ]]; then
  fail offsite_configuration 'OFFSITE_PREFIX aceita apenas letras, números, ".", "_", "-" e "/".'
fi
for number in "$keep_daily" "$keep_weekly" "$keep_monthly" "$max_local_age_hours"; do
  [[ "$number" =~ ^[0-9]+$ ]] || fail offsite_configuration 'Retenção e idade máxima devem ser inteiros.'
done
if (( keep_daily < 1 )); then
  fail offsite_configuration 'OFFSITE_KEEP_DAILY deve ser pelo menos 1.'
fi
[[ "$prune" == 0 || "$prune" == 1 ]] || fail offsite_configuration 'OFFSITE_PRUNE deve ser 0 ou 1.'

for tool in age rclone flock date sha256sum; do
  command -v "$tool" >/dev/null 2>&1 || fail offsite_dependency "Comando necessário não instalado: $tool"
done

if [[ ! -r "$rclone_config" ]]; then
  fail offsite_rclone_config 'rclone.conf não encontrado ou ilegível.'
fi
rclone_mode="$(stat -c %a -- "$rclone_config" 2>/dev/null || stat -f %Lp -- "$rclone_config")"
if [[ ! "$rclone_mode" =~ ^[0-7]*[0-7]00$ ]]; then
  fail offsite_rclone_config 'rclone.conf contém credenciais e precisa ser modo 600 (sem leitura por grupo/outros).'
fi

if [[ ! -r "$recipients_file" ]]; then
  fail offsite_age_recipients 'Arquivo de chaves públicas age não encontrado ou ilegível.'
fi
recipient_count=0
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%$'\r'}"
  [[ "$line" =~ ^[[:space:]]*(#.*)?$ ]] && continue
  if [[ "$line" == AGE-SECRET-KEY-* || "$line" == AGE-PLUGIN-* ]]; then
    fail offsite_age_recipients 'Há uma chave PRIVADA age no arquivo de destinatários. Remova-a da VPS; use só a chave pública (age1...).'
  fi
  if [[ ! "$line" =~ ^age1[0-9a-z]{58,}$ ]]; then
    fail offsite_age_recipients 'Destinatário age inválido: cada linha deve ser uma chave pública age1...'
  fi
  recipient_count=$((recipient_count + 1))
done < "$recipients_file"
(( recipient_count > 0 )) || fail offsite_age_recipients 'Nenhuma chave pública age configurada.'

install -d -m 700 "$state_dir"
exec 9> "$state_dir/lock"
flock -n 9 || fail offsite_lock 'Outra execução do backup externo está em andamento.'
# Com o lock, restos de uma execução interrompida (só texto cifrado) podem sair.
find "$state_dir" -mindepth 1 -maxdepth 1 -type d -name '.work-*' -exec rm -rf -- {} +

# Dump mais recente publicado por backup-local.sh. Temporários começam com
# ".daily-" e dumps manuais (pre-*) não entram na rotação externa.
newest=""
shopt -s nullglob
for file in "$backup_dir"/daily-*.dump; do
  name="${file##*/}"
  [[ -f "$file" && "$name" =~ ^daily-[0-9]{8}T[0-9]{6}Z\.dump$ ]] || continue
  if [[ -z "$newest" || "$name" > "${newest##*/}" ]]; then newest="$file"; fi
done
shopt -u nullglob
[[ -n "$newest" ]] || fail offsite_source 'Nenhum dump local daily-*.dump encontrado.'

dump_epoch="$(stat -c %Y -- "$newest" 2>/dev/null || stat -f %m -- "$newest")"
now_epoch="$(date +%s)"
if [[ ! "$dump_epoch" =~ ^[0-9]+$ ]] || (( now_epoch - dump_epoch > max_local_age_hours * 3600 )); then
  fail offsite_source 'O dump local mais recente está velho demais; verifique farelo-backup-local.service.'
fi
if [[ ! -s "$newest" || "$(head -c 5 -- "$newest")" != PGDMP ]]; then
  fail offsite_source 'O dump local mais recente não tem cabeçalho de pg_dump -Fc.'
fi

work="$(mktemp -d "$state_dir/.work-XXXXXX")"
trap 'rm -rf -- "$work"' EXIT
rclone_err="$work/rclone.err"
rc() {
  if rclone --config "$rclone_config" --retries 3 --low-level-retries 10 -q "$@" 2>>"$rclone_err"; then
    return 0
  fi
  if [[ "${OFFSITE_DEBUG:-0}" == 1 ]]; then cat -- "$rclone_err" >&2; fi
  return 1
}

if [[ -z "$prefix" ]]; then
  destination="$remote"
elif [[ "$remote" == *: ]]; then
  destination="$remote$prefix"
else
  destination="${remote%/}/$prefix"
fi
destination="${destination%/}"

dump_name="${newest##*/}"
object="$dump_name.age"
partial="$object.partial"

rc mkdir "$destination" || fail offsite_remote 'Não foi possível acessar o destino externo.'
listing="$(rc lsf --files-only --max-depth 1 "$destination")" || fail offsite_remote 'Não foi possível listar o destino externo.'

if printf '%s\n' "$listing" | grep -Fxq "$object" && printf '%s\n' "$listing" | grep -Fxq "$object.sha256"; then
  printf 'offsite_upload=SKIPPED_ALREADY_PRESENT\n'
else
  encrypted="$work/$object"
  if ! age --encrypt -R "$recipients_file" -o "$encrypted" -- "$newest" 2>/dev/null; then
    fail offsite_encrypt 'Falha ao cifrar o dump com age.'
  fi
  if [[ "$(head -c 21 -- "$encrypted")" != 'age-encryption.org/v1' ]]; then
    fail offsite_encrypt 'Arquivo cifrado sem cabeçalho age.'
  fi
  local_size="$(wc -c < "$encrypted" | tr -d ' ')"
  local_hash="$(sha256sum -- "$encrypted" | awk '{ print $1 }')"

  # Envia com nome provisório, confere tamanho e SHA-256 lendo de volta o
  # que o destino guardou e só então publica o nome definitivo.
  rc copyto "$encrypted" "$destination/$partial" || fail offsite_upload 'Falha no envio ao destino externo.'
  remote_json="$(rc lsjson --files-only --no-mimetype "$destination/$partial")" || remote_json=""
  remote_size="$(printf '%s' "$remote_json" | grep -o '"Size":[0-9]*' | head -n 1 | cut -d: -f2)"
  remote_hash="$(rc hashsum sha256 --download "$destination/$partial" | awk '{ print $1 }')" || remote_hash=""
  if [[ "$remote_size" != "$local_size" || "$remote_hash" != "$local_hash" ]]; then
    rc deletefile "$destination/$partial" || true
    fail offsite_verify 'O objeto enviado não confere em tamanho/SHA-256; foi descartado.'
  fi
  rc moveto "$destination/$partial" "$destination/$object" || fail offsite_upload 'Falha ao publicar o objeto verificado.'
  final_json="$(rc lsjson --files-only --no-mimetype "$destination/$object")" || final_json=""
  final_size="$(printf '%s' "$final_json" | grep -o '"Size":[0-9]*' | head -n 1 | cut -d: -f2)"
  [[ "$final_size" == "$local_size" ]] || fail offsite_verify 'O objeto publicado não confere em tamanho.'
  printf '%s  %s\n' "$local_hash" "$object" > "$work/$object.sha256"
  rc copyto "$work/$object.sha256" "$destination/$object.sha256" || fail offsite_upload 'Falha ao enviar o arquivo de SHA-256.'
  printf 'offsite_upload=OK\noffsite_size_bytes=%s\n' "$local_size"
fi
printf 'offsite_object=%s\n' "$object"

# Retenção: o objeto mais novo de cada um dos N dias, M semanas ISO e K meses
# mais recentes (UTC). Nomes fora do padrão nunca são apagados.
if [[ "$prune" == 1 ]]; then
  listing="$(rc lsf --files-only --max-depth 1 "$destination")" || fail offsite_retention 'Não foi possível listar o destino para a retenção.'
  declare -A keep=() seen_day=() seen_week=() seen_month=() present=()
  objects=()
  while IFS= read -r name; do
    [[ -n "$name" ]] || continue
    present[$name]=1
    if [[ "$name" =~ $object_pattern ]]; then objects+=("$name"); fi
  done <<< "$listing"
  keep[$object]=1
  days=0 weeks=0 months=0
  if (( ${#objects[@]} > 0 )); then
    while IFS= read -r name; do
      [[ "$name" =~ $object_pattern ]] || continue
      day="${BASH_REMATCH[1]}"
      if ! week="$(date -u -d "${day:0:4}-${day:4:2}-${day:6:2}" +%G-W%V 2>/dev/null)"; then
        keep[$name]=1
        continue
      fi
      month="${day:0:6}"
      if (( days < keep_daily )) && [[ -z "${seen_day[$day]:-}" ]]; then
        seen_day[$day]=1; keep[$name]=1; days=$((days + 1))
      fi
      if (( weeks < keep_weekly )) && [[ -z "${seen_week[$week]:-}" ]]; then
        seen_week[$week]=1; keep[$name]=1; weeks=$((weeks + 1))
      fi
      if (( months < keep_monthly )) && [[ -z "${seen_month[$month]:-}" ]]; then
        seen_month[$month]=1; keep[$name]=1; months=$((months + 1))
      fi
    done < <(printf '%s\n' "${objects[@]}" | sort -r)
  fi
  pruned=0
  for name in "${!present[@]}"; do
    base=""
    if [[ "$name" =~ $object_pattern ]]; then
      base="$name"
    elif [[ "$name" == *.dump.age.sha256 && "${name%.sha256}" =~ $object_pattern ]]; then
      base="${name%.sha256}"
      # Sidecar órfão (objeto já removido) também sai.
      [[ -n "${present[$base]:-}" ]] || base="__orphan__"
    elif [[ "$name" == *.dump.age.partial && "${name%.partial}" =~ $object_pattern ]]; then
      base="__partial__"
    else
      continue
    fi
    if [[ "$base" == __orphan__ || "$base" == __partial__ || -z "${keep[$base]:-}" ]]; then
      rc deletefile "$destination/$name" || fail offsite_retention 'Falha ao apagar objeto antigo no destino.'
      if [[ "$name" =~ $object_pattern ]]; then pruned=$((pruned + 1)); fi
    fi
  done
  printf 'offsite_retained=%s\noffsite_pruned=%s\n' "${#keep[@]}" "$pruned"
else
  printf 'offsite_retention=SKIPPED_PROVIDER_MANAGED\n'
fi

# Marcador lido por monitoring/check-private-vps.sh (offsite_backup_age).
printf 'object=%s\nverified_epoch=%s\n' "$object" "$(date +%s)" > "$state_dir/last-success.tmp"
mv -f -- "$state_dir/last-success.tmp" "$state_dir/last-success"
printf 'offsite_backup=OK\n'
