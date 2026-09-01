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
