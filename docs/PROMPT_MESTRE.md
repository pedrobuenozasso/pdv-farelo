# PROMPT MESTRE — FARELO OS

> Especificação original completa do projeto, colada verbatim pelo usuário.
> Salva aqui em `docs/` como fonte da verdade permanente do roadmap e das
> regras de desenvolvimento — evita depender de memória de sessão/contexto
> compactado para essas decisões. Se este arquivo e o `docs/architecture.md`
> divergirem em algum detalhe de status de progresso, este arquivo é a
> especificação original; `docs/architecture.md`/`docs/domain-model.md`
> registram o estado real de implementação.

Você é o engenheiro principal responsável por construir o Farelo OS, um sistema próprio de operação para uma cafeteria.

O projeto deve ser construído com extrema atenção à confiabilidade, simplicidade operacional, segurança, rastreabilidade e facilidade de manutenção.

Não implemente tudo de uma vez.

Trabalhe obrigatoriamente em tickets pequenos e incrementais, com mudanças de no máximo aproximadamente 500 linhas de lógica por commit.

Arquivos gerados automaticamente, migrations extensas, lockfiles e código boilerplate não precisam entrar de forma rígida nessa contagem, porém nenhuma feature deve ser grande demais para revisão humana.

---

## 1. Objetivo do sistema

O Farelo OS substituirá gradualmente o sistema atual de:

- PDV;
- cardápio QR;
- comandas;
- impressão;
- estoque;
- operação de cozinha;
- notificações;
- posteriormente emissão fiscal.

O sistema é inicialmente para uma única cafeteria, porém deve ser construído de forma suficientemente organizada para suportar mais unidades no futuro sem reescrita completa.

Não construir como SaaS multi-tenant neste momento.

---

## 2. Stack obrigatória

### Backend

Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Hibernate, Flyway, PostgreSQL, Redis, Maven, JUnit 5, Testcontainers.

Arquitetura: **Modular Monolith**. Não utilizar microserviços neste momento.

### Frontend

Next.js, React, TypeScript, Tailwind CSS, shadcn/ui.

Preferir: App Router, Server Components quando fizer sentido, TanStack Query para estado remoto, React Hook Form, Zod.

### Infraestrutura

Docker, Docker Compose, Ubuntu LTS, Hostinger VPS, Cloudflare, Caddy, GitHub Actions.

Produção inicialmente: Hostinger VPS KVM2, 2 vCPU, 8 GB RAM, 100 GB NVMe.

---

## 3. Estrutura geral

Três interfaces principais:

- `pedido.farelo.com.br` — Cardápio QR do cliente.
- `app.farelo.com.br` — Aplicação interna. Rotas: `/pdv`, `/kitchen`, `/admin`.
- `api.farelo.com.br` — Backend.

---

## 4. Single Source of Truth

**REGRA FUNDAMENTAL**: Produtos não podem existir duplicados entre PDV e cardápio. Deve existir apenas `Product` como entidade central, usada por PDV, Cardápio QR, Estoque, Fiscal, Relatórios, KDS.

Exemplo de campos: nome, descrição, preço, categoria, imagem, ativo, disponível no QR, disponível no PDV, setor de produção, perfil fiscal, receita.

Alterar o preço no Admin deve automaticamente refletir em PDV e QR sem sincronizações manuais.

---

## 5. Domínios do backend

`auth`, `customer`, `command`, `ordering`, `kitchen`, `printing`, `inventory`, `recipe`, `notification`, `payment`, `fiscal`, `reporting`, `audit`.

Evite dependências cruzadas desnecessárias. Cada domínio deve expor serviços claros.

---

## 6. Fluxo principal do cliente

```
Cliente chega → Recebe Comanda 37 → Escaneia QR → pedido.farelo.com.br/c/37
→ Cardápio → Adiciona produtos → Nome → WhatsApp → Confirma → Order criado
```

