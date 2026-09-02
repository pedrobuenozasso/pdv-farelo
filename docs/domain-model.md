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
| `kitchen` | KDS — visualização e transição de status de preparo | Não iniciado — `GET /api/v1/orders` (fila da cozinha, FARELO-059) já existe, mas ficou em `ordering` por enquanto; ver nota na seção `ordering` abaixo |
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
  validação de positivo na camada de DTO/service — `@Positive` em
  `OrderItemRequest`, sem `CHECK` no banco), `unitPrice` (`BigDecimal`,
  `NUMERIC(10,2)`), `createdAt` (UTC).

  **`unitPrice` é snapshot congelado, não referência ao preço atual do
  produto** (convenção de snapshot de preço do AGENTS.md): o valor é
  capturado do preço atual do produto no momento da criação do pedido
  (`OrderService.create`, FARELO-052) e nunca deriva de
  `product.getPrice()` depois disso — se o preço do produto mudar, o
  pedido já criado mantém o preço antigo.

  **Sem `updatedAt`** — decisão deliberada, diferente de toda outra
  entidade do projeto até aqui: neste MVP um item de pedido é imutável
  depois de criado (não há fluxo de edição ainda; nenhum endpoint altera
  quantidade/produto/preço após a criação). Adicionar `updatedAt` agora
  seria uma coluna sem nenhum escritor; mais barato adicionar depois, se
  e quando um caso de uso de edição realmente aparecer.

  Tabela `order_item` criada pela migration `V8__create_order_item_table.sql`,
  com FK `NOT NULL` para `orders(id)` e para `product(id)`, mais índices
  nas duas FKs.

`POST /api/v1/orders` (FARELO-052/053) cria um `Order` com seus
`OrderItem`s numa única transação, com o snapshot de preço descrito acima.
Reaproveita `CommandNotFoundException`/`ProductNotFoundException` dos
domínios `command`/`catalog`; adiciona `CommandCannotAcceptOrdersException`
(`command`, comanda em estado que não aceita pedidos) e
`ProductNotAvailableException` (`catalog`, produto inativo). Ver
`docs/api.md` para o endpoint completo, incluindo a decisão de negócio
sobre `AVAILABLE`→`OPEN` automático ao criar o primeiro pedido.

`GET /api/v1/commands/{number}/orders` (FARELO-055) lista os pedidos de
uma comanda, cada um com seus itens, sem paginação. Implementado em
`CommandOrdersController` (pacote `ordering.web`, apesar da URL começar
com `/commands` — decisão de manter a dependência `ordering`→`command`
numa direção só, ver javadoc da classe).

- **`OrderStatusHistory`** (FARELO-056): entidade JPA append-only — `id`
  (UUID, mesma estratégia dos demais domínios), `order` (`@ManyToOne`
  obrigatório para `Order`), `fromStatus` (enum `OrderStatus`,
  **nullable** — a primeira entrada, quando o pedido é criado como
  `CREATED`, não tem "de onde veio"), `toStatus` (enum `OrderStatus`,
  obrigatório), `changedAt` (UTC). **Sem setters** — diferente de
  `OrderItem` (que tem setters mesmo sendo "imutável por enquanto" só
  porque nenhum endpoint edita ainda), `OrderStatusHistory` é uma
  trilha de auditoria append-only por natureza: uma transição passada é
  um fato histórico que nunca deveria ser reescrito, então a entidade
  não expõe forma alguma de mutar uma linha depois de criada. Tabela
  `order_status_history` criada pela migration
  `V9__create_order_status_history_table.sql`, com FK `NOT NULL` para
  `orders(id)`, `from_status`/`to_status` como `VARCHAR` + `CHECK`
  (mesma convenção de `orders.status`, `from_status` sem `NOT NULL`).
  `OrderStatusHistoryRepository` expõe
  `findByOrderOrderByChangedAtAsc(Order)` para consulta futura.

  `OrderService.create(...)` (FARELO-052/053) passou a gravar a
  primeira entrada de histórico (`fromStatus = null`,
  `toStatus = CREATED`) na mesma transação da criação do pedido. Ainda
  sem endpoint para consultar o histórico nem as transições
  `PREPARING`/`READY` — escopo de FARELO-057/058, que vão reaproveitar
  esse mecanismo para cada transição futura.

