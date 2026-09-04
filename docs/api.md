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

### Autenticação/RBAC (FARELO-121/122/123/124/127)

A maioria dos endpoints abaixo não exige nenhuma autenticação — esse é o
padrão do projeto até aqui (ver `docs/domain-model.md`, seção `security`).
Um endpoint marcado **"Requer: `<PAPEL>`"** abaixo é a exceção: precisa de
um header `Authorization: Bearer <token>` (token emitido por
`POST /api/v1/auth/login`) cujo usuário tenha um dos papéis listados
(`ADMIN`/`MANAGER`/`CASHIER`/`KITCHEN`/`ATTENDANT`, ver seção 26 do prompt
mestre). Quando um endpoint marcado assim é chamado:

- **Sem header `Authorization` (ou malformado/token inválido/expirado)** —
  `401 Unauthorized`:

  ```json
  {
    "code": "UNAUTHENTICATED",
    "message": "...",
    "correlationId": "..."
  }
  ```

- **Header presente e token válido, mas o papel do usuário não está na
  lista exigida** — `403 Forbidden`:

  ```json
  {
    "code": "FORBIDDEN",
    "message": "...",
    "correlationId": "..."
  }
  ```

Um endpoint **sem** a marca "Requer" continua acessível sem nenhum header,
exatamente como sempre foi — inclusive depois do FARELO-123 (que adicionou
a marca a alguns endpoints de `categories`/`products`/`users`), do
FARELO-124 (que adicionou a marca a alguns endpoints de `commands`/
`orders`/`print-jobs` — a superfície PDV/cozinha), do FARELO-127 (que
adicionou a marca a exatamente dois endpoints de `ingredients`) e do
FARELO-141 (que adicionou a marca ao único `POST` de `payments`, deixando o
`GET` irmão sem marca — ver essa seção abaixo) — ver essas seções abaixo e o
raciocínio completo em `docs/domain-model.md`, subseções FARELO-123,
FARELO-124, FARELO-127 e FARELO-141. Dois casos seguem deliberadamente sem
a marca mesmo depois do FARELO-124: os dois endpoints que o Cardápio QR
(cliente anônimo, sem login) depende diretamente
(`GET /api/v1/commands/{number}`, `POST /api/v1/orders`), e três dos quatro
endpoints de `print-jobs`, que são chamados só pelo Farelo Edge Agent — uma
máquina, não uma pessoa logada (ver a subseção FARELO-124 de
`docs/domain-model.md` para o porquê de RBAC não se aplicar a um endpoint
machine-to-machine). O restante de `ingredients` (todo `GET`, `POST`/`PUT`
de ingredientes em si) e todo `recipes` seguem sem a marca mesmo depois do
FARELO-127 — ver a subseção FARELO-127 de `docs/domain-model.md` para por
que esse ticket protegeu só dois endpoints, não a superfície de estoque
inteira.

## Endpoints

### `POST /api/v1/categories`

Cria uma categoria de produto. (FARELO-012)

**Requer: `ADMIN`, `MANAGER`** (FARELO-123) — ver a seção "Autenticação/RBAC"
acima para o formato do header e das respostas `401`/`403`.

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

**Sem autenticação — deliberado (FARELO-123).** Apesar de `POST` acima
exigir `ADMIN`/`MANAGER`, este `GET` fica público de propósito: é
consumido pelo Cardápio QR cliente-facing (`pedido.farelo.com.br`,
FARELO-042), que não tem login de nenhum tipo. Ver
`docs/domain-model.md`, subseção FARELO-123, para o raciocínio completo.

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

**Requer: `ADMIN`, `MANAGER`** (FARELO-123) — ver a seção "Autenticação/RBAC"
acima para o formato do header e das respostas `401`/`403`.

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

**Sem autenticação — deliberado (FARELO-123).** Mesmo raciocínio de
`GET /api/v1/categories` acima: consumido pelo Cardápio QR cliente-facing
(FARELO-043), sem login de nenhum tipo, apesar de `POST`/`PUT` deste mesmo
recurso exigirem `ADMIN`/`MANAGER`.

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

**Requer: `ADMIN`, `MANAGER`** (FARELO-123) — ver a seção "Autenticação/RBAC"
acima para o formato do header e das respostas `401`/`403`.

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

**Sem marca "Requer"** (FARELO-124) — deliberadamente público: é
dependência direta do Cardápio QR (`apps/web/src/app/c/[commandNumber]`,
sem login algum), que valida o número da comanda através deste endpoint.
Ver a subseção FARELO-124 de `docs/domain-model.md` para o raciocínio
completo.

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

**Requer: `ADMIN`, `MANAGER`, `CASHIER`, `ATTENDANT`** (FARELO-124) — ação
de frente de loja sem implicação de dinheiro ainda; ver a seção
"Autenticação/RBAC" acima para o formato do header e das respostas
`401`/`403`, e a subseção FARELO-124 de `docs/domain-model.md` para o
raciocínio completo (inclusive por que `close()` abaixo usa uma lista
diferente).

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

