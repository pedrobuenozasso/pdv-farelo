# AGENTS.md — Farelo OS

Este arquivo define como agentes (humanos ou IA) devem trabalhar neste repositório.
Leia isto e `docs/architecture.md`, `docs/domain-model.md`, `docs/api.md` antes de
implementar qualquer ticket.

## Papel de cada agente do time

| Agente | Diretórios de propriedade | Responsabilidade |
|---|---|---|
| `backend-agent` | `apps/api/**`, `docs/domain-model.md`, `docs/api.md` | Spring Boot, PostgreSQL, Flyway, entidades, regras de negócio, testes backend |
| `frontend-agent` | `apps/web/**` | Next.js — Admin, PDV, cardápio QR, KDS |
| `infra-agent` | `infra/**`, `.github/**`, `docs/architecture.md`, `docs/decisions/**`, `README.md`, `.gitignore`, arquivos de configuração na raiz | Docker Compose, CI/CD, documentação de arquitetura, ambiente de desenvolvimento |

**Regra de propriedade de arquivos**: um agente só edita arquivos dentro do seu
próprio diretório de propriedade. Se um ticket exigir mudança fora dela, o agente
deve reportar ao líder em vez de editar diretamente.

## Regra fundamental: tickets pequenos

- Um ticket = um objetivo principal.
- Máximo ~500 linhas de lógica alterada por commit (migrations extensas, lockfiles e
  boilerplate gerado não contam rigidamente nesse limite, mas nenhuma feature deve ser
  grande demais para revisão humana).
- Se o ticket não couber, ele deve ser dividido antes de implementar — não implementar
  parcialmente e deixar o resto "para depois" dentro do mesmo commit.
- Trabalhar **somente** nos tickets definidos no roadmap (`docs/architecture.md` →
  seção de milestones; lista completa de tickets é mantida pelo líder do time).
- Cada agente trabalha em **um ticket por vez**. Não iniciar um novo ticket sem
  reportar a conclusão do anterior ao líder.

## Antes de cada implementação

1. Ler o código existente relevante.
2. Ler `AGENTS.md` e a documentação em `/docs`.
3. Apresentar um plano curto (o que vai mudar, por quê, arquivos afetados).
4. Implementar apenas o escopo do ticket.
5. Rodar testes e validações (compilar, testes, lint, migrations).
6. Revisar o próprio diff.
7. Commitar usando Conventional Commits.

Não fazer push nem merge para `main` sem revisão do líder do time. Cada agente deve
deixar o resultado pronto (branch/worktree + commit) e reportar para revisão.

## Convenções técnicas obrigatórias

- **Dinheiro**: sempre `BigDecimal` / `NUMERIC`. Nunca `double`/`float`.
- **Datas**: backend em UTC (`TIMESTAMP WITH TIME ZONE`); frontend converte para
  `America/Sao_Paulo`.
- **Snapshot de preço**: `OrderItem` nunca depende do preço atual de `Product`.
- **Estoque**: ledger via `InventoryMovement`, nunca um saldo editável direto.
- **Idempotência** obrigatória em operações críticas (baixa de estoque, criação de
  pedido, etc).
- **API**: prefixo `/api/v1`, nunca expor entidades JPA diretamente (sempre DTOs),
  erros no formato:

  ```json
  { "code": "COMMAND_NOT_AVAILABLE", "message": "...", "correlationId": "..." }
  ```

- **Commits**: [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`), com prefixo do ticket
  no corpo da mensagem (ex: `FARELO-002: ...`).
- **Testes**: todo comportamento importante deve ter teste (JUnit/Testcontainers no
  backend). Não commitar com testes existentes quebrados.
- **Não fazer**: reescrever módulos inteiros sem necessidade, adicionar dependências
  sem justificar, criar abstrações prematuras, criar microserviços, adicionar
  tecnologias fora da stack definida, alterar código fora do escopo do ticket.

## Stack

Backend: Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Hibernate, Flyway,
PostgreSQL, Redis, Maven, JUnit 5, Testcontainers.

Frontend: Next.js, React, TypeScript, Tailwind CSS, shadcn/ui, App Router, TanStack
Query, React Hook Form, Zod.

Infra: Docker, Docker Compose, Ubuntu LTS, Hostinger VPS, Cloudflare, Caddy,
GitHub Actions.

Arquitetura: Modular Monolith (ver `docs/decisions/ADR-001-modular-monolith.md`).
Sem microserviços neste momento.

## Coordenação entre agentes

O líder do time (sessão principal) controla dependências entre tickets, não inicia
tickets bloqueados por trabalho pendente de outro agente, e revisa cada resultado
antes de decidir sobre merge. Nenhum agente deve merjar seu próprio trabalho em
`main` sem essa revisão.
