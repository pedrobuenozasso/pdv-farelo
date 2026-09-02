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
  "availableOnPos": true,
  "productionStation": "BAR"
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
| `productionStation` | string | não | Um de `BAR`/`KITCHEN` (FARELO-073). Sem default — ausente/`null` significa "sem estação atribuída" |

`availableOnMenu` controla se o produto aparece no cardápio QR
(cliente-facing); `availableOnPos` controla se aparece no PDV
(staff-facing) — independentes um do outro.

`productionStation` indica qual estação de produção prepara o produto (ex:
`BAR` para bebidas, `KITCHEN` para comida) — usado para rotear tickets de
impressão por setor (FARELO-074, fora do escopo deste endpoint). Diferente
de `availableOnMenu`/`availableOnPos`, não tem um default seguro: um produto
sem estação óbvia fica `null` até ser atribuído explicitamente.

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
  "productionStation": "BAR",
  "createdAt": "2026-09-01T13:00:00Z",
  "updatedAt": "2026-09-01T13:00:00Z"
}
```

`productionStation` é `null` na resposta quando o produto ainda não tem
estação atribuída.

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
    "productionStation": "BAR",
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

`productionStation` continua **opcional** aqui, diferente dos três campos
booleanos acima: `null` é um valor legítimo e intencional ("sem estação
atribuída"), não um placeholder de "campo esquecido" — omiti-lo no `PUT`
explicitamente limpa a estação já atribuída, o que faz sentido para uma
substituição completa.

```json
{
  "name": "Café Espresso Duplo",
  "description": "Dose dupla",
  "price": 9.90,
  "categoryId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "imageUrl": "https://example.com/espresso-duplo.png",
  "active": false,
  "availableOnMenu": false,
  "availableOnPos": true,
  "productionStation": "BAR"
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
| `productionStation` | string | não | Um de `BAR`/`KITCHEN` (FARELO-073). Omitido/`null` limpa a estação atribuída |

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

**Nome/telefone do cliente**: aceita `customerName`/`customerPhone`
opcionais no corpo, persistidos como snapshot simples no próprio pedido
(mesmo espírito do snapshot de preço de `unitPrice`, ver
`docs/domain-model.md` seção `ordering`) — não é um domínio `customer`
próprio ainda. O formulário de checkout do cardápio QR (`apps/web`,
FARELO-045) já coletava esses dados; até agora eles nunca eram enviados ao
backend.

**Request body**

```json
{
  "commandNumber": 1,
  "items": [
    { "productId": "8a1b2c3d-4e5f-6789-0abc-def123456789", "quantity": 2 }
  ],
  "customerName": "Maria",
  "customerPhone": "+55 11 91234-5678"
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `commandNumber` | int | sim | Precisa apontar para uma `Command` existente |
| `items` | array | sim | Não pode ser vazio (`@NotEmpty`) |
| `items[].productId` | UUID | sim | Precisa apontar para um `Product` existente e `active` |
| `items[].quantity` | int | sim | `> 0` (`@Positive`) |
| `customerName` | string | não | Sem validação de formato — dado de contato para o atendente, não verificação de identidade |
| `customerPhone` | string | não | Sem validação de formato (YAGNI) |

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
itens, a transição `AVAILABLE`→`OPEN` quando aplicável, e a publicação do
evento de outbox `OrderCreated` — FARELO-060, ver seção "Outbox" em
`docs/domain-model.md`) roda em uma única transação: ou tudo comita junto,
ou nada comita. Ainda sem consumidor real do evento (impressão/notificação/
estoque são epics futuros, não iniciados) — só o mecanismo de publicação.

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
  "customerName": "Maria",
  "customerPhone": "+55 11 91234-5678",
  "createdAt": "2026-09-01T13:00:00Z"
}
```

`customerName`/`customerPhone` são `null` na resposta quando o pedido foi
criado sem esses campos.

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
    "customerName": "Maria",
    "customerPhone": "+55 11 91234-5678",
    "createdAt": "2026-09-01T13:00:00Z"
  }
]
```

Lista vazia (`[]`) quando a comanda ainda não tem pedidos.

**Erros**

- `404 Not Found` — `number` não corresponde a nenhuma comanda existente,
  `code: "COMMAND_NOT_FOUND"` (mesmo formato dos demais endpoints).

### `POST /api/v1/orders/{id}/preparing`

Marca um pedido como em preparo: transição de status `CREATED` →
`PREPARING`. (FARELO-057)

Mesma razão para `POST` em vez de `PATCH` do `open`/`close` de comanda —
é uma ação, não uma atualização parcial da representação do recurso.

**Estado de origem válido**: apenas `CREATED`. `CONFIRMED` é um status
reservado no enum `OrderStatus`, mas ainda não tem nenhuma transição
para ou a partir dele no roadmap — não é aceito aqui.

Grava uma entrada em `OrderStatusHistory` (`fromStatus: "CREATED"`,
`toStatus: "PREPARING"`), reaproveitando o mecanismo do FARELO-056.

**Response — `200 OK`**

`OrderResponse` atualizado, com `status: "PREPARING"`.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhum pedido existente,
  `code: "ORDER_NOT_FOUND"`.
- `409 Conflict` — o pedido existe mas não está `CREATED`:

  ```json
  {
    "code": "ORDER_INVALID_TRANSITION",
    "message": "Order d4e5f6a7-8901-2bcd-ef34-567890abcdef cannot transition to PREPARING (current status: PREPARING)",
    "correlationId": "..."
  }
  ```

### `POST /api/v1/orders/{id}/ready`

Marca um pedido como pronto: transição de status `PREPARING` → `READY`.
(FARELO-058)

**Estado de origem válido**: apenas `PREPARING` — pular direto de
`CREATED` para `READY` (sem passar pela cozinha) é rejeitado como
qualquer outra origem inválida.

Grava uma entrada em `OrderStatusHistory` (`fromStatus: "PREPARING"`,
`toStatus: "READY"`).

`ORDER_NOT_FOUND`/`ORDER_INVALID_TRANSITION` são um `code`/exceção únicos
e reaproveitados pelos dois endpoints acima (`OrderInvalidTransitionException`)
— diferente de `COMMAND_NOT_AVAILABLE`/`COMMAND_CANNOT_BE_CLOSED` (dois
`code`s distintos no domínio `command`). Lá, reaproveitar um único `code`
teria deixado a mensagem invertida no caso mais comum de erro do `close`
(uma comanda `AVAILABLE` *está* disponível — por isso não pode ser
fechada). Aqui a mensagem já nomeia o status atual e o status-alvo
explicitamente, então não há essa ambiguidade a evitar.

**Response — `200 OK`**

`OrderResponse` atualizado, com `status: "READY"`.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhum pedido existente,
  `code: "ORDER_NOT_FOUND"`.
- `409 Conflict` — o pedido existe mas não está `PREPARING`, mesmo
  formato de `ORDER_INVALID_TRANSITION` acima.

Ainda não há transição para `CONFIRMED`, nem endpoint para consultar o
histórico — escopo de tickets futuros.

### `POST /api/v1/orders/{id}/deliver`

Marca um pedido como entregue: transição de status `READY` → `DELIVERED`.
Fecha o ciclo de vida normal do pedido (follow-up sem número FARELO
explícito no roadmap original — ver nota no topo da seção `ordering` em
`docs/domain-model.md`).

Mesma razão para `POST` em vez de `PATCH` dos demais endpoints de
transição acima.

**Estado de origem válido**: apenas `READY` — mesmo formato de único
estado de origem de `/preparing`/`/ready`, reaproveitando o mesmo
mecanismo de transição sem alteração nele.

Grava uma entrada em `OrderStatusHistory` (`fromStatus: "READY"`,
`toStatus: "DELIVERED"`).

`DELIVERED` é um status terminal: nenhuma transição sai dele. Uma vez
entregue, um pedido não pode ser marcado como entregue de novo nem
cancelado (ver `/cancel` abaixo).

**Response — `200 OK`**

`OrderResponse` atualizado, com `status: "DELIVERED"`.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhum pedido existente,
  `code: "ORDER_NOT_FOUND"`.
- `409 Conflict` — o pedido existe mas não está `READY`, mesmo formato de
  `ORDER_INVALID_TRANSITION` de `/preparing`/`/ready` acima.

### `POST /api/v1/orders/{id}/cancel`

Cancela um pedido: transição de status para `CANCELLED`, a partir de
**qualquer** status não-terminal — `CREATED`, `CONFIRMED`, `PREPARING` ou
`READY`. Diferente de `/preparing`/`/ready`/`/deliver` (um único status de
origem cada), cancelamento faz sentido em qualquer ponto do ciclo de vida
até o pedido ser entregue — por isso múltiplos estados de origem são
aceitos aqui.

Mesma razão para `POST` em vez de `PATCH` dos demais endpoints de
transição acima.

**Estados de origem válidos**: `CREATED`, `CONFIRMED`, `PREPARING`,
`READY`. `DELIVERED` (já entregue) e `CANCELLED` (já cancelado) são
inválidos como origem — ambos são status terminais.

Grava uma entrada em `OrderStatusHistory` (`fromStatus`: o status atual
do pedido no momento do cancelamento, `toStatus: "CANCELLED"`).

**Response — `200 OK`**

`OrderResponse` atualizado, com `status: "CANCELLED"`.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhum pedido existente,
  `code: "ORDER_NOT_FOUND"`.
- `409 Conflict` — o pedido existe mas já está `DELIVERED` ou `CANCELLED`,
  mesmo formato de `ORDER_INVALID_TRANSITION` acima:

  ```json
  {
    "code": "ORDER_INVALID_TRANSITION",
    "message": "Order d4e5f6a7-8901-2bcd-ef34-567890abcdef cannot transition to CANCELLED (current status: DELIVERED)",
    "correlationId": "..."
  }
  ```

Ainda não há endpoint para consultar o histórico de transições — escopo
de um ticket futuro.

### `GET /api/v1/orders`

Lista a fila de pedidos da cozinha: todo pedido, de **todas** as comandas,
que ainda precisa de atenção da cozinha — status `CREATED`, `CONFIRMED`
ou `PREPARING` (tudo antes de `READY`) — do mais antigo para o mais novo
(`createdAt` asc, fila FIFO). (FARELO-059)

Pensado para o KDS (tela da cozinha, ticket de frontend futuro): mostra
tudo que ainda não chegou em `READY`. Pedidos `READY`, `DELIVERED` ou
`CANCELLED` nunca aparecem aqui — uma vez prontos/entregues/cancelados,
saem da fila.

Sem paginação — mesma lógica YAGNI já aplicada em
`GET /api/v1/commands/{number}/orders`/`GET /api/v1/categories`: o volume
de pedidos ativos simultaneamente é naturalmente baixo.

Reaproveita `OrderResponse`/`OrderItemResponse`, mesmo formato de
resposta de `POST /api/v1/orders`/`GET /api/v1/commands/{number}/orders`.
Implementado na mesma classe `OrderController` do FARELO-057/058 (ver nota
de domínio em `docs/domain-model.md`, seção `ordering`, sobre por que este
endpoint não ganhou um pacote `kitchen` próprio ainda).

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
    "customerName": "Maria",
    "customerPhone": "+55 11 91234-5678",
    "createdAt": "2026-09-01T13:00:00Z"
  }
]
```