Após criar o pedido: `ORDER_CREATED` → Kitchen, Printing, Inventory, Analytics.

---

## 7. Comanda

Comanda não é pedido. `Command` 1—N `Order`. Uma comanda pode receber diversos pedidos durante a permanência do cliente. O fechamento calcula a soma dos pedidos válidos.

---

## 8. Status da comanda

`AVAILABLE`, `OPEN`, `PAYMENT_REQUESTED`, `CLOSED`, `BLOCKED`.

Não apagar comandas antigas. Cada ciclo operacional deve possuir registro histórico.

---

## 9. Pedido

Status: `CREATED`, `CONFIRMED`, `PREPARING`, `READY`, `DELIVERED`, `CANCELLED`.

Registrar mudanças em histórico. Nunca depender apenas do status atual.

---

## 10. Impressão

Pedidos devem gerar `PrintJob`s. Nunca chamar impressora diretamente de dentro da transação HTTP.

Fluxo: `Order criado → PrintJob PENDING → Farelo Edge Agent → impressora → PRINTED`. Falha: `FAILED`, permitindo retry.

---

## 11. Edge Agent

Mini PC localizado no Farelo executa um serviço separado: `farelo-edge-agent`.

Responsabilidades: conectar com API, buscar/receber PrintJobs, imprimir, reportar status, verificar impressoras, manter fila temporária local.

Preferir ESC/POS quando suportado pelo hardware. O Edge Agent nunca deve possuir regra de negócio de pedidos — é apenas infraestrutura de dispositivos.

---

## 12. Impressão por setor

Produtos deverão possuir `productionStation` (ex: `BAR`, `KITCHEN`). Um pedido com itens de estações diferentes gera um ticket por estação, cada um mostrando com destaque o número da comanda.

---

## 13. Estoque

Não armazenar apenas um número editável de saldo. Utilizar ledger: `InventoryMovement`.

Tipos: `PURCHASE`, `ORDER_CONSUMPTION`, `LOSS`, `ADJUSTMENT`, `RETURN`, `CANCELLATION`, `INTERNAL_CONSUMPTION`. O saldo deve ser rastreável (derivado do ledger, nunca editado diretamente).

---

## 14. Ingredientes e unidades

Entidade `Ingredient`. Unidades suportadas: `UN`, `G`, `KG`, `ML`, `L`.

Unidades de compra podem possuir conversão (ex: 1 bandeja de ovo = 30 UN). Não misturar unidade de estoque com descrição de embalagem — internamente preferir unidade base.

---

## 15. Receita / ficha técnica

Produto pode possuir `Recipe` (ex: pão com ovos e bacon = 3 UN ovos + 1 UN pão + 80 G bacon + 10 G manteiga). Vender 10 unidades desconta os ingredientes proporcionalmente.

---

## 16. Baixa de estoque

A baixa deve acontecer após confirmação válida do pedido conforme regra definida. Evitar dupla baixa em retries HTTP. **Toda operação crítica deve possuir idempotência** (ex: `ORDER_CONSUMPTION orderId=123 ingredientId=5` não deve conseguir ser processado duas vezes).

---

## 17. Estoque mínimo

`Ingredient`: `currentStock`, `minimumStock`, `criticalStock`. Eventos: `STOCK_LOW`, `STOCK_CRITICAL`, `OUT_OF_STOCK`. Os alertas são determinísticos — não utilizar IA para decidir quando algo acabou.

---

## 18. Produto indisponível

Produtos podem depender de ingredientes. Caso ingrediente essencial esteja indisponível (`ingredient stock = 0`), é permitido automaticamente `Product.available = false`. Essa automação deve ser configurável.

---

## 19. WhatsApp

Utilizar futuramente: Meta WhatsApp Cloud API. Fluxo: `ORDER_READY → Notification Worker → WhatsApp`. Notificações internas também poderão existir: estoque baixo, estoque zerado, falha de impressão.

---