`GET /api/v1/orders` (FARELO-059) lista a fila de pedidos que ainda
precisam de atenção da cozinha — status `CREATED`, `CONFIRMED` ou
`PREPARING`, tudo antes de `READY` — de **todas** as comandas, ordenados
por `createdAt` ASC (fila FIFO). Sem paginação, mesma lógica YAGNI de
`GET /api/v1/commands/{number}/orders`. Implementado em `OrderController`
(mesma classe do FARELO-057/058), reaproveitando
`OrderRepository`/`OrderService` já existentes — novo método
`OrderRepository#findByStatusInOrderByCreatedAtAsc`, com o mesmo `JOIN
FETCH o.command` das demais queries deste repositório (evita
`LazyInitializationException`, lição do FARELO-055 documentada acima).

**Nota de domínio**: esta consulta é conceitualmente do domínio `kitchen`
(ver tabela no topo deste documento, ainda "Não iniciado"), mas ficou em
`ordering` por enquanto — criar um pacote `kitchen` só para um único
endpoint de leitura seria uma abstração prematura (AGENTS.md: "não criar
abstrações prematuras"), e reaproveitar `OrderController`/`OrderService`
existentes evita duplicar a lógica de fetch/serialização de `Order`+
`OrderItem` que `GET /api/v1/commands/{number}/orders` já resolveu
(FARELO-055). Deve migrar para um domínio `kitchen` dedicado quando ele
ganhar mais responsabilidades reais (impressão de comanda de cozinha,
notificações à cozinha, etc) que justifiquem o pacote próprio.

- **`OrderCreatedEvent`** (FARELO-060): record de payload do evento de
  outbox `OrderCreated` (`orderId`, `commandNumber`, `items` — cada um com
  `productId`/`quantity`/`unitPrice`, o mesmo snapshot de preço do
  `OrderItem`). Fica em `ordering`, não no pacote `outbox` (ver seção
  "Outbox" abaixo) — o formato do payload de um evento pertence ao domínio
  que o produz, não à infraestrutura genérica que só sabe transportá-lo.
  `OrderService.create(...)` publica esse evento via `OutboxPublisher` na
  mesma transação da criação do pedido — primeira integração real do
  mecanismo de outbox, provando-o ponta a ponta contra um caso de uso já
  existente. Payload deliberadamente simples: ainda não há nenhum
  consumidor real (impressão/notificação/estoque são epics futuros, não
  iniciados), então isto só prova o mecanismo de publicação, não é um
  contrato de evento fechado para consumidores futuros.

**Fechamento do ciclo de vida do pedido** (`markAsDelivered`/
`markAsCancelled`, `OrderService`): follow-up sem número FARELO explícito
no roadmap original — `OrderStatus` já tinha `DELIVERED`/`CANCELLED`
desde FARELO-050, mas nenhuma transição para eles existia até aqui, então
um pedido ficava travado em `READY` para sempre (mesmo padrão de
follow-up não ticketado que já aconteceu com FARELO-019).

- `markAsDelivered`: `READY` → `DELIVERED`, único estado de origem —
  mesmo formato de `markAsPreparing`/`markAsReady` (FARELO-057/058),
  reaproveita o helper privado `transition(...)` sem alteração de
  comportamento.
- `markAsCancelled`: `CANCELLED`, a partir de qualquer status
  não-terminal (`CREATED`, `CONFIRMED`, `PREPARING` ou `READY`).
  Diferente das transições de único estado de origem acima, isso não se
  encaixava no helper `transition(orderId, requiredCurrentStatus,
  targetStatus)` existente. **Decisão**: sobrecarregar `transition(...)`
  com uma variante `transition(orderId, Set<OrderStatus>
  validCurrentStatuses, targetStatus)` que checa pertencimento ao invés
  de igualdade; a variante de status único vira um wrapper fino que chama
  a nova com `Set.of(requiredCurrentStatus)`. Alternativa descartada: uma
  segunda cópia do método só para o caso de múltiplos estados, que
  duplicaria a lógica de fetch/validação/histórico/save já existente.
  Resultado: `markAsPreparing`/`markAsReady`/`markAsDelivered` continuam
  chamando a mesma assinatura de sempre, sem nenhuma mudança nelas.

  `DELIVERED` e `CANCELLED` são estados terminais — nenhuma transição sai
  deles. Cancelar um pedido já `DELIVERED`, ou cancelar um já
  `CANCELLED`, é rejeitado como qualquer outra transição inválida
  (`OrderInvalidTransitionException`/`ORDER_INVALID_TRANSITION`,
  reaproveitado sem alteração).

  Ambas gravam sua entrada em `OrderStatusHistory`, mesmo mecanismo do
  FARELO-056/057/058.

  **Efeito no `GET /api/v1/orders` (fila do KDS)**: nenhuma mudança
  necessária — `QUEUE_STATUSES` (`CREATED`/`CONFIRMED`/`PREPARING`) nunca
  incluiu `DELIVERED`/`CANCELLED`, então um pedido que chega a qualquer
  um dos dois já desaparece da fila automaticamente, mesma forma como
  `READY` já desaparecia. Confirmado com teste
  (`kitchenQueueExcludesDeliveredAndCancelledOrders`) em vez de assumido.

  **Transições válidas de `OrderStatus`** (visão completa do ciclo de
  vida até aqui):

  ```
  CREATED → PREPARING → READY → DELIVERED
     \          \          \
      \          \          +--→ CANCELLED
       \          +-------------→ CANCELLED
        +------------------------→ CANCELLED
  ```

  `CONFIRMED` permanece reservado no enum sem nenhuma transição de/para
  ele em nenhum endpoint real — só alcançável manipulando o registro
  diretamente (ex: em teste) — mas, por ser não-terminal, já está incluído
  como origem válida de `markAsCancelled` acima; cabe a um ticket futuro
  decidir se/quando ele ganha uma transição real. `DELIVERED` e
  `CANCELLED` são os dois únicos estados terminais — nenhuma seta sai
  deles.

  Ver `docs/api.md` para os endpoints `POST /api/v1/orders/{id}/deliver`
  e `POST /api/v1/orders/{id}/cancel`.

## Outbox (infraestrutura cross-cutting)

Pacote: `com.farelo.api.outbox`. **Não é um domínio de negócio** —
propositalmente ausente da tabela de domínios no topo deste documento. É a
fundação do Transactional Outbox, o princípio arquitetural já registrado em
`docs/architecture.md`: "Eventos internos de domínio via Transactional
Outbox + Worker, antes de introduzir um broker externo (sem Kafka neste
momento)."

> **Nota de reconciliação de numeração** (ver `docs/PROMPT_MESTRE.md`,
> Epic 5): o roadmap original define FARELO-060 (`OutboxEvent`), FARELO-061
> (publicar `ORDER_CREATED` na mesma transação), FARELO-062 (worker básico)
> e FARELO-063 (processar eventos com idempotência) como quatro tickets
> separados. Por uma perda de contexto de sessão, esses três primeiros
> foram implementados juntos num único ticket abaixo rotulado "FARELO-060"
> — cobre o escopo real de FARELO-060/061/062. As referências a
> "FARELO-061" (retenção/limpeza) e "FARELO-062" (métricas) abaixo NÃO são
> os tickets reais de mesmo número no roadmap — são follow-ups sem número
> oficial que o líder priorizou (mesma categoria dos follow-ups de
> DELIVERED/CANCELLED e de nome/telefone do cliente, documentados em
> `docs/api.md`). O FARELO-063 real (idempotência) ainda não tinha sido
> feito até esta nota; ver entrada correspondente abaixo assim que
> implementado.

- **`OutboxEvent`** (FARELO-060): entidade JPA — `id` (UUID, mesma
  estratégia das demais entidades), `aggregateType` (`varchar`, ex:
  `"Order"`), `aggregateId` (UUID), `eventType` (`varchar`, ex:
  `"OrderCreated"`), `payload` (mapeado como `String` com
  `@JdbcTypeCode(SqlTypes.JSON)` — suporte nativo do Hibernate 6 a JSON,
  sem biblioteca extra — escrito como JSON já serializado pelo
  `OutboxPublisher`, coluna `jsonb`), `status` (enum `OutboxEventStatus`:
  `PENDING`/`PROCESSED`, `@Enumerated(EnumType.STRING)`, default
  `PENDING`), `createdAt` (UTC), `processedAt` (nullable, UTC, setado por
  `markProcessed()`). Tabela `outbox_event` criada pela migration
  `V10__create_outbox_event_table.sql`, `status` na mesma convenção
  `VARCHAR` + `CHECK` de `command.status`/`orders.status`, com índice em
  `status` (o worker faz polling por `PENDING`).
- **`OutboxPublisher`**: serviço com um método
  `publish(aggregateType, aggregateId, eventType, payload)` que serializa
  `payload` via Jackson (`ObjectMapper`, já disponível via
  `spring-boot-starter-web`) e grava um `OutboxEvent` `PENDING`.
  **Precisa ser chamado dentro da mesma transação da escrita de domínio
  que está registrando** — é isso que torna o outbox "transacional": ou
  tudo comita junto, ou nada comita. Essa exigência é reforçada em tempo de
  execução, não só documentada: `publish` usa
  `@Transactional(propagation = Propagation.MANDATORY)`, então chamá-lo
  fora de uma transação já ativa falha imediatamente
  (`IllegalTransactionStateException`) em vez de persistir uma linha sem
  garantia nenhuma de atomicidade.
- **`OutboxWorker`**: `@Component` com um método `@Scheduled` (a cada 5s)
  que busca eventos `PENDING` (`OutboxEventRepository.
  findByStatusOrderByCreatedAtAsc`, FIFO) e, por enquanto, apenas loga e
  marca cada um como `PROCESSED` — **stub deliberado**: não existe nenhum
  consumidor real ainda (impressão/notificação/estoque são epics futuros,
  ainda não iniciados). Ponto de extensão futuro documentado no javadoc da
  classe: quando um consumidor real aparecer, ele se pluga em torno desse
  loop, provavelmente via um handler registrado por `event_type` —
  mecanismo de dispatch deliberadamente não decidido/construído agora
  (YAGNI, não há um segundo consumidor ainda para desenhar contra).
  Requer `@EnableScheduling` em `FareloApiApplication` (primeiro uso de
  `@Scheduled` no projeto).
- **`OutboxRetentionCleaner`** (FARELO-061): resolve uma lacuna
  operacional deixada pelo FARELO-060 — `OutboxWorker` marca eventos como
  `PROCESSED`, mas nunca os remove, então `outbox_event` cresceria sem
  limite para sempre, mesmo depois de cada linha já ter cumprido seu
  papel. `@Component` próprio, separado de `OutboxWorker` — decisão
  deliberada, documentada no javadoc da classe: a responsabilidade do
  worker é "notar trabalho novo e fazê-lo" (poll de `PENDING` +
  dispatch); a do cleaner é limpeza não relacionada de linhas que o
  worker já terminou, sem estado ou fluxo de controle compartilhado com
  ele, e com uma cadência natural bem mais espaçada (a cada hora — a
  correção da retenção não depende de rodar tão frequentemente quanto o
  poll de 5s do worker, que existe para manter baixa a latência de
  dispatch quando um consumidor real existir). Um segundo método
  `@Scheduled` dentro de `OutboxWorker` funcionaria também, mas faria
  aquela classe fazer duas coisas não relacionadas sem benefício real.

  Deleta, via `OutboxEventRepository.deleteByStatusAndProcessedAtBefore`
  (`@Modifying @Query` com `DELETE` JPQL em bulk — não um método derivado
  de delete do Spring Data, que carregaria e removeria cada entidade
  individualmente, o que não escala bem para um job de limpeza cujo
  propósito inteiro é reclamar linhas que podem ter se acumulado), toda
  linha `PROCESSED` cujo `processedAt` seja mais antigo que o período de
  retenção configurado — **nunca** uma linha `PENDING`, não importa a
  idade: deletar uma seria perda de dado real (é exatamente o dado que o
  outbox existe para proteger até um consumidor processá-la).

  Período de retenção configurável via `outbox.retention.processed-days`
  (`@Value`, `application.yml`), **default 7 dias** — tempo suficiente
  para investigação operacional de um evento processado recentemente
  (ex: "esse evento do pedido X realmente foi drenado, e quando?"), curto
  o bastante para a tabela não acumular um histórico efetivamente sem
  limite de linhas que ninguém mais lê. Nenhum consumidor/relatório
  depende hoje de histórico de outbox além dessa janela — se algum
  aparecer no futuro, deve ler de um lugar próprio para isso (ex: futuro
  domínio `audit`, já listado na tabela de domínios no topo deste
  documento), não depender desta tabela como log de longo prazo.

  Migration `V11__add_outbox_event_processed_at_index.sql` adiciona um
  índice composto `(status, processed_at)` — suporta o filtro `status =
  'PROCESSED' AND processed_at < :cutoff` da query de limpeza, sem
  substituir o índice de `status` sozinho do V10 (que continua servindo o
  poll de `PENDING` do worker, uma query diferente).

- **`OutboxMetrics`** (FARELO-062): resolve uma lacuna operacional deixada
  pelo FARELO-060/061 — nada, de fora do processo, indicava se
  `OutboxWorker` estava saudável; se ele parasse de rodar (crash, deploy
  quebrado etc), eventos `PENDING` simplesmente se acumulariam em
  silêncio, sem nenhum sinal externo. `@Component` que registra dois
  `Gauge`s do Micrometer (`io.micrometer.core.instrument`, já disponível
  via `spring-boot-starter-actuator`) no `MeterRegistry` injetado, ambos
  escopados a `OutboxEventStatus.PENDING`:
  - `outbox.events.pending`: contagem de linhas `PENDING`
    (`OutboxEventRepository#countByStatus`, query de contagem derivada do
    Spring Data).
  - `outbox.events.pending.oldest.age` (segundos, `baseUnit("seconds")`):
    idade da linha `PENDING` mais antiga — o sinal mais direto de "o
    worker parou de drenar" (um worker saudável mantém esse valor perto do
    seu intervalo de poll de 5s, independente da profundidade da fila; um
    worker morto deixa esse valor crescer sem limite). Retorna `0` quando
    a fila está vazia, em vez de um valor ausente/negativo — o gauge
    sempre tem uma leitura bem definida. Usa
    `OutboxEventRepository#findOldestCreatedAtByStatus`, uma query
    `MIN(created_at)` própria — deliberadamente **não** reaproveita
    `findByStatusOrderByCreatedAtAsc` (que já existe e serviria, lendo só
    o primeiro elemento): esse método carrega toda a lista de eventos
    `PENDING` (payload incluído) para descartar todos menos um, e um
    gauge é reavaliado a cada scrape de métricas — exatamente o cenário
    mais caro é justo o que esta métrica existe para detectar (fila
    `PENDING` crescendo sem limite).

  `Gauge`, não `Counter` — ambos os valores sobem e descem com a
  profundidade da fila, nunca um total cumulativo.

  Endpoints Micrometer/Actuator ficam desligados por padrão no Spring
  Boot — `metrics` foi adicionado a
  `management.endpoints.web.exposure.include` no `application.yml` (antes
  só tinha `health`), então os gauges ficam acessíveis em
  `/actuator/metrics/outbox.events.pending` e
  `/actuator/metrics/outbox.events.pending.oldest.age`.

  Nenhuma dependência nova — `micrometer-core` já vem transitivamente via
  `spring-boot-starter-actuator`, que o projeto já usa para
  `/actuator/health`.