**Requer: `ADMIN`, `MANAGER`, `CASHIER`** (FARELO-124) — tratado como ação
de manuseio de caixa (mesmo sem validação de pagamento real ainda);
deliberadamente **sem** `ATTENDANT`, diferente do `open()` acima — ver a
subseção FARELO-124 de `docs/domain-model.md` para o raciocínio completo.

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

**Sem marca "Requer"** (FARELO-124) — deliberadamente público: é o
endpoint que o checkout do Cardápio QR
(`apps/web/src/app/c/[commandNumber]/menu.tsx`, sem login algum) chama
diretamente para finalizar o pedido do cliente (prompt mestre seção 6).
Verificado contra o código-fonte do front, não assumido a partir do método
HTTP — protegê-lo quebraria o fluxo de pedido do cliente por completo. Ver
a subseção FARELO-124 de `docs/domain-model.md` para o raciocínio
completo.

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
itens, a transição `AVAILABLE`→`OPEN` quando aplicável, a baixa de estoque
via receita — ver abaixo — e a publicação do evento de outbox
`OrderCreated` — FARELO-060, ver seção "Outbox" em `docs/domain-model.md`)
roda em uma única transação: ou tudo comita junto, ou nada comita. Ainda
sem consumidor real do evento outbox (impressão/notificação são epics
futuros, não iniciados) — só o mecanismo de publicação.

**Baixa de estoque via receita (FARELO-096, prompt mestre seção 16)**: não
é um campo do request nem do response — é um efeito colateral interno.
Para cada item vendido cujo produto tenha uma `Recipe` ativa, cada
`RecipeItem` dela contribui para uma linha `ORDER_CONSUMPTION` (quantidade
negativa) em `InventoryMovement` por *ingrediente*, vinculada a este pedido
via `orderId` — quantidade = soma de `RecipeItem.quantity` ×
`items[].quantity` do pedido, por ingrediente. Um produto sem receita ativa
simplesmente não gera nenhum movimento (não é erro). **FARELO-097**: se
dois produtos do mesmo pedido compartilham um ingrediente, a quantidade é
agregada numa única linha por ingrediente (não uma por produto) — a chave
de idempotência é `(ORDER_CONSUMPTION, orderId, ingredientId)`, reforçada
por um índice único parcial no banco, então repetir a criação de baixa
para o mesmo `orderId` (cenário defensivo, não alcançável hoje só via
`POST /api/v1/orders`) nunca duplica um movimento já gravado. Ver seção
`inventory` (FARELO-096/FARELO-097) em `docs/domain-model.md` para o
desenho completo; não há endpoint novo para consultar isso além do já
existente `GET /api/v1/ingredients/{ingredientId}/movements`.

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

**Requer: `ADMIN`, `MANAGER`, `CASHIER`, `ATTENDANT`** (FARELO-124) — não é
dependência do Cardápio QR (verificado contra o código-fonte de
`apps/web`: só a tela interna `/pdv` chama este endpoint); `KITCHEN` fica
de fora — a cozinha tem sua própria fila (`GET /api/v1/orders` abaixo). Ver
a subseção FARELO-124 de `docs/domain-model.md` para o raciocínio
completo.

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

**Requer: `ADMIN`, `MANAGER`, `KITCHEN`, `CASHIER`** (FARELO-124) — ação de
cozinha, mas `Order` não é segmentado por estação de produção (diferente
de `PrintJob`), então um cashier preparando itens `BAR` do mesmo pedido
também precisa poder chamar isto; `ATTENDANT` fica de fora. Ver a
subseção FARELO-124 de `docs/domain-model.md` para o raciocínio completo.

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

**Requer: `ADMIN`, `MANAGER`, `KITCHEN`, `CASHIER`** (FARELO-124) — mesma
lista e raciocínio de `/preparing` acima: quem pode iniciar o preparo
também pode marcar como pronto.

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

**Requer: `ADMIN`, `MANAGER`, `CASHIER`, `ATTENDANT`** (FARELO-124) — ação
de frente de loja (entregar ao cliente); `KITCHEN` fica de fora, oposto de
`/preparing`/`/ready` acima. Ver a subseção FARELO-124 de
`docs/domain-model.md` para o raciocínio completo.

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

**Requer: `ADMIN`, `MANAGER`, `CASHIER`, `ATTENDANT`** (FARELO-124) — mesma
lista e mesma tela/persona de `/deliver` acima
(`apps/web/src/app/pdv/page.tsx`, `OrderCard`).

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

**Requer: `ADMIN`, `MANAGER`, `KITCHEN`** (FARELO-124) — único consumidor
real hoje é o KDS (`apps/web/src/app/kds/page.tsx`); `CASHIER`/`ATTENDANT`
ficam de fora deste endpoint de leitura por não haver hoje nenhuma tela de
frente de loja que o consuma (diferente de `/preparing`/`/ready`, que
também permitem `CASHIER`). Ver a subseção FARELO-124 de
`docs/domain-model.md` para o raciocínio completo.

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