## 20. IA

IA é apenas camada analítica. Pode futuramente: prever consumo, prever ruptura, sugerir compras, analisar vendas, gerar resumos.

Nunca utilizar LLM como fonte de verdade para: saldo, preço, pedido, pagamento, imposto, NCM.

---

## 21. Admin

Painel `/admin`. Módulos: Dashboard, Produtos, Categorias, Adicionais, Comandas, Pedidos, Ingredientes, Receitas, Estoque, Compras, Perdas, Impressoras, Usuários, Permissões, Fiscal, Relatórios, Configurações.

---

## 22. Produto

Cadastro deve prever desde o início: `id`, `name`, `description`, `price`, `active`, `availableOnMenu`, `availableOnPos`, `categoryId`, `productionStation`, `imageUrl`, `fiscalProfileId`, `createdAt`, `updatedAt`.

Não colocar dados fiscais complexos diretamente na entidade `Product` — associar `FiscalProfile`.

---

## 23. Fiscal

O sistema futuramente emitirá NFC-e. Não implementar emissão fiscal completa no primeiro MVP, **porém a arquitetura e o banco devem nascer preparados**.

Entidades previstas: `FiscalProfile`, `FiscalDocument`, `FiscalDocumentItem`, `FiscalEvent`, `CompanyFiscalConfiguration`. `Product → FiscalProfile`.

---

## 24. Dados fiscais previstos

Perfis fiscais devem permitir futuramente: NCM, CFOP, CST/CSOSN, CEST, origem, cBenef, ICMS, CBS, IBS. A lógica tributária deve ser configurável — nunca hardcode tributação por nome do produto.

---

## 25. NFC-e

Fluxo futuro: `Close Command → Payment → Fiscal Service → NFC-e → SEFAZ → AUTHORIZED`.

Estados: `PENDING`, `PROCESSING`, `AUTHORIZED`, `REJECTED`, `CANCELLED`, `CONTINGENCY`.

---

## 26. Segurança

Obrigatório: HTTPS, RBAC, Spring Security, audit log, rate limiting, secrets fora do Git, database não exposto publicamente.

Perfis: `ADMIN`, `MANAGER`, `CASHIER`, `KITCHEN`, `ATTENDANT`.

---

## 27. Auditoria

Operações sensíveis precisam registrar: quem, quando, o quê, valor anterior, valor novo. Principalmente: preço, estoque, cancelamento, pagamento, configuração fiscal, produto.

---

## 28. Banco

PostgreSQL será a fonte de verdade. Não utilizar Redis como banco principal — Redis será apenas infraestrutura auxiliar.

---

## 29. Eventos

Preferir eventos internos de domínio. Exemplos: `ORDER_CREATED`, `ORDER_READY`, `ORDER_CANCELLED`, `COMMAND_CLOSED`, `PRINT_REQUESTED`, `PRINT_COMPLETED`, `PRINT_FAILED`, `STOCK_LOW`, `STOCK_CRITICAL`, `OUT_OF_STOCK`.

Inicialmente não instalar Kafka. Preferir Transactional Outbox + Worker antes de introduzir broker externo.

---

## 30. Transações

Operações críticas devem utilizar transações. Exemplo, criação de pedido:

```
BEGIN
validar comanda
validar produtos
snapshot de preços
criar pedido
criar itens
registrar evento outbox
COMMIT
```

Processamentos secundários podem ocorrer depois.

---

## 31. Snapshot de preço

**IMPORTANTE**. `OrderItem` não deve depender do preço atual de `Product`. Ao comprar com `Product.price = R$15`, `OrderItem` deve guardar `unitPrice = R$15` — se amanhã o produto virar R$20, o pedido antigo continua R$15. Isso também vale futuramente para dados fiscais relevantes do momento da venda.

---

## 32. Observabilidade

Adicionar gradualmente: health endpoint, structured logging, request correlation id, metrics, error tracking. Não instalar uma stack pesada de observabilidade no primeiro ticket.

