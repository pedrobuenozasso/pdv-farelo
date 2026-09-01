# Arquitetura — Farelo OS

## Visão geral

Farelo OS é um sistema de operação para uma cafeteria (PDV, cardápio QR, comandas,
impressão, estoque, cozinha, notificações e futuramente emissão fiscal), inicialmente
para uma única unidade, mas organizado para suportar múltiplas unidades no futuro sem
reescrita completa. Não é construído como SaaS multi-tenant neste momento.

## Estilo arquitetural

**Modular Monolith.** Ver [ADR-001](decisions/ADR-001-modular-monolith.md). Sem
microserviços neste momento.

## Apps

| App | Stack | Responsabilidade |
|---|---|---|
| `apps/web` | Next.js, TypeScript, Tailwind, shadcn/ui | Cardápio QR (`pedido.farelo.com.br`), app interna (`app.farelo.com.br`: `/pdv`, `/kitchen`, `/admin`) |
| `apps/api` | Java 21, Spring Boot 3 | Backend modular monolith (`api.farelo.com.br`) |
| `apps/edge-agent` | Serviço local | Impressão física via dispositivo na cafeteria |

## Domínios do backend

`auth`, `catalog`, `customer`, `command`, `ordering`, `kitchen`, `printing`,
`inventory`, `recipe`, `notification`, `payment`, `fiscal`, `reporting`, `audit`.

Detalhado em [`domain-model.md`](domain-model.md) conforme cada domínio é implementado.

## Princípios fundamentais

- **Single Source of Truth**: `Product` é a entidade central única, usada por PDV,
  cardápio QR, estoque, fiscal, relatórios e KDS — sem duplicação entre sistemas.
- **Snapshot de preço**: `OrderItem` armazena `unitPrice` no momento da venda,
  independente do preço atual de `Product`.
- **Estoque como ledger**: saldo nunca é um número editável direto — é derivado de
  `InventoryMovement`.
- **Eventos internos de domínio** via Transactional Outbox + Worker, antes de introduzir
  um broker externo (sem Kafka neste momento).
- **Dinheiro**: sempre `BigDecimal` / `NUMERIC`, nunca `double`/`float`.
- **Datas**: backend em UTC (`TIMESTAMP WITH TIME ZONE`), frontend converte para
  `America/Sao_Paulo`.
- **Idempotência** obrigatória em operações críticas (baixa de estoque, criação de pedido).

## Infraestrutura

Docker + Docker Compose, Ubuntu LTS, Hostinger VPS (KVM2 — 2 vCPU / 8 GB RAM / 100 GB NVMe),
Cloudflare, Caddy, GitHub Actions.

## Roadmap de milestones

1. **Primeira entrega**: Admin cadastra produto → aparece no QR → cliente cria pedido →
   aparece no PDV/KDS → fica READY. Sem fiscal, estoque avançado ou WhatsApp.
2. **Segunda**: Print Agent, impressão, estoque, receitas.
3. **Terceira**: WhatsApp, pagamentos, auditoria completa.
4. **Quarta**: NFC-e, fiscal, relatórios.
