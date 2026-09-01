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

Ainda não há `PUT`/`DELETE` para categoria (tickets futuros).

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

### `POST /api/v1/products`

Cria um produto vendável do cardápio. (FARELO-014)

**Request body**

```json
{
  "name": "Café Espresso",
  "description": "Espresso curto, torra média",
  "price": 7.50,
  "categoryId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "imageUrl": "https://example.com/espresso.png"
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `name` | string | sim | Não pode ser vazio/branco (`@NotBlank`) |
| `description` | string | não | |
| `price` | decimal | sim | `>= 0.00` (`@DecimalMin`), nunca negativo |
| `categoryId` | UUID | sim | Precisa apontar para uma `Category` existente |
| `imageUrl` | string | não | |

**Response — `201 Created`**

Header `Location: /api/v1/products/{id}`.

```json
{
  "id": "8a1b2c3d-4e5f-6789-0abc-def123456789",
  "name": "Café Espresso",
  "description": "Espresso curto, torra média",
  "price": 7.50,
  "active": true,
  "categoryId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "imageUrl": "https://example.com/espresso.png",
  "createdAt": "2026-09-01T13:00:00Z",
  "updatedAt": "2026-09-01T13:00:00Z"
}
```

**Erros**

- `400 Bad Request` — `name`/`price`/`categoryId` ausente, `name` em branco
  ou `price` negativo, no formato de erro padrão com
  `code: "VALIDATION_ERROR"`.
- `404 Not Found` — `categoryId` não corresponde a nenhuma categoria
  existente:

  ```json
  {
    "code": "CATEGORY_NOT_FOUND",
    "message": "Category not found: b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
    "correlationId": "..."
  }
  ```

### `GET /api/v1/products`

Lista todos os produtos, ordenados por `name` (asc). (FARELO-015)

Sem paginação nem filtros (ex: por `categoryId`) por enquanto — decisão
deliberada de escopo (YAGNI, mesma lógica de `GET /api/v1/categories`):
nenhum consumidor (Admin/PDV) existe ainda pedindo isso. Um filtro por
`categoryId` via query param é um candidato natural para quando esse
consumidor existir.

**Response — `200 OK`**

```json
[
  {
    "id": "8a1b2c3d-4e5f-6789-0abc-def123456789",
    "name": "Café Espresso",
    "description": "Espresso curto, torra média",
    "price": 7.50,
    "active": true,
    "categoryId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
    "imageUrl": "https://example.com/espresso.png",
    "createdAt": "2026-09-01T13:00:00Z",
    "updatedAt": "2026-09-01T13:00:00Z"
  }
]
```

Lista vazia (`[]`) quando não há produtos cadastrados.

### `PUT /api/v1/products/{id}`

Atualiza (substituição completa) um produto existente. (FARELO-016)

Fecha o CRUD básico de `Product` — sem `DELETE` por enquanto (fora do
roadmap atual).

**Request body**

Mesmos campos de `POST /api/v1/products`, mais `active` (obrigatório aqui).
Usa um DTO próprio (`ProductUpdateRequest`) em vez de reaproveitar o request
de criação — `active` não faz sentido na criação (sempre começa `true`), e
torná-lo opcional lá seria arriscado: como o Jackson zera um `boolean`
primitivo ausente para `false` em records, um client que esquecesse de
enviá-lo na criação desativaria o produto silenciosamente.

```json
{
  "name": "Café Espresso Duplo",
  "description": "Dose dupla",
  "price": 9.90,
  "categoryId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "imageUrl": "https://example.com/espresso-duplo.png",
  "active": false
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `name` | string | sim | Não pode ser vazio/branco (`@NotBlank`) |
| `description` | string | não | |
| `price` | decimal | sim | `>= 0.00` (`@DecimalMin`), nunca negativo |
| `categoryId` | UUID | sim | Precisa apontar para uma `Category` existente |
| `imageUrl` | string | não | |
| `active` | boolean | sim | |

**Response — `200 OK`**

`ProductResponse` com os campos atualizados (mesmo formato de
`POST /api/v1/products`).

**Erros**

- `400 Bad Request` — mesmas validações do `POST`, `code: "VALIDATION_ERROR"`.
- `404 Not Found` — `{id}` do produto não existe, `code: "PRODUCT_NOT_FOUND"`;
  ou `categoryId` não existe, `code: "CATEGORY_NOT_FOUND"` (mesmo formato do
  `POST`).

_(demais endpoints a preencher conforme implementados)_
