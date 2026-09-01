# Domain Model — Farelo OS

Este documento é preenchido incrementalmente à medida que cada domínio é implementado.
Ainda não há entidades implementadas neste repositório (ver FARELO-001, apenas bootstrap).

## Domínios previstos

| Domínio | Responsabilidade | Status |
|---|---|---|
| `auth` | Autenticação e RBAC de usuários internos | Não iniciado |
| `catalog` | `Product`, `Category` — fonte única de verdade do cardápio | Não iniciado |
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