Lista vazia (`[]`) quando não há pedidos ativos em nenhuma comanda.

Sem erros específicos — sempre `200 OK` (lista, potencialmente vazia; sem
parâmetro de path para validar).

### `GET /api/v1/print-jobs`

Lista os `PrintJob`s pendentes de impressão — status `PENDING`, do mais
antigo para o mais novo (`createdAt` asc, fila FIFO). (FARELO-076)

Primeiro endpoint REST do domínio `printing` — antes deste ticket não
existia nenhum, nem para `PrintJob` nem para `Printer`. Consumido pelo
Farelo Edge Agent (FARELO-075, `apps/edge-agent`) para saber o que ainda
precisa ser impresso: `Order criado → PrintJob PENDING → Edge Agent →
impressora → PRINTED` (prompt mestre seção 10).

Sem query param de status: mesma lógica de `GET /api/v1/orders` (a fila da
cozinha) — o propósito inteiro deste endpoint já é "o que está pendente",
então filtrar por outro status não faz sentido aqui. Sem paginação — mesma
lógica YAGNI já aplicada aos demais endpoints de fila deste projeto: volume
baixo.

**Response — `200 OK`**

```json
[
  {
    "id": "f1a2b3c4-5678-90ab-cdef-1234567890ab",
    "orderId": "d4e5f6a7-8901-2bcd-ef34-567890abcdef",
    "content": {
      "commandNumber": 37,
      "productionStation": "BAR",
      "items": [
        { "productName": "Cappuccino", "quantity": 2 }
      ]
    },
    "status": "PENDING",
    "retryCount": 0,
    "createdAt": "2026-09-01T13:00:00Z"
  }
]
```

