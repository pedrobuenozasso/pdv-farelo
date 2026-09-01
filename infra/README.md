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
