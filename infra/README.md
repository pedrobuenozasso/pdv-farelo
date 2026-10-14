# infra

Configuração de infraestrutura: Docker Compose, Caddy, GitHub Actions e deploy.

## PostgreSQL (FARELO-003)

Sobe um container PostgreSQL para desenvolvimento local, com dados persistidos em
volume Docker nomeado.

### Configuração

1. Copie o arquivo de exemplo de variáveis de ambiente:

   ```bash
   cp infra/.env.example infra/.env
   ```

2. Edite `infra/.env` e defina valores próprios para `POSTGRES_USER`,
   `POSTGRES_PASSWORD`, `POSTGRES_DB` e `POSTGRES_PORT` (porta do host que será
   mapeada para a porta 5432 do container). **Nunca commitar `infra/.env`** — apenas
   `infra/.env.example` deve ir para o repositório.

### Subir o Postgres

```bash
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d
```

### Verificar status / logs

```bash
docker compose -f infra/docker-compose.yml ps
docker compose -f infra/docker-compose.yml logs -f postgres
```

### Parar

```bash
docker compose -f infra/docker-compose.yml down
```

Para apagar também os dados persistidos (volume `postgres_data`), adicione `-v`:

```bash
docker compose -f infra/docker-compose.yml down -v
```

### Conectar

Com os valores padrão do `.env.example`, a string de conexão fica:

```
postgresql://farelo:change-me@localhost:5432/farelo
```

Ajuste usuário, senha, database e porta conforme o `infra/.env` real.

## Stack completa de desenvolvimento (backend + frontend + Postgres)

`docker-compose.yml` sozinho sobe só o Postgres (uso mínimo — ex: rodar o
backend/frontend nativamente fora de container, como os tickets anteriores
sempre fizeram). `docker-compose.dev.yml` é um compose **complementar**
que adiciona `backend`/`frontend`, combinado com `-f`:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

Sobe os três serviços na rede do compose: Postgres (`:5432`), backend
(`:8080`, aponta pro Postgres via `POSTGRES_HOST=postgres`, não
`localhost`) e frontend (`:3000`, aponta pro backend via
`API_BASE_URL=http://backend:8080`). `depends_on: condition: service_healthy`
no backend garante que ele só sobe depois do Postgres passar no healthcheck.

Dois arquivos separados em vez de um único `docker-compose.yml`, porque o
mínimo (só Postgres) continua sendo útil por si só — quem quiser rodar
backend/frontend nativamente (`./mvnw spring-boot:run` / `npm run dev`,
como sempre foi possível) não precisa de Dockerfiles nem builds de imagem
para isso.

Dockerfiles em `infra/docker/` (`api.Dockerfile`, `web.Dockerfile`) — de
**desenvolvimento**, não de produção (sem ticket de deploy no roadmap
atual, ver `docs/architecture.md`):

- **Backend**: multi-stage (build com Maven completo, runtime só com JRE).
  Sem hot-reload — uma mudança no código exige rebuild
  (`up -d --build backend`).
- **Frontend**: roda `next dev` de verdade, com `apps/web` montado como
  bind mount por cima da imagem — mudanças no código do host são pegas
  pelo Fast Refresh sem rebuild. `node_modules`/`.next` ficam em volumes
  anônimos (preserva o que foi instalado dentro da imagem Linux, em vez de
  deixar o bind mount do host sobrescrever).