**Sem marca "Requer"** (FARELO-124) — deliberadamente sem RBAC: chamado
exclusivamente pelo Farelo Edge Agent, um processo de máquina sem login
(verificado contra `apps/edge-agent/src/printJobsClient.ts`), nunca por
uma pessoa. Ver a subseção FARELO-124 de `docs/domain-model.md` para o
raciocínio completo sobre por que RBAC não se aplica a um endpoint
machine-to-machine.

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

**Sem marca "Requer"** (FARELO-124) — mesmo raciocínio de
`GET /api/v1/print-jobs` acima: endpoint machine-to-machine chamado só
pelo Edge Agent.

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

**Sem marca "Requer"** (FARELO-124) — mesmo raciocínio de
`GET /api/v1/print-jobs`/`.../printed` acima: endpoint machine-to-machine
chamado só pelo Edge Agent.

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

**Requer: `ADMIN`, `MANAGER`, `CASHIER`, `KITCHEN`, `ATTENDANT`**
(FARELO-124, todos os cinco papéis operacionais) — diferente dos três
endpoints acima, este é um endpoint **manual** (ver abaixo), acionado por
uma pessoa, não pelo Edge Agent — verificado contra o código-fonte de
`apps/edge-agent` e `apps/web`, nenhum dos dois chama este endpoint hoje.
Um `PrintJob` com falha não pertence a um único papel/estação, então
qualquer funcionário autenticado pode reenviá-lo; ver a subseção
FARELO-124 de `docs/domain-model.md` para o raciocínio completo.

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

Cria um ingrediente. (FARELO-090; `minimumStock` é FARELO-099)

Primeiro endpoint do domínio `inventory`. Sem `active` no corpo — um
ingrediente novo sempre começa `true`, mesmo padrão de `Category`/`Product`.

**Request body**

