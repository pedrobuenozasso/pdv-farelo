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

### `POST /api/v1/commands/{number}/open`

Abre uma comanda: transição de status `AVAILABLE` → `OPEN`, quando um
cliente começa a usá-la. (FARELO-033)

**Por que `POST`, não `PATCH`**: `/open` é uma ação ("abra esta comanda"),
não uma atualização parcial da representação do recurso — convenção comum
em APIs REST pragmáticas para endpoints de transição de estado (sufixo
verbo + `POST`). `PATCH` normalmente implica um corpo descrevendo a mudança
parcial (ex: JSON Patch/Merge Patch), que este endpoint não tem — não
recebe corpo algum.

**Response — `200 OK`**

`CommandResponse` atualizado, com `status: "OPEN"`.

**Erros**

- `404 Not Found` — `number` não corresponde a nenhuma comanda existente,
  `code: "COMMAND_NOT_FOUND"` (mesmo formato do `GET`).
- `409 Conflict` — a comanda existe mas não está `AVAILABLE` (já está
  `OPEN`, `PAYMENT_REQUESTED`, `CLOSED` ou `BLOCKED`):

  ```json
  {
    "code": "COMMAND_NOT_AVAILABLE",
    "message": "Command 3 is not available (current status: OPEN)",
    "correlationId": "..."
  }
  ```

  `COMMAND_NOT_AVAILABLE` é o `code` de exemplo usado na seção "Convenções
  previstas" deste documento para o formato de erro padrão.

### `POST /api/v1/commands/{number}/close`

Fecha uma comanda: transição de status `OPEN`/`PAYMENT_REQUESTED` →
`CLOSED`. (FARELO-034)

**Sem validação de pagamento/fiscal ainda** — por enquanto é só a
transição de estado; validar que o total foi pago antes de fechar é o
FARELO-143 (Epic 10).

Mesma razão para `POST` em vez de `PATCH` do `open` acima.

**Estados de origem válidos**: `OPEN` e `PAYMENT_REQUESTED` — ambos fazem
sentido operacionalmente (a comanda pode ser fechada direto ou depois de
pedir o pagamento). `AVAILABLE`, `CLOSED` e `BLOCKED` são inválidos como
origem.

**Response — `200 OK`**

`CommandResponse` atualizado, com `status: "CLOSED"`.

**Erros**

- `404 Not Found` — `number` não corresponde a nenhuma comanda existente,
  `code: "COMMAND_NOT_FOUND"` (mesmo formato dos demais endpoints).
- `409 Conflict` — a comanda existe mas não está em um estado fechável
  (`AVAILABLE`, já `CLOSED`, ou `BLOCKED`):

  ```json
  {
    "code": "COMMAND_CANNOT_BE_CLOSED",
    "message": "Command 6 cannot be closed (current status: AVAILABLE, expected OPEN or PAYMENT_REQUESTED)",
    "correlationId": "..."
  }
  ```

  Usa um `code`/exceção próprios (`COMMAND_CANNOT_BE_CLOSED`), em vez de
  reaproveitar `COMMAND_NOT_AVAILABLE` do `open` — a mensagem "not
  available" só faz sentido quando `AVAILABLE` é o único estado de origem
  válido (caso do `open`); reaproveitá-la aqui soaria invertido no caso
  mais comum de erro (uma comanda ainda `AVAILABLE`, nunca aberta, *está*
  disponível — é exatamente por isso que não pode ser fechada).

Ainda não há endpoint para transição a `PAYMENT_REQUESTED` nem para
`BLOCKED` — escopo de tickets futuros. Isso fecha o Epic 2 (Comandas) do
lado do backend por enquanto.

### `POST /api/v1/orders`

Cria um pedido, com snapshot de preço, dentro de uma comanda.
(FARELO-052/053)

**Sem nome/telefone do cliente** — esses campos ficam só no frontend por
enquanto (FARELO-045); persistir dados de cliente é escopo de um domínio
`customer` que ainda não existe.

**Request body**