`retryCount` (FARELO-079): quantas vezes este job já foi reenviado de
`FAILED` para `PENDING` via `POST /api/v1/print-jobs/{id}/retry` (ver
abaixo) — `0` para um job que nunca falhou/nunca foi reenviado.

`content` é o objeto estruturado já desserializado — não uma string com
JSON escapado — mesmo formato de `PrintJobContent` (`com.farelo.api.printing`,
FARELO-072/074): `commandNumber`, `productionStation` (`BAR`/`KITCHEN`,
`null` quando nenhum item do job tem estação atribuída — ver
`docs/domain-model.md`, seção `printing`, FARELO-074) e `items` (nome e
quantidade de cada produto). `PrintJob.content` é persistido como uma string
JSON (snapshot serializado); o DTO de resposta desserializa de volta para
esse objeto antes de servir, para o Edge Agent não precisar fazer
double-parsing.

Lista vazia (`[]`) quando não há `PrintJob`s pendentes.

Sem erros específicos — sempre `200 OK` (lista, potencialmente vazia; sem
parâmetro de path para validar), mesmo formato de `GET /api/v1/orders`.

### `POST /api/v1/print-jobs/{id}/printed`

Reporta que o Edge Agent imprimiu o ticket com sucesso: transição de status
`PENDING` → `PRINTED`. (FARELO-077)

