# apps/api

Spring Boot 3 (Java 21) — backend do Farelo OS, servido em `api.farelo.com.br`.

Arquitetura: Modular Monolith. Domínios previstos (ver `docs/domain-model.md`):

```
auth, catalog, customer, command, ordering, kitchen, printing,
inventory, recipe, notification, payment, fiscal, reporting, audit
```

Inicialização do projeto Spring Boot é escopo do FARELO-002.
