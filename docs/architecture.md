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
| `apps/edge-agent` | Node.js, TypeScript | Impressão física via dispositivo na cafeteria (esqueleto inicial — FARELO-075; ver [ADR-002](decisions/ADR-002-edge-agent-nodejs.md)) |

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

**Ambiente de desenvolvimento**: `infra/docker-compose.yml` (Postgres) +
`infra/docker-compose.dev.yml` (backend + frontend, complementar — ver
`infra/README.md`) sobem a stack completa localmente com um único comando.
São Dockerfiles/compose de **desenvolvimento**, não de produção — nenhum
ticket do roadmap atual cobre o deploy real (VPS/Caddy/Cloudflare listados
acima permanecem não implementados). `apps/edge-agent` não faz parte desse
compose: roda como processo separado (fisicamente num mini PC dedicado em
produção), sem lógica real de rede ainda para justificar orquestração local
junto do resto da stack (ver `apps/edge-agent/README.md`).

## Roadmap de milestones

1. **Primeira entrega** ✅ **completa** (FARELO-059, KDS): Admin cadastra produto → aparece
   no QR → cliente cria pedido → aparece no PDV/KDS → fica READY. Ciclo de vida do pedido
   também fechado: transições `DELIVERED`/`CANCELLED` (não numeradas no roadmap original,
   follow-up justificado — ver docs/domain-model.md). Sem fiscal, estoque avançado ou
   WhatsApp. Falta ainda, fora do escopo deste marco: deploy real em produção
   (VPS/Caddy/Cloudflare — infra-agent não tem ticket para isso no roadmap dado). Epic 5
   (Transactional Outbox — fundação, retenção, observabilidade) também já concluído,
   adiantado em relação a este marco, como base para os epics de impressão/notificação
   futuros.
2. **Segunda**: Print Agent, impressão, estoque, receitas.
3. **Terceira**: WhatsApp, pagamentos, auditoria completa.
4. **Quarta**: NFC-e, fiscal, relatórios.