### Parar / rebuildar

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml down
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build   # após mudança no backend
```

## CI — GitHub Actions (FARELO-007)

Dois workflows independentes em `.github/workflows/`, disparados em `push` e
`pull_request` para `main`, cada um restrito por `paths` ao seu app (evita rodar
o job de backend quando só o frontend mudou, e vice-versa):

- **`backend.yml`** — Java 21 (Temurin, `actions/setup-java`) e roda
  `./mvnw test` em `apps/api`. Os testes de integração usam Testcontainers
  (`AbstractIntegrationTest`), que precisa de Docker; o runner `ubuntu-latest`
  já vem com Docker instalado e em execução por padrão, sem configuração
  extra.
- **`frontend.yml`** — Node.js (`actions/setup-node`) e roda, em `apps/web`:
  `npm ci`, `npm run lint`, `npm run format:check` e `npm run build`. Não há
  `.nvmrc` no repositório ainda; o workflow fixa Node **24** (LTS ativa no
  momento deste ticket — Node 22 já está em manutenção). Se um `.nvmrc` for
  adicionado depois, o workflow deve passar a lê-lo em vez de fixar a versão.

Ambos usam cache de dependências (`cache: maven` / `cache: npm`) para acelerar
execuções subsequentes.

## Preparação de produção (ainda não implantada)

`compose.production.yml`, `Caddyfile.production` e os Dockerfiles `*.production`
descrevem a stack web + API + PostgreSQL. Só Caddy publica portas no host
(80/tcp, 443/tcp e 443/udp); web e API não publicam portas, e PostgreSQL está
na rede Docker `data` interna. Caddy atende `/api/*` na API, o restante no
Next.js e nega `/actuator*` ao público. Há healthchecks, reinício automático
e limites iniciais de memória/CPU. O volume `postgres_data` contém os dados;
`caddy_data` preserva certificados ACME. As imagens usam usuários sem root
para API/web. Esta configuração **não é um deploy executado nem um sinal de
liberação para vendas reais**.

Na VPS, após aprovação dos gates abaixo, instalar Docker Engine/Compose,
Git, `restic` e copiar o repositório em uma versão revisada. Criar os arquivos
de configuração **fora do Git** e protegê-los com `chmod 600`:

```bash
sudo install -d -m 700 /etc/farelo
sudo cp infra/production.env.example /etc/farelo/production.env
sudo chmod 600 /etc/farelo/production.env
sudoedit /etc/farelo/production.env
docker compose --env-file /etc/farelo/production.env \
  -f infra/compose.production.yml config --quiet
```

Substituir todos os valores do exemplo. `FARELO_DOMAIN` precisa apontar para
esta VPS; `ACME_EMAIL` recebe avisos de certificados. Escolher senha PostgreSQL
única e `JWT_SECRET` aleatório com no mínimo 32 bytes; nunca reutilizar a senha
de login humano. A configuração via variáveis de ambiente ainda é visível a
administradores do daemon Docker, portanto acesso ao socket equivale a acesso
aos segredos. O Compose inicia Flyway automaticamente na API: antes de cada
atualização, revisar migrations e garantir backup restaurável.

O comando de subida, **somente depois da revisão de segurança e dos testes de
negócio**:

```bash
docker compose --env-file /etc/farelo/production.env \
  -f infra/compose.production.yml up -d --build
docker compose --env-file /etc/farelo/production.env \
  -f infra/compose.production.yml ps
```

O build ocorre na VPS e pode consumir recursos além dos limites de runtime;
medir CPU, RAM, swap e disco primeiro. Para atualização com rollback confiável,
construir e publicar imagens versionadas e testar restore em staging antes de
ligar esse Compose a clientes reais. O arquivo atual usa `build` local e
tags base móveis, adequado apenas como preparação revisável.

### Backup externo e teste de restauração

O backup lógico usa `pg_dump -Fc`, via `infra/backup-postgres.sh`, e envia o
stream diretamente ao [restic](https://restic.readthedocs.io/en/stable/040_backup.html)
(`--stdin-from-command` cancela o snapshot se o dump falhar) para um
repositório externo criptografado. Não substitui uma cópia física/PITR caso o RPO exigido seja
menor que o intervalo entre dumps. Contratar/configurar o destino externo
(por exemplo, bucket S3 compatível com credenciais próprias) antes de rodar.
Criar `/etc/farelo/backup.env` (`chmod 600`), contendo por exemplo:

```dotenv
RESTIC_REPOSITORY=s3:https://storage.example.com/farelo-backup
RESTIC_PASSWORD_FILE=/etc/farelo/restic-password
AWS_ACCESS_KEY_ID=preencher-fora-do-git
AWS_SECRET_ACCESS_KEY=preencher-fora-do-git
```

O arquivo `/etc/farelo/restic-password` também deve ter modo `600` e sua senha
precisa ser guardada em cofre **fora da VPS**; sem ela o backup não pode ser
recuperado. Inicializar o repositório uma vez e fazer backup manual de teste:

```bash
set -a; . /etc/farelo/backup.env; set +a
restic init
infra/backup-postgres.sh /etc/farelo/production.env /etc/farelo/backup.env
restic snapshots --tag farelo-db
```

Depois de validar, agendar o script diariamente em systemd timer (usuário
com acesso a Docker e aos arquivos de configuração), monitorar falhas e
definir retenção conforme requisitos fiscais/contábeis. Sem destino, senha,
agendamento, alerta de falha e teste de restauração, backup ainda não existe.
Não usar snapshot da VPS como única cópia.

Teste de recuperação **em staging isolado**, com mesma versão do PostgreSQL:
carregar `backup.env`, escolher explicitamente um snapshot em `restic
snapshots --tag farelo-db`, então:

```bash
restic dump SNAPSHOT_ID /farelo-postgres.dump > /tmp/farelo-restore.dump
pg_restore --list /tmp/farelo-restore.dump >/dev/null
createdb -h STAGING_HOST -U STAGING_USER farelo_restore_test
pg_restore -h STAGING_HOST -U STAGING_USER -d farelo_restore_test \
  --no-owner --no-acl /tmp/farelo-restore.dump
rm -f /tmp/farelo-restore.dump
```

Executar `umask 077` antes de exportar o dump e usar disco criptografado no
staging. Conferir contagens de pedidos, pagamentos, usuários e integridade das
migrations; registrar duração e ponto de recuperação. **Nunca restaurar por
cima do banco da produção** sem plano específico de indisponibilidade e
validação prévia. Um banco de teste restaurado deve ser isolado de serviços
capazes de enviar WhatsApp ou emitir documentos fiscais.

### Gates ainda pendentes

- Domínio definitivo, DNS, e-mail ACME e portas 80/443 liberadas para HTTPS.
- Auditoria real da VPS: sistema, recursos, serviços já existentes, disco,
  firewall, SSH por chave e usuário administrativo alternativo testado.
- Segurança da API e fechamento dos fluxos de comanda, pagamento, caixa e
  emissão fiscal antes de qualquer venda real; conta `caixa.farelodebolo`
  criada pelo administrador com senha definida na interface.
- Destino externo de backup, credenciais, retenção, monitoramento e teste de
  restauração; política de atualização e reversão.
- Teste ponta a ponta em staging com dados fictícios e comparação dos recursos
  observados com os limites iniciais (Postgres 2 GiB, API 2,5 GiB, web 1,5 GiB,
  Caddy 256 MiB; no total ~6,25 GiB de 8 GiB anunciados, antes do sistema,
  daemon Docker, buffers e builds).