Fecha o ciclo aberto pelo FARELO-076 (`GET /api/v1/print-jobs`): o Edge
Agent consultava os `PrintJob`s pendentes, mas nunca reportava de volta o
resultado — os jobs ficavam `PENDING` para sempre.

Mesma razão para `POST` em vez de `PATCH` dos demais endpoints de ação
deste projeto (ex: `/api/v1/orders/{id}/deliver`/`/cancel`): é uma ação,
não uma atualização parcial de representação. Sem corpo de requisição —
nada a reportar além do próprio `id` do job.

**Estado de origem válido**: apenas `PENDING` — mesmo formato de único
estado de origem de `/preparing`/`/ready`/`/deliver` em
`OrderController`, reaproveitando o mesmo mecanismo de transição
(`PrintJobService`) usado por `/failed` abaixo. Marcar um job já
`PRINTED`/`FAILED` como `PRINTED` de novo é rejeitado como qualquer outra
transição inválida, não aceito silenciosamente.

**Response — `200 OK`**

`PrintJobResponse` atualizado (mesmo formato de `GET /api/v1/print-jobs`),
com `status: "PRINTED"`.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhum `PrintJob` existente,
  `code: "PRINT_JOB_NOT_FOUND"`.
- `409 Conflict` — o job existe mas não está `PENDING`:

  ```json
  { "code": "PRINT_JOB_INVALID_TRANSITION", "message": "...", "correlationId": "..." }
  ```

### `POST /api/v1/print-jobs/{id}/failed`

Reporta que o Edge Agent falhou ao imprimir o ticket: transição de status
`PENDING` → `FAILED`. (FARELO-077)

Mesma razão para `POST` em vez de `PATCH`, e mesmo formato de resposta/erros
de `/printed` acima. Sem corpo de requisição: nenhum motivo estruturado da
falha é aceito por enquanto — YAGNI, não existe nenhum consumidor para esse
dado ainda (ex: um painel mostrando por que uma impressora falhou); se isso
for necessário no futuro, é escopo de outro ticket. Sem transição de volta
`FAILED` → `PENDING` aqui — esse é o `POST /api/v1/print-jobs/{id}/retry`
dedicado, logo abaixo (FARELO-079).

**Estado de origem válido**: apenas `PENDING`, mesmo raciocínio de
`/printed` acima.

**Response — `200 OK`**

`PrintJobResponse` atualizado, com `status: "FAILED"`.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhum `PrintJob` existente,
  `code: "PRINT_JOB_NOT_FOUND"`.
- `409 Conflict` — o job existe mas não está `PENDING`, mesmo formato de
  `PRINT_JOB_INVALID_TRANSITION` acima.

### `POST /api/v1/print-jobs/{id}/retry`

Reenvia um `PrintJob` `FAILED` de volta para `PENDING`, fazendo-o reaparecer
em `GET /api/v1/print-jobs` no próximo poll do Edge Agent: transição de
status `FAILED` → `PENDING`. (FARELO-079)

