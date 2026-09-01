# API — Farelo OS

Este documento é preenchido conforme cada endpoint é criado.

## Convenções previstas

- Prefixo de versão: `/api/v1`
- Entidades JPA nunca são expostas diretamente — sempre via DTOs.
- Formato de erro padrão:

```json
{
  "code": "COMMAND_NOT_AVAILABLE",
  "message": "Comanda não está disponível.",
  "correlationId": "..."
}
```

## Endpoints

### `POST /api/v1/categories`

Cria uma categoria de produto. (FARELO-012)

**Request body**

```json
{
  "name": "Bebidas"
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `name` | string | sim | Não pode ser vazio/branco (`@NotBlank`) |

**Response — `201 Created`**

Header `Location: /api/v1/categories/{id}`.

```json
{
  "id": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "name": "Bebidas",
  "active": true,
  "createdAt": "2026-09-01T13:00:00Z",
  "updatedAt": "2026-09-01T13:00:00Z"
}
```

**Erros**

- `400 Bad Request` — `name` ausente ou em branco, no formato de erro padrão
  abaixo, com `code: "VALIDATION_ERROR"`:

  ```json
  {
    "code": "VALIDATION_ERROR",
    "message": "name: must not be blank",
    "correlationId": "..."
  }
  ```

Ainda não há `PUT`/`DELETE` para categoria (tickets futuros) nem endpoints
para `Product`.

### `GET /api/v1/categories`

Lista todas as categorias, ordenadas por `name` (asc). (FARELO-013)

Sem paginação/filtros por enquanto (YAGNI — mantido para um ticket futuro se
o Admin precisar).

**Response — `200 OK`**

```json
[
  {
    "id": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
    "name": "Bebidas",
    "active": true,
    "createdAt": "2026-09-01T13:00:00Z",
    "updatedAt": "2026-09-01T13:00:00Z"
  }
]
```

Lista vazia (`[]`) quando não há categorias cadastradas.

_(demais endpoints a preencher conforme implementados)_