---

## 33. Backup

Produção deve possuir backup PostgreSQL + armazenamento externo. Nunca considerar snapshot da VPS como único backup.

---

## 34. Regras de desenvolvimento (obrigatório)

**Ticket pequeno**: cada ticket deve possuir apenas um objetivo principal.

Exemplos corretos: "Criar entidade Product", "Criar endpoint de criação de categoria", "Adicionar migration de Command".

Exemplos incorretos: "Implementar sistema de pedidos completo", "Criar estoque inteiro".

---

## 35. Limite por commit

Objetivo: máximo ~500 linhas de lógica alterada por commit. Se passar disso: **divida o ticket**. Não usar limite artificial para lockfiles/metadata/arquivos gerados, mas código de negócio deve permanecer pequeno.

---

## 36. Um ticket = um commit principal

`Ticket FARELO-012 → commit → feat(catalog): create product endpoint`. Commits intermediários, se necessários, devem possuir escopo claro.

---

## 37. Commits

Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.

---

## 38. Requisitos antes de cada commit

Compilar, rodar testes, rodar lint, verificar migrations, verificar diff. Não fazer commit se testes existentes estiverem falhando.

---

## 39. Testes

Todo comportamento importante deve possuir teste. Backend: JUnit, Mockito quando realmente necessário, Testcontainers para integração. Preferir testar comportamento em vez de implementação.

Casos críticos: criação de pedido, snapshot de preço, baixa de estoque, idempotência, cancelamento, permissões.

---

## 40. Tratamento de dinheiro

NUNCA usar `double`/`float` para dinheiro. Java: `BigDecimal`. Banco: `NUMERIC`. Definir explicitamente escala e arredondamento.

---

## 41. IDs

Preferir UUID/UUIDv7 ou estratégia consistente. Não expor IDs sequenciais sensíveis sem necessidade. Comanda terá também `number` humano (1..100), separado do ID técnico.

---

## 42. Datas

Backend: UTC. Banco: `TIMESTAMP WITH TIME ZONE`. Frontend converte para `America/Sao_Paulo`.

---

## 43. API

Utilizar `/api/v1`. Não expor entidades JPA diretamente — utilizar DTOs.

---

## 44. Erros

Padrão consistente:

```json
{
  "code": "COMMAND_NOT_AVAILABLE",
  "message": "Comanda não está disponível.",
  "correlationId": "..."
}
```

Evitar erros genéricos sem código.

---

## 45. Documentação

Cada módulo deve possuir documentação mínima. Manter `/docs` com `architecture.md`, `domain-model.md`, `api.md`, `decisions/`. Usar ADRs para decisões relevantes (ex: `ADR-001-modular-monolith.md`, `ADR-002-postgresql.md`, `ADR-003-transactional-outbox.md`).

---

## 46. Regra para a IA

ANTES de implementar qualquer ticket:

1. Leia documentação existente.
2. Leia código relacionado.
3. Identifique impacto.
4. Escreva plano curto.
5. Confirme que ticket cabe em aproximadamente 500 linhas de lógica.
6. Caso não caiba, divida antes de implementar.
7. Implemente somente o escopo daquele ticket.
8. Adicione testes.
9. Rode validações.
10. Revise o próprio diff.
11. Só então finalize.

NÃO: reescrever módulos inteiros sem necessidade; adicionar dependências sem justificar; criar abstrações prematuras; criar microserviços; adicionar tecnologias fora da stack; fazer alterações "en passant" fora do ticket; alterar código não relacionado.

---

## 47. Estratégia inicial de tickets

### EPIC 0 — Bootstrap

