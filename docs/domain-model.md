# Domain Model — Farelo OS

Este documento é preenchido incrementalmente à medida que cada domínio é implementado.

## Domínios previstos

| Domínio | Responsabilidade | Status |
|---|---|---|
| `auth` | Autenticação e RBAC de usuários internos | Não iniciado |
| `catalog` | `Product`, `Category` — fonte única de verdade do cardápio | Em andamento |
| `customer` | Dados do cliente coletados no fluxo de pedido (nome, WhatsApp) | Não iniciado |
| `command` | `Command` (comanda) e seu ciclo de vida | Em andamento |
| `ordering` | `Order`, `OrderItem`, snapshot de preço, histórico de status | Em andamento |
| `kitchen` | KDS — visualização e transição de status de preparo | Não iniciado |
| `printing` | `Printer`, `PrintJob`, integração com Edge Agent | Não iniciado |
| `inventory` | `Ingredient`, `InventoryMovement` (ledger) | Não iniciado |
| `recipe` | `Recipe`, `RecipeItem` — ficha técnica de produtos | Não iniciado |
| `notification` | `Notification`, adapter WhatsApp Cloud API | Não iniciado |
| `payment` | `Payment`, múltiplos pagamentos por comanda | Não iniciado |
| `fiscal` | `FiscalProfile`, `FiscalDocument`, NFC-e (futuro) | Não iniciado |
| `reporting` | Relatórios e analytics | Não iniciado |
| `audit` | `AuditLog` de operações sensíveis | Não iniciado |

Cada domínio deve expor serviços claros e evitar dependências cruzadas desnecessárias.

## catalog

Pacote: `com.farelo.api.catalog`.

- **`Category`** (FARELO-010): entidade JPA — `id` (UUID, gerado via
  `@UuidGenerator` do Hibernate, style `RANDOM`; Hibernate 6.6 não tem suporte
  nativo a UUIDv7, ver comentário na classe), `name`, `active` (default
  `true`), `createdAt`/`updatedAt` (`OffsetDateTime` em UTC). Tabela criada
  pela migration `V2__create_category_table.sql`. Sem endpoint REST ainda
  (escopo de FARELO-012/013).
- **`Product`** (FARELO-011): entidade JPA — `id` (UUID, mesma estratégia de
  `Category`), `name`, `description` (opcional), `price` (`BigDecimal`,
  coluna `NUMERIC(10,2)` — nunca `double`/`float`, ver AGENTS.md), `active`
  (default `true`), `category` (`@ManyToOne` obrigatório para `Category`),
  `imageUrl` (opcional), `createdAt`/`updatedAt` (UTC). Tabela criada pela
  migration `V3__create_product_table.sql`, com FK para `category(id)`.
  Ganhou `availableOnMenu`/`availableOnPos` (default `true` cada, visibilidade
  independente no cardápio QR vs. no PDV) em FARELO-017. Escopo ainda restrito
  propositalmente: sem `productionStation` (FARELO-073), sem `fiscalProfileId`
  (FARELO-151/Epic 11), sem receita/estoque.

CRUD REST completo do lado do backend: `Category` (`POST`/`GET`,
FARELO-012/013), `Product` (`POST`/`GET`/`PUT`, FARELO-014/015/016). Sem
`DELETE` para nenhum dos dois (fora do roadmap atual). Ver `docs/api.md`
para os endpoints.

## command

Pacote: `com.farelo.api.command`.

- **`Command`** (FARELO-030): entidade JPA — `id` (UUID, mesma estratégia de
  `Category`/`Product`), `number` (int, identificador de negócio
  humano-legível, 1-100, impresso na comanda física — deliberadamente
  separado do `id` técnico, que nunca é exposto como identificador de
  negócio; ver prompt mestre seções 7-8), `status` (enum `CommandStatus`:
  `AVAILABLE`, `OPEN`, `PAYMENT_REQUESTED`, `CLOSED`, `BLOCKED` —
  `@Enumerated(EnumType.STRING)`, nunca `ORDINAL`, para não quebrar se a
  ordem dos valores do enum mudar; default `AVAILABLE`),
  `createdAt`/`updatedAt` (UTC, mesmo padrão dos demais domínios). Tabela
  criada pela migration `V5__create_command_table.sql`, com `number` `UNIQUE
  NOT NULL` e `status` como `VARCHAR` + `CHECK` constraint (rigidez extra no
  banco, espelhando os valores do enum Java — trade-off: um novo valor de
  `CommandStatus` no futuro exige uma migration para estender o `CHECK`).
  Comandas nunca são apagadas — cada ciclo operacional (visita de um
  cliente) deixa um registro histórico; reabertura/histórico por `number`
  fica para tickets futuros. `CommandRepository` expõe `findByNumber(int)`,
  já pensando no FARELO-032.
