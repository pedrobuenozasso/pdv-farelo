# Domain Model — Farelo OS

Este documento é preenchido incrementalmente à medida que cada domínio é implementado.

## Domínios previstos

| Domínio | Responsabilidade | Status |
|---|---|---|
| `auth` | Autenticação e RBAC de usuários internos | Não iniciado |
| `catalog` | `Product`, `Category` — fonte única de verdade do cardápio | Em andamento |
| `customer` | Dados do cliente coletados no fluxo de pedido (nome, WhatsApp) | Não iniciado |
| `command` | `Command` (comanda) e seu ciclo de vida | Não iniciado |
| `ordering` | `Order`, `OrderItem`, snapshot de preço, histórico de status | Não iniciado |
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
  Escopo restrito propositalmente: sem `availableOnMenu`/`availableOnPos`
  (FARELO-017), sem `productionStation` (FARELO-073), sem `fiscalProfileId`
  (FARELO-151/Epic 11), sem receita/estoque, e ainda sem endpoint REST
  (FARELO-014/015/016).
