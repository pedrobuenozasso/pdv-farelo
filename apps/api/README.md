# apps/api

Spring Boot 3 (Java 21) — backend do Farelo OS, servido em `api.farelo.com.br`.

Arquitetura: Modular Monolith. Domínios previstos (ver `docs/domain-model.md`):

```
auth, catalog, customer, command, ordering, kitchen, printing,
inventory, recipe, notification, payment, fiscal, reporting, audit
```

## Rodando localmente

Pré-requisitos: Java 21 e um PostgreSQL acessível (veja `infra/docker-compose.yml`).

1. Suba o Postgres local (a partir da raiz do repo):

   ```bash
   cd infra
   cp .env.example .env   # ajuste os valores se necessário
   docker compose up -d
   ```

2. A aplicação lê a conexão via variáveis de ambiente — mesmas usadas pelo
   `infra/.env.example` — com defaults sensatos para dev caso não sejam
   definidas:

   | Variável | Default |
   |---|---|
   | `POSTGRES_HOST` | `localhost` |
   | `POSTGRES_PORT` | `5432` |
   | `POSTGRES_DB` | `farelo` |
   | `POSTGRES_USER` | `farelo` |
   | `POSTGRES_PASSWORD` | `change-me` |

   Exporte-as no shell (ou use um `.env` carregado pela sua IDE) se os valores
   locais diferirem dos defaults.

3. Rode a aplicação:

   ```bash
   # usando o Maven Wrapper (recomendado, não requer Maven instalado)
   ./mvnw spring-boot:run

   # ou, com Maven instalado localmente
   mvn spring-boot:run
   ```

A aplicação sobe por padrão em `http://localhost:8080`. Ao iniciar, o Flyway
aplica automaticamente as migrations em `src/main/resources/db/migration`.
Health check disponível em `http://localhost:8080/actuator/health`.

## Testes

```bash
./mvnw test
# ou
mvn test
```

Os testes de contexto e de integração (Flyway/Postgres) usam
[Testcontainers](https://testcontainers.com/), que sobe um container
PostgreSQL real e descarta a necessidade de um banco configurado manualmente
— **é necessário ter o Docker rodando localmente** no modo padrão. O
container é criado uma única vez por execução (padrão "singleton container")
e reaproveitado entre as classes de teste.

Se o Docker local estiver indisponível, a suíte aceita um PostgreSQL **novo e
descartável** já iniciado, acessível apenas em loopback por uma porta diferente
de 5432. Configure `FARELO_TEST_JDBC_URL` com a URL
`jdbc:postgresql://127.0.0.1:<porta>/farelo_integration_test`, além de
`FARELO_TEST_DB_USER` e `FARELO_TEST_DB_PASSWORD`, e execute `mvn test`.
O código de teste recusa outros hosts, a porta padrão, outro nome de banco,
parâmetros de URL e credenciais ausentes. Nunca encaminhe a porta do banco
real para esse modo: os testes aplicam migrations e escrevem dados.