- **Seed 1-100** (FARELO-031): migration `V6__seed_commands_1_to_100.sql`
  insere as 100 comandas físicas do estabelecimento (`INSERT INTO command
  (number) SELECT generate_series(1, 100)`), todas com `status` `AVAILABLE`
  (default da coluna, não precisa ser especificado no `INSERT`).

CRUD REST do lado do backend, focado no ciclo de vida: `GET /{number}`
(FARELO-032), `POST /{number}/open` (`AVAILABLE`→`OPEN`, FARELO-033),
`POST /{number}/close` (`OPEN`/`PAYMENT_REQUESTED`→`CLOSED`, sem validação
de pagamento/fiscal ainda — FARELO-143/Epic 10 — FARELO-034). Fecha o
Epic 2 (Comandas) do lado do backend por enquanto. Ver `docs/api.md`.

## ordering

Pacote: `com.farelo.api.ordering`.

- **`Order`** (FARELO-050): entidade JPA — `id` (UUID, mesma estratégia dos
  demais domínios), `command` (`@ManyToOne` obrigatório para `Command` —
  todo pedido pertence a uma comanda), `status` (enum `OrderStatus`:
  `CREATED`, `CONFIRMED`, `PREPARING`, `READY`, `DELIVERED`, `CANCELLED` —
  `@Enumerated(EnumType.STRING)`, default `CREATED`),
  `createdAt`/`updatedAt` (UTC, mesmo padrão dos demais domínios). Tabela
  criada pela migration `V7__create_order_table.sql`, com FK para
  `command(id)`.

  **Nota de nomeação**: a tabela é `orders` (plural), não `order` —
  `ORDER` é palavra reservada em SQL (usada em `ORDER BY`); um nome de
  tabela `order` sem aspas quebraria qualquer SQL bruto que a referenciasse,
  incluindo migrations futuras (ex: FK de `OrderItem` no FARELO-051). O
  arquivo de migration manteve o nome `V7__create_order_table.sql` do
  ticket por rastreabilidade, mas cria a tabela `orders`. `status` usa a
  mesma convenção `VARCHAR` + `CHECK` constraint de `command.status`
  (`V5__create_command_table.sql`), por consistência.

  Escopo restrito propositalmente: sem `OrderItem` (FARELO-051), sem
  snapshot de preço (FARELO-052), sem endpoints REST (FARELO-053+).
- **`OrderItem`** (FARELO-051): entidade JPA — `id` (UUID, mesma estratégia
  dos demais domínios), `order` (`@ManyToOne` obrigatório para `Order`),
  `product` (`@ManyToOne` obrigatório para `Product`), `quantity` (int;
  validação de positivo fica para a camada de DTO/service quando o
  endpoint existir, FARELO-053 — não há `CHECK` no banco para isso),
  `unitPrice` (`BigDecimal`, `NUMERIC(10,2)`), `createdAt` (UTC).

  **`unitPrice` é snapshot congelado, não referência ao preço atual do
  produto** (convenção de snapshot de preço do AGENTS.md): o valor é
  capturado no momento da venda e nunca deriva de `product.getPrice()`,
  nem se atualiza se o preço do produto mudar depois. Este ticket
  (FARELO-051) só adiciona a coluna e a entidade — a lógica que de fato
  captura o preço atual do produto automaticamente ao criar um item (e
  qualquer validação relacionada) ainda não existe, é escopo de
  FARELO-052/053, quando o endpoint for criado.

  **Sem `updatedAt`** — decisão deliberada, diferente de toda outra
  entidade do projeto até aqui: neste MVP um item de pedido é imutável
  depois de criado (não há fluxo de edição ainda; nenhum endpoint altera
  quantidade/produto/preço após a criação). Adicionar `updatedAt` agora
  seria uma coluna sem nenhum escritor; mais barato adicionar depois, se
  e quando um caso de uso de edição realmente aparecer.

  Tabela `order_item` criada pela migration `V8__create_order_item_table.sql`,
  com FK `NOT NULL` para `orders(id)` e para `product(id)`, mais índices
  nas duas FKs.