- **Idempotência sob workers concorrentes** (FARELO-063 — o ticket real
  desse número no roadmap, ver nota de reconciliação no topo desta seção):
  fecha a lacuna que a nota apontava. Até aqui, `OutboxWorker.
  processPendingEvents()` buscava **todos** os eventos `PENDING` via
  `findByStatusOrderByCreatedAtAsc` (sem lock, sem limite), processava em
  memória e só depois salvava tudo como `PROCESSED`. Hoje só existe uma
  instância da aplicação rodando, então isso nunca gerou um bug real — mas
  nada garante que isso continue verdade para sempre: se o backend algum
  dia escalar horizontalmente (múltiplas instâncias atrás de um load
  balancer, ou simplesmente dois schedulers concorrentes por qualquer
  motivo), duas instâncias rodando o poll de 5s ao mesmo tempo poderiam
  ambas selecionar os **mesmos** eventos `PENDING` antes de qualquer uma
  comitar, processando (e logando) o mesmo evento duas vezes — exatamente o
  tipo de "operação crítica" que o prompt mestre (seção 16) exige ser
  idempotente.

  **Solução**: nova query `OutboxEventRepository#findPendingForUpdateSkipLocked`
  — `SELECT ... WHERE status = :status ORDER BY created_at ASC LIMIT :limit
  FOR UPDATE SKIP LOCKED`, nativa (`@Query(nativeQuery = true)`), porque
  JPQL não tem uma palavra-chave `SKIP LOCKED` (Hibernate tem um truque
  não-documentado via `@Lock(PESSIMISTIC_WRITE)` + hint
  `jakarta.persistence.lock.timeout=-2`, mas isso depende de um detalhe de
  implementação interno do Hibernate, não de uma API estável — SQL nativo
  explícito é mais direto e portável entre versões). Cada chamada trava
  (`FOR UPDATE`) as linhas que seleciona pelo resto da sua transação;
  qualquer outra transação concorrente rodando a mesma query
  automaticamente pula (`SKIP LOCKED`) linhas já travadas em vez de
  bloquear nelas ou selecioná-las também. Isso não impede duas instâncias
  de fazerem *poll* ao mesmo tempo — impede que elas selecionem as
  **mesmas** linhas, o que é exatamente a garantia necessária: sem
  seleção compartilhada, não há processamento duplicado possível.
  `OutboxWorker.processPendingEvents()` passou a usar essa query em vez da
  antiga (que continua existindo no repositório, ainda coberta por
  `OutboxEventRepositoryIntegrationTests` — não removida, só deixou de ser
  usada pelo worker), e passou a **retornar** o lote processado (antes
  `void`) — só para dar aos testes algo concreto para verificar; o
  disparo real via `@Scheduled` ignora o valor de retorno.

  **Limite de lote**: `outbox.worker.batch-size` (`@Value`,
  `application.yml`, default 100) — bound de quantas linhas uma única
  chamada pode travar/processar, para uma execução nunca segurar um
  número ilimitado de locks (nem gastar um tempo ilimitado numa única
  transação) não importa o tamanho da fila `PENDING`. Uma fila mais funda
  que o batch simplesmente é drenada em mais ciclos de poll (a cada 5s),
  cada um travando até `batchSize` linhas a mais.

  **Teste de idempotência**: `OutboxEventRepositoryConcurrencyIntegrationTests`
  prova a garantia de forma **determinística**, não por corrida de
  wall-clock (duas threads disparadas "ao mesmo tempo" e torcer para que
  se sobreponham seria inerentemente flaky). Em vez disso, duas
  transações são controladas manualmente via `TransactionTemplate` e dois
  `CountDownLatch`: a transação A seleciona (e trava) todos os eventos
  `PENDING`, sinaliza que já travou e só então bloqueia (transação ainda
  aberta, sem commit); só depois que A confirma que está segurando os
  locks é que a transação B roda a mesma query — seu resultado é
  capturado e verificado **antes** de A poder comitar, então não existe
  janela onde A já teria liberado os locks. Resultado esperado e
  verificado: `A` contém todos os eventos semeados pelo teste; `B` não
  contém nenhum deles (nenhum double-select possível); depois que ambas
  comitam, todos os eventos semeados terminam `PROCESSED` exatamente uma
  vez. Roda contra o Postgres real do Testcontainers (`AbstractIntegrationTest`),
  não H2/mock — lock de linha é comportamento real do motor de banco, sem
  equivalente confiável em memória; é exatamente o tipo de bug que só
  aparece contra o banco de verdade. `OutboxWorkerBatchSizeIntegrationTests`
  cobre separadamente o limite de lote (contexto Spring próprio, com
  `outbox.worker.batch-size=3` via `@TestPropertySource`, para não forçar
  os outros testes do worker — que dependem do default de 100 — a
  conviver com esse override).

  **`outbox.worker.poll-interval-ms`** (default 5000, mesmo `@Value` do
  `batch-size`): controla o `fixedDelayString` do `@Scheduled`, em vez do
  literal `5000` fixo que existia antes. Motivo real, não hipotético: como
  `OutboxWorkerBatchSizeIntegrationTests` sobe um `@SpringBootTest`
  completo (não um slice), o `@Scheduled` de verdade continuava rodando no
  fundo enquanto o teste semeava sua própria fixture e fazia sua chamada
  explícita — e chegou a "roubar" um evento semeado antes da chamada
  explícita, quebrando a contagem exata esperada pelo teste. Esse teste
  agora sobrescreve `poll-interval-ms` para 1 hora via
  `@TestPropertySource`, tornando essa corrida impossível em vez de só
  improvável — os demais testes do worker continuam no default de 5s.

**Direção de dependência**: domínios de negócio (ex: `ordering`) dependem
de `outbox` para publicar eventos; `outbox` nunca depende de volta para um
pacote de domínio — o formato de cada payload (ex: `OrderCreatedEvent`)
vive no domínio que o produz, não aqui.