- **FARELO-001**: Criar monorepo/repositório inicial. Resultado: `/apps/web`, `/apps/api`, `/apps/edge-agent`, `/docs`, `/infra`.
- **FARELO-002**: Inicializar Spring Boot Java 21. Adicionar apenas dependências essenciais.
- **FARELO-003**: Configurar PostgreSQL + Docker Compose.
- **FARELO-004**: Adicionar Flyway. Criar primeira migration de infraestrutura.
- **FARELO-005**: Inicializar Next.js + TypeScript + Tailwind.
- **FARELO-006**: Configurar lint, formatter e validações.
- **FARELO-007**: Criar GitHub Actions para: backend test, frontend lint, frontend build.

### EPIC 1 — Catálogo

- **FARELO-010**: Criar Category.
- **FARELO-011**: Criar Product. Sem receita, estoque ou fiscal avançado ainda.
- **FARELO-012**: Criar endpoint POST Category.
- **FARELO-013**: Criar endpoint GET Categories.
- **FARELO-014**: Criar endpoint POST Product.
- **FARELO-015**: Criar endpoint GET Products.
- **FARELO-016**: Criar atualização de Product.
- **FARELO-017**: Adicionar `availableOnMenu`, `availableOnPos`.
- **FARELO-018**: Criar tela Admin de categorias.
- **FARELO-019**: Criar tela Admin de produtos.
- **FARELO-020**: Alterar preço pelo Admin e refletir no catálogo.

### EPIC 2 — Comandas

- **FARELO-030**: Criar entidade Command.
- **FARELO-031**: Criar comandas 1–100 via migration/seed.
- **FARELO-032**: Buscar comanda pelo número.
- **FARELO-033**: Abrir comanda.
- **FARELO-034**: Fechar comanda sem pagamento/fiscal ainda.
- **FARELO-035**: Criar tela de comandas no PDV.

### EPIC 3 — Cardápio QR

- **FARELO-040**: Criar rota `/c/{commandNumber}`.
- **FARELO-041**: Validar comanda via API.
- **FARELO-042**: Listar categorias disponíveis no cardápio.
- **FARELO-043**: Listar produtos disponíveis.
- **FARELO-044**: Criar carrinho local.
- **FARELO-045**: Solicitar nome e telefone antes de finalizar.

### EPIC 4 — Pedidos

- **FARELO-050**: Criar Order.
- **FARELO-051**: Criar OrderItem.
- **FARELO-052**: Implementar snapshot de preço.
- **FARELO-053**: Endpoint criar pedido.
- **FARELO-054**: Relacionar pedido com comanda.
- **FARELO-055**: Listar pedidos da comanda.
- **FARELO-056**: Criar histórico de status.
- **FARELO-057**: Alterar pedido para PREPARING.
- **FARELO-058**: Alterar pedido para READY.
- **FARELO-059**: Criar KDS inicial.

### EPIC 5 — Outbox

- **FARELO-060**: Criar OutboxEvent.
- **FARELO-061**: Publicar ORDER_CREATED na mesma transação do pedido.
- **FARELO-062**: Criar worker básico.
- **FARELO-063**: Processar eventos com idempotência.

### EPIC 6 — Impressão

- **FARELO-070**: Criar Printer.
- **FARELO-071**: Criar PrintJob.
- **FARELO-072**: Criar PrintJob em ORDER_CREATED.
- **FARELO-073**: Adicionar ProductionStation ao produto.
- **FARELO-074**: Separar PrintJobs por estação.
- **FARELO-075**: Criar Edge Agent mínimo.
- **FARELO-076**: Edge Agent consultar PrintJobs.
- **FARELO-077**: Reportar impressão concluída.
- **FARELO-078**: Implementar impressão ESC/POS.
- **FARELO-079**: Criar retry de impressão.

### EPIC 7 — Estoque