Implementa a última lacuna da seção 10 do prompt mestre — "Falha: `FAILED`,
permitindo retry" — deixada em aberto desde o FARELO-071/077: até este
ticket, um job `FAILED` ficava `FAILED` para sempre.

Endpoint manual — não existe (ainda) nenhum retry automático agendado; ver
`docs/domain-model.md`, seção `printing`, entrada FARELO-079, para a
justificativa completa dessa escolha (e por que ela não fecha a porta para
um agendador futuro chamar este mesmo mecanismo). Mesma razão de `POST` em
vez de `PATCH`, mesmo padrão dos demais endpoints de ação deste domínio.
Sem corpo de requisição — nada a reportar além do próprio `id` do job.

**Estado de origem válido**: apenas `FAILED` — reenviar um job `PENDING`
(nada para reenviar) ou já `PRINTED` é rejeitado como transição inválida,
mesmo `code: "PRINT_JOB_INVALID_TRANSITION"` de `/printed`/`/failed` acima.

**Limite de tentativas**: um job só pode ser reenviado até 3 vezes
(`PrintJobService.MAX_RETRY_COUNT`) — a quarta tentativa (ou qualquer
tentativa além dela) é rejeitada com um código de erro **diferente** de
`PRINT_JOB_INVALID_TRANSITION` (ver "Erros" abaixo). Ver
`docs/domain-model.md`, seção `printing`, para a justificativa completa do
limite (e por que ele é uma constante fixa, não configurável).

**Response — `200 OK`**

`PrintJobResponse` atualizado (mesmo formato de `GET /api/v1/print-jobs`),
com `status: "PENDING"` e `retryCount` incrementado em 1.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhum `PrintJob` existente,
  `code: "PRINT_JOB_NOT_FOUND"`.
- `409 Conflict` — o job existe mas não está `FAILED`:

  ```json
  { "code": "PRINT_JOB_INVALID_TRANSITION", "message": "...", "correlationId": "..." }
  ```

- `409 Conflict` — o job está `FAILED`, mas já atingiu o limite máximo de
  tentativas (`retryCount >= 3`):

  ```json
  { "code": "PRINT_JOB_RETRY_LIMIT_EXCEEDED", "message": "...", "correlationId": "..." }
  ```

### `POST /api/v1/ingredients`

Cria um ingrediente. (FARELO-090)

Primeiro endpoint do domínio `inventory`. Sem `active` no corpo — um
ingrediente novo sempre começa `true`, mesmo padrão de `Category`/`Product`.

**Request body**

