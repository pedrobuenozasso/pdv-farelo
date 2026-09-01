# apps/api

Spring Boot 3 (Java 21) — backend do Farelo OS, servido em `api.farelo.com.br`.

Arquitetura: Modular Monolith. Domínios previstos (ver `docs/domain-model.md`):

```
auth, catalog, customer, command, ordering, kitchen, printing,
inventory, recipe, notification, payment, fiscal, reporting, audit
```

## Rodando localmente

Pré-requisito: Java 21.

```bash
# usando o Maven Wrapper (recomendado, não requer Maven instalado)
./mvnw spring-boot:run

# ou, com Maven instalado localmente
mvn spring-boot:run
```

A aplicação sobe por padrão em `http://localhost:8080`. Health check disponível em
`http://localhost:8080/actuator/health`.

## Testes

```bash
./mvnw test
# ou
mvn test
```