```json
{
  "commandNumber": 1,
  "items": [
    { "productId": "8a1b2c3d-4e5f-6789-0abc-def123456789", "quantity": 2 }
  ]
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `commandNumber` | int | sim | Precisa apontar para uma `Command` existente |
| `items` | array | sim | Não pode ser vazio (`@NotEmpty`) |
| `items[].productId` | UUID | sim | Precisa apontar para um `Product` existente e `active` |
| `items[].quantity` | int | sim | `> 0` (`@Positive`) |

**Comanda precisa aceitar novos pedidos**: apenas `AVAILABLE` e `OPEN` são
válidos. Se a comanda estiver `AVAILABLE`, o próprio ato de criar o
pedido a transiciona para `OPEN` automaticamente — não existe um passo
explícito de "abrir" no fluxo do cliente antes de pedir pelo cardápio QR
(prompt mestre seção 6). Uma comanda já `OPEN` aceita o pedido sem mudar
de estado (ex: um segundo pedido na mesma visita). `PAYMENT_REQUESTED`,
`CLOSED` e `BLOCKED` rejeitam com erro de negócio.

**Snapshot de preço**: para cada item, `unitPrice` é capturado do preço
atual do produto **no momento da criação** — nunca uma referência viva.
Se o preço do produto mudar depois, o pedido já criado mantém o preço
antigo (AGENTS.md, convenção de snapshot de preço).

Toda a operação (validar comanda, validar produtos, criar o pedido e seus
itens, e a transição `AVAILABLE`→`OPEN` quando aplicável) roda em uma
única transação — sem outbox/eventos ainda (Epic 5, FARELO-060+).

**Response — `201 Created`**

Header `Location: /api/v1/orders/{id}` (ainda não há `GET` para resolvê-lo
— ticket futuro; o header já nomeia a URI do recurso corretamente mesmo
assim).

```json
{
  "id": "d4e5f6a7-8901-2bcd-ef34-567890abcdef",
  "commandNumber": 1,
  "status": "CREATED",
  "items": [
    {
      "id": "e5f6a7b8-9012-3cde-f456-7890abcdef12",
      "productId": "8a1b2c3d-4e5f-6789-0abc-def123456789",
      "productName": "Café Espresso",
      "quantity": 2,
      "unitPrice": 7.50
    }
  ],
  "createdAt": "2026-09-01T13:00:00Z"
}
```

**Erros**

- `400 Bad Request` — `commandNumber`/`items` ausente, `items` vazio, ou
  algum item com `productId` ausente ou `quantity` não positiva, no
  formato padrão com `code: "VALIDATION_ERROR"`.
- `404 Not Found` — `commandNumber` não corresponde a nenhuma comanda
  existente (`code: "COMMAND_NOT_FOUND"`), ou algum `productId` não
  corresponde a nenhum produto existente (`code: "PRODUCT_NOT_FOUND"`).
- `409 Conflict` — a comanda existe mas não aceita novos pedidos no estado
  atual:

  ```json
  {
    "code": "COMMAND_CANNOT_ACCEPT_ORDERS",
    "message": "Command 1 cannot accept orders (current status: CLOSED)",
    "correlationId": "..."
  }
  ```

  — ou algum produto existe mas está inativo:

  ```json
  {
    "code": "PRODUCT_NOT_AVAILABLE",
    "message": "Product not available: 8a1b2c3d-4e5f-6789-0abc-def123456789",
    "correlationId": "..."
  }
  ```

### `GET /api/v1/commands/{number}/orders`

Lista todos os pedidos de uma comanda, cada um com seus itens, do mais
antigo para o mais novo (`createdAt` asc). (FARELO-055)

Sem paginação — mesma lógica YAGNI já aplicada em
`GET /api/v1/categories`/`GET /api/v1/products`: o número de pedidos por
comanda é naturalmente pequeno.

Reaproveita `OrderResponse`/`OrderItemResponse` de `POST /api/v1/orders` —
mesmo formato de resposta.

**Response — `200 OK`**

```json
[
  {
    "id": "d4e5f6a7-8901-2bcd-ef34-567890abcdef",
    "commandNumber": 1,
    "status": "CREATED",
    "items": [
      {
        "id": "e5f6a7b8-9012-3cde-f456-7890abcdef12",
        "productId": "8a1b2c3d-4e5f-6789-0abc-def123456789",
        "productName": "Café Espresso",
        "quantity": 2,
        "unitPrice": 7.50
      }
    ],
    "createdAt": "2026-09-01T13:00:00Z"
  }
]
```

Lista vazia (`[]`) quando a comanda ainda não tem pedidos.

**Erros**

- `404 Not Found` — `number` não corresponde a nenhuma comanda existente,
  `code: "COMMAND_NOT_FOUND"` (mesmo formato dos demais endpoints).

_(demais endpoints a preencher conforme implementados)_
