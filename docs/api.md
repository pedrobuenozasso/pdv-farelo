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
  "imageUrl": "https://example.com/espresso.png",
  "availableOnMenu": true,
  "availableOnPos": true
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `name` | string | sim | Não pode ser vazio/branco (`@NotBlank`) |
| `description` | string | não | |
| `price` | decimal | sim | `>= 0.00` (`@DecimalMin`), nunca negativo |
| `categoryId` | UUID | sim | Precisa apontar para uma `Category` existente |
| `imageUrl` | string | não | |
| `availableOnMenu` | boolean | não | Default `true` se ausente (FARELO-017) |
| `availableOnPos` | boolean | não | Default `true` se ausente (FARELO-017) |

`availableOnMenu` controla se o produto aparece no cardápio QR
(cliente-facing); `availableOnPos` controla se aparece no PDV
(staff-facing) — independentes um do outro.

**Response — `201 Created`**

Header `Location: /api/v1/products/{id}`.

```json
{
  "id": "8a1b2c3d-4e5f-6789-0abc-def123456789",
  "name": "Café Espresso",
  "description": "Espresso curto, torra média",
  "price": 7.50,
  "active": true,
  "availableOnMenu": true,
  "availableOnPos": true,
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
    "availableOnMenu": true,
    "availableOnPos": true,
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

Mesmos campos de `POST /api/v1/products`, mais `active` — mas aqui
`active`, `availableOnMenu` e `availableOnPos` são todos **obrigatórios**
(diferente do `POST`, onde `availableOnMenu`/`availableOnPos` são opcionais
com default `true`). Usa um DTO próprio (`ProductUpdateRequest`) em vez de
reaproveitar o request de criação — esses três campos booleanos não fazem
sentido como opcionais na criação (sempre começam `true`), e torná-los
opcionais lá seria arriscado: como o Jackson zera um `boolean` primitivo
ausente para `false` em records, um client que esquecesse de enviá-los na
criação desativaria/esconderia o produto silenciosamente. No `PUT`, que é
substituição completa, faz sentido exigir os três explicitamente.

```json
{
  "name": "Café Espresso Duplo",
  "description": "Dose dupla",
  "price": 9.90,
  "categoryId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "imageUrl": "https://example.com/espresso-duplo.png",
  "active": false,
  "availableOnMenu": false,
  "availableOnPos": true
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
| `availableOnMenu` | boolean | sim | Independente de `availableOnPos` |
| `availableOnPos` | boolean | sim | Independente de `availableOnMenu` |

**Response — `200 OK`**

`ProductResponse` com os campos atualizados (mesmo formato de
`POST /api/v1/products`).

**Erros**

- `400 Bad Request` — mesmas validações do `POST`, `code: "VALIDATION_ERROR"`.
- `404 Not Found` — `{id}` do produto não existe, `code: "PRODUCT_NOT_FOUND"`;
  ou `categoryId` não existe, `code: "CATEGORY_NOT_FOUND"` (mesmo formato do
  `POST`).

### `GET /api/v1/commands/{number}`

Busca uma comanda pelo `number`. (FARELO-032)

Primeiro endpoint do domínio `command`. `{number}` é o identificador
humano de negócio (1-100, ver seed FARELO-031) — **não** o `id` técnico
(UUID); o `id` nunca é usado como identificador na URL/negócio (prompt
mestre seção 41).

**Response — `200 OK`**

```json
{
  "id": "c1d2e3f4-5678-90ab-cdef-1234567890ab",
  "number": 1,
  "status": "AVAILABLE",
  "createdAt": "2026-09-01T13:00:00Z",
  "updatedAt": "2026-09-01T13:00:00Z"
}
```

`status` é um dos valores de `CommandStatus`: `AVAILABLE`, `OPEN`,
`PAYMENT_REQUESTED`, `CLOSED`, `BLOCKED`.

**Erros**

- `404 Not Found` — `number` não corresponde a nenhuma comanda existente:

  ```json
  {
    "code": "COMMAND_NOT_FOUND",
    "message": "Command not found: 999",
    "correlationId": "..."
  }
  ```

Ainda não há endpoints para transição de status da comanda (abrir, fechar,
etc.) — escopo de FARELO-033/034 em diante.

_(demais endpoints a preencher conforme implementados)_