```json
{
  "name": "Leite",
  "unit": "MILLILITER"
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `name` | string | sim | Não pode ser vazio/branco (`@NotBlank`) |
| `unit` | string | sim | Um de `GRAM`/`MILLILITER`/`UNIT` |

**Response — `201 Created`**

Header `Location: /api/v1/ingredients/{id}`.

```json
{
  "id": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "name": "Leite",
  "unit": "MILLILITER",
  "active": true,
  "createdAt": "2026-09-02T13:00:00Z",
  "updatedAt": "2026-09-02T13:00:00Z"
}
```

**Erros**

- `400 Bad Request` — `name` ausente/em branco ou `unit` ausente/inválido,
  no formato de erro padrão, com `code: "VALIDATION_ERROR"`.

### `GET /api/v1/ingredients`

Lista todos os ingredientes (ativos e inativos), ordenados por `name` (asc).
(FARELO-090)

Sem paginação/filtro `active`-only por enquanto (YAGNI, mesmo padrão de
`GET /api/v1/categories`/`GET /api/v1/products` — mantido para um ticket
futuro se o Admin precisar).

**Response — `200 OK`**

```json
[
  {
    "id": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
    "name": "Leite",
    "unit": "MILLILITER",
    "active": true,
    "createdAt": "2026-09-02T13:00:00Z",
    "updatedAt": "2026-09-02T13:00:00Z"
  }
]
```

Lista vazia (`[]`) quando não há ingredientes cadastrados.

### `GET /api/v1/ingredients/{id}`

Busca um ingrediente pelo `id` técnico (UUID). (FARELO-090)

**Response — `200 OK`**

Mesmo formato de item de `GET /api/v1/ingredients`.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhum ingrediente existente,
  `code: "INGREDIENT_NOT_FOUND"`.

### `PUT /api/v1/ingredients/{id}`

Atualiza (substituição completa) um ingrediente existente. (FARELO-090)

Fecha o CRUD básico de `Ingredient` — sem `DELETE` por enquanto (fora do
roadmap atual). Mesmo raciocínio de `PUT /api/v1/products/{id}`: usa um DTO
próprio (`IngredientUpdateRequest`) em vez de reaproveitar o request de
criação, para poder exigir `active` explicitamente sem torná-lo opcional na
criação.

**Request body**

```json
{
  "name": "Leite integral",
  "unit": "MILLILITER",
  "active": true
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `name` | string | sim | Não pode ser vazio/branco (`@NotBlank`) |
| `unit` | string | sim | Um de `GRAM`/`MILLILITER`/`UNIT` |
| `active` | boolean | sim | |

**Response — `200 OK`**

`IngredientResponse` com os campos atualizados (mesmo formato de
`POST /api/v1/ingredients`).

**Erros**

- `400 Bad Request` — mesmas validações do `POST`, mais `active` ausente,
  `code: "VALIDATION_ERROR"`.
- `404 Not Found` — `{id}` do ingrediente não existe,
  `code: "INGREDIENT_NOT_FOUND"`.

### `POST /api/v1/recipes`

Cria o cabeçalho de uma receita para um produto. (FARELO-091)

Apenas o cabeçalho — a lista de ingredientes/quantidades (`RecipeItem`,
FARELO-092) é gerenciada separadamente por
`POST`/`GET /api/v1/recipes/{recipeId}/items` (ver adiante nesta seção).

**Request body**

```json
{
  "productId": "8a1f2c3d-4e5f-6789-0abc-def123456789"
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `productId` | UUID | sim | Precisa referenciar um `Product` existente |

**Response — `201 Created`**

Header `Location: /api/v1/recipes/{id}`.

```json
{
  "id": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "productId": "8a1f2c3d-4e5f-6789-0abc-def123456789",
  "productName": "Pão com ovos e bacon",
  "active": true,
  "createdAt": "2026-09-02T13:00:00Z",
  "updatedAt": "2026-09-02T13:00:00Z"
}
```

**Erros**

- `400 Bad Request` — `productId` ausente, `code: "VALIDATION_ERROR"`.
- `404 Not Found` — `productId` não corresponde a nenhum produto existente,
  `code: "PRODUCT_NOT_FOUND"`.
- `409 Conflict` — o produto já tem uma receita ativa,
  `code: "RECIPE_ALREADY_EXISTS"`.

### `GET /api/v1/recipes`

Lista todas as receitas (ativas e inativas), ordenadas por `createdAt` (asc).
(FARELO-091)

**Response — `200 OK`**

```json
[
  {
    "id": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
    "productId": "8a1f2c3d-4e5f-6789-0abc-def123456789",
    "productName": "Pão com ovos e bacon",
    "active": true,
    "createdAt": "2026-09-02T13:00:00Z",
    "updatedAt": "2026-09-02T13:00:00Z"
  }
]
```

Lista vazia (`[]`) quando não há receitas cadastradas.

### `GET /api/v1/recipes/{id}`

Busca uma receita pelo `id` técnico (UUID). (FARELO-091)

**Response — `200 OK`**

Mesmo formato de item de `GET /api/v1/recipes`.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhuma receita existente,
  `code: "RECIPE_NOT_FOUND"`.

### `PATCH /api/v1/recipes/{id}/deactivate`

Desativa uma receita (`active` → `false`). (FARELO-091)

Sem corpo de requisição. Sem endpoint de reativação (fora do escopo deste
ticket — reativar exigiria checar de novo a regra de unicidade de receita
ativa por produto). Para trocar a composição de uma receita, o caminho é
desativar esta e criar uma nova (`POST /api/v1/recipes`).

**Response — `200 OK`**

Mesmo formato de `GET /api/v1/recipes/{id}`, com `active: false`.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhuma receita existente,
  `code: "RECIPE_NOT_FOUND"`.

### `POST /api/v1/recipes/{recipeId}/items`

Adiciona um item (ingrediente + quantidade) à composição de uma receita.
(FARELO-092)

**Request body**

```json
{
  "ingredientId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "quantity": 80
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `ingredientId` | UUID | sim | Precisa referenciar um `Ingredient` existente |
| `quantity` | number | sim | Estritamente positivo (`@Positive`). Sempre na unidade base do `Ingredient` referenciado (ver `Ingredient.unit`) — sem conversão de unidade de compra |

**Response — `201 Created`**

Header `Location: /api/v1/recipes/{recipeId}/items/{id}`.

```json
{
  "id": "c4a2d3f1-7d0b-5b3c-af4b-2b3c4d5e6f70",
  "recipeId": "8a1f2c3d-4e5f-6789-0abc-def123456789",
  "ingredientId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "ingredientName": "Bacon",
  "ingredientUnit": "GRAM",
  "quantity": 80,
  "createdAt": "2026-09-02T13:00:00Z",
  "updatedAt": "2026-09-02T13:00:00Z"
}
```

**Erros**

- `400 Bad Request` — `ingredientId` ausente ou `quantity` ausente/não
  positivo, `code: "VALIDATION_ERROR"`.
- `404 Not Found` — `{recipeId}` não corresponde a nenhuma receita existente,
  `code: "RECIPE_NOT_FOUND"`.
- `404 Not Found` — `ingredientId` não corresponde a nenhum ingrediente
  existente, `code: "INGREDIENT_NOT_FOUND"`.
- `409 Conflict` — a receita já tem um item para esse ingrediente,
  `code: "RECIPE_ITEM_ALREADY_EXISTS"`.

### `GET /api/v1/recipes/{recipeId}/items`

Lista os itens (ingredientes + quantidades) de uma receita, ordenados por
`createdAt` (asc). (FARELO-092)

**Response — `200 OK`**

```json
[
  {
    "id": "c4a2d3f1-7d0b-5b3c-af4b-2b3c4d5e6f70",
    "recipeId": "8a1f2c3d-4e5f-6789-0abc-def123456789",
    "ingredientId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
    "ingredientName": "Bacon",
    "ingredientUnit": "GRAM",
    "quantity": 80,
    "createdAt": "2026-09-02T13:00:00Z",
    "updatedAt": "2026-09-02T13:00:00Z"
  }
]
```

Lista vazia (`[]`) quando a receita existe mas ainda não tem itens.

**Erros**

- `404 Not Found` — `{recipeId}` não corresponde a nenhuma receita
  existente, `code: "RECIPE_NOT_FOUND"` (distingue "receita sem itens" de
  "receita inexistente" — ambas retornariam a mesma lista vazia sem essa
  checagem).

### `DELETE /api/v1/recipes/{recipeId}/items/{itemId}`

Remove um item da composição de uma receita. (FARELO-092)

Remoção física (não uma flag de desativação como em `Recipe`) — ver
`docs/domain-model.md`, seção `inventory`/`RecipeItem`, para a justificativa
completa dessa divergência do padrão de `Recipe`. Sem corpo de requisição.

**Response — `204 No Content`**

**Erros**

- `404 Not Found` — `{recipeId}` não corresponde a nenhuma receita
  existente, `code: "RECIPE_NOT_FOUND"`.
- `404 Not Found` — `{itemId}` não corresponde a nenhum item existente
  *dessa* receita (inclusive se `{itemId}` existir, mas pertencer a outra
  receita — um delete cross-recipe é tratado como 404, não executado),
  `code: "RECIPE_ITEM_NOT_FOUND"`.

### `POST /api/v1/users`

Cria um usuário (uma conta de quem pode operar o sistema — funcionário do
Farelo). (FARELO-120)

Primeiro endpoint do domínio `security`. `password` viaja em texto plano
neste request (sobre HTTPS, prompt mestre seção 26) exatamente uma vez — o
serviço hasheia (BCrypt) antes de persistir, e nunca é logada. Sem `active`
no corpo — um usuário novo sempre começa `true`, mesmo padrão de
`Category`/`Ingredient`.

**Request body**

```json
{
  "name": "Ana Souza",
  "email": "ana@farelo.dev",
  "password": "uma-senha-forte",
  "role": "MANAGER"
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `name` | string | sim | Não pode ser vazio/branco (`@NotBlank`) |
| `email` | string | sim | Formato de email válido (`@Email`), único no sistema |
| `password` | string | sim | 8 a 72 caracteres — teto casado com o limite de entrada do BCrypt |
| `role` | string | sim | Um de `ADMIN`/`MANAGER`/`CASHIER`/`KITCHEN`/`ATTENDANT` |

**Response — `201 Created`**

Header `Location: /api/v1/users/{id}`. **Nunca inclui `passwordHash`** — em
nenhuma resposta deste controller, sem exceção.

```json
{
  "id": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "name": "Ana Souza",
  "email": "ana@farelo.dev",
  "role": "MANAGER",
  "active": true,
  "createdAt": "2026-09-02T13:00:00Z",
  "updatedAt": "2026-09-02T13:00:00Z"
}
```

**Erros**

- `400 Bad Request` — `name`/`email`/`password`/`role` ausentes/inválidos
  (email mal formado, senha fora de 8-72 caracteres, role fora da lista),
  `code: "VALIDATION_ERROR"`.
- `409 Conflict` — já existe um usuário com esse `email`,
  `code: "USER_EMAIL_ALREADY_EXISTS"`.

### `GET /api/v1/users`

Lista todos os usuários (ativos e inativos), ordenados por `name` (asc).
(FARELO-120)

Sem paginação/filtro `active`-only por enquanto (YAGNI, mesmo padrão de
`GET /api/v1/ingredients`).

**Response — `200 OK`**

```json
[
  {
    "id": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
    "name": "Ana Souza",
    "email": "ana@farelo.dev",
    "role": "MANAGER",
    "active": true,
    "createdAt": "2026-09-02T13:00:00Z",
    "updatedAt": "2026-09-02T13:00:00Z"
  }
]
```

Lista vazia (`[]`) quando não há usuários cadastrados.

### `GET /api/v1/users/{id}`

Busca um usuário pelo `id` técnico (UUID). (FARELO-120)

**Response — `200 OK`**

Mesmo formato de item de `GET /api/v1/users`.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhum usuário existente,
  `code: "USER_NOT_FOUND"`.

### `PUT /api/v1/users/{id}`

Atualiza (substituição completa) o perfil de um usuário existente —
`name`/`email`/`role`/`active`. Não altera a senha (ver
`PATCH /api/v1/users/{id}/password` abaixo). (FARELO-120)

Mesmo raciocínio de `PUT /api/v1/ingredients/{id}`: DTO próprio
(`UserUpdateRequest`) em vez de reaproveitar o request de criação, para
poder exigir `active` explicitamente sem torná-lo opcional na criação.

**Request body**

```json
{
  "name": "Ana Souza",
  "email": "ana@farelo.dev",
  "role": "ADMIN",
  "active": true
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `name` | string | sim | Não pode ser vazio/branco (`@NotBlank`) |
| `email` | string | sim | Formato de email válido; único (ignorando o próprio usuário) |
| `role` | string | sim | Um de `ADMIN`/`MANAGER`/`CASHIER`/`KITCHEN`/`ATTENDANT` |
| `active` | boolean | sim | |

**Response — `200 OK`**

`UserResponse` com os campos atualizados (mesmo formato de
`POST /api/v1/users`).

**Erros**

- `400 Bad Request` — mesmas validações do `POST` (exceto senha), mais
  `active` ausente, `code: "VALIDATION_ERROR"`.
- `404 Not Found` — `{id}` do usuário não existe, `code: "USER_NOT_FOUND"`.
- `409 Conflict` — `email` já pertence a outro usuário,
  `code: "USER_EMAIL_ALREADY_EXISTS"`.

### `PATCH /api/v1/users/{id}/password`

Troca a senha de um usuário. (FARELO-120)

Endpoint separado do `PUT` geral acima — deliberado, ver
`docs/domain-model.md` (seção `security`) para a justificativa completa.
**Sem exigir a senha atual**: não existe ainda mecanismo de
login/autenticação (FARELO-121) contra o qual validar uma "senha atual" de
um chamador autenticado.

**Request body**

```json
{
  "newPassword": "uma-senha-nova"
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `newPassword` | string | sim | 8 a 72 caracteres, mesma regra de `POST /api/v1/users` |

**Response — `200 OK`**

`UserResponse` do usuário (sem `passwordHash`, como sempre).

**Erros**

- `400 Bad Request` — `newPassword` ausente ou fora de 8-72 caracteres,
  `code: "VALIDATION_ERROR"`.
- `404 Not Found` — `{id}` do usuário não existe, `code: "USER_NOT_FOUND"`.

_(demais endpoints a preencher conforme implementados)_