- **FARELO-090**: Criar Ingredient.
- **FARELO-091**: Criar Recipe.
- **FARELO-092**: Criar RecipeItem.
- **FARELO-093**: Criar InventoryMovement.
- **FARELO-094**: Criar entrada manual de estoque.
- **FARELO-095**: Calcular saldo do ingrediente.
- **FARELO-096**: Consumir receita ao criar pedido.
- **FARELO-097**: Implementar idempotência da baixa.
- **FARELO-098**: Criar movimento de perda.
- **FARELO-099**: Criar estoque mínimo.
- **FARELO-100**: Publicar STOCK_LOW.
- **FARELO-101**: Publicar OUT_OF_STOCK.

### EPIC 8 — WhatsApp

- **FARELO-110**: Criar Notification.
- **FARELO-111**: Criar adapter da Meta WhatsApp API.
- **FARELO-112**: Disparar WhatsApp em ORDER_READY.
- **FARELO-113**: Alertar estoque baixo.

### EPIC 9 — Segurança/Admin

- **FARELO-120**: Criar User.
- **FARELO-121**: Criar autenticação.
- **FARELO-122**: Implementar RBAC.
- **FARELO-123**: Proteger Admin.
- **FARELO-124**: Proteger PDV.
- **FARELO-125**: Criar AuditLog.
- **FARELO-126**: Auditar alteração de preço.
- **FARELO-127**: Auditar ajuste de estoque.

### EPIC 10 — Pagamentos

- **FARELO-140**: Criar Payment.
- **FARELO-141**: Registrar pagamento manual. Métodos: `PIX`, `CREDIT_CARD`, `DEBIT_CARD`, `CASH`, `OTHER`.
- **FARELO-142**: Permitir múltiplos pagamentos por comanda.
- **FARELO-143**: Validar total pago antes de fechar.

### EPIC 11 — Fiscal Base

NÃO EMITIR NFC-e ainda.

- **FARELO-150**: Criar FiscalProfile.
- **FARELO-151**: Associar Product com FiscalProfile.
- **FARELO-152**: Adicionar NCM.
- **FARELO-153**: Adicionar CFOP.
- **FARELO-154**: Adicionar CST/CSOSN.
- **FARELO-155**: Criar CompanyFiscalConfiguration.
- **FARELO-156**: Criar FiscalDocument.
- **FARELO-157**: Criar estados fiscais.

### EPIC 12 — NFC-e

Somente iniciar após validação contábil.

- **FARELO-170**: Definir adapter fiscal. Interface `FiscalProvider`. Não acoplar o domínio diretamente à SEFAZ.
- **FARELO-171**: Implementar ambiente de homologação.
- **FARELO-172**: Criar XML NFC-e ou integrar provedor fiscal escolhido.
- **FARELO-173**: Implementar assinatura.
- **FARELO-174**: Transmitir homologação.
- **FARELO-175**: Persistir autorização.
- **FARELO-176**: Persistir rejeição.
- **FARELO-177**: Implementar cancelamento.
- **FARELO-178**: Implementar contingência.

---

## 48. Primeira entrega funcional

```
Admin cadastra produto → Produto aparece no QR → Cliente acessa comanda
→ Cria pedido → Pedido aparece no PDV/KDS → Pedido é preparado → Pedido fica READY
```

Sem: fiscal, estoque avançado, WhatsApp inicialmente.

---

## 49. Segunda milestone

Adicionar: Print Agent, Impressão, Estoque, Receitas.

---

## 50. Terceira milestone

Adicionar: WhatsApp, Pagamentos, Auditoria completa.

---

## 51. Quarta milestone

Adicionar: NFC-e, Fiscal, Relatórios.

---

## 52. Instrução inicial

Comece SOMENTE pelo ticket FARELO-001. Antes de escrever código: proponha estrutura de diretórios; explique brevemente as decisões; garanta que não exista implementação funcional prematura; mantenha o ticket pequeno. Depois implemente apenas o FARELO-001.

Ao finalizar, apresente: arquivos criados, decisões tomadas, testes/validações executados, diff resumido, estimativa de linhas de lógica alteradas, próximo ticket recomendado. Não implemente o próximo ticket automaticamente.