```json
{
  "name": "Leite",
  "unit": "MILLILITER",
  "minimumStock": 5000
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `name` | string | sim | Não pode ser vazio/branco (`@NotBlank`) |
| `unit` | string | sim | Um de `GRAM`/`MILLILITER`/`UNIT` |
| `minimumStock` | number | não | (FARELO-099) Limite mínimo de estoque, na unidade base do ingrediente. Omitido/`null` = "nenhum limite configurado ainda" (não é o mesmo que um limite `0`). Quando enviado, deve ser `>= 0` (`@DecimalMin`) |

**Response — `201 Created`**

Header `Location: /api/v1/ingredients/{id}`.

```json
{
  "id": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "name": "Leite",
  "unit": "MILLILITER",
  "active": true,
  "minimumStock": 5000,
  "createdAt": "2026-09-02T13:00:00Z",
  "updatedAt": "2026-09-02T13:00:00Z"
}
```

`minimumStock` é `null` quando nenhum limite foi configurado.

**Erros**

- `400 Bad Request` — `name` ausente/em branco, `unit` ausente/inválido, ou
  `minimumStock` negativo, no formato de erro padrão, com
  `code: "VALIDATION_ERROR"`.

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
    "minimumStock": 5000,
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

Atualiza (substituição completa) um ingrediente existente. (FARELO-090;
`minimumStock` é FARELO-099)

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
  "active": true,
  "minimumStock": 5000
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `name` | string | sim | Não pode ser vazio/branco (`@NotBlank`) |
| `unit` | string | sim | Um de `GRAM`/`MILLILITER`/`UNIT` |
| `active` | boolean | sim | |
| `minimumStock` | number | não | (FARELO-099) Mesma regra do `POST` acima. **Substituição completa**: omitir o campo (ou enviar `null`) *limpa* um limite configurado anteriormente de volta para "não configurado" — não deixa o valor anterior inalterado, mesmo comportamento que `Product.productionStation` já tem em `PUT /api/v1/products/{id}` |

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

### `POST /api/v1/ingredients/{ingredientId}/movements`

Registra uma entrada manual de estoque: um humano (ex: um gerente)
confirmando que estoque chegou fisicamente (uma compra). Sempre cria uma
linha `InventoryMovement` do tipo `PURCHASE` com `quantity` positiva.
(FARELO-094)

**Requer: `ADMIN`, `MANAGER`** (FARELO-127) — ver a seção "Autenticação/RBAC"
acima para o formato do header e das respostas `401`/`403`. Também grava
uma linha em `AuditLog` (`action: "STOCK_PURCHASE_RECORDED"`,
`entityType: "Ingredient"`) com o ator resolvido do token — ver
`docs/domain-model.md`, seção `inventory`/FARELO-127, para o raciocínio
completo (por que este endpoint precisou ganhar RBAC, e o formato exato do
snapshot de auditoria).

**Request body**

```json
{
  "quantity": 3000
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `quantity` | number | sim | Estritamente positivo (`@Positive`) — zero ou negativo não é o que `PURCHASE` significa. Sempre na unidade base do `Ingredient` referenciado (ver `Ingredient.unit`) — sem conversão de unidade de compra |

Sem campo `type`: é sempre `PURCHASE`, fixado no servidor — este endpoint é
especificamente o fluxo de entrada manual, não uma criação genérica de
qualquer tipo de movimento. Ver `docs/domain-model.md`, seção
`inventory`/`InventoryMovement`/FARELO-094, para a justificativa completa.

**Response — `201 Created`**

Header `Location: /api/v1/ingredients/{ingredientId}/movements/{id}` (esse
recurso não tem um `GET` de item único — só é recuperável via o `GET` de
lista abaixo, mesmo padrão de `POST /api/v1/recipes/{recipeId}/items`).

```json
{
  "id": "c4a2d3f1-7d0b-5b3c-af4b-2b3c4d5e6f70",
  "ingredientId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "quantity": 3000,
  "type": "PURCHASE",
  "orderId": null,
  "createdAt": "2026-09-02T13:00:00Z"
}
```

**Erros**

- `400 Bad Request` — `quantity` ausente, zero ou negativa,
  `code: "VALIDATION_ERROR"`.
- `404 Not Found` — `{ingredientId}` não corresponde a nenhum ingrediente
  existente, `code: "INGREDIENT_NOT_FOUND"`.

### `GET /api/v1/ingredients/{ingredientId}/movements`

Lista os movimentos do ledger de estoque (`InventoryMovement`) de um
ingrediente, ordenados por `createdAt` (asc — mais antigo primeiro).
(FARELO-093)

**Response — `200 OK`**

```json
[
  {
    "id": "c4a2d3f1-7d0b-5b3c-af4b-2b3c4d5e6f70",
    "ingredientId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
    "quantity": -500,
    "type": "ORDER_CONSUMPTION",
    "orderId": "8a1f2c3d-4e5f-6789-0abc-def123456789",
    "createdAt": "2026-09-02T13:00:00Z"
  }
]
```

| Campo | Observações |
|---|---|
| `quantity` | Positivo (entrada) ou negativo (saída/consumo/perda), sempre na unidade base do `Ingredient` (ver `Ingredient.unit`) |
| `type` | Um de `PURCHASE`/`ORDER_CONSUMPTION`/`LOSS`/`ADJUSTMENT`/`RETURN`/`CANCELLATION`/`INTERNAL_CONSUMPTION` |
| `orderId` | `null` exceto em movimentos com origem em um pedido (hoje, nenhum é produzido por este ticket) |

Sem `updatedAt` — este é um registro de ledger, imutável desde a criação
(ver `docs/domain-model.md`, seção `inventory`/`InventoryMovement`).

Lista vazia (`[]`) quando o ingrediente existe mas ainda não tem movimentos.

**Erros**

- `404 Not Found` — `{ingredientId}` não corresponde a nenhum ingrediente
  existente, `code: "INGREDIENT_NOT_FOUND"` (distingue "ingrediente sem
  movimentos" de "ingrediente inexistente" — ambas retornariam a mesma lista
  vazia sem essa checagem).

### `GET /api/v1/ingredients/{ingredientId}/balance`

Calcula e retorna o saldo atual de estoque de um ingrediente — a soma de
todas as linhas do seu ledger (`InventoryMovement`), nunca um campo mutável
armazenado (`docs/domain-model.md`, seção `inventory`, prompt mestre seção
13). (FARELO-095; `belowMinimum` é FARELO-099)

**Response — `200 OK`**

```json
{
  "ingredientId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "balance": 2200,
  "unit": "GRAM",
  "belowMinimum": false
}
```

| Campo | Observações |
|---|---|
| `balance` | Soma de todo `quantity` do ledger desse ingrediente, na unidade base do `Ingredient`; `0` (nunca `null`) quando não há nenhum movimento ainda |
| `unit` | A unidade do próprio ingrediente (`Ingredient.unit`), incluída para o cliente interpretar `balance` sem uma segunda chamada a `GET /api/v1/ingredients/{id}` |
| `belowMinimum` | (FARELO-099) `true` quando `balance` está **estritamente abaixo** de `Ingredient.minimumStock` — um saldo exatamente igual ao limite não conta como abaixo dele. Sempre `false` quando o ingrediente não tem `minimumStock` configurado (`null`), **mesmo com saldo negativo** — sem limite configurado, este ingrediente nunca é reportado como baixo |

**Erros**

- `404 Not Found` — `{ingredientId}` não corresponde a nenhum ingrediente
  existente, `code: "INGREDIENT_NOT_FOUND"` (mesma checagem/motivo de
  `GET /api/v1/ingredients/{ingredientId}/movements` acima).

### `POST /api/v1/ingredients/{ingredientId}/losses`

Registra uma perda de estoque: um humano (ex: um gerente) reportando que
uma quantidade de um ingrediente foi perdida — estragou, quebrou, foi
roubada — não uma venda. Sempre cria uma linha `InventoryMovement` do tipo
`LOSS` com `quantity` **negativa** (saída de estoque) e sem `orderId` (não
tem origem em pedido). (FARELO-098)

**Requer: `ADMIN`, `MANAGER`** (FARELO-127) — ver a seção "Autenticação/RBAC"
acima para o formato do header e das respostas `401`/`403`. Também grava
uma linha em `AuditLog` (`action: "STOCK_LOSS_RECORDED"`,
`entityType: "Ingredient"`) com o ator resolvido do token — mesmo
raciocínio de `POST .../movements` acima; ver `docs/domain-model.md`, seção
`inventory`/FARELO-127.

**Request body**

```json
{
  "quantity": 250
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `quantity` | number | sim | Estritamente positivo (`@Positive`) — o cliente reporta "quanto foi perdido" como uma magnitude positiva; o servidor nega o valor internamente ao gravar a linha do ledger (`quantity` negativa é um detalhe interno, não algo que o cliente codifica). Sempre na unidade base do `Ingredient` referenciado (ver `Ingredient.unit`) — sem conversão de unidade de compra |

Sem campo `type`: é sempre `LOSS`, fixado no servidor — mesmo raciocínio de
`POST /api/v1/ingredients/{ingredientId}/movements` não aceitar `type`. Sem
campo `reason`/`note`: considerado e deliberadamente fora do escopo deste
ticket — nem o prompt mestre (seção 13) nem a descrição de `LOSS` em
`InventoryMovementType` pedem um campo de motivo/observação na entidade;
ver `docs/domain-model.md`, seção `inventory`/`InventoryMovement`/
FARELO-098, para a justificativa completa.

**Response — `201 Created`**

Header `Location: /api/v1/ingredients/{ingredientId}/movements/{id}` (mesmo
padrão de `POST .../movements`: este recurso não tem um `GET` de item
único — só é recuperável via `GET /api/v1/ingredients/{ingredientId}/movements`
acima, que lista todos os tipos, incluindo `LOSS`).

```json
{
  "id": "d5b3e4f2-8e1c-6c4d-bf5c-3c4d5e6f7081",
  "ingredientId": "b3f1c2e0-6c9a-4a2b-9e3a-1a2b3c4d5e6f",
  "quantity": -250,
  "type": "LOSS",
  "orderId": null,
  "createdAt": "2026-09-02T13:05:00Z"
}
```

**Erros**

- `400 Bad Request` — `quantity` ausente, zero ou negativa,
  `code: "VALIDATION_ERROR"`.
- `404 Not Found` — `{ingredientId}` não corresponde a nenhum ingrediente
  existente, `code: "INGREDIENT_NOT_FOUND"`.

### `POST /api/v1/users`

Cria um usuário (uma conta de quem pode operar o sistema — funcionário do
Farelo). (FARELO-120)

**Requer: `ADMIN`** (FARELO-123) — só `ADMIN`, diferente dos `GET` abaixo
(que também aceitam `MANAGER`): este endpoint aceita um `role` livre no
corpo, então permitir `MANAGER` deixaria um gerente criar/promover uma
conta `ADMIN`. Ver a seção "Autenticação/RBAC" acima para o formato do
header e das respostas `401`/`403`, e `docs/domain-model.md` (subseção
FARELO-123) para o raciocínio completo.

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

**Requer: `ADMIN`, `MANAGER`** (FARELO-123) — mais permissivo que `POST`/
`PUT`/`PATCH .../password` acima porque ler a lista de funcionários não
pode ser usado para escalonar privilégio nenhum, diferente de criar/editar
contas. `UserResponse` nunca inclui `passwordHash`, para qualquer papel.

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

**Requer: `ADMIN`, `MANAGER`** (FARELO-123) — mesmo raciocínio de
`GET /api/v1/users` acima.

**Response — `200 OK`**

Mesmo formato de item de `GET /api/v1/users`.

**Erros**

- `404 Not Found` — `{id}` não corresponde a nenhum usuário existente,
  `code: "USER_NOT_FOUND"`.

### `PUT /api/v1/users/{id}`

Atualiza (substituição completa) o perfil de um usuário existente —
`name`/`email`/`role`/`active`. Não altera a senha (ver
`PATCH /api/v1/users/{id}/password` abaixo). (FARELO-120)

**Requer: `ADMIN`** (FARELO-123) — mesmo raciocínio de `POST` acima: este
endpoint também define `role`, então permitir `MANAGER` deixaria um
gerente se auto-promover (ou promover outra conta) a `ADMIN`.

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

**Requer: `ADMIN`** (FARELO-123) — mantido estritamente `ADMIN` (não
`MANAGER`): este endpoint ainda não confirma a senha atual (ver abaixo),
então permitir `MANAGER` deixaria um gerente sequestrar a senha de
qualquer conta, inclusive de outro `ADMIN`.

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

### `POST /api/v1/auth/login`

Autentica um usuário por email+senha e emite um token (JWT) para uso nas
próximas requisições. (FARELO-121)

Primeiro e único endpoint do login em si — nenhum endpoint (nem os já
existentes, nem este) passa a exigir esse token depois deste ticket; ver
`docs/domain-model.md` (seção `security`, subseção FARELO-121) para o
desenho completo do token (JWT HS256, sem tabela de sessão, expiração
configurável, sem revogação) e da checagem de credenciais.

**Request body**

```json
{
  "email": "ana@farelo.dev",
  "password": "uma-senha-forte"
}
```

| Campo | Tipo | Obrigatório | Observações |
|---|---|---|---|
| `email` | string | sim | Apenas não-vazio (`@NotBlank`) — sem `@Email`, deliberado (ver `docs/domain-model.md`) |
| `password` | string | sim | Apenas não-vazio (`@NotBlank`) — sem checagem de tamanho aqui |

**Response — `200 OK`**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9....",
  "expiresAt": "2026-09-02T21:00:00Z"
}
```

`expiresAt` é UTC. Token de uso futuro como
`Authorization: Bearer <token>` — o *mecanismo* que valida esse header
(interceptor + anotação `@RequireRole`) já existe desde FARELO-122, mas
**nenhum endpoint real o exige ainda**: FARELO-122 é infraestrutura pura,
provada apenas contra um controller dedicado a teste; decidir quais roles
podem chamar quais endpoints reais é FARELO-123 (Admin) / FARELO-124 (PDV/
cozinha). Ver `docs/domain-model.md` (seção `security`, subseção
FARELO-122) para o desenho completo do mecanismo.

**Erros**

- `400 Bad Request` — `email`/`password` ausentes/vazios,
  `code: "VALIDATION_ERROR"`.
- `401 Unauthorized` — email não cadastrado, senha incorreta, ou usuário
  encontrado mas `active: false` — **os três casos retornam exatamente a
  mesma resposta**, deliberadamente, para não revelar se a conta existe:

  ```json
  {
    "code": "INVALID_CREDENTIALS",
    "message": "Invalid credentials",
    "correlationId": "..."
  }
  ```

### `GET /api/v1/notifications`

Lista notificações (`Notification`) — registros de algo que precisa ser
(ou já foi) enviado a um destinatário, hoje sempre uma mensagem de
WhatsApp. (FARELO-110)

Somente leitura. Não há endpoints de transição (`/sent`, `/failed`)
diretos — o único endpoint de escrita deste domínio é `POST
/api/v1/notifications/{id}/send` (abaixo, FARELO-111), que relata um
resultado em vez de aceitar um.

**Query parameter opcional**

| Parâmetro | Observações |
|---|---|
| `status` | Um de `PENDING`/`SENT`/`FAILED`. Omitido: lista todas as notificações, independente de status. |

**Response — `200 OK`**

Lista ordenada por `createdAt` (asc — mais antiga primeiro), com ou sem o
filtro de `status`.

```json
[
  {
    "id": "c4a2d3f1-7d0b-5b3c-af4b-2b3c4d5e6f70",
    "type": "ORDER_READY",
    "recipient": "5511999999999",
    "content": "Seu pedido está pronto!",
    "status": "PENDING",
    "createdAt": "2026-09-02T13:00:00Z",
    "updatedAt": "2026-09-02T13:00:00Z"
  }
]
```

| Campo | Observações |
|---|---|
| `type` | Um de `ORDER_READY`/`STOCK_LOW`/`STOCK_CRITICAL`/`OUT_OF_STOCK`/`PRINT_FAILED` |
| `recipient` | Número de WhatsApp formatado do destinatário |
| `content` | Texto já formatado, congelado no momento da criação |
| `status` | Um de `PENDING`/`SENT`/`FAILED` |

Lista vazia (`[]`) quando não existe nenhuma notificação (ou nenhuma com o
`status` filtrado).

### `POST /api/v1/notifications/{id}/send`

Aciona manualmente o mecanismo real de envio (FARELO-111): tenta entregar a
notificação `{id}` via Meta WhatsApp Cloud API
(`com.farelo.api.notification.whatsapp.WhatsAppCloudApiClient`) e marca o
resultado (`SENT` ou `FAILED`). **Não é o gatilho automático** que a seção
19 do prompt mestre eventualmente quer (`ORDER_READY`/estoque baixo —
FARELO-112/113, ainda tickets futuros) — existe para operabilidade (um
operador forçando/reenviando manualmente) e testabilidade do mecanismo de
envio hoje. Sem corpo de requisição.

Sem validação de status atual: pode ser chamado numa notificação já `SENT`
ou `FAILED` (tenta reenviar) — ver javadoc de `NotificationSender#send`
para o porquê de nenhuma regra de transição existir ainda.

**Response — `200 OK`**

Sempre `200 OK`, esteja o resultado `SENT` ou `FAILED` — é um relato de
resultado de uma tentativa de entrega, não uma validação de request; uma
falha de entrega não é um erro de request malformado.

### `GET /api/v1/audit-logs`

Lista registros de auditoria (`AuditLog`) — um registro durável e
append-only de uma operação sensível: quem fez, quando, o quê, e (quando
aplicável) o que mudou. (FARELO-125)

Somente leitura. **Nenhum produtor real existe ainda** — nada neste ticket
cria uma `AuditLog` de verdade; auditar alteração de preço (FARELO-126) e
auditar ajuste de estoque (FARELO-127) são tickets futuros e distintos. Sem
marca "Requer" — este endpoint não exige nenhuma autenticação, mesmo padrão
não-protegido de todo endpoint de primeiro corte deste projeto até aqui (ver
seção "Autenticação/RBAC" acima e `docs/domain-model.md`, subseção `audit`
para o raciocínio completo dessa decisão).

**Query parameters opcionais**

| Parâmetro | Observações |
|---|---|
| `userId` | UUID de um `User`. Quando presente, tem prioridade sobre `entityType`/`entityId` (não são combinados). |
| `entityType` | Nome da entidade afetada (ex: `Product`, `Ingredient`). Só filtra quando `entityId` também é dado. |
| `entityId` | UUID da entidade afetada. Só filtra quando `entityType` também é dado. |

Nenhum filtro: lista tudo. Só `entityType` **ou** só `entityId` (sem o par
completo): tratado como se nenhum dos dois tivesse sido dado — lista tudo,
sem erro.

**Response — `200 OK`**

Lista ordenada por `createdAt` (desc — mais recente primeiro, diferente do
`createdAt asc` de `GET /api/v1/notifications` — ver javadoc de
`AuditLogService#list` para o porquê).

```json
[
  {
    "id": "c4a2d3f1-7d0b-5b3c-af4b-2b3c4d5e6f70",
    "userId": "a1b2c3d4-0000-0000-0000-000000000001",
    "userName": "Ana Souza",
    "userEmail": "ana@farelo.dev",
    "action": "PRICE_CHANGED",
    "entityType": "Product",
    "entityId": "b2c3d4e5-0000-0000-0000-000000000002",
    "previousValue": {"price": 10.50},
    "newValue": {"price": 12.00},
    "createdAt": "2026-09-02T13:00:00Z"
  }
]
```

| Campo | Observações |
|---|---|
| `userId`/`userName`/`userEmail` | Snapshot de quem executou a ação, congelado no momento da criação — não muda mesmo que a conta `User` seja depois renomeada ou o email trocado. |
| `action` | `String` livre (ex: `PRICE_CHANGED`, `STOCK_ADJUSTED`) — vocabulário aberto, definido por cada produtor futuro, não um enum fechado. |
| `entityType`/`entityId` | Tipo (ex: `Product`, `Ingredient`) e id da entidade afetada. |
| `previousValue`/`newValue` | JSON de formato livre, opaco a este ticket; qualquer um dos dois pode ser `null` (ex: uma criação não tem `previousValue`). |

Lista vazia (`[]`) quando não existe nenhum registro de auditoria (ou
nenhum que bata com o filtro dado).

```json
{
  "id": "c4a2d3f1-7d0b-5b3c-af4b-2b3c4d5e6f70",
  "type": "ORDER_READY",
  "recipient": "5511999999999",
  "content": "Seu pedido está pronto!",
  "status": "SENT",
  "createdAt": "2026-09-02T13:00:00Z",
  "updatedAt": "2026-09-02T13:00:05Z"
}
```

**Erros**

- `404 Not Found` — `{id}` não existe, `code: "NOTIFICATION_NOT_FOUND"`.

Sempre `200 OK` — sem parâmetro de path a validar.

### `GET /api/v1/commands/{number}/payments`

Lista todos os pagamentos registrados contra uma comanda, mais antigo
primeiro (`createdAt` asc). (FARELO-140)

Somente leitura. **Nenhum produtor real existe ainda** — nada neste ticket
cria um `Payment` de verdade; registrar pagamento manual (FARELO-141) é um
ticket futuro e distinto. Sem marca "Requer" — este endpoint não exige
nenhuma autenticação, mesmo padrão não-protegido de todo endpoint de
primeiro corte deste projeto até aqui (ver seção "Autenticação/RBAC" acima
e `docs/domain-model.md`, seção `payment`, para o raciocínio completo dessa
decisão e da colocação do controller no próprio pacote `payment`, não em
`command`).

Sem paginação — mesma lógica YAGNI já aplicada em
`GET /api/v1/commands/{number}/orders`/`GET
/api/v1/ingredients/{ingredientId}/movements`: o número de pagamentos por
comanda é naturalmente pequeno (FARELO-142 permite múltiplos, mas não um
fluxo ilimitado deles).

**Response — `200 OK`**

```json
[
  {
    "id": "c4a2d3f1-7d0b-5b3c-af4b-2b3c4d5e6f70",
    "commandNumber": 1,
    "amount": 25.50,
    "method": "PIX",
    "createdAt": "2026-09-02T13:00:00Z"
  }
]
```

| Campo | Observações |
|---|---|
| `commandNumber` | Número de negócio da comanda (não o `id` técnico) — mesma convenção de `OrderResponse.commandNumber`. |
| `amount` | `BigDecimal`, sempre positivo — o valor efetivamente pago nesta transação. |
| `method` | Um de `PIX`/`CREDIT_CARD`/`DEBIT_CARD`/`CASH`/`OTHER`. |

Sem `updatedAt` — este é um registro de ledger, imutável desde a criação
(ver `docs/domain-model.md`, seção `payment`/`Payment`).

Lista vazia (`[]`) quando a comanda existe mas ainda não tem pagamentos.

**Erros**

- `404 Not Found` — `number` não corresponde a nenhuma comanda existente,
  `code: "COMMAND_NOT_FOUND"` (mesmo formato dos demais endpoints).

### `POST /api/v1/commands/{number}/payments`

Registra um pagamento manual contra uma comanda — `PIX`, `CREDIT_CARD`,
`DEBIT_CARD`, `CASH` ou `OTHER`. (FARELO-141, "Registrar pagamento manual")

**Requer: `ADMIN`, `MANAGER`, `CASHIER`** (FARELO-141) — tratado como ação
de manuseio de caixa, mesma lista de papéis que `POST
/api/v1/commands/{number}/close` já usa para a ação de comanda mais
parecida (ver acima); ver a seção "Autenticação/RBAC" no topo deste
documento para o formato do header e das respostas `401`/`403`, e a
subseção FARELO-141 de `docs/domain-model.md` para o raciocínio completo.

Não soma pagamentos já registrados contra a comanda nem valida um total
pago — isso é FARELO-142/FARELO-143, tickets futuros e distintos. Este
endpoint só registra o pagamento passado nesta chamada, um por requisição.

**Request body**

```json
{
  "amount": 25.50,
  "method": "PIX"
}
```

| Campo | Observações |
|---|---|
| `amount` | `BigDecimal`, obrigatório, estritamente positivo (`@Positive`) — zero ou negativo é `400 VALIDATION_ERROR`. |
| `method` | Obrigatório, um de `PIX`/`CREDIT_CARD`/`DEBIT_CARD`/`CASH`/`OTHER` — ausente/`null` é `400 VALIDATION_ERROR`. |

**Estados de origem válidos da comanda**: `OPEN` e `PAYMENT_REQUESTED` —
mesmos dois estados que `POST /api/v1/commands/{number}/close` já aceita
como origem (ver acima); `AVAILABLE` (nada foi pedido ainda), `CLOSED` (a
conta já foi liquidada) e `BLOCKED` são inválidos como origem. Ver a
subseção FARELO-141 de `docs/domain-model.md` para o raciocínio completo
por trás dessa precondição.

**Response — `201 Created`**

Mesmo formato de `PaymentResponse` do `GET` acima. Header `Location`
apontando para `/api/v1/commands/{number}/payments/{id}` — mesma convenção
já usada por `POST /api/v1/orders`/`POST
/api/v1/ingredients/{ingredientId}/movements`: essa URL não resolve para
nenhum handler ainda (não existe `GET` de um pagamento individual, só a
listagem acima), mas continua sendo a URI correta do recurso pela convenção
REST `coleção/{id}`.

```json
{
  "id": "c4a2d3f1-7d0b-5b3c-af4b-2b3c4d5e6f70",
  "commandNumber": 1,
  "amount": 25.50,
  "method": "PIX",
  "createdAt": "2026-09-02T13:00:00Z"
}
```

**Erros**

- `404 Not Found` — `number` não corresponde a nenhuma comanda existente,
  `code: "COMMAND_NOT_FOUND"` (mesmo formato dos demais endpoints).
- `409 Conflict` — a comanda existe mas não está em um estado que aceita
  pagamento (`AVAILABLE`, `CLOSED` ou `BLOCKED`):

  ```json
  {
    "code": "COMMAND_CANNOT_ACCEPT_PAYMENTS",
    "message": "Command 62 cannot accept payments (current status: AVAILABLE, expected OPEN or PAYMENT_REQUESTED)",
    "correlationId": "..."
  }
  ```

- `400 Bad Request` — `amount` ausente/zero/negativo, ou `method`
  ausente/inválido, `code: "VALIDATION_ERROR"` (mesmo formato padrão de
  validação usado em todo o resto da API).

_(demais endpoints a preencher conforme implementados)_
