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
