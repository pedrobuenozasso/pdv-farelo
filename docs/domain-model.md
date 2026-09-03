# Domain Model — Farelo OS

Este documento é preenchido incrementalmente à medida que cada domínio é implementado.

## Domínios previstos

| Domínio | Responsabilidade | Status |
|---|---|---|
| `security` | `User` (contas de quem opera o sistema), futura autenticação/RBAC | Em andamento |
| `catalog` | `Product`, `Category` — fonte única de verdade do cardápio | Em andamento |
| `customer` | Dados do cliente coletados no fluxo de pedido (nome, WhatsApp) | Snapshot simples em `orders` (ver seção `ordering`) — domínio próprio ainda **não iniciado** |
| `command` | `Command` (comanda) e seu ciclo de vida | Em andamento |
| `ordering` | `Order`, `OrderItem`, snapshot de preço, histórico de status | Em andamento |
| `kitchen` | KDS — visualização e transição de status de preparo | Não iniciado — `GET /api/v1/orders` (fila da cozinha, FARELO-059) já existe, mas ficou em `ordering` por enquanto; ver nota na seção `ordering` abaixo |
| `printing` | `Printer`, `PrintJob`, integração com Edge Agent | Em andamento |
| `inventory` | `Ingredient`, `Recipe`, `RecipeItem` (ficha técnica), `InventoryMovement` (ledger) | Em andamento |
| `recipe` | Ficha técnica de produtos — **implementada dentro do pacote `inventory`** (`Recipe`/`RecipeItem`, FARELO-091/092), não como pacote próprio; linha mantida por rastreabilidade com o roadmap original | Em andamento (ver `inventory`) |
| `notification` | `Notification`, adapter WhatsApp Cloud API | Em andamento |
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
  propositalmente: sem `fiscalProfileId` (FARELO-151/Epic 11), sem
  receita/estoque.

  **`productionStation`** (FARELO-073): enum `ProductionStation` (`BAR`,
  `KITCHEN` — `@Enumerated(EnumType.STRING)`, mesma convenção de
  `CommandStatus`/`OrderStatus`), indicando qual estação física prepara o
  produto — usado para rotear tickets de impressão por setor quando um
  pedido é criado (prompt mestre seção 12, Epic 6/"Impressão por setor": 2
  Cappuccino + 1 Coca-Cola vão para o ticket do `BAR`, 1 Croissant vai para
  o ticket da `KITCHEN`). **Escopo deste ticket é só o campo** — a
  separação real de `PrintJob`s por estação é FARELO-074.

  **Por que só `BAR`/`KITCHEN`**: são os dois valores de exemplo dados
  literalmente no prompt mestre, e já cobrem a divisão natural do fluxo de
  produção de uma cafeteria — bebidas/itens de balcão (`BAR`) vs. comida
  que exige preparo/cozimento (`KITCHEN`). Nenhum terceiro valor foi
  adicionado sem uma necessidade concreta (AGENTS.md: não criar
  abstrações prematuras); estender o enum no futuro custa uma migration de
  follow-up para ampliar o `CHECK` constraint (mesmo trade-off já aceito
  para `CommandStatus`/`OrderStatus`, ver `V5__create_command_table.sql`).

  **Nullable, diferente de `availableOnMenu`/`availableOnPos`**: aqueles
  dois booleanos têm um default inequívoco e seguro (`true` — um produto
  novo deveria ser visível em todo lugar até dizerem o contrário).
  `productionStation` não tem esse default: fabricar um (ex: sempre
  `KITCHEN`) estaria silenciosamente errado para muitos produtos (um suco
  não é obviamente `BAR` nem `KITCHEN` por alguma regra universal) e, uma
  vez que FARELO-074 passar a rotear tickets por este campo, um default
  errado desviaria um ticket impresso sem ninguém ter escolhido isso.
  `null` significa "ainda não atribuído" — a equipe define explicitamente
  por produto.

  Coluna `production_station` adicionada pela migration
  `V13__add_product_production_station_column.sql`
  (`VARCHAR(20)`, sem `NOT NULL`, com `CHECK` espelhando os valores do
  enum — mesma convenção `VARCHAR` + `CHECK` de `command.status`/
  `orders.status`). **Sem backfill**: produtos existentes ficam `NULL`
  ("sem estação atribuída") em vez de receber um valor fabricado — mesmo
  raciocínio da decisão de nullability acima, agora aplicado às linhas já
  existentes na tabela. Nos DTOs (`ProductRequest`/`ProductUpdateRequest`
  em `catalog.web`), o campo é opcional em ambos — inclusive no `PUT`
  (diferente de `active`/`availableOnMenu`/`availableOnPos`, que são
  `@NotNull` lá): como `null` é ele mesmo um valor legítimo e não um
  placeholder de "campo esquecido", uma substituição completa via `PUT`
  precisa poder enviá-lo para limpar uma estação já atribuída. Sem o
  gotcha de primitivo-vs-wrapper do Jackson que motivou `Boolean` em vez de
  `boolean` nesses três campos (documentado em `ProductUpdateRequest`) —
  um enum já é tipo referência, então um campo ausente no JSON simplesmente
  vira `null` sem risco de default silencioso.

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
  `@Enumerated(EnumType.STRING)`, default `CREATED`), `customerName`/
  `customerPhone` (ambos `String`, opcionais — ver nota dedicada logo
  abaixo), `createdAt`/`updatedAt` (UTC, mesmo padrão dos demais domínios).
  Tabela criada pela migration `V7__create_order_table.sql`, com FK para
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

  **`customerName`/`customerPhone`**: follow-up sem número FARELO explícito
  no roadmap original (mesmo padrão dos follow-ups não ticketados que já
  aconteceram com FARELO-019 e com o fechamento do ciclo de vida do pedido,
  ambos documentados nesta seção) — o formulário de checkout do cardápio QR
  (`apps/web`, FARELO-045) já coletava nome e telefone do cliente, mas esses
  dados nunca eram enviados ao backend nem persistidos em lugar nenhum
  (ver nota antiga em `docs/api.md`, agora removida). Ambos os campos são
  `String` nullable, adicionados pela migration
  `V12__add_order_customer_columns.sql` (`customer_name VARCHAR(120)`,
  `customer_phone VARCHAR(30)`).

  **Deliberadamente um snapshot simples em `Order`, não um domínio
  `customer` próprio** (ver linha `customer` na tabela de domínios no topo
  deste documento): não existe conta de cliente, histórico por telefone, ou
  fidelidade hoje — construir um domínio inteiro (entidade própria,
  repositório, consulta por telefone) para isso agora seria uma abstração
  prematura (AGENTS.md). Mesmo espírito do snapshot de preço em
  `OrderItem.unitPrice`: o valor é capturado uma vez, no momento da criação
  do pedido, e nunca mais muda. Diferente de `unitPrice`, porém, a entidade
  não expõe setters para esses dois campos — só getters, o mesmo padrão de
  imutabilidade pós-criação de `createdAt` — já que, ao contrário de
  `OrderItem` (FARELO-051, que ganhou setters só por nenhum endpoint editar
  ainda), aqui já se sabe de início que não há nenhum caso de uso de edição
  cogitado.

  **Quando isso deveria virar um domínio `customer` de verdade**: se o
  produto ganhar conta de cliente (login/cadastro), histórico de pedidos
  por cliente, ou programa de fidelidade — qualquer coisa que precise
  consultar/agregar por identidade do cliente ao longo de múltiplos
  pedidos/comandas, não só exibir o nome/telefone de um pedido específico
  para quem está atendendo.

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

## printing

Pacote: `com.farelo.api.printing`.

- **`Printer`** (FARELO-070): entidade JPA — `id` (UUID, mesma estratégia
  de `Category`/`Product`/`Command`), `name` (identificação legível do
  dispositivo físico, ex: "Impressora Bar"), `active` (default `true`,
  mesmo padrão de `Category`/`Product`), `createdAt`/`updatedAt`
  (`OffsetDateTime` em UTC, mesmo padrão dos demais domínios). Tabela
  criada pela migration `V13__create_printer_table.sql`. Sem endpoint REST
  ainda (mesmo padrão minimalista do primeiro ticket de outros domínios,
  ex: `Category`/FARELO-010).

  Escopo deliberadamente restrito: sem `productionStation` aqui — isso é
  escopo de FARELO-073/074, e pertence a `Product` (roteamento de
  impressão por setor de produção), não a `Printer`. Primeira peça do
  Epic 6 (Impressão, ver `docs/PROMPT_MESTRE.md` seções 10-12): pedidos
  vão gerar `PrintJob`s (FARELO-071/072) — nunca impressos diretamente da
  transação HTTP — que um `Farelo Edge Agent` separado (FARELO-075+, fora
  do escopo do backend) busca e imprime.

  `PrinterRepository` (Spring Data JPA), sem métodos de consulta próprios
  ainda além do CRUD padrão do `JpaRepository` — mesmo formato de
  `CategoryRepository`.

- **`PrintJob`** (FARELO-071): entidade JPA — `id` (UUID, mesma estratégia
  dos demais domínios), `order` (`@ManyToOne` obrigatório para `Order` —
  todo `PrintJob` existe para imprimir o conteúdo de um pedido específico,
  mesmo formato de `OrderItem.order`), `content` (snapshot do que precisa
  ser impresso — número da comanda e nomes/quantidades dos itens — ver
  decisão de desenho abaixo), `status` (enum `PrintJobStatus`: `PENDING`,
  `PRINTED`, `FAILED` — literal da seção 10 do prompt mestre,
  `@Enumerated(EnumType.STRING)`, default `PENDING`), `createdAt`/
  `updatedAt` (`OffsetDateTime` em UTC, mesmo padrão dos demais domínios).
  Tabela criada pela migration `V15__create_print_job_table.sql`, com FK
  `NOT NULL` para `orders(id)` e `status` na mesma convenção `VARCHAR` +
  `CHECK` de `command.status`/`orders.status`/`outbox_event.status`.

  **Escopo deste ticket**: só a entidade em si. Nada cria um `PrintJob`
  automaticamente quando um `Order` é criado ainda — isso é FARELO-072
  (ver entrada dedicada logo abaixo). Nada roteia um job para um `Printer`
  específico ainda — isso é roteamento por `productionStation`
  (FARELO-073/074, seção 12). Sem endpoint REST (mesmo padrão minimalista
  do primeiro ticket de outros domínios, ex: `Printer`/FARELO-070).

  **Decisão de desenho 1 — referencia `Order`, não `Printer`**: `order` é
  `@ManyToOne` obrigatório, mesmo formato de `OrderItem.order` — um
  `PrintJob` sempre existe para imprimir um pedido específico. Não
  referencia `Printer`: qual impressora física recebe o job é uma decisão
  de roteamento (por `productionStation`, por item, seção 12) que ainda
  não existe — modelar isso agora seria adivinhar um desenho que
  FARELO-073/074 ainda não decidiu. Um pedido com itens de estações
  diferentes pode inclusive virar mais de um ticket impresso nesse
  momento futuro; acoplar `PrintJob` a um único `Printer` hoje atrapalharia
  isso.

  **Decisão de desenho 2 — `content` é snapshot congelado, não referência
  viva a `Order`**: `content` guarda o que precisa ser impresso (número da
  comanda, nome/quantidade de cada item) capturado no momento da criação
  do job — não é só a FK `order` para quem consome (`Edge Agent`) buscar
  os itens depois via API. Mesmo raciocínio do snapshot de preço em
  `OrderItem.unitPrice` e do snapshot de itens dentro de
  `OrderCreatedEvent`: o que foi pedido no momento da impressão não pode
  mudar depois só porque, por exemplo, um produto foi renomeado ou um
  `OrderItem` foi editado por alguma feature futura — uma comanda impressa
  já é, ela mesma, um registro histórico físico, e precisa refletir a
  realidade de quando foi enfileirada, não o que o banco disser sempre que
  o Edge Agent conseguir drenar a fila (que pode ser segundos depois, ou
  muito mais se uma impressora estiver fora do ar).

  Há um segundo motivo, independente do snapshot: o prompt mestre (seção
  11) é explícito que o Edge Agent "nunca deve possuir regra de negócio de
  pedidos — é apenas infraestrutura de dispositivos". Se `PrintJob`
  guardasse só a referência a `Order`, quem lê o job (o Edge Agent, ou
  algo que prepara os dados para ele) precisaria saber buscar o pedido,
  percorrer seus itens e formatar um ticket — regra de negócio de pedido
  vazando para dentro da infraestrutura de dispositivo. Embutir o snapshot
  já pronto mantém o lado do Edge Agent burro: ler `content`, imprimir,
  reportar o resultado.

  **Armazenamento de `content`**: mapeado como `String` com
  `@JdbcTypeCode(SqlTypes.JSON)`, coluna `jsonb` — a mesma convenção já
  usada por `OutboxEvent.payload` para "dado estruturado de snapshot cujo
  formato esta classe não precisa opinar": quem constrói um `PrintJob`
  (FARELO-072) serializa o snapshot (ex: um record pequeno com número da
  comanda e uma lista de nome/quantidade por item) para JSON antes de
  construir esta entidade; a entidade só guarda e devolve a string como
  está.

  **`markPrinted()`/`markFailed()`**: únicos jeitos de mudar `status` depois
  da criação (sem setter público) — mesmo espírito de
  `OutboxEvent.markProcessed()`. Nenhuma validação de transição ainda (ex:
  rejeitar sair de um estado já terminal): não existe nenhum chamador real
  desses métodos ainda (isso é FARELO-072+), então adicionar essa guarda
  agora seria adivinhar uma regra sem um caso de uso real para testar
  contra. A transição de volta `FAILED`→`PENDING` (retry) também não
  existia neste ticket — a seção 10 do prompt mestre menciona "permitindo
  retry", mas o mecanismo real de retry (quem decide re-enfileirar, com que
  critério) só foi desenhado no FARELO-079 (ver entrada dedicada mais
  abaixo: `PrintJob.retry()` + `PrintJobService.retry(UUID)`).

  `PrintJobRepository` (Spring Data JPA), sem métodos de consulta próprios
  ainda além do CRUD padrão — mesmo formato minimalista de
  `PrinterRepository` — até FARELO-072 abaixo, que adiciona
  `findByOrder(Order)`.

- **Criar `PrintJob` em `ORDER_CREATED`** (FARELO-072): fecha a lacuna
  deixada pelo FARELO-071 — o `OutboxWorker` (ver seção "Outbox" abaixo)
  ganha aqui seu **primeiro consumidor real**: ao drenar um evento
  `OrderCreated`, ele cria um `PrintJob` `PENDING` para aquele pedido.
  Novas classes em `printing`: `PrintJobContent` (record — `commandNumber`
  + lista de `Item(productName, quantity)`, o shape exato já usado por
  `PrintJobRepositoryIntegrationTests` desde o FARELO-071) e
  `PrintJobService` (`createForOrder(UUID orderId)` — busca o pedido,
  monta o `PrintJobContent`, serializa via Jackson e salva o `PrintJob`).
  Mecanismo de dispatch e tratamento de falha ficam documentados no
  javadoc de `OutboxWorker` (ver seção "Outbox" abaixo, entrada do próprio
  `OutboxWorker`) — aqui só as decisões específicas de `printing`:

  **Decisão — de onde vem o conteúdo (nome dos itens)**: `OrderCreatedEvent`
  (o payload do evento) só carrega `productId`, não o nome do produto —
  mas `PrintJob.content` precisa de um nome legível para virar um ticket.
  Duas opções cogitadas: (a) desserializar o payload do evento e buscar os
  nomes dos produtos em lote (um novo método de "buscar produtos por
  lista de ids", que não existe hoje); ou (b) ignorar o payload para esse
  fim e buscar `Order`+`OrderItem`s direto do banco pelo `aggregateId`
  (o id do próprio pedido), reaproveitando a query `JOIN FETCH product`
  que `OrderItemRepository#findByOrder` já expõe (a mesma que
  `CommandOrdersController`/a fila da cozinha já usam para evitar
  `LazyInitializationException` lendo `item.getProduct().getName()`).
  Escolhida a opção (b): reaproveita busca já existente e testada, em vez
  de duplicá-la atrás de um método novo que mais ninguém precisa ainda —
  e, como efeito colateral, `PrintJobService` nunca precisou conhecer o
  formato do payload do evento, só o `aggregateId`, então `OrderCreatedEvent`
  continua livre para mudar de forma independente do que a impressão
  precisa dele. `PrintJobRepository` ganhou `findByOrder(Order)` (derivado,
  Spring Data) só para permitir que os testes confirmem qual `PrintJob`
  foi criado para um pedido.

  **Decisão — nenhum split por estação ainda** (revisado no FARELO-074,
  ver entrada dedicada logo abaixo): neste ticket, um `PrintJob` por
  pedido, não um por `productionStation` — `Product.productionStation`
  (FARELO-073) ainda não era lido por nada em `printing`.

  Teste de integração: `OutboxWorkerPrintJobIntegrationTests` (pacote
  `outbox`, mesmo padrão de `OutboxWorkerIntegrationTests`) cria um pedido
  de verdade via `OrderService.create(...)`, chama
  `OutboxWorker#processPendingEvents()` explicitamente e confirma um
  `PrintJob` `PENDING` com `content` correto (nomes/quantidades dos dois
  itens, número da comanda) — mais um segundo teste que prova o
  comportamento de falha documentado no `OutboxWorker` (evento com
  `aggregateId` que não corresponde a nenhum pedido: a exceção propaga,
  o lote inteiro reverte, o evento permanece `PENDING`). Foi este teste
  (junto com `OutboxWorkerBatchSizeIntegrationTests`) que expôs uma
  fragilidade estrutural da suíte — corrigida na raiz em
  `AbstractIntegrationTest`, não aqui: ver a seção "Idempotência sob
  workers concorrentes" abaixo.

- **Separar `PrintJob`s por estação** (FARELO-074): fecha a lacuna deixada
  pelo FARELO-072 — `PrintJobService.createForOrder(...)` agora cria **um
  `PrintJob` por `productionStation` presente no pedido**, não mais um por
  pedido inteiro com todos os itens misturados. Implementa literalmente o
  exemplo do prompt mestre (seção 12): um pedido com 2 Cappuccino + 1
  Coca-Cola (`BAR`) + 1 Croissant (`KITCHEN`) gera dois `PrintJob`s — um
  `BAR` com os dois primeiros itens, um `KITCHEN` com o Croissant.

  **Agrupamento**: os `OrderItem`s já buscados (mesma query `JOIN FETCH
  product` do FARELO-072, `OrderItemRepository#findByOrder`) são agrupados
  por `item.getProduct().getProductionStation()` num `Map<ProductionStation,
  List<OrderItem>>` — **não** via `Collectors.groupingBy`, mas com um loop
  simples e `Map#computeIfAbsent` (`LinkedHashMap`, só por ordem
  determinística de iteração — sem significado de negócio). Motivo
  descoberto durante a implementação, não hipotético: o classificador de
  `Collectors.groupingBy` faz `Objects.requireNonNull(...)` internamente
  desde o Java 9 e lança `NullPointerException` ("element cannot be mapped
  to a null key") quando a função de classificação devolve `null` — exatamente
  o caso de um item cujo produto não tem `productionStation` atribuído (ver
  decisão abaixo). Um `HashMap`/`LinkedHashMap` comum não tem essa restrição:
  `computeIfAbsent(null, ...)` funciona como qualquer outra chave. Um
  `PrintJob` é criado por grupo não vazio; um pedido cujos itens
  pertencem todos à mesma estação (ou todos sem estação) continua gerando
  exatamente 1 `PrintJob` — o agrupamento simplesmente colapsa para uma
  única entrada, sem regressão silenciosa do caso comum.

  **Itens sem estação atribuída** (`productionStation == null` no
  produto, ver nota em `catalog`/FARELO-073): **não são descartados da
  impressão**. Eles são agrupados na própria chave `null` do `Map`, e esse
  grupo também vira um `PrintJob` de verdade — com
  `PrintJobContent.productionStation` explicitamente `null`. Duas
  alternativas descartadas: (a) simplesmente ignorar/pular esses itens —
  rejeitada porque a equipe perderia rastro físico do que precisa ser
  preparado, só um aviso verbal (frágil, exatamente o tipo de coisa que um
  ticket impresso existe para evitar); (b) fabricar uma estação default
  (ex: sempre `KITCHEN`) — rejeitada pela mesma razão que
  `Product.productionStation` já rejeitou um default ao ser criado
  (FARELO-073): um default errado desviaria silenciosamente um ticket sem
  ninguém ter escolhido isso. A escolha feita — grupo próprio, sinalizado
  explicitamente como "sem estação" no `content` — garante que o pedido
  seja impresso (nada é perdido) e que quem lê o ticket (hoje, um humano
  olhando o JSON/futuramente o Edge Agent formatando o papel) perceba
  claramente que aquele item não tem estação definida, em vez de aparecer
  misturado sem explicação num dos outros tickets.

  **`PrintJobContent` ganhou o campo `productionStation`**
  (`ProductionStation`, nullable, mesmo enum de `Product`): antes só
  `commandNumber` + `items`. Cada `PrintJobContent` agora nomeia
  explicitamente a estação daquele grupo, para que um consumidor futuro
  (Edge Agent, FARELO-076+) saiba qual ticket é de qual estação **sem
  precisar inferir a partir dos itens** — inferir colocaria conhecimento de
  negócio (quais produtos pertencem a qual estação) de volta na
  infraestrutura do dispositivo, o que o prompt mestre (seção 11) diz
  explicitamente que o Edge Agent nunca deve ter. Como nenhuma configuração
  de `ObjectMapper` no projeto suprime campos nulos na serialização, o
  grupo "sem estação" serializa como `"productionStation":null` — presença
  explícita do campo no JSON, não uma chave ausente que um leitor futuro
  poderia confundir com "campo que ainda não existia". A formatação do
  ticket físico em si (ex: um cabeçalho de alerta) continua fora de escopo
  aqui — trabalho do Edge Agent.

  **`createForOrder` passou a retornar `List<PrintJob>`** (antes,
  `PrintJob` único) — reflete que agora pode criar mais de um job por
  chamada. Único chamador, `OutboxWorker.dispatch(...)`, já descartava o
  valor de retorno, então não precisou de nenhuma alteração.

  **Sem migration nova**: `productionStation` em `PrintJobContent` vive só
  dentro do JSON de `PrintJob.content` (coluna `jsonb` já existente desde
  o FARELO-071) — nenhuma coluna nova na tabela `print_job`.

  **Escopo**: só a lógica de agrupamento/criação dentro de
  `PrintJobService`/`PrintJobContent`. `OutboxWorker` não foi alterado — a
  chamada a `printJobService.createForOrder(...)` continua idêntica, só o
  que acontece dentro do método mudou.

  **Teste**: `PrintJobServiceIntegrationTests` (pacote `printing`, novo
  arquivo) — deliberadamente chama `PrintJobService.createForOrder(...)`
  diretamente, sem passar por `OutboxWorker#processPendingEvents()` como
  `OutboxWorkerPrintJobIntegrationTests` faz. O escopo deste ticket é
  estritamente a lógica de agrupamento dentro de `PrintJobService`; o
  mecanismo de dispatch/rollback do outbox já está coberto naquele outro
  arquivo e não muda aqui — passar pela camada de outbox de novo só
  adicionaria indireção sem exercitar nada novo. Cobre os três cenários
  pedidos: pedido só com itens de uma estação (continua gerando exatamente
  1 `PrintJob`), pedido com itens de estações diferentes (replica o
  exemplo do prompt mestre — 2 Cappuccino + 1 Coca-Cola no `BAR`, 1
  Croissant na `KITCHEN`, cada `PrintJob` só com os itens certos), e
  pedido com item sem estação atribuída (gera o grupo "sem estação",
  confirmando `productionStation` explicitamente `null` no JSON via
  `JsonNode#isNull()`/`has(...)`, não só ausência de assert).

  **Nota de manutenção de teste**: como `PrintJobServiceIntegrationTests`
  chama `PrintJobService` diretamente (não `OutboxWorker`), o evento de
  outbox `OrderCreated` publicado por `OrderService.create(...)` nunca é
  drenado organicamente nesses testes — o `@AfterEach` chama
  `outboxWorker.processPendingEvents()` explicitamente antes de apagar o
  pedido, para não deixar um `OutboxEvent` `PENDING` órfão (apontando para
  um pedido já deletado) que quebraria um teste não relacionado assim que
  a suíte inteira drenasse a fila real mais tarde
  (`OrderNotFoundException`). Isso cria um segundo conjunto de `PrintJob`s
  via `OutboxWorker`/`PrintJobService` chamado de novo — inofensivo, já
  limpo pelo mesmo `printJobRepository.findByOrder(order).forEach(...)`
  que já existia. Usa o número de comanda semeado 19 — distinto de todos
  os já reservados por outros testes (1-18, 101, 999; ver javadoc da
  própria classe para a lista completa).

- **`GET /api/v1/print-jobs`** (FARELO-076): primeiro endpoint REST do
  domínio `printing` — até aqui não existia nenhum, nem para `PrintJob`
  nem para `Printer`. Novo subpacote `com.farelo.api.printing.web`, mesmo
  padrão de `com.farelo.api.ordering.web`. Existe para o Farelo Edge Agent
  (FARELO-075, `apps/edge-agent`) consultar quais `PrintJob`s ainda
  precisam ser impressos — contrato consumido diretamente por aquele
  ticket, então a forma exata da resposta é deliberadamente estável. Ver
  `docs/api.md` para o endpoint completo.

  **`PrintJobService.listPending()`**: novo método (`@Transactional(readOnly
  = true)`, mesmo raciocínio de `listQueue()`/`listByCommand(...)`) que
  delega para `PrintJobRepository#findByStatusOrderByCreatedAtAsc(PENDING)`
  — status `PENDING`, `createdAt` ASC (FIFO), mesmo padrão já usado por
  `OrderRepository#findByStatusInOrderByCreatedAtAsc` (fila da cozinha) e
  `OutboxEventRepository#findByStatusOrderByCreatedAtAsc`. `PrintJobController`
  passa por esse service em vez de chamar o repository diretamente — mesma
  convenção de todo outro controller do projeto (`ProductController`/
  `OrderController` nunca chamam repository sem passar por um service).
  Sem filtro de status via query param e sem paginação: mesma lógica já
  usada em `GET /api/v1/orders` (a fila da cozinha) — o propósito inteiro
  do endpoint já é "o que está pendente", e o volume de impressão é
  naturalmente baixo.

  **`PrintJobRepository#findByStatusOrderByCreatedAtAsc` usa `JOIN FETCH
  p.order`**: mesma razão de `OrderRepository`'s queries (evitar
  `LazyInitializationException` — lição do FARELO-055) — `PrintJobResponse`
  lê `job.getOrder().getId()` no controller, depois que a transação
  (curta) do repository já fechou.

  **`PrintJobResponse.content` é o objeto desserializado, não a string
  JSON crua**: `PrintJob.content` é armazenado como uma string JSON
  (snapshot serializado — ver decisão de desenho 2 na entrada do
  `PrintJob` acima). Servi-la como está forçaria o Edge Agent a fazer
  double-parsing (uma string de JSON aninhada dentro do JSON da resposta).
  `PrintJobResponse.from(...)` desserializa via Jackson de volta para
  `PrintJobContent` — reaproveitando o mesmo record que `PrintJobService`
  já constrói e persiste, em vez de inventar uma segunda forma (ex: um
  `JsonNode` genérico) para manter sincronizada. `PrintJobContent` vive em
  `com.farelo.api.printing` (não `.web`), então essa é uma referência
  comum entre subpacotes do mesmo domínio — sem problema de cross-package,
  diferente de alcançar um pacote de outro domínio.

  **Teste**: `PrintJobControllerIntegrationTests` (pacote `printing.web`) —
  diferente da fila da cozinha (`OrderControllerIntegrationTests`, que
  escopa asserções aos próprios ids porque `orders` é uma tabela
  compartilhada por muitas outras classes de teste), esta classe limpa a
  própria tabela `print_job` em `@BeforeEach` para um ponto de partida
  determinístico a cada teste, inclusive uma asserção literal de lista
  vazia — mesmo raciocínio já documentado em
  `CategoryControllerIntegrationTests` para limpar as tabelas de catálogo.
  Seguro aqui especificamente porque `print_job` é uma tabela filha pura
  (nada mais referencia `PrintJob`) e porque as classes de teste desta
  suíte rodam sequencialmente, não concorrentemente. Cobre lista vazia,
  conteúdo desserializado corretamente (via um pedido real criado por
  `OrderService.create(...)` e drenado por `outboxWorker.
  processPendingEvents()` — mesmo wiring de produção do FARELO-072),
  ordenação FIFO (dois pedidos com `createdAt` distinto), e exclusão de
  jobs `PRINTED`/`FAILED` (marcados diretamente via `PrintJob.markPrinted()`/
  `markFailed()` + `printJobRepository.save(...)`, já que ainda não existe
  endpoint para essas transições — FARELO-077+). Usa o número de comanda
  semeado 20 — o próximo livre após todos os já reservados (1-19, 101,
  999).

- **`POST /api/v1/print-jobs/{id}/printed` e `POST /api/v1/print-jobs/{id}/failed`**
  (FARELO-077): fecha o ciclo aberto pelo FARELO-076 — o Edge Agent
  consultava `GET /api/v1/print-jobs`, mas nunca reportava de volta o
  resultado, então todo `PrintJob` ficava `PENDING` para sempre. Estes dois
  endpoints deixam o Edge Agent dizer "imprimi este" (`PENDING` →
  `PRINTED`) ou "falhei ao imprimir este" (`PENDING` → `FAILED`). Ver
  `docs/api.md` para os dois endpoints completos, incluindo exemplos de
  request/response.

  **`PrintJobService.markPrinted(UUID)`/`markFailed(UUID)`**: novos
  métodos, reaproveitando `PrintJob.markPrinted()`/`markFailed()` — que já
  existiam desde o FARELO-071, mas sem nenhuma validação de estado (não
  havia chamador real ainda para escrever um teste contra). A validação de
  transição entra aqui, no `Service`, não na entidade — mesma divisão de
  responsabilidade já estabelecida por `OrderService#transition` vs.
  `Order#setStatus` (`ordering`): a entidade continua um mutador burro
  (`this.status = ...`, sem checagem), e o `Service` é quem decide se a
  transição é válida antes de chamá-la. Ambos os métodos compartilham um
  helper privado `transition(id, targetStatus, mutator)` — mais simples que
  o par de overloads de `OrderService#transition` (`OrderStatus`/`Set
  <OrderStatus>`), porque aqui as duas transições têm exatamente a mesma
  origem válida (`PENDING`), sem o caso de múltiplos estados de origem que
  motivou o overload com `Set` em `ordering` (`markAsCancelled`).

  **Estado de origem válido**: só `PENDING`, para as duas transições —
  marcar um job já `PRINTED` ou já `FAILED` de novo (com qualquer um dos
  dois endpoints) é rejeitado como qualquer outra transição inválida
  (`PrintJobInvalidTransitionException`/`PRINT_JOB_INVALID_TRANSITION`),
  não aceito silenciosamente. A transição de volta `FAILED` → `PENDING`
  (retry) continuava sem existir neste ticket — mecanismo desenhado depois,
  no FARELO-079 (ver entrada dedicada mais abaixo).

  **`PrintJobNotFoundException`/`PrintJobInvalidTransitionException`**
  (pacote `printing`): espelham `OrderNotFoundException`/
  `OrderInvalidTransitionException` (`ordering`) — mesmo formato de
  mensagem, mesma decisão de uma única exceção reutilizada pelas duas
  transições (a mensagem já nomeia tanto o status atual quanto o alvo
  tentado, então não há ambiguidade a evitar separando por endpoint).
  Registradas em `ApiExceptionHandler` como `404`/`PRINT_JOB_NOT_FOUND` e
  `409`/`PRINT_JOB_INVALID_TRANSITION`, mesmo padrão dos pares já
  existentes de `ordering`/`command`.

  **`PrintJobRepository.findByIdWithOrder(UUID)`**: nova query derivada
  com `JOIN FETCH p.order` — mesma razão de `findByStatusOrderByCreatedAtAsc`
  (FARELO-076) e de `OrderRepository#findByIdWithCommand`: `PrintJobResponse`
  lê `job.getOrder().getId()` no controller, depois que a transação (curta)
  do service já fechou; sem o fetch antecipado, `order` ficaria um proxy
  lazy não inicializado nesse ponto (`LazyInitializationException`).

  **Sem corpo de requisição em `/failed`**: nenhum motivo estruturado da
  falha é aceito — YAGNI, sem nenhum consumidor para esse dado ainda (ex:
  um painel mostrando por que uma impressora falhou); se um caso de uso
  real precisar disso no futuro, é escopo de um ticket próprio, não
  antecipado aqui.

  **Sem migration nova**: a coluna `print_job.status` e seu `CHECK
  (status IN ('PENDING', 'PRINTED', 'FAILED'))` já suportam os três
  valores desde `V15__create_print_job_table.sql` (FARELO-071) — este
  ticket só adiciona validação de transição na camada de aplicação, nenhuma
  mudança de schema.

  **Testes**: adicionados a `PrintJobControllerIntegrationTests` (mesma
  classe do FARELO-076, agora cobrindo os três endpoints REST do domínio)
  — transições válidas de `PENDING` para cada endpoint, `409` ao tentar
  transicionar um job já `PRINTED`/já `FAILED` (para os dois endpoints, já
  que ambos compartilham a mesma origem válida), e `404` para um `id` de
  job inexistente em cada endpoint. O helper `markStatus(...)` já existente
  (usado para preparar jobs `PRINTED`/`FAILED` sem passar pelos novos
  endpoints, ex. para o teste de exclusão da listagem do FARELO-076)
  continua reaproveitado como setup desses novos testes.

- **`POST /api/v1/print-jobs/{id}/retry`** (FARELO-079): fecha a lacuna
  deixada em aberto desde o FARELO-071/077 — um `PrintJob` `FAILED` ficava
  `FAILED` para sempre, porque `GET /api/v1/print-jobs` só devolve jobs
  `PENDING` (FARELO-076) e nada trazia um job de volta pra fila. A seção 10
  do prompt mestre é explícita: "Falha: `FAILED`, permitindo retry" — este
  ticket implementa esse "permitindo retry". Ver `docs/api.md` para o
  endpoint completo.

  **Decisão de desenho — endpoint manual, não retry automático agendado**:
  duas abordagens foram cogitadas: (a) um endpoint manual (`POST
  /api/v1/print-jobs/{id}/retry`) que simplesmente transiciona `FAILED` →
  `PENDING` sob pedido, fazendo o job reaparecer no próximo poll do Edge
  Agent; ou (b) o próprio backend (ou o Edge Agent) reagendando
  automaticamente um job falho depois de um tempo, com contador de
  tentativas e backoff próprios. Escolhida a opção (a), pelos motivos:

  - O Edge Agent (prompt mestre seção 11) já lista "manter fila temporária
    local" como responsabilidade **futura, ainda não implementada** — hoje
    não existe nenhuma fila/mecanismo de backoff do lado do Edge Agent para
    um retry agendado se apoiar, e construir isso agora do lado do backend
    significaria inventar uma política de tempo (quanto esperar, com ou sem
    backoff exponencial) sem nenhum dado real de padrão de falha para
    basear essa escolha.
  - Não existe hoje nenhum consumidor pedindo retry automático
    desacompanhado — mesma disciplina YAGNI já aplicada a `Printer`/
    `Ingredient` (primeira versão deliberadamente mínima, estendida depois
    quando uma necessidade concreta aparecer).
  - Um endpoint manual não fecha a porta para a opção (b) no futuro: nada
    aqui impede um ticket futuro de adicionar um agendador que simplesmente
    chame este mesmo `PrintJobService#retry(UUID)` numa timer, uma vez que
    a fila local do Edge Agent (ou outro gatilho) exista pra decidir quando.
    Construir o caminho manual primeiro não compromete essa opção.

  **`PrintJobService.retry(UUID)`**: novo método, mas **não** reaproveita o
  helper privado `transition(id, targetStatus, mutator)` já usado por
  `markPrinted`/`markFailed` — ao contrário dos dois (que exigem a mesma
  origem `PENDING`), esta transição exige origem `FAILED` **e** tem uma
  pré-condição extra (o limite de tentativas) que nada tem a ver com o
  status de origem — encaixar isso no helper compartilhado (que só sabe
  comparar um status e chamar um mutator) faria ele fazer duas coisas sem
  relação. Um método dedicado e pequeno ficou mais simples de ler do que
  generalizar o helper para um único chamador. Estado de origem válido:
  apenas `FAILED` — tentar `retry` num job `PENDING` (nada para reenviar) ou
  `PRINTED` é rejeitado como transição inválida
  (`PrintJobInvalidTransitionException`/`PRINT_JOB_INVALID_TRANSITION`,
  mesmo código já usado por `/printed`/`/failed`), não aceito
  silenciosamente.

  **Limite de tentativas — `retryCount` + `PrintJobService.MAX_RETRY_COUNT`
  (3)**: um retry sem limite (um operador — hoje um humano, o endpoint não
  distingue quem chama — clicando "retry" pra sempre num job cuja
  impressora simplesmente sumiu) deixaria um job ciclando
  `PENDING`/`FAILED` indefinidamente, sem nenhum sinal de que está
  "travado". `PrintJob` ganhou o campo `retryCount` (`int`, default `0`,
  coluna `retry_count`), incrementado só por `PrintJob#retry()` a cada
  retry bem-sucedido. `PrintJobService#retry(UUID)` rejeita uma nova
  tentativa assim que `retryCount` atinge `MAX_RETRY_COUNT`, com uma
  exceção **distinta** de `PrintJobInvalidTransitionException`:
  `PrintJobRetryLimitExceededException`/`PRINT_JOB_RETRY_LIMIT_EXCEEDED`
  (também `409`, registrada em `ApiExceptionHandler` junto às demais). Os
  dois `409` deste domínio descrevem problemas diferentes para quem chama:
  transição inválida significa "este job não está `FAILED`, não há o que
  reenviar" (tentar de novo mais tarde, se o job vier a falhar, pode
  funcionar); limite de tentativas excedido significa "este job está
  `FAILED` e é elegível em princípio, mas já foi reenviado o número máximo
  de vezes" (reenviar de novo por este endpoint nunca vai funcionar — quem
  chama precisa de outro remédio, ex. investigar a impressora). Colapsar os
  dois numa única exceção/código esconderia essa distinção de quem
  consome a API.

  `MAX_RETRY_COUNT` é uma constante fixa pequena (`3`), não configurável
  por job: nada no projeto hoje precisa que ela varie (nenhum consumidor,
  nenhuma tela de Admin para isso ainda) — um parâmetro de configuração
  seria especulativo, mesma disciplina YAGNI do resto desta seção. O valor
  escolhido espelha o tipo de orçamento pequeno e fixo de tentativas comum
  para falhas transitórias de hardware (impressora momentaneamente fora do
  ar/sem papel), sem deixar uma impressora permanentemente quebrada
  acumular jobs pra sempre.

  **Migration**: `V18__add_print_job_retry_count_column.sql` adiciona
  `retry_count INTEGER NOT NULL DEFAULT 0` (com `CHECK (retry_count >= 0)`)
  a `print_job` — o limite máximo em si (`MAX_RETRY_COUNT`) **não** vira um
  `CHECK` no banco: é política de aplicação que pode mudar independente do
  schema, mesmo raciocínio já usado para as próprias regras de transição de
  status deste domínio (nunca viraram `CHECK` de banco).

  **`PrintJobResponse.retryCount`**: novo campo na resposta (presente em
  todos os endpoints do domínio, não só em `/retry`) — deixa um consumidor
  futuro (ex: uma tela de Admin) ver quantas vezes um job já foi reenviado
  sem precisar de uma consulta separada.

  **Testes**: novos casos em `PrintJobControllerIntegrationTests` — retry
  bem-sucedido de um job `FAILED` (status vira `PENDING`, `retryCount`
  incrementa, e o job reaparece em `GET /api/v1/print-jobs`), `409` ao
  tentar `retry` num job `PENDING` ou `PRINTED`, `404` para `id`
  inexistente, e um teste dedicado do limite de tentativas (cicla
  `FAILED` → retry → `FAILED` `MAX_RETRY_COUNT` vezes, confirmando que cada
  uma é aceita e incrementa `retryCount`, e que a tentativa seguinte é
  rejeitada com `PRINT_JOB_RETRY_LIMIT_EXCEEDED`, com o job permanecendo
  `FAILED` e `retryCount` parado em `MAX_RETRY_COUNT`).

## inventory

Pacote: `com.farelo.api.inventory`.

- **`Ingredient`** (FARELO-090): entidade JPA — `id` (UUID, mesma estratégia
  de `Category`/`Product`/`Printer`), `name` (ex: "Leite", "Café em grão",
  "Copo 300ml"), `unit` (enum `IngredientUnit` —
  `@Enumerated(EnumType.STRING)`, mesma convenção de `ProductionStation`),
  `active` (default `true`, mesmo padrão de `Category`/`Product`/`Printer`),
  `createdAt`/`updatedAt` (`OffsetDateTime` em UTC, mesmo padrão dos demais
  domínios). Tabela criada pela migration
  `V16__create_ingredient_table.sql`. Primeira peça do Epic 7 (Estoque, ver
  `docs/PROMPT_MESTRE.md` seções 13-18) — a fundação para `Recipe`/
  `RecipeItem` (FARELO-091/092) e o ledger de estoque, `InventoryMovement`
  (FARELO-093).

  **Escopo deste ticket é só a entidade `Ingredient` em si.** Nenhum outro
  ticket do Epic 7 foi antecipado aqui — sem `Recipe`/`RecipeItem`
  (FARELO-091/092), sem `InventoryMovement`/ledger (FARELO-093+), sem
  `currentStock`/`minimumStock`/`criticalStock` (FARELO-095/099) e sem custo
  unitário do ingrediente (nenhum ticket até este pede preço/custo de
  ingrediente) — mesmo raciocínio YAGNI já aplicado a `Printer`
  (FARELO-070): adicionar campos apenas quando um ticket concreto precisar
  deles, não especulativamente (AGENTS.md: não criar abstrações
  prematuras).

  **`IngredientUnit`: só `GRAM`/`MILLILITER`/`UNIT`, não a lista completa do
  prompt mestre**: a seção 14 do prompt mestre lista `UN`, `G`, `KG`, `ML`,
  `L` como unidades de ingrediente, mais conversão de unidade de compra (ex:
  "1 bandeja de ovo = 30 UN") sobre uma unidade base interna. Nada disso é
  necessário ainda — este ticket não tem nenhum consumidor real de
  `Ingredient` (`Recipe`/`InventoryMovement` são tickets futuros), então
  modelar a lista completa de unidades e um mecanismo de conversão agora
  seria adivinhar um desenho para um consumidor que ainda não existe. O
  enum cobre apenas as três unidades que já são, elas mesmas, unidades
  *base* de medida: `GRAM` e `MILLILITER` (grandezas contínuas, massa e
  volume) e `UNIT` (itens discretos, como copos ou embalagens). `KG`/`L` do
  prompt mestre são unidades de *compra/exibição*, não unidades base — o
  mesmo peso é apenas `GRAM` em outra escala (1 KG = 1000 G), exatamente a
  "conversão de unidade de compra" que o próprio prompt mestre já pede para
  manter separada da unidade de estoque ("não misturar unidade de estoque
  com descrição de embalagem — internamente preferir unidade base").
  Modelar `KG`/`L` como unidade de estoque própria hoje forçaria toda
  entrada futura do ledger (`InventoryMovement`, FARELO-093) a converter
  entre unidades misturadas antes de somar um saldo; uma única unidade base
  por ingrediente evita isso por completo. Extensível depois (ex: um
  conceito dedicado de unidade de compra/conversão) se um ticket real
  precisar — ver javadoc de `IngredientUnit` para a nota completa.

  Coluna `unit` na migration usa a mesma convenção `VARCHAR` + `CHECK` de
  `product.production_station`/`command.status`/`orders.status`, espelhando
  os valores do enum também no nível do banco.

  `IngredientRepository` (Spring Data JPA), sem métodos de consulta
  próprios além do CRUD padrão do `JpaRepository` — mesmo formato de
  `CategoryRepository`/`ProductRepository`/`PrinterRepository`. `GET
  /api/v1/ingredients` lista **todos** os ingredientes (não só os ativos),
  mesmo padrão já usado por `GET /api/v1/categories`/`GET
  /api/v1/products` — nenhum filtro `active`-only foi pedido ainda; um
  `findByActiveTrue` fica para quando um consumidor real (Admin) precisar
  disso.

  CRUD REST completo do lado do backend: `POST`/`GET`
  (lista)/`GET /{id}`/`PUT /{id}` — mesmo padrão de forma de `Product`
  (`POST`/`GET`/`PUT`, FARELO-014/015/016), incluindo a divisão entre
  `IngredientRequest` (criação, sem `active` — sempre começa `true`) e
  `IngredientUpdateRequest` (`PUT`, substituição completa, com `active`
  como `Boolean` obrigatório — mesmo motivo primitivo-vs-wrapper já
  documentado em `ProductUpdateRequest`: um `boolean` primitivo ausente no
  JSON viraria silenciosamente `false` via Jackson). Sem `DELETE` (fora do
  roadmap atual). `IngredientNotFoundException`
  (`404`/`INGREDIENT_NOT_FOUND`) registrada em `ApiExceptionHandler`, mesmo
  formato de `CategoryNotFoundException`/`ProductNotFoundException`. Ver
  `docs/api.md` para os endpoints.

  **Testes**: `IngredientRepositoryIntegrationTests` (mapeamento JPA contra
  Postgres real, mesmo formato de `CategoryRepositoryIntegrationTests`) e
  `IngredientControllerIntegrationTests` (HTTP real via `MockMvc`, cobrindo
  os quatro endpoints — sucesso, validação e `404` — mesmo formato de
  `ProductControllerIntegrationTests`).

- **`Recipe`** (FARELO-091): entidade JPA — o "cabeçalho" da receita/ficha
  técnica (prompt mestre seção 15) de um `Product` (ex: "pão com ovos e
  bacon" consome 3 UN ovos + 1 UN pão + 80 G bacon + 10 G manteiga). `id`
  (UUID, mesma estratégia dos demais domínios), `product` (`@ManyToOne`
  obrigatório para `Product`), `active` (default `true`, mesmo padrão de
  `Category`/`Product`/`Printer`/`Ingredient`), `createdAt`/`updatedAt`
  (UTC). Tabela criada pela migration `V17__create_recipe_table.sql`.

  **Escopo deste ticket é só o cabeçalho.** A lista de ingredientes e
  quantidades que compõem a receita (`RecipeItem`) é FARELO-092, tickets
  futuros — deliberadamente não modelada aqui, mesma abordagem incremental
  de `Ingredient` não carregar campos de saldo de estoque até um ticket
  precisar deles. Nenhuma lógica de consumo de receita ao criar pedido
  (FARELO-096) tampouco — este ticket só estabelece que uma receita existe
  para um produto.

  **Decisão de desenho — relação com `Product`: `@ManyToOne` + índice único
  parcial, não `@OneToOne`**: o alvo do roadmap é "um produto tem no máximo
  uma receita *ativa* por vez" (nada no prompt mestre pede histórico/
  versionamento de receita), o que sugeriria `@OneToOne`. Mas isso forçaria
  toda "troca de receita" a mutar a mesma linha no lugar (sem histórico) ou
  apagar-e-recriar, descartando um trilha de auditoria natural e barata.
  `@ManyToOne` + `active` (mesmo padrão que toda outra entidade deste
  código já carrega) mantém toda receita passada quando uma nova a
  substitui — desativa a antiga, insere uma nova ativa — sem custo extra de
  modelagem e com um benefício real (a composição de um produto num
  momento específico é reconstituível depois, relevante quando
  `InventoryMovement` referenciar uma receita específica no consumo). A
  regra de "só uma ativa por vez" continua garantida — só que via índice
  único parcial (`CREATE UNIQUE INDEX ... WHERE active`, ver
  `V17__create_recipe_table.sql`) em vez da cardinalidade do FK. Essa é a
  fonte de verdade real da regra (duas requisições concorrentes poderiam
  passar por uma checagem só na camada de aplicação antes de qualquer uma
  commitar); `RecipeService#create` também checa isso primeiro (falha
  rápido sem bater no banco, e traduz violação de constraint em
  `RecipeAlreadyExistsException` — mesmo formato independente de qual das
  duas camadas pegar primeiro).

  `RecipeRepository` (Spring Data JPA): `findByProductIdAndActiveTrue`
  (chave natural da entidade). **`findByIdWithProduct`/
  `findAllWithProductOrderByCreatedAtAsc` usam `JOIN FETCH r.product`** —
  mesmo raciocínio de `PrintJobRepository#findByIdWithOrder`/
  `findByStatusOrderByCreatedAtAsc` (a lição do FARELO-055, originalmente
  documentada em `OrderRepository`): `open-in-view` é `false`
  (`application.yml`), e `RecipeResponse#from` lê
  `recipe.getProduct().getName()` no controller, depois que a transação
  (curta) do método do repository já fechou — sem buscar `product` de
  forma antecipada aqui, isso seria um proxy lazy não inicializado
  precisando de uma sessão viva, ou seja, um `LazyInitializationException`
  garantido. **Isso foi encontrado e corrigido em revisão** (o agente
  original usou `findById`/`findAll` simples) — ver histórico do commit.

  CRUD REST (`/api/v1/recipes`): `POST` (cria, recebendo `productId`; 404
  `PRODUCT_NOT_FOUND` se o produto não existir, 409
  `RECIPE_ALREADY_EXISTS` se já houver receita ativa pro produto),
  `GET`/`GET {id}` (404 `RECIPE_NOT_FOUND`), e `PATCH /{id}/deactivate`
  (desativa — **`PATCH`, não `PUT`**: diferente de `Ingredient`/`Product`,
  que usam `PUT` de substituição completa por terem vários campos
  editáveis independentemente, `Recipe` só tem uma coisa que este ticket
  permite mudar — `active` — então um `PATCH` parcial que só recebe esse
  campo cabe melhor que um endpoint de substituição completa que seria só
  um alias do mesmo campo único). Reatribuir uma receita a outro produto
  não é suportado — o caminho certo pra mudar a composição é desativar e
  criar uma nova receita. Ver `docs/api.md` para os endpoints.

  **Testes**: `RecipeRepositoryIntegrationTests` (mapeamento JPA contra
  Postgres real, incluindo o índice único parcial rejeitando duas receitas
  ativas pro mesmo produto) e `RecipeControllerIntegrationTests` (HTTP real
  via `MockMvc`, cobrindo sucesso, 404 e 409). **Nota de isolamento de
  teste, encontrada em revisão**: diferente de
  `IngredientRepositoryIntegrationTests`/outros testes de `catalog`, estas
  duas classes **não** fazem `productRepository.deleteAll()`/
  `categoryRepository.deleteAll()` no `@BeforeEach` — só
  `recipeRepository.deleteAll()`. Sob a suíte completa,
  `OrderControllerIntegrationTests` cria seus próprios `Product`s/`Order`s
  e nunca limpa (não é um bug isolado deste ticket, é uma característica
  pré-existente da suíte), então um `deleteAll()` cego nas tabelas
  compartilhadas de `product`/`category` pode esbarrar num
  `order_item` ainda referenciando o produto, dependendo da ordem de
  execução das classes — isso surgiu como uma falha real, dependente de
  ordem, durante a revisão deste ticket. Nenhuma asserção destas duas
  classes depende das tabelas de produto/categoria estarem vazias (cada
  teste cria seu próprio produto com nome único), então simplesmente não
  tocar nessas tabelas compartilhadas resolve sem qualquer risco.

- **`RecipeItem`** (FARELO-092): entidade JPA — uma linha da composição de
  uma `Recipe` (prompt mestre seção 15): um `Ingredient` e a quantidade dele
  consumida por unidade vendida do produto da receita — ex: "pão com ovos e
  bacon" tem um `RecipeItem` por ingrediente: 3 UN ovos, 1 UN pão, 80 G
  bacon, 10 G manteiga. `id` (UUID, mesma estratégia dos demais domínios),
  `recipe` (`@ManyToOne` obrigatório para `Recipe`), `ingredient`
  (`@ManyToOne` obrigatório para `Ingredient`), `quantity` (`BigDecimal`,
  coluna `NUMERIC(12,3)` — nunca `double`/`float`, ver AGENTS.md; mesma
  escolha já usada pra dinheiro no projeto, apropriada aqui também porque
  quantidades fracionárias são legítimas, ex: `0.5` L de leite),
  `createdAt`/`updatedAt` (UTC). Tabela criada pela migration
  `V19__create_recipe_item_table.sql`.

  **Escopo deste ticket é só a entidade `RecipeItem` em si e o CRUD dela.**
  Nenhuma baixa de estoque ao criar pedido (FARELO-096, "vender 10 unidades
  desconta os ingredientes proporcionalmente") foi antecipada aqui — mesma
  abordagem incremental já usada em `Ingredient`/`Recipe`: este ticket só
  registra a composição da receita, não age sobre ela.

  **`quantity` está sempre na unidade base de `Ingredient.unit`, sem
  conversão de unidade de compra**: mesma regra "internamente preferir
  unidade base" já aplicada a `IngredientUnit` (prompt mestre seção 14) —
  ex: 80 G de bacon é armazenado como `80` contra um ingrediente cuja
  unidade é `GRAM`, nunca em alguma outra unidade de exibição/compra.
  Converter para uma unidade de exibição (ex: mostrar o bacon em KG num
  relatório) é responsabilidade de UI/relatório de um ticket futuro, não
  deste.

  **Sem coleção `@OneToMany` de `RecipeItem` em `Recipe`, e `Recipe.java`
  intencionalmente não foi tocado por este ticket.** A relação é um
  `@ManyToOne` unidirecional de `RecipeItem` para `Recipe`, não uma
  associação bidirecional. Razões: (1) nada em "listar os itens de uma
  receita" precisa de uma coleção gerenciada viva do lado dono —
  `RecipeItemRepository#findByRecipeId` é uma consulta explícita simples que
  faz o mesmo trabalho sem nenhuma das armadilhas usuais de uma associação
  bidirecional (manter os dois lados sincronizados em add/remove, risco de
  N+1 se a coleção for EAGER, ou risco de `LazyInitializationException` se
  for LAZY e lida fora de uma transação — este projeto roda com
  `open-in-view=false`, então esse risco é real, ver
  `PrintJobRepository`/`RecipeRepository` pro precedente de `JOIN FETCH` que
  o repository deste ticket também segue); (2) `Recipe.java` foi mergeado
  muito recentemente como um ticket isolado (FARELO-091) — mexer nele aqui
  por uma conveniência que não é necessária adicionaria superfície de
  revisão e uma chance pequena de colidir com trabalho concorrente de outro
  agente no mesmo arquivo, sem ganho de comportamento. Se um ticket futuro
  precisar de "apagar em cascata os itens de uma receita quando a própria
  receita for apagada", esse é o ponto pra reconsiderar isso — hoje `Recipe`
  nunca é apagada de verdade (só desativada), então esse cascade também não
  é necessário ainda.

  **Restrição de duplicidade**: o mesmo `Ingredient` não pode aparecer duas
  vezes na mesma `Recipe` — duas linhas pro mesmo ingrediente na mesma
  receita é erro de cadastro, não um caso de uso real (o caminho certo pra
  mudar a quantidade é atualizar a linha existente, não duplicá-la).
  Aplicado via `UNIQUE(recipe_id, ingredient_id)` na migration — a fonte de
  verdade real da regra, pelo mesmo motivo de corrida entre duas requisições
  concorrentes já documentado na decisão do índice único parcial de
  `Recipe` — e traduzido em `RecipeItemAlreadyExistsException`
  (`409`/`RECIPE_ITEM_ALREADY_EXISTS`) na camada de serviço
  (`RecipeItemService#create`, que também checa isso primeiro via
  `RecipeItemRepository#existsByRecipeIdAndIngredientId`, falhando rápido
  sem depender só da constraint do banco).

  `RecipeItemRepository` (Spring Data JPA): `findByRecipeId(UUID recipeId)`
  — a consulta que a listagem/composição de uma receita precisa, com `JOIN
  FETCH ri.ingredient` (mesmo raciocínio de
  `PrintJobRepository#findByIdWithOrder`/`RecipeRepository#findByIdWithProduct`:
  `open-in-view` é `false`, e `RecipeItemResponse#from` lê
  `item.getIngredient().getName()`/`getUnit()` no controller, depois que a
  transação do repository já fechou). Sem `JOIN FETCH ri.recipe` — a
  resposta só lê `recipe.getId()` (o valor da FK, já presente no proxy lazy
  sem precisar inicializá-lo), nunca dados reais da entidade `Recipe`.
  Também `existsByRecipeIdAndIngredientId` (duplicidade) e
  `findByIdAndRecipeId` (usado por `delete` — confirma que o item existe *e*
  pertence à receita informada, tratando um par recipeId/itemId
  incompatível como 404, não como um delete cross-recipe silencioso).

  CRUD REST (`/api/v1/recipes/{recipeId}/items`): `POST` (adiciona um item,
  recebendo `ingredientId` + `quantity`; 404 `RECIPE_NOT_FOUND` se a receita
  não existir, 404 `INGREDIENT_NOT_FOUND` se o ingrediente não existir, 409
  `RECIPE_ITEM_ALREADY_EXISTS` se o ingrediente já estiver na receita),
  `GET` (lista os itens da receita — também 404 `RECIPE_NOT_FOUND` se a
  receita não existir, pra distinguir "receita sem itens" de "receita
  inexistente", que de outro modo retornariam a mesma lista vazia), e
  `DELETE /{itemId}` (remove um item — física, não desativação; ver seção
  seguinte pra justificativa). Ver `docs/api.md` para os endpoints.

  **`DELETE` físico, não uma flag `active`/desativação**: diferente do
  padrão do próprio `Recipe` (que nunca apaga de verdade, só desativa — ver
  javadoc de `Recipe`), e é uma divergência deliberada, não um descuido. A
  desativação de `Recipe` existe pra preservar um rastro histórico da
  *composição de um produto ao longo do tempo* (benefício real, se
  secundário, já citado no javadoc de `Recipe` — relevante quando
  `InventoryMovement` referenciar uma receita específica no consumo). Um
  `RecipeItem` isolado não carrega esse mesmo valor de auditoria
  independente: é uma linha de composição, não um fato que alguém precisa
  reconstruir isoladamente do resto da receita. Remover um ingrediente de
  uma receita já ativa é presumivelmente uma correção administrativa direta
  (ex: "não usamos mais manteiga nessa receita"), não uma operação que
  precise preservar histórico linha-a-linha — do mesmo jeito que
  `PUT /api/v1/ingredients/{id}` reescreve os campos de um ingrediente livre
  no lugar, sem conceito de soft-delete pros valores anteriores. Se um
  ticket futuro precisar saber exatamente qual era a composição de uma
  receita num ponto específico do passado (além de "essa versão inteira da
  receita estava ativa/inativa"), o ajuste é versionar `Recipe` de forma
  mais granular, não preservar linhas de `RecipeItem` apagadas.

  **Testes**: `RecipeItemRepositoryIntegrationTests` (mapeamento JPA contra
  Postgres real, incluindo a constraint `UNIQUE(recipe_id, ingredient_id)`
  rejeitando ingrediente duplicado na mesma receita, e o `JOIN FETCH` de
  `findByRecipeId` não lançando `LazyInitializationException` ao ler
  `ingredient` fora da transação) e `RecipeItemControllerIntegrationTests`
  (HTTP real via `MockMvc`, cobrindo sucesso, 404 (receita/ingrediente/item
  inexistente, inclusive delete cross-recipe), 409 e validação). **Mesmo
  cuidado de isolamento de teste já documentado acima pra `Recipe`**: nenhum
  `@BeforeEach` limpa `product`/`category`/`recipe`/`ingredient` — cada
  teste cria seu próprio produto/categoria/ingrediente/receita com nome
  único, e como toda consulta deste domínio é sempre filtrada por um
  `recipeId` específico, nenhuma asserção depende dessas tabelas
  compartilhadas estarem vazias.

- **`InventoryMovement`** (FARELO-093): entidade JPA — o ledger append-only
  de estoque (prompt mestre seção 13: "Não armazenar apenas um número
  editável de saldo. Utilizar ledger: `InventoryMovement`"). `id` (UUID,
  mesma estratégia dos demais domínios), `ingredient` (`@ManyToOne`
  obrigatório para `Ingredient`), `quantity` (`BigDecimal`, coluna
  `NUMERIC(12,3)` — mesma precisão de `RecipeItem.quantity` — **pode ser
  positivo ou negativo**: positivo é entrada de estoque, ex: `PURCHASE`;
  negativo é saída, ex: `ORDER_CONSUMPTION`/`LOSS`; o saldo de um
  ingrediente é `SUM(quantity)` sobre suas linhas, nunca um campo mutável em
  `Ingredient`), `type` (enum `InventoryMovementType`, ver abaixo),
  `orderId` (UUID, nullable — ver "Campo de origem/idempotência" abaixo),
  `createdAt` (UTC). Tabela criada pela migration
  `V21__create_inventory_movement_table.sql`. Segue `Ingredient`
  (FARELO-090), `Recipe` (FARELO-091) e `RecipeItem` (FARELO-092).

  **Escopo deste ticket é só a entidade `InventoryMovement` em si — o
  ledger e a infraestrutura de consulta.** Nenhum "produtor" automático de
  movimentos foi criado aqui: nada neste código constrói um
  `InventoryMovement` a partir de um pedido (FARELO-096), de uma entrada
  manual (FARELO-094) ou de uma perda registrada (FARELO-098) — todos
  tickets futuros. Hoje a única forma de uma linha existir é
  `InventoryMovementRepository.save(...)` chamado diretamente (nos testes
  deste ticket). Mesma abordagem incremental já usada em
  `Ingredient`/`Recipe`/`RecipeItem`: esta entidade só existe e é gravável,
  não age sobre nada ainda.

  **Append-only, nunca mutado**: reforçado estruturalmente, não só por
  convenção — toda coluna da tabela é `updatable = false` no mapeamento
  JPA, e a classe não expõe nenhum *setter*. Uma vez persistida, uma
  instância não pode ser alterada através da própria API da entidade. Se um
  movimento estiver errado, a correção é uma nova linha *compensatória*
  (ex: um `ADJUSTMENT` ou `CANCELLATION`), nunca uma edição da linha
  original — o histórico do ledger permanece fiel aos fatos.

  **`createdAt` sem `updatedAt`, de propósito** — diverge do padrão
  `createdAt`/`updatedAt` usado por toda outra entidade deste código
  (`Ingredient`/`Recipe`/`RecipeItem` inclusive). Uma linha de ledger
  append-only não tem o conceito de "atualizado depois" — é um fato sobre
  um instante único (esse movimento aconteceu), imutável desde que é
  escrita. Emparelhar com um `updatedAt` que só poderia sempre ser igual a
  `createdAt` seria enganoso — sugeriria que a linha *pode* ser revisada no
  lugar, exatamente o anti-padrão que esta entidade existe para evitar (um
  saldo mutável).

  **`InventoryMovementType`**: `PURCHASE`, `ORDER_CONSUMPTION`, `LOSS`,
  `ADJUSTMENT`, `RETURN`, `CANCELLATION`, `INTERNAL_CONSUMPTION` — a lista
  **literal e completa** do prompt mestre (seção 13), usada verbatim, não
  reduzida. Diferente de `IngredientUnit` (FARELO-090, que deliberadamente
  cortou a lista de unidades do prompt mestre para só o que um consumidor
  real precisava na hora), aqui o prompt mestre já nomeia as sete opções
  explícita e exaustivamente para esta entidade específica — não há nada
  para "adivinhar": usar uma lista reduzida hoje só garantiria uma migration
  de `ALTER` no `CHECK` assim que cada ticket do roadmap abaixo precisasse
  de um valor que a fonte da verdade já previu.
  `ORDER_CONSUMPTION` é FARELO-096/097 (o exemplo literal de idempotência da
  seção 16: `ORDER_CONSUMPTION orderId=123 ingredientId=5`); `PURCHASE` é
  FARELO-094 ("Criar entrada manual de estoque"); `LOSS` é FARELO-098
  ("Criar movimento de perda"). `ADJUSTMENT`/`RETURN`/`CANCELLATION`/
  `INTERNAL_CONSUMPTION` são nomeados pelo prompt mestre mas ainda sem um
  ticket numerado dedicado — plausivelmente: `RETURN`/`CANCELLATION`
  revertem um `ORDER_CONSUMPTION` quando um pedido/item é cancelado ou
  devolvido; `ADJUSTMENT` cobre uma correção manual distinta de uma
  `PURCHASE`; `INTERNAL_CONSUMPTION` cobre uso de estoque fora de uma
  venda (ex: consumo interno da equipe, teste de receita). Nenhum código
  produz nenhum desses quatro ainda. Coluna `type` na migration usa a
  mesma convenção `VARCHAR` + `CHECK` de `ingredient.unit`/
  `product.production_station`/`command.status`/`orders.status`.

  **Campo de origem/idempotência — `orderId`, preparando terreno para
  FARELO-097, sem implementar a constraint em si**: o prompt mestre seção
  16 dá a chave de idempotência literalmente como
  `ORDER_CONSUMPTION orderId=123 ingredientId=5` — ou seja, a chave natural
  que não pode ser processada duas vezes é (conceitualmente)
  `(type, orderId, ingredientId)` para movimentos com origem em pedido.
  Esta entidade já tem `type` e `ingredient`; a peça que faltava era a
  origem do pedido, então este ticket adiciona `orderId` agora para evitar
  uma migration extra quando FARELO-097 precisar de uma coluna pra
  construir a constraint única contra.

  Deliberadamente uma coluna `UUID` nullable simples — **não** um
  `@ManyToOne` para `com.farelo.api.ordering.Order`, diferente da relação
  cross-domain que `com.farelo.api.printing.PrintJob` já tem para `Order`.
  Três razões: (1) nada lê um campo real de `Order` através desta entidade
  — nenhum produtor de `ORDER_CONSUMPTION` (ou qualquer outro tipo
  relacionado a pedido) existe ainda, então uma associação lazy ficaria
  permanentemente não-inicializada, superfície sem uso; (2) só
  `ORDER_CONSUMPTION` (e plausivelmente `RETURN`/`CANCELLATION`) chegaria a
  preencher esse campo — `PURCHASE`/`LOSS`/`ADJUSTMENT`/
  `INTERNAL_CONSUMPTION` não têm origem em pedido nenhuma, então uma
  relação obrigatória estaria errada, e uma `@ManyToOne` opcional não traz
  nada que uma coluna nullable simples já não traga; (3) isso deixa a forma
  exata da chave de idempotência — ex: se vira mesmo uma constraint única
  em `(type, order_id, ingredient_id)`, ou algo mais amplo — como decisão
  de FARELO-097, não deste ticket. Este ticket só adiciona a coluna e um
  índice correspondente (não-único); **nenhuma constraint de unicidade é
  criada aqui** — ver comentário explícito em
  `V21__create_inventory_movement_table.sql` deixando claro que garantir
  "não processar duas vezes" é responsabilidade de FARELO-097, não deste
  ticket. A coluna carrega uma FK de nível de banco simples para
  `orders(id)` para integridade referencial básica quando estiver
  preenchida (sem ids órfãos), o que não custa nada hoje e não precisa de
  nenhuma relação do lado Java pra existir.

  `InventoryMovementRepository` (Spring Data JPA):
  `findByIngredientIdOrderByCreatedAtAsc` (consulta derivada simples — o
  Spring Data resolve `IngredientId` através da associação `ingredient`,
  suporte padrão a "property path through a relation"; sem `JOIN FETCH`
  necessário, porque `InventoryMovementResponse` só lê
  `ingredient.getId()`, já presente no proxy lazy sem precisar
  inicializá-lo — diferente de `RecipeItemResponse`, que lê nome/unidade do
  ingrediente e por isso precisa de `JOIN FETCH`) e
  `sumQuantityByIngredientId` (`@Query` somando `quantity` via
  `COALESCE(SUM(...), 0)` — **não usada por nenhum endpoint ainda**; existe
  como a infraestrutura de consulta que o cálculo de saldo "de verdade"
  como funcionalidade exposta, FARELO-095 ("Calcular saldo do
  ingrediente"), vai reusar em vez de reimplementar).

  `InventoryMovementService`: só um método, `listByIngredient` — valida que
  o ingrediente existe primeiro (reusa `IngredientService#getById`, 404
  `INGREDIENT_NOT_FOUND` se não existir, mesmo raciocínio de
  `RecipeItemService#listByRecipe` pra distinguir "ingrediente sem
  movimentos" de "ingrediente inexistente") e então lista os movimentos.
  **Sem método `create`**: nada neste ticket produz uma linha do ledger
  (ver acima).

  **Endpoint**: só `GET /api/v1/ingredients/{ingredientId}/movements`
  (lista os movimentos do ingrediente, mais antigo primeiro) —
  **somente leitura**, sem `POST`. Um endpoint de criação genérico agora
  anteciparia o desenho de FARELO-094 ("Criar entrada manual de estoque",
  o ticket que de fato é dono de "como um humano registra um movimento
  manual"). Mesmo padrão minimalista já usado pelo primeiro ticket de
  outros domínios (ex: `Printer`/FARELO-070, sem endpoint no primeiro
  ticket) — aqui optou-se por um endpoint de leitura mínimo em vez de
  nenhum, já que a infraestrutura de consulta (`findByIngredientIdOrderByCreatedAtAsc`)
  já existe e expor "ver o ledger de um ingrediente" tem valor
  independente de quem/o que grava nele. Ver `docs/api.md` para o
  endpoint completo.

  **Testes**: `InventoryMovementRepositoryIntegrationTests` (mapeamento
  JPA contra Postgres real, cobrindo movimento positivo, movimento
  negativo com `orderId`, listagem por ingrediente em ordem — inclusive
  não vazando movimentos de outro ingrediente — e a soma de saldo via
  `sumQuantityByIngredientId`) e
  `InventoryMovementControllerIntegrationTests` (HTTP real via `MockMvc`,
  cobrindo lista vazia, 404 `INGREDIENT_NOT_FOUND`, e listagem ordenada
  com `orderId` presente/ausente no JSON). **Mesmo cuidado de isolamento
  de teste já documentado para `Recipe`/`RecipeItem`**: nenhum
  `@BeforeEach` limpa `ingredient`/`inventory_movement` — cada teste cria
  seu próprio ingrediente com nome único e só afirma sobre movimentos
  filtrados por aquele `ingredientId` específico, então nenhuma asserção
  depende dessas tabelas compartilhadas estarem vazias.

  ### FARELO-094 — Criar entrada manual de estoque

  Primeiro *produtor* real de `InventoryMovement`: `InventoryMovementService`
  ganha um método `create(UUID ingredientId, BigDecimal quantity)`, e
  `InventoryMovementController` ganha `POST
  /api/v1/ingredients/{ingredientId}/movements` — antes deste ticket a
  única forma de uma linha existir era `InventoryMovementRepository.save(...)`
  chamado diretamente pelos testes (ver seção FARELO-093 acima). Isso muda
  agora: um humano (ex: um gerente) confirma que estoque chegou fisicamente
  (uma compra), e o sistema grava uma linha `PURCHASE` com `quantity`
  positiva.

  **Sem campo `type` no request — de propósito.** O request body
  (`InventoryMovementRequest`, em `com.farelo.api.inventory.web`) só recebe
  `quantity`; o `type` é fixado como `PURCHASE` no lado do servidor, dentro
  de `InventoryMovementService#create`, nunca escolhido pelo cliente. Este
  endpoint é especificamente o fluxo de *entrada manual* — "como um humano
  registra que estoque chegou" — não um endpoint genérico de "criar
  qualquer tipo de movimento". Deixar o cliente escolher `type` permitiria
  submeter `ORDER_CONSUMPTION`/`LOSS`/etc. por uma URL que não tem nada a
  ver com pedidos ou perdas, pulando por cima da validação que esses fluxos
  ainda vão precisar quando FARELO-096/098 os implementarem de fato (ex:
  `ORDER_CONSUMPTION` plausivelmente vai exigir um `orderId` real e a
  checagem de idempotência de FARELO-097; nada disso existe para uma
  entrada manual). Quando um desses tickets futuros precisar de seu próprio
  endpoint de criação, ele ganha o seu — este aqui continua dono só de
  `PURCHASE`.

  **`quantity` estritamente positiva (`@Positive`, mesmo padrão de
  `RecipeItemRequest#quantity()`)**: zero ou negativo não é o que
  `PURCHASE` significa (ver javadoc de `InventoryMovement`: o sinal
  codifica direção, e `PURCHASE` é sempre entrada de estoque). Um valor
  negativo de entrada seria uma saída disfarçada de compra — se um dia for
  necessário registrar uma correção negativa manual, esse é o escopo de
  `ADJUSTMENT` (ainda sem ticket dedicado), não deste.

  **Sem exceção nova.** As únicas falhas possíveis já tinham tratamento
  pronto em `ApiExceptionHandler`: `400`/`VALIDATION_ERROR` via
  `MethodArgumentNotValidException` (a mesma infraestrutura de validação
  Bean Validation usada em todo o resto da API) e `404`/
  `INGREDIENT_NOT_FOUND` via `IngredientNotFoundException`, reusando
  `IngredientService#getById` (mesma ordem de validação — ingrediente
  existe primeiro — já usada por `listByIngredient` e por
  `RecipeItemService#create`).

  **`@Transactional` em `create`**, mesmo sendo um único `save`: alinha com
  todo outro método mutante do domínio (`RecipeItemService#create`,
  `IngredientService#update`) em vez de depender da transação implícita
  por método do Spring Data — assim uma futura extensão aqui (ex: tocar um
  saldo corrente, se FARELO-095 algum dia decidir manter um agregado
  redundante) não precisaria adicionar a anotação retroativamente.

  **Endpoint**: `POST /api/v1/ingredients/{ingredientId}/movements`
  responde `201 Created` com `Location:
  /api/v1/ingredients/{ingredientId}/movements/{id}` (mesmo padrão de
  `POST /api/v1/recipes/{recipeId}/items`, incluindo o mesmo detalhe: não
  existe um `GET` de item único nesse path — o `Location` aponta para um
  recurso que só é recuperável via o `GET` de lista já existente, mesma
  forma de `RecipeItemController`) e corpo `InventoryMovementResponse`
  (reusado sem alteração — já expunha todos os campos necessários). Ver
  `docs/api.md` para o endpoint completo.

  **Testes**: `InventoryMovementServiceIntegrationTests` (novo — chama
  `InventoryMovementService#create` diretamente contra Postgres real,
  cobrindo criação com sucesso — tipo/quantidade/vínculo com o ingrediente
  corretos — e `IngredientNotFoundException` para ingrediente
  inexistente) e novos testes em `InventoryMovementControllerIntegrationTests`
  (HTTP real via `MockMvc`: criação com sucesso e persistência confirmada
  via repository, `404 INGREDIENT_NOT_FOUND`, e `400 VALIDATION_ERROR`
  para quantidade zero, negativa e ausente). Mesmo cuidado de isolamento
  de teste do restante desta seção: nenhum `@BeforeEach` limpa
  `ingredient`/`inventory_movement`, e nenhum teste toca
  `product`/`category` (ver nota de isolamento já documentada acima para
  `Recipe`/`RecipeItem` sobre `OrderControllerIntegrationTests` deixar
  linhas em `order_item`).

  ### FARELO-095 — Calcular saldo do ingrediente

  Primeiro *consumidor* real de `InventoryMovementRepository#sumQuantityByIngredientId`
  — a query `@Query` com `COALESCE(SUM(quantity), 0)` que FARELO-093 já
  deixou pronta especificamente para este ticket (ver comentário em
  `InventoryMovementRepository`, que dizia explicitamente "não usada por
  nenhum endpoint ainda... existe como a infraestrutura de consulta que o
  cálculo de saldo 'de verdade'... vai reusar em vez de reimplementar"). Este
  ticket só conecta essa query já existente a um endpoint — nenhuma soma é
  reimplementada em Java, reforçando a regra do prompt mestre (seção 13): "O
  saldo deve ser rastreável (derivado do ledger, nunca editado
  diretamente)".

  **`IngredientBalance`** (novo, pacote `com.farelo.api.inventory`): um
  record simples — não uma entidade JPA, nunca persistido — carregando o
  `Ingredient` e o `balance` (`BigDecimal`) calculado. Existe para que
  `InventoryMovementService#getBalance` tenha um tipo de retorno único que já
  inclua a unidade do ingrediente (via `ingredient.getUnit()`), em vez de o
  controller precisar buscar o ingrediente separadamente só para montar a
  resposta.

  **`InventoryMovementService#getBalance(UUID ingredientId)`**: mesma ordem
  de validação de `listByIngredient` — valida que o ingrediente existe
  primeiro (reusa `IngredientService#getById`, 404 `INGREDIENT_NOT_FOUND` se
  não existir), pelo mesmo motivo já documentado ali: um saldo `0` porque o
  ingrediente genuinamente não tem movimentos ainda precisa continuar
  distinguível de um id de ingrediente inexistente. Em seguida chama
  `sumQuantityByIngredientId` diretamente — sem nenhuma lógica adicional — e
  embrulha o resultado em `IngredientBalance` junto com o `Ingredient` já
  carregado pela validação.

  **Decisão de design — endpoint fica em `InventoryMovementController`, não
  em `IngredientController`**: a rota nova, `GET
  /api/v1/ingredients/{ingredientId}/balance`, poderia viver em qualquer um
  dos dois controllers do pacote `inventory.web` que já respondem sob
  `/api/v1/ingredients/{ingredientId}/...`. Decisão: `InventoryMovementController`,
  cujo `@RequestMapping` de classe foi generalizado de
  `/api/v1/ingredients/{ingredientId}/movements` para
  `/api/v1/ingredients/{ingredientId}` (com `/movements` movido para os dois
  `@GetMapping`/`@PostMapping` existentes) especificamente para abrir espaço
  para esta rota irmã, `.../balance` — um saldo não é mais um tipo de linha
  do ledger, é um valor *derivado* dele, então não faz sentido aninhá-lo sob
  `.../movements` também. Motivo de manter no mesmo controller do ledger em
  vez de mover para `IngredientController`: o cálculo em si
  (`InventoryMovementService#getBalance`) já vive no serviço que este
  controller já usa exclusivamente; colocar o endpoint em
  `IngredientController` faria dele o primeiro controller do projeto a
  depender de dois serviços, sem nenhum ganho — mesmo raciocínio de "manter
  a dependência unidirecional" que o javadoc de `CommandOrdersController` já
  documenta para uma escolha parecida (lá, entre dois domínios cruzados;
  aqui, dentro do mesmo pacote).

  **`IngredientBalanceResponse`** (novo DTO, pacote `com.farelo.api.inventory.web`):
  `ingredientId`, `balance` (`BigDecimal`), `unit` (`IngredientUnit`). O
  campo `unit` é incluído por exigência explícita deste ticket: um número
  isolado é ambíguo (`500` do quê?), então a resposta já carrega a unidade
  do ingrediente para o cliente não precisar de uma segunda chamada a `GET
  /api/v1/ingredients/{id}` só para interpretar o valor.

  **Endpoint**: `GET /api/v1/ingredients/{ingredientId}/balance` — só
  leitura, mesmo padrão minimalista do restante deste domínio. Ver
  `docs/api.md` para o endpoint completo.

  **Testes**: novos casos em `InventoryMovementControllerIntegrationTests`
  (HTTP real via `MockMvc`, mesmo arquivo/template dos testes de
  `movements`): saldo `0` para ingrediente sem nenhum movimento, saldo
  correto como soma de múltiplos movimentos (incluindo um `PURCHASE`
  positivo e um `ORDER_CONSUMPTION` negativo, com um movimento de outro
  ingrediente presente na mesma tabela para provar que não vaza para o
  saldo calculado), e `404 INGREDIENT_NOT_FOUND` para ingrediente
  inexistente. Mesmo cuidado de isolamento de teste do restante deste
  arquivo: nenhum `@BeforeEach` limpa `ingredient`/`inventory_movement` —
  cada teste cria seus próprios ingredientes com nome único.

  ### FARELO-096 — Consumir receita ao criar pedido

  Segundo *produtor* real de `InventoryMovement` (o primeiro foi FARELO-094,
  `PURCHASE`): `InventoryMovementService` ganha
  `consumeForOrder(UUID orderId, List<OrderItemConsumption> items)`, que
  materializa o exemplo literal do prompt mestre seção 15/16 — "vender 10
  unidades desconta os ingredientes proporcionalmente" — como uma linha
  `ORDER_CONSUMPTION` (quantidade negativa) por `RecipeItem` de cada
  produto vendido que tenha receita ativa.

  **Ponto de disparo — dentro de `OrderService#create`, não um endpoint
  próprio.** Este ticket não expõe nenhuma rota nova: `POST
  /api/v1/orders` (FARELO-052/053, o único caminho de criação de pedido —
  `CommandOrdersController` só lista, nunca cria) continua sendo a única
  porta de entrada. `OrderService#create` chama
  `inventoryMovementService.consumeForOrder(order.getId(), consumption)`
  logo depois que todo `OrderItem` já foi persistido (assim `order.getId()`
  existe) e antes do publish do evento outbox `OrderCreated` (FARELO-060) —
  ambos dentro da mesma `@Transactional`. Isso segue o mesmo desenho
  "mais uma coisa acontece depois da criação do pedido, mesma transação"
  que o próprio outbox publish já estabeleceu: se a baixa de estoque
  falhar, a criação do pedido inteira sofre rollback junto — não fica um
  pedido "órfão" sem o movimento de estoque correspondente. `@Transactional`
  em `consumeForOrder` documenta esse requisito explicitamente (propagação
  padrão do Spring, `REQUIRED`, entra na transação já aberta pelo
  chamador em vez de abrir uma nova).

  **`OrderItemConsumption`** (novo record, pacote `com.farelo.api.inventory`):
  `(UUID productId, int quantity)` — a entrada mínima de `consumeForOrder`
  para "um produto foi vendido N vezes". Deliberadamente não é
  `com.farelo.api.ordering.OrderItem` em si: a direção de dependência já
  estabelecida no projeto é `ordering` depende de `inventory` (e de
  `catalog`), nunca o contrário (mesmo raciocínio de "manter a dependência
  unidirecional" que o javadoc de `CommandOrdersController` já documenta
  para um par diferente de pacotes) — usar o tipo `OrderItem` de `ordering`
  aqui criaria uma dependência circular entre os dois domínios. Mesmo
  padrão que `ordering.NewOrderItem` já usa para desacoplar a entrada de
  um serviço do tipo de outro domínio.

  **Matemática da quantidade**: `RecipeItem.quantity` é "quanto desse
  ingrediente para UMA unidade do produto" (javadoc de `RecipeItem`). O
  movimento gravado é essa quantidade multiplicada por quantas unidades do
  produto foram pedidas (`OrderItemConsumption.quantity()`), negada — saída
  de estoque, mesma convenção de sinal já documentada em
  `InventoryMovement.getQuantity()`. `BigDecimal` do início ao fim
  (AGENTS.md): `RecipeItem.quantity` (escala 3) é multiplicado por um
  inteiro exato via `BigDecimal.valueOf(long)`, então o produto mantém a
  mesma escala — sem arredondamento, sem `double` em nenhum ponto. Exemplo
  do próprio prompt mestre: "pão com ovos e bacon" (3 UN ovos, 80 G bacon,
  10 G manteiga) vendido em quantidade 4 gera `-12` UN ovos, `-320` G
  bacon, `-40` G manteiga.

  **Produto sem receita ativa: nenhum movimento, sem erro.** Nem todo
  produto tem receita ainda (`Recipe` é opcional por produto, por design —
  ver javadoc de `Recipe`/`RecipeItem`, que já antecipavam este ticket
  exatamente). `RecipeRepository#findByProductIdAndActiveTrue` retornando
  vazio para um item do pedido é simplesmente "nada a consumir nessa
  linha" — `consumeForOrder` pula esse item silenciosamente e segue para o
  próximo. Uma receita desativada (`active = false`) conta como "sem
  receita" também, pelo mesmo motivo (a chave natural da entidade,
  documentada no javadoc de `Recipe`).

  **Sem checagem de saldo suficiente** — a seção 16 do prompt mestre não
  pede uma aqui; ficar negativo é permitido por ora (plausivelmente
  preocupação de FARELO-099, "estoque mínimo", não deste ticket). **Sem
  idempotência** — `InventoryMovement` já teria uma chave natural conceitual
  `(type, orderId, ingredientId)` (seção 16: "`ORDER_CONSUMPTION
  orderId=123 ingredientId=5` não deve conseguir ser processado duas
  vezes"), mas nenhuma constraint/checagem contra reprocessamento é
  adicionada aqui — deliberadamente adiado para FARELO-097, exatamente como
  o javadoc de `InventoryMovement` ("orderId" section) já antecipava desde
  FARELO-093. Este ticket só adiciona o produtor, não a garantia de "não
  processar duas vezes".

  `InventoryMovementRepository` ganha `findByOrderId(UUID orderId)` —
  consulta derivada simples sobre a coluna `order_id` (já indexada desde
  FARELO-093), usada pelos testes para verificar todo movimento que um
  pedido específico produziu.

  **Testes**: novos casos em `InventoryMovementServiceIntegrationTests`
  (chamando `consumeForOrder` diretamente: matemática de quantidade com
  pedido de quantidade > 1, produto sem receita ativa, receita desativada,
  múltiplos produtos cada um com sua própria receita escopados
  corretamente) e um novo arquivo,
  `OrderInventoryConsumptionIntegrationTests` (pacote
  `com.farelo.api.ordering.web`), cobrindo o fluxo HTTP completo
  (`POST /api/v1/orders`) end-to-end: cria o pedido de verdade e confirma
  via `InventoryMovementRepository#findByOrderId` que os movimentos
  `ORDER_CONSUMPTION` corretos foram persistidos com o `orderId` real —
  prova a fiação (`OrderService#create` realmente chama
  `consumeForOrder`), não só a lógica isolada do serviço.

  **Nota de isolamento de teste, encontrada em revisão**: `RecipeItem` não
  tem nenhum teste que limpe a tabela em `@BeforeEach` em lugar nenhum do
  domínio (ver notas de FARELO-092/093 acima), mas três classes *fora*
  desse domínio fazem limpeza cega de tabelas relacionadas em
  `@BeforeEach` — `RecipeRepositoryIntegrationTests`/
  `RecipeControllerIntegrationTests` (`recipeRepository.deleteAll()`) e
  `IngredientControllerIntegrationTests`
  (`inventoryMovementRepository.deleteAll(); ingredientRepository.deleteAll();`).
  A ordem real de execução dos testes sob a suíte completa (definida pelo
  Surefire, **não** alfabética por pacote/classe) determinou que tanto
  `InventoryMovementServiceIntegrationTests` quanto
  `OrderInventoryConsumptionIntegrationTests` (ambas novas deste ticket, e
  ambas agora criando `Recipe`/`RecipeItem`) rodam *antes* dessas três
  classes na mesma execução — qualquer `RecipeItem` deixado para trás por
  elas faz um desses `deleteAll()` cegos falhar com violação de FK
  (`recipe_item` ainda referenciando a `recipe`/`ingredient` sendo
  deletada). Isso foi encontrado rodando a suíte completa, não só as
  classes novas isoladamente — reforça o próprio aviso deste ticket sobre
  "não limpar cegamente tabelas compartilhadas em `@BeforeEach`". A
  correção: ambas as classes novas agora têm um `@AfterEach` *segmentado*
  (não uma limpeza cega) que deleta só os `RecipeItem`s que a própria
  classe criou — resolve sem reintroduzir um `deleteAll()` genérico que
  outras classes (ex: `RecipeItemRepositoryIntegrationTests`) ainda
  dependem de não sofrer.

  ### FARELO-097 — Implementar idempotência da baixa de estoque

  Torna real a chave de idempotência que o javadoc de `InventoryMovement`
  (seção "orderId") e o de `InventoryMovementType` já antecipavam desde
  FARELO-093, e que o prompt mestre seção 16 dá literalmente:
  `ORDER_CONSUMPTION orderId=123 ingredientId=5` não pode ser processado
  duas vezes. Este ticket **não adiciona nenhum método novo** —
  `InventoryMovementService` continua com a mesma API pública de
  FARELO-096; o que muda é o comportamento interno de
  `consumeForOrder(UUID orderId, List<OrderItemConsumption> items)`, que
  passa a ser seguro chamar mais de uma vez para o mesmo pedido.

  **Constraint no nível do banco — a fonte de verdade real**: nova
  migration `V23__add_inventory_movement_order_consumption_unique_index.sql`
  cria `idx_inventory_movement_order_consumption`, um índice único
  **parcial** — `UNIQUE (order_id, ingredient_id) WHERE type =
  'ORDER_CONSUMPTION'` — mesmo padrão/raciocínio do índice único parcial de
  `Recipe` (`idx_recipe_product_id_active`, ver seção `Recipe` acima).
  **Por que parcial, e não um `UNIQUE(type, order_id, ingredient_id)`
  simples**: verificado diretamente no Postgres que `NULL` nunca é igual a
  outro `NULL` para fins de unicidade — `INSERT`s repetidos com o mesmo `a`
  e `b = NULL` contra um `UNIQUE(a, b)` sempre passam. Como `order_id` é
  `NULL` para todo `InventoryMovement` que não seja `ORDER_CONSUMPTION`
  (`PURCHASE`/`LOSS`/`ADJUSTMENT`/`RETURN`/`CANCELLATION`/
  `INTERNAL_CONSUMPTION`), um `UNIQUE` simples nessas três colunas jamais
  rejeitaria nada para esses seis tipos de qualquer forma (o `order_id`
  deles é sempre `NULL`, e `NULL`s nunca colidem) — na prática ele
  equivaleria ao índice parcial, mas de forma enganosa: sugeriria uma regra
  uniforme sobre os sete tipos, quando na verdade só `ORDER_CONSUMPTION`
  pode ter `order_id` preenchido. O índice parcial expressa exatamente o
  que a regra significa: "nenhuma linha `ORDER_CONSUMPTION` duplicada para
  o mesmo par `(order_id, ingredient_id)`", nada sobre os outros tipos —
  `PURCHASE`/FARELO-094 permanece totalmente intocado (múltiplas linhas
  `PURCHASE` para o mesmo ingrediente, todas com `order_id = NULL`,
  continuam permitidas sem qualquer restrição nova). O índice não-único
  já existente (`idx_inventory_movement_order_id`, V21) permanece — ainda
  serve `findByOrderId` para qualquer tipo de movimento, papel que este
  novo índice (escopado a `ORDER_CONSUMPTION` e moldado como
  `(order_id, ingredient_id)`, não só `order_id`) não substitui.

  **Decisão de desenho — agregação por ingrediente dentro do mesmo pedido,
  necessária pela própria chave de idempotência, não um efeito colateral**:
  a chave dada pelo prompt mestre é `(type, orderId, ingredientId)` —
  **não** `(type, orderId, productId, ingredientId)` nem uma linha por
  linha de receita. Isso tem uma consequência direta: se dois produtos
  diferentes no mesmo pedido consomem o mesmo ingrediente (ex: um latte e
  um cappuccino, ambos usando leite), `consumeForOrder` agora **soma** a
  quantidade de todas as linhas de receita que tocam aquele ingrediente,
  para todos os itens do pedido, **antes** de gravar qualquer coisa — e
  grava **uma única** linha `ORDER_CONSUMPTION` por ingrediente, não uma
  por produto/linha de receita. Sem essa agregação, um pedido perfeitamente
  normal (dois produtos compartilhando um ingrediente) tentaria gravar duas
  linhas para o mesmo par `(orderId, ingredientId)` já na **primeira**
  chamada — o índice único parcial rejeitaria isso como se fosse uma
  tentativa de reprocessamento, quando nunca foi uma. Este comportamento é,
  portanto, parte deliberada da implementação correta de FARELO-097, não um
  workaround incidental. Não muda a matemática de quantidade em si (ainda
  `RecipeItem.quantity * OrderItemConsumption.quantity()`, negado,
  `BigDecimal` do início ao fim) — apenas onde a soma por ingrediente
  acontece (em memória, antes da gravação, via um
  `Map<UUID ingredientId, BigDecimal>` acumulado com `BigDecimal::add`).
  Nenhum teste existente de FARELO-096 tinha ingredientes compartilhados
  entre produtos no mesmo pedido, então este ajuste não altera nenhum
  resultado já coberto — um novo teste dedicado
  (`aggregatesQuantityWhenTwoProductsInSameOrderShareAnIngredient`) cobre
  esse cenário especificamente.

  **Decisão de desenho — pré-checagem por ingrediente na camada de
  serviço, não uma checagem por pedido, e por quê isso resolve conclusão
  parcial de forma segura**: antes de gravar a linha agregada de um
  ingrediente, `consumeForOrder` chama o novo
  `InventoryMovementRepository#existsByTypeAndOrderIdAndIngredientId(ORDER_CONSUMPTION,
  orderId, ingredientId)`; se já existe, aquele ingrediente é pulado — nada
  é gravado, e nada é adicionado à lista retornada. Checar por *ingrediente*
  (em vez de, por exemplo, "esse pedido já tem alguma linha
  `ORDER_CONSUMPTION`? se sim, pula a chamada inteira") é o que torna uma
  **retentativa após conclusão parcial segura e auto-corretiva**: se uma
  chamada anterior gravou a linha do ingrediente A e então falhou
  (processo morto, conexão com o banco perdida, o que for) antes de chegar
  ao ingrediente B, uma nova chamada com os mesmos argumentos recalcula as
  mesmas quantidades agregadas, encontra A já registrado (pula — sem
  dedução duplicada) e encontra B ainda faltando (grava — sem ingrediente
  permanentemente não deduzido). Nenhum dos dois modos de falha que o
  ticket pede para evitar acontece: não faz *no-op* silencioso da chamada
  inteira (que deixaria B nunca deduzido) e não falha de forma
  irrecuperável (o que deixaria o chamador sem caminho para completar). Uma
  chamada em que todo ingrediente já estava registrado é, portanto, um
  *no-op* completo: faz as mesmas consultas, não grava nada, e retorna
  lista vazia — **sucesso idempotente**, não uma exceção, porque do ponto
  de vista do chamador "já totalmente consumido" e "acabou de ser
  consumido" são o mesmo resultado observável (o estoque do pedido foi
  deduzido exatamente uma vez).

  **Por que pré-checagem em vez de capturar a violação de constraint do
  banco**: mesmo padrão já estabelecido por `RecipeService#create` e
  `RecipeItemService#create` neste mesmo pacote — ambos checam primeiro via
  consulta ao repositório (falha rápido, sem o custo de um `INSERT`
  fracassado) e dependem da constraint do banco só como reforço para uma
  corrida genuína entre duas chamadas concorrentes, sem envolver o `save`
  em `try/catch`. `consumeForOrder` segue a mesma divisão de trabalho: a
  pré-checagem cobre o caso esperado (uma retentativa/replay sequencial,
  exatamente o que FARELO-097 existe para resolver), e o índice único
  parcial cobre o caso raro (duas chamadas concorrentes para o mesmo pedido
  passando pela pré-checagem antes que qualquer uma tenha commitado) ao
  deixar o segundo `save` lançar exceção sem captura — igual ao que
  `RecipeItemService#create` já faz para sua própria constraint
  `UNIQUE(recipe_id, ingredient_id)`.

  **Contrato de retorno, atualizado por este ticket**: `consumeForOrder`
  agora retorna só as linhas que a própria chamada de fato gravou — não
  todo `ORDER_CONSUMPTION` já existente para o pedido (isso é papel de
  `InventoryMovementRepository#findByOrderId`). Uma segunda chamada para um
  pedido já totalmente consumido retorna lista vazia; uma retentativa após
  conclusão parcial retorna só as linhas dos ingredientes recém-completados
  nessa chamada. Isso é seguro para o único chamador real hoje,
  `OrderService#create`, que descarta o valor de retorno por completo.

  **Rollback de `OrderService#create` vs. essa constraint ser
  infraestrutura defensiva para um chamador futuro**: `consumeForOrder`
  continua rodando dentro da mesma transação de `OrderService#create`
  (`@Transactional` inalterado desde FARELO-096) — se uma corrida genuína
  disparasse a constraint aqui, a exceção propagaria sem captura e a
  transação inteira de criação do pedido sofreria rollback junto, mesmo
  formato "tudo ou nada" já estabelecido pelo publish do outbox
  (FARELO-060). Dito isso, a criação de pedido em si **não** é o chamador
  esperado a disparar essa constraint: `OrderService#create` sempre insere
  uma linha `Order` nova, então não existe cenário de "mesmo pedido duas
  vezes" alcançável só por esse caminho — um `orderId` recém-criado nunca
  pode já ter linhas `ORDER_CONSUMPTION` antes de sua própria primeira
  chamada a `consumeForOrder`. Esta guarda de idempotência é, portanto,
  infraestrutura defensiva para um chamador **futuro** que legitimamente
  reprocesse/repita a baixa para um `orderId` **já existente** — ex: uma
  requisição HTTP reenviada atingindo algum endpoint futuro que dispare a
  baixa de novo, um bug causando uma chamada duplicada, ou um mecanismo
  futuro de replay/reconciliação — não um cenário que o próprio ponto de
  chamada de FARELO-096 produz sozinho.

  `InventoryMovementRepository` ganha
  `existsByTypeAndOrderIdAndIngredientId(InventoryMovementType type, UUID
  orderId, UUID ingredientId)` — consulta derivada simples, usada pela
  pré-checagem acima.

  **Testes**: novos casos em `InventoryMovementRepositoryIntegrationTests`
  (nível JPA/banco real, provando o índice único parcial em si —
  rejeita duplicata `ORDER_CONSUMPTION` para o mesmo par
  `orderId`/`ingredientId`, permite duas linhas `PURCHASE` duplicadas para
  o mesmo ingrediente com `order_id = NULL`, e permite o mesmo ingrediente
  consumido por dois pedidos diferentes) e novos casos em
  `InventoryMovementServiceIntegrationTests` (chamando `consumeForOrder`
  diretamente: segunda chamada idêntica não produz movimentos adicionais,
  terceira chamada também continua segura, retentativa após conclusão
  parcial simulada só grava o ingrediente faltante, e agregação quando dois
  produtos do mesmo pedido compartilham um ingrediente). Mesmo cuidado de
  isolamento de teste já documentado nas duas classes acima — nenhuma
  limpeza cega de tabela nova foi introduzida.

  **FARELO-098 — "Criar movimento de perda"**: o terceiro produtor real de
  `InventoryMovement`, depois da entrada manual (FARELO-094) e do consumo
  de pedido (FARELO-096/097). Registra que uma quantidade de um ingrediente
  foi perdida — estragou, quebrou, foi roubada — **não** uma venda. Sempre
  cria uma linha `LOSS` com `quantity` **negativa** (saída de estoque, mesma
  convenção de sinal já documentada acima para `quantity`) e sem `orderId`
  (não tem origem em pedido — ver a seção "Campo de origem/idempotência"
  acima: só `ORDER_CONSUMPTION`, e plausivelmente `RETURN`/`CANCELLATION`,
  preenchem essa coluna).

  `InventoryMovementService` ganha `recordLoss(UUID ingredientId,
  BigDecimal quantity)` — um método novo e separado, **sem** modificar
  `create(UUID, BigDecimal)` (FARELO-094, entrada manual/`PURCHASE`) nem
  `consumeForOrder(UUID, List)` (FARELO-096/097). Mesmo formato de validação
  de `create`: o ingrediente precisa existir primeiro (404
  `IngredientNotFoundException` antes de qualquer outra coisa, mesma
  ordem/motivo já documentado para `create`/`listByIngredient`/`getBalance`
  acima), e `quantity > 0` é responsabilidade do `@Positive` na camada de
  request, não re-checado no service. A diferença real de `create` é
  puramente o tipo (`LOSS`, não `PURCHASE`) e o sinal — `recordLoss` recebe
  `quantity` como uma magnitude **positiva** ("quanto foi perdido") e é o
  único lugar que faz `quantity.negate()` antes de construir a linha; quem
  chama nunca codifica o sinal diretamente, mesma divisão de trabalho já
  estabelecida por `InventoryMovement`'s javadoc ("cada produtor decide seu
  próprio sinal ao construir a linha").

  Endpoint: `POST /api/v1/ingredients/{ingredientId}/losses` — um irmão de
  `.../movements`/`.../balance`, aninhado da mesma forma, e deliberadamente
  um endpoint próprio em vez de um campo `type` em `POST .../movements`
  (mesmo raciocínio já documentado no javadoc de `InventoryMovementRequest`
  para por que aquele endpoint é só `PURCHASE`: deixar o cliente escolher o
  tipo abriria caminho pra submeter `ORDER_CONSUMPTION`/`LOSS`/etc por uma
  URL que não tem nada a ver com pedidos ou perdas). Request DTO próprio,
  `InventoryLossRequest` (`com.farelo.api.inventory.web`) — **não**
  reutiliza/modifica `InventoryMovementRequest`, mesma separação de
  responsabilidade por fluxo. `quantity` é `@NotNull @Positive`, mesma
  convenção de validação de `InventoryMovementRequest#quantity()` — o
  cliente sempre envia uma magnitude positiva ("perdemos 250 G"), nunca um
  valor já negativo; o sinal que efetivamente vai pro ledger é um detalhe de
  servidor (`InventoryMovementService#recordLoss`), não algo que o corpo da
  requisição codifica. Reutiliza `InventoryMovementResponse` pra resposta
  (já tinha `from(InventoryMovement)` genérico o suficiente — nenhum campo
  novo precisou ser adicionado a ela).

  **Decisão de desenho — sem campo `reason`/`note`, nem no request nem na
  entidade**: considerado e deliberadamente deixado fora do escopo deste
  ticket. Nem o prompt mestre (seção 13, que só nomeia `LOSS` como um dos
  sete valores de `InventoryMovementType`, sem mencionar nenhum campo de
  motivo/observação no movimento em si) nem o javadoc do próprio `LOSS` em
  `InventoryMovementType` ("stock removed for spoilage/breakage/theft, not
  a sale" — uma descrição do *tipo*, não um pedido de campo livre) pedem
  isso. Adicionar uma coluna na tabela `inventory_movement` (append-only,
  compartilhada por todos os sete tipos) para um único produtor novo, sem
  nenhum consumidor concreto pra ler esse valor de volta, seria exatamente
  o tipo de adição especulativa que o precedente de tickets deste código
  evita — mesmo raciocínio YAGNI já aplicado a `Ingredient` não carregar
  `currentStock`/`minimumStock`/`criticalStock` até o FARELO-099 precisar
  deles. Se um ticket futuro precisar de motivo/trilha de auditoria pra uma
  perda, essa é a migration/coluna daquele ticket — adiar isso aqui não
  custa nada hoje (nenhuma migration, nenhum campo não usado em toda linha
  de `InventoryMovement` de todo outro tipo).

  Nenhuma alteração em `InventoryMovementRequest`/`POST .../movements`
  (FARELO-094) — endpoint distinto, para um tipo de movimento distinto,
  deliberadamente não tocado por este ticket.

  **Testes**: novos casos em `InventoryMovementControllerIntegrationTests`
  (HTTP real via `MockMvc`, cobrindo sucesso — linha `LOSS` com `quantity`
  negada e `orderId` nulo —, `404 INGREDIENT_NOT_FOUND`, `400
  VALIDATION_ERROR` para `quantity` zero/negativa/ausente, e um cenário
  ponta-a-ponta confirmando que `GET .../balance` reflete a perda —
  compra de 5000 G seguida de perda de 800 G resulta em saldo 4200 G) e
  novos casos em `InventoryMovementServiceIntegrationTests` (chamando
  `recordLoss` diretamente: linha persistida com tipo/sinal/`orderId`
  corretos, 404 pra ingrediente inexistente, e o saldo derivado — via
  `getBalance`, não HTTP — refletindo `create` seguido de `recordLoss`).
  Mesmo cuidado de isolamento de teste já documentado nas classes acima —
  cada teste cria seu próprio ingrediente com nome único, nenhuma limpeza
  cega de tabela compartilhada foi introduzida; como `LOSS` nunca tem
  `orderId`, nenhum destes testes novos precisou de um `Command`/`Order`
  seedado (diferente dos testes de `ORDER_CONSUMPTION` acima).

  ### FARELO-099 — Criar estoque mínimo

  Prompt mestre seção 17: "`Ingredient`: `currentStock`, `minimumStock`,
  `criticalStock`. Eventos: `STOCK_LOW`, `STOCK_CRITICAL`, `OUT_OF_STOCK`."
  Este ticket é só a metade "dado" dessa seção — adiciona o campo
  `minimumStock` a `Ingredient` e expõe se um ingrediente está abaixo dele,
  agora. **Sem `criticalStock`** (ticket futuro, não numerado ainda — este
  ticket é literalmente "Criar estoque mínimo", não "estoque
  mínimo/crítico") e **sem nenhum evento outbox** (`STOCK_LOW`/
  `STOCK_CRITICAL` são FARELO-100/`OUT_OF_STOCK` é FARELO-101, ambos fora do
  escopo aqui de propósito — este ticket só produz o dado e o cálculo que
  esses eventos futuros vão reagir a, sem nenhum efeito colateral/publish).

  **`Ingredient.minimumStock`** (novo campo, `BigDecimal`, coluna
  `minimum_stock NUMERIC(12,3)`, migration
  `V24__add_ingredient_minimum_stock_column.sql`): **nullable, sem valor
  default — `NULL` significa "nenhum limite configurado ainda"**, uma
  decisão de desenho deliberada, não a alternativa mencionada no ticket
  ("ou defaultar para zero"). Motivo: `NULL` e `0` significam coisas
  diferentes e ambas são estados legítimos e distinguíveis — um limite
  configurado como `0` é uma escolha real de um operador ("avise assim que
  o saldo for negativo"), enquanto `NULL` é "ninguém decidiu um limite para
  este ingrediente ainda". Defaultar automaticamente para `0` faria todo
  ingrediente existente (e todo ingrediente novo criado sem o campo)
  mentir sobre isso — pareceria que alguém escolheu deliberadamente `0`
  como limite, quando na verdade ninguém configurou nada. Essa escolha lê
  bem contra o desenho já existente de `InventoryMovementService#getBalance`/
  `IngredientBalance` (FARELO-095): aquele saldo já usa `0` (nunca `null`)
  para "sem movimentos ainda" — um estado real e calculado — então usar
  `NULL` aqui para "sem limite configurado" mantém os dois conceitos
  claramente distintos em vez de sobrecarregar `0` com dois significados
  diferentes em dois campos vizinhos. Mesma coluna `NUMERIC(12,3)` de
  `RecipeItem.quantity`/`InventoryMovement.quantity`, para comparar
  diretamente contra um saldo calculado sem qualquer mismatch de escala.
  `CHECK (minimum_stock IS NULL OR minimum_stock >= 0)` no nível do banco
  (defesa em profundidade — não há uma única DTO de request que cubra todo
  caminho de escrita futuro desta coluna) mais `@DecimalMin(value =
  "0.00")` (sem `@NotNull`, já que `null` é um valor válido) nos DTOs de
  request. Ver javadoc do campo em `Ingredient.java` para o raciocínio
  completo.

  **Sem `currentStock`**: esse conceito já existe — é o saldo derivado do
  ledger (`InventoryMovementService#getBalance`, FARELO-095), nunca uma
  coluna própria armazenada/mutável (prompt mestre seção 13: "O saldo deve
  ser rastreável... nunca editado diretamente"). Adicionar uma coluna
  `currentStock` redundante seria reintroduzir exatamente o anti-padrão que
  o ledger existe para evitar.

  **Endpoint — dobrado em `POST`/`PUT /api/v1/ingredients`, não um endpoint
  dedicado**: o ticket deu a escolha de desenho em aberto (endpoint próprio,
  ex: `PATCH .../{id}/minimum-stock`, vs. dobrar no fluxo de update
  existente) condicionada a checar `IngredientController`/
  `IngredientUpdateRequest` primeiro. `Ingredient` já usa `PUT
  /api/v1/ingredients/{id}` como substituição completa de vários campos
  (`name`/`unit`/`active`) — diferente de `Recipe`/FARELO-091, cujo `PATCH
  /{id}/deactivate` existe porque `Recipe` só tinha *um* campo mutável
  (`active`), tornando um endpoint de substituição completa um alias inútil
  do mesmo campo único. `Ingredient` não está nessa situação: já é dono de
  um `PUT` de substituição completa genuíno, então adicionar mais um campo
  a ele é a extensão natural, não uma invenção de endpoint novo. O
  precedente real e já mergeado para "campo opcional, sem default óbvio,
  que uma `PUT` de substituição completa precisa poder enviar como `null`
  para limpar" é `ProductUpdateRequest.productionStation`/
  `ProductRequest.productionStation` (FARELO-073) — `minimumStock` segue
  exatamente essa mesma forma em `IngredientUpdateRequest`/
  `IngredientRequest`: opcional em ambos os DTOs (inclusive na criação, já
  que também não há um valor default inequívoco para "assumir" quando
  omitido), sem `@NotNull`, e uma `PUT` que omite o campo o envia como
  `null` — limpando explicitamente um limite configurado anteriormente, não
  deixando-o inalterado (mesma semântica de substituição completa que
  `name`/`unit`/`active` já têm). `IngredientService#create`/`#update`
  ganham o parâmetro `minimumStock` correspondente; nenhum construtor novo
  de `Ingredient` foi necessário — o construtor de dois argumentos existente
  já deixa o campo `null` por padrão, então ambos os métodos do service só
  chamam `setMinimumStock(...)` depois. `IngredientResponse` também ganha
  `minimumStock` (o mesmo padrão de "toda outra coluna simples de
  `Ingredient` aparece na resposta").

  **`GET /api/v1/ingredients/{ingredientId}/balance` ganha `belowMinimum`
  (boolean)** — a exposição de "este ingrediente está abaixo do limite
  agora", pedida explicitamente pelo ticket. Lugar natural: este endpoint
  (FARELO-095) já calcula um saldo ao vivo a partir do ledger, e o limite
  contra o qual comparar (`Ingredient.minimumStock`) pertence ao mesmo
  ingrediente que a resposta já é sobre — nenhuma segunda consulta/endpoint
  é necessária. A computação em si vive em `IngredientBalance` (pacote de
  domínio `com.farelo.api.inventory`, não `.web`), um novo método
  `isBelowMinimum()` no record já existente — não em
  `InventoryMovementService#getBalance` nem em nenhum outro método
  restrito pelo ticket (`create`/`consumeForOrder`/`recordLoss` não foram
  tocados). `IngredientBalanceResponse.from` só encaminha
  `ingredientBalance.isBelowMinimum()` para o novo campo `belowMinimum` da
  resposta.

  **Semântica exata de `isBelowMinimum()`**: `false` quando
  `ingredient.getMinimumStock() == null` (sem limite configurado — "nunca é
  reportado como baixo", exatamente o requisito de teste explícito do
  ticket, **incluindo saldo negativo**: `InventoryMovementService#
  consumeForOrder`'s "sem checagem de saldo suficiente" (FARELO-096/097) já
  permite saldo negativo hoje, e um ingrediente sem limite configurado
  continua retornando `false` mesmo nesse caso). Quando há limite
  configurado, comparação estritamente menor (`balance.compareTo(minimumStock)
  < 0`) — um saldo exatamente **igual** ao limite não é "abaixo" dele (lê
  como "no limite", não uma violação), mesma leitura natural de "abaixo" em
  português/inglês cotidiano.

  **Testes**: novos casos em `IngredientControllerIntegrationTests`
  (`POST`/`PUT` persistindo `minimumStock` corretamente — presente,
  ausente na criação, limpo para `null` numa atualização subsequente — e
  `400 VALIDATION_ERROR` para valor negativo em ambos os verbos) e novos
  casos em `InventoryMovementControllerIntegrationTests` (`GET .../balance`
  reportando `belowMinimum` corretamente nos três casos — saldo abaixo, no
  exato limite, e acima — mais um ingrediente sem limite configurado nunca
  reportando `belowMinimum: true`, mesmo com saldo negativo alcançado via
  `PURCHASE` seguido de `ORDER_CONSUMPTION` maior, e mais o caso trivial de
  saldo `0`/sem movimentos/sem limite). Mesmo cuidado de isolamento de
  teste do restante deste arquivo: nenhuma limpeza cega de tabela
  compartilhada foi introduzida; a nova sobrecarga do helper
  `createIngredient(name, unit, minimumStock)` só configura o campo antes
  de salvar, sem mudar o helper de dois argumentos já usado por todos os
  outros testes da classe.

## security

Pacote: `com.farelo.api.security`.

- **`User`** (FARELO-120): entidade JPA — `id` (UUID, mesma estratégia dos
  demais domínios), `name`, `email` (obrigatório, **único**, backed por
  `uk_app_user_email` — será o identificador de login quando FARELO-121
  existir, mesmo que o mecanismo de login em si ainda não exista),
  `passwordHash`, `role` (enum `UserRole`, ver decisão dedicada abaixo),
  `active` (default `true`, mesmo padrão de `Category`/`Product`/`Printer`/
  `Ingredient` — permite desativar um funcionário sem apagar histórico),
  `createdAt`/`updatedAt` (UTC). Tabela `app_user` (não `user` — palavra
  reservada em SQL, mesmo raciocínio já usado para `orders` vs. `order` no
  domínio `ordering`) criada pela migration `V20__create_user_table.sql`.

  **Escopo deste ticket, explicitamente**: só a entidade `User` em si e seu
  CRUD básico — o cadastro de quem *pode* existir no sistema. **Nada aqui
  autentica ninguém**: sem login, sem verificação de senha, sem
  tokens/sessão, sem Spring Security habilitado, sem nenhum endpoint
  protegido (nem os já existentes, nem os novos deste próprio ticket). Isso
  é o Epic 9 completo do prompt mestre (seção 26/47): FARELO-121
  (autenticação), FARELO-122 (RBAC), FARELO-123/124 (proteger Admin/PDV) —
  todos tickets futuros e distintos.

  **`passwordHash` — decisão de algoritmo e de dependência**: sempre um hash
  BCrypt (`BCryptPasswordEncoder`), nunca texto plano — `UserService` é o
  único escritor do campo e sempre hasheia antes de persistir; a senha em
  texto plano recebida no `POST`/`PATCH .../password` nunca é logada em
  lugar nenhum (nem em log de erro, nem em mensagem de exceção — ver
  `UserEmailAlreadyExistsException`, que carrega só o email).

  A pergunta em aberto era **de onde vem o encoder**: trazer
  `spring-boot-starter-security` agora (a dependência que FARELO-121 vai
  precisar de qualquer forma para autenticação de verdade) ou esperar por
  aquele ticket. **Decisão: nenhuma das duas — trazer só
  `spring-security-crypto`** (o módulo standalone de `BCryptPasswordEncoder`,
  sem depender de `spring-security-core`/`-web`/`-config`). Motivo decisivo:
  o starter completo autoconfigura, só por estar no classpath, uma cadeia de
  segurança padrão que exige autenticação em **toda** requisição (login
  gerado, senha aleatória no log de boot) a menos que um
  `SecurityFilterChain` diga o contrário — isso quebraria instantaneamente
  todo endpoint já existente no projeto (categorias, produtos, comandas,
  pedidos, print jobs, ingredientes, receitas — nenhum protegido hoje, por
  desenho, até FARELO-123/124), obrigando este ticket a escrever uma
  configuração `permitAll()` só para não quebrar a suíte — ou seja,
  pré-decidir o desenho de FARELO-121/123 dentro de um ticket que as
  instruções explicitamente pedem para não tocar nisso. `spring-security-crypto`
  evita esse efeito colateral por completo (a autoconfiguração do Spring
  Boot é condicionada a classes dos outros módulos, ausentes aqui), resolve
  "decidir a biblioteca de hash duas vezes" (FARELO-121 reaproveita o mesmo
  bean `PasswordEncoder`), e mantém este ticket mínimo. Ver javadoc de
  `PasswordEncoderConfig` para o raciocínio completo.

  **`role` — decisão de incluir agora**: a seção 26 do prompt mestre já
  nomeia literalmente um conjunto fechado de cinco perfis (`ADMIN`,
  `MANAGER`, `CASHIER`, `KITCHEN`, `ATTENDANT`). Diferente de adivinhar um
  desenho que FARELO-122 (RBAC) ainda não decidiu, transcrever essa lista já
  dada é o mesmo raciocínio já aplicado a `ProductionStation`
  (`BAR`/`KITCHEN`, literal do prompt mestre para FARELO-073). **Decisão:
  incluir `role` agora** como coluna `VARCHAR` + `CHECK` (mesma convenção de
  `IngredientUnit`/`ProductionStation`/`CommandStatus`) — nada lê ou aplica
  esse campo ainda (nenhuma checagem de permissão em lugar nenhum do
  código), então isso é só rótulo de dado, não RBAC. Ver javadoc de
  `UserRole` para a lista completa de motivos e para o que
  **deliberadamente não** foi antecipado (nenhum mapeamento
  papel→permissão — a seção 21 lista "Usuários" e "Permissões" como módulos
  Admin separados, e um modelo de permissões mais granular, se algum dia for
  necessário, é decisão de FARELO-122, não deste ticket).

  `UserRepository` expõe `findByEmail` (Spring Data) — chave natural da
  entidade (mesmo raciocínio de `RecipeRepository#findByProductIdAndActiveTrue`),
  usada por `UserService` para o pré-check de unicidade e reaproveitável por
  FARELO-121 para localizar o usuário no login.

CRUD REST em `/api/v1/users` (`UserController`, pacote `security.web`):

- `POST /api/v1/users` — cria um usuário (`name`/`email`/`password`/`role`
  em texto plano no request, hasheado pelo `UserService` antes de
  persistir). `409 USER_EMAIL_ALREADY_EXISTS` se o email já existir.
- `GET /api/v1/users` / `GET /api/v1/users/{id}` — lista (ordenada por
  `name`, sem filtro `active`-only ainda, mesma YAGNI de
  `Category`/`Ingredient`) e busca por id (`404 USER_NOT_FOUND`).
- `PUT /api/v1/users/{id}` — substituição completa de
  `name`/`email`/`role`/`active` (mesmo formato de
  `PUT /api/v1/ingredients/{id}`, `active` como `Boolean` para forçar envio
  explícito). Reverifica unicidade de email (ignorando o próprio usuário).
- `PATCH /api/v1/users/{id}/password` — endpoint **separado** para trocar
  senha, decisão deliberada em vez de incluir a senha no `PUT` geral: trocar
  senha tem implicações de segurança distintas de editar nome/email. **Sem
  exigir a senha atual**: como o mecanismo de login/autenticação
  (FARELO-121) ainda não existe, não há nenhuma identidade autenticada de
  chamador para validar contra — pedir a senha atual hoje seria só mais um
  campo em texto plano que este mesmo endpoint teria que também nunca logar,
  sem nada real para checar. Revisitar quando FARELO-121 existir: um fluxo
  de "trocar minha própria senha" provavelmente vai querer confirmar a senha
  atual (ou reautenticação), enquanto um reset feito por um admin para outra
  conta normalmente não — essa distinção não existe ainda hoje (existe
  exatamente uma forma de chamador: um cliente HTTP autenticado-em-nada
  batendo na API do Admin), então não foi adivinhada aqui.

**`UserResponse` nunca inclui `passwordHash`** — em nenhum dos cinco
endpoints acima, sem exceção (mais crítico que o padrão usual de "nunca
expor entidade JPA direto", já que aqui o campo é literalmente segredo).
Confirmado por teste dedicado (`neverExposesPasswordHashInAnyResponse`,
`UserControllerIntegrationTests`), que bate em todos os cinco endpoints e
verifica a ausência do campo no JSON de cada resposta — em vez de apenas
assumir que omitir o campo do record é suficiente. Outro teste dedicado
(`createsUserAndPersistsIt`) prova que o valor persistido nunca é igual à
senha em texto plano enviada e que verifica com sucesso através do encoder
real (BCrypt) — prova de que o hash está sendo aplicado de verdade, não só
"alguma string diferente".

Ver `docs/api.md` para os cinco endpoints completos.

### FARELO-121 — Autenticação (login + emissão/verificação de token)

Primeiro mecanismo real de autenticação do sistema: dado email+senha,
verifica contra `User`/`BCryptPasswordEncoder` (FARELO-120, já mergeado) e
emite um token que o cliente reapresenta nas próximas requisições. Endpoint
único: `POST /api/v1/auth/login` (`AuthController`, pacote `security.web`).
Ver `docs/api.md` para o endpoint completo.

**Escopo deste ticket, explicitamente**: só login + emissão/verificação de
token. RBAC (FARELO-122) e proteger os endpoints Admin/PDV (FARELO-123/124)
são tickets futuros e distintos — **nenhum endpoint, nem os já existentes
nem o `POST /api/v1/auth/login` deste próprio ticket, passa a exigir token
depois deste ticket**. É esperado e correto que, ao final, seja possível
obter um token válido sem que nada ainda o exija.

**Decisão de dependência — por que não `spring-boot-starter-security`**:
mesmo raciocínio já registrado em `PasswordEncoderConfig` (FARELO-120): o
starter completo autoconfigura uma cadeia de segurança que exige
autenticação em toda requisição assim que está no classpath, quebrando
instantaneamente todo endpoint hoje desprotegido por desenho — efeito
colateral que pré-decidiria o desenho de FARELO-123/124 dentro deste
ticket. Em vez disso, este ticket adiciona **apenas uma biblioteca de JWT
focada** (`io.jsonwebtoken:jjwt-api`/`-impl`/`-jackson`, versão `0.12.6`) —
sem dependência de `spring-security-core`/`-web`/`-config`, sem
autoconfiguração de filtro algum. `JwtTokenService`
(`com.farelo.api.security.auth`) constrói/valida o token com uma função
pura, sem depender da máquina de `AuthenticationManager` do Spring
Security — não há necessidade dela para o escopo deste ticket.

**Formato do token — JWT assinado (JWS), não um token opaco com tabela de
sessão**: duas formas foram consideradas — (a) um token aleatório opaco
persistido em uma nova tabela (`session`/`auth_token`), consultado a cada
requisição; ou (b) um JWT autocontido, verificado localmente por assinatura,
sem round-trip ao banco. **Decisão: (b)**. Motivos: nenhuma migration nova
é necessária (uma tabela de sessão é exatamente o tipo de decisão que
FARELO-122/123/124 deveriam tomar quando de fato precisarem de revogação,
não algo a adivinhar aqui); e a verificação futura (o filtro/interceptor que
FARELO-123/124 vão escrever) não precisa tocar o banco — só uma checagem
pura de assinatura/expiração, então FARELO-123/124 herdam uma função
(`JwtTokenService#parse`) sem acoplamento a infraestrutura de request que
ainda não existe.

**Assinatura — HMAC-SHA256 (HS256), segredo único simétrico**: escolhido
sobre um algoritmo assimétrico (ex. RS256/par de chaves) porque é o próprio
backend que emite e verifica seus tokens — nenhum outro serviço precisa
verificar um token sem também ter acesso ao segredo, então a complexidade
extra de gerenciar um par de chaves não compra nada aqui (revisitar apenas
se um serviço externo algum dia precisar verificar tokens que não emitiu).
O segredo (`security.jwt.secret`, `application.yml`) vem de variável de
ambiente (`JWT_SECRET`), no mesmo padrão `${ENV_VAR:default}` de
`spring.datasource.password`/`whatsapp.api.access-token` — nunca hardcoded,
nunca logado. O default em `application.yml` é deliberadamente inseguro
(mesmo espírito do `change-me` de `spring.datasource.password`), só longo o
suficiente (61 bytes, acima do mínimo de 256 bits/32 bytes exigido pelo
HS256) para o app subir em dev/test sem configuração adicional.

**Expiração — sim, configurável, default de 8 horas**: um token que nunca
expira significa que um token vazado (ou de um funcionário desligado, ver
`User#active`) fica válido para sempre, sem forma de desligá-lo a não ser
trocar o segredo de assinatura para todo mundo de uma vez.
`security.jwt.expiration-minutes` (default `480` = 8h, um turno de
trabalho) limita essa janela de exposição sem forçar reautenticação no meio
do turno.

**O que este ticket deliberadamente NÃO faz — sem revogação/logout, sem
refresh token**: um JWT stateless não pode ser invalidado antes do próprio
`exp` sem reintroduzir uma consulta ao banco por requisição (uma blocklist
server-side), o que anularia o motivo de ter escolhido JWT sobre token
opaco+tabela. A expiração de 8h é toda a mitigação por enquanto — pior que
revogação instantânea, mas aceitável para o primeiro ticket de autenticação,
quando nenhum endpoint sequer checa o token ainda (FARELO-123/124).
Revisitar se/quando surgir um requisito real de "deslogar este dispositivo
agora".

**Verificação de credenciais — `AuthenticationService#login`** (pacote
`security`, ao lado de `UserService`): reaproveita `UserRepository#findByEmail`
e o mesmo bean `PasswordEncoder` de `PasswordEncoderConfig` (nenhuma
dependência de hashing nova). Falha com `InvalidCredentialsException` —
sempre a mesma mensagem genérica ("Invalid credentials"), sem carregar
email nem motivo — em três casos, deliberadamente indistinguíveis pela
resposta: email não existe; senha errada; usuário existe e senha está
certa, mas `active = false` (funcionário desligado não deve conseguir
token novo mesmo lembrando a senha). **Proteção contra enumeração por
tempo de resposta**: quando o email não existe, o serviço ainda executa
`passwordEncoder.matches` contra um hash BCrypt "dummy" pré-computado (uma
vez, no construtor, com o encoder real injetado), descartando o resultado —
sem isso, um email inexistente responderia imediatamente enquanto um email
real sempre pagaria o custo (deliberadamente alto) do BCrypt, uma diferença
de tempo que um atacante poderia usar para enumerar contas válidas.

Mapeado em `ApiExceptionHandler` (`INVALID_CREDENTIALS`, `401
Unauthorized`) — mesmo padrão de handler das demais exceções de domínio,
mas contrastando deliberadamente com `UserNotFoundException` (mensagem
específica é aceitável ali, é um contexto de CRUD administrativo já
autenticado-em-nada; aqui, qualquer detalhe vazaria existência de conta).

`LoginRequest` valida apenas `@NotBlank` em `email`/`password` — sem
`@Email`/`@Size`, deliberadamente diferente de `UserCreateRequest`: validar
formato/tamanho aqui separaria "credenciais erradas" em duas respostas
distintas (400 `VALIDATION_ERROR` vs. 401 `INVALID_CREDENTIALS`) para o
mesmo fato subjacente (login nunca vai funcionar), o oposto do objetivo de
"todo tipo de erro parece igual" deste endpoint.

Testes: `AuthControllerIntegrationTests` (login com sucesso emite token;
senha errada e email desconhecido retornam exatamente o mesmo
status/código/mensagem — prova de que os dois casos são mesmo
indistinguíveis, não só "algum 401" cada um; usuário inativo com senha
correta também falha) e `JwtTokenServiceTests` (unitário, sem Spring/
Postgres: token emitido é validado de volta para a mesma identidade,
`expiresAt` bate com o `expiration-minutes` configurado, assinatura com
segredo diferente é rejeitada, token malformado é rejeitado, token já
expirado é rejeitado).

### FARELO-122 — RBAC (mecanismo de enforcement, ainda não aplicado a nada)

**Este ticket é só o mecanismo, não a política.** Dado um request com um JWT
(emitido por `POST /api/v1/auth/login`, FARELO-121), este ticket constrói
(1) como resolver identidade/role do chamador a partir do token e (2) uma
forma declarativa de um método de controller dizer "só estes `UserRole`
podem chamar isto" e ter isso de fato aplicado (`401` sem token válido,
`403` com token válido mas role não permitida). **Nenhum endpoint real
passa a exigir token depois deste ticket** — decidir *quem* pode chamar
*qual* endpoint real é explicitamente FARELO-123 (superfície Admin) e
FARELO-124 (superfície PDV/cozinha), ambos tickets futuros e distintos.
Pacote: `com.farelo.api.security.rbac`.

**Decisão de mecanismo — `HandlerInterceptor` do Spring MVC puro, ainda sem
`spring-boot-starter-security`**: mesma restrição já registrada em
`PasswordEncoderConfig` (FARELO-120) e `JwtTokenService` (FARELO-121) — o
starter completo autoconfigura, só por estar no classpath, uma cadeia de
segurança que exige autenticação em toda requisição a menos que um
`SecurityFilterChain` diga o contrário, o que quebraria instantaneamente
todo endpoint hoje desprotegido (por desenho, até FARELO-123/124) e
obrigaria este ticket a pré-decidir a política de `permitAll()` que
justamente pertence àqueles tickets futuros. Em vez disso: um
`HandlerInterceptor` (`RoleAuthorizationInterceptor`) registrado via
`WebMvcConfigurer` (`RbacWebMvcConfig`), sem dependência nova nenhuma —
`JwtTokenService#parse` (já existente, FARELO-121) faz 100% do trabalho de
verificação; esta classe é só a fiação HTTP em torno dele (extrair header,
localizar a anotação, comparar role, converter falha em status HTTP).

**`@RequireRole(UserRole... value)`** — anotação (`RequireRole`,
`@Target({TYPE, METHOD})`) que marca um método de controller (ou uma classe
inteira, valendo para todo método sem anotação própria) como restrito a uma
lista de `UserRole`. **Nível de método tem prioridade sobre nível de
classe** quando os dois existem — nunca há união das duas listas, sempre a
anotação mais próxima do método vence. Nenhum controller de produção usa
esta anotação hoje (ver lista completa abaixo).

**Pipeline do `RoleAuthorizationInterceptor#preHandle`**:

1. Handler não é um `HandlerMethod` (ex.: recurso estático) → passa direto.
2. Nem o método nem a classe declarante têm `@RequireRole` → passa direto,
   **sem nunca ler o header `Authorization`** — este é o ponto central da
   fronteira de escopo deste ticket: qualquer endpoint hoje existente
   (catálogo, comanda, pedido, impressão, estoque, notificação,
   usuários/auth) continua acessível exatamente como está, com ou sem
   header, porque nenhum deles carrega a anotação.
3. Header `Authorization` ausente ou não no formato `"Bearer <token>"` →
   `InvalidTokenException` (já existia, FARELO-121, até então não
   conectada a nenhum handler) → `ApiExceptionHandler` mapeia para `401
   Unauthorized` / código `UNAUTHENTICATED`.
4. Header presente mas `JwtTokenService#parse` rejeita o token (assinatura
   inválida, malformado, expirado) → mesma `InvalidTokenException` → mesmo
   `401`. Deliberadamente o mesmo resultado de "sem header nenhum": das
   duas perspectivas o chamador simplesmente não está autenticado.
5. Token válido, mas `AuthenticatedPrincipal#role()` não está em
   `@RequireRole#value()` → `InsufficientRoleException` (nova, pacote
   `security.rbac`) → `ApiExceptionHandler` mapeia para `403 Forbidden` /
   código `FORBIDDEN`. Diferente do passo 4: aqui a identidade do chamador
   **é** conhecida, ele só não tem permissão para esta operação.
6. Sucesso: o `AuthenticatedPrincipal` é guardado como atributo do request
   e a chamada segue normalmente.

**Tornando o principal disponível ao controller**: um método protegido pode
simplesmente declarar um parâmetro do tipo `AuthenticatedPrincipal` e
recebê-lo já resolvido — `AuthenticatedPrincipalArgumentResolver`
(`HandlerMethodArgumentResolver`, também registrado por `RbacWebMvcConfig`)
lê o atributo de request deixado pelo interceptor. Mesmo espírito do
`@AuthenticationPrincipal` do Spring Security, sem precisar da anotação
(bastou casar pelo tipo, já que nada mais no projeto teria motivo para
receber um parâmetro `AuthenticatedPrincipal`).

**Nenhum path allowlist/denylist** — o interceptor é registrado sem
`addPathPatterns`, válido para toda a aplicação; a decisão de agir ou não é
inteiramente dirigida pela presença de `@RequireRole` no handler resolvido
pelo Spring MVC, nunca por padrão de URL. Isso evita duas classes de erro
opostas: esquecer de proteger um endpoint novo sob um prefixo já
restrito, e — o que mais importa para a fronteira deste ticket — proteger
sem querer um endpoint existente que nunca deveria ter sido tocado.

**Fronteira de escopo — explicitamente nada está protegido ainda**:
nenhum controller de produção (`CategoryController`, `ProductController`,
`CommandController`, `OrderController`, `PrintJobController`,
`IngredientController`, `RecipeController`, `UserController`,
`AuthController`, `NotificationController` — todo controller existente
neste ticket) usa `@RequireRole`. A única prova do mecanismo funcionando é
via um controller dedicado a teste (`RbacDemoTestController`), que vive em
`src/test/java` e nunca é empacotado com a aplicação — decidir *quem* pode
chamar *qual* endpoint real é FARELO-123 (Admin) / FARELO-124 (PDV/cozinha).

Testes:

- `RoleAuthorizationInterceptorTests` — unitário, sem Spring/Postgres:
  `preHandle` chamado diretamente com `JwtTokenService` mockado e
  `HandlerMethod`s reais (não mockados — a resolução de anotação faz
  reflection de verdade) sobre classes fixture locais. Cobre: handler que
  não é `HandlerMethod`; nem classe nem método anotados; header ausente;
  header malformado; token rejeitado por `JwtTokenService`; role não
  permitida; role permitida (e o `AuthenticatedPrincipal` é de fato
  guardado no atributo do request); anotação de método sobrepondo a de
  classe (não união).
- `RoleAuthorizationInterceptorIntegrationTests` — `@SpringBootTest` +
  `MockMvc` real batendo em `RbacDemoTestController`
  (`@RequireRole(UserRole.ADMIN)`): sem header → `401`/`UNAUTHENTICATED`;
  token malformado → `401`; token de usuário com role errada → `403`/
  `FORBIDDEN`; token de usuário `ADMIN` → `200`, e o corpo da resposta
  confirma que o `AuthenticatedPrincipal` injetado é o do usuário correto.
- `RoleAuthorizationInterceptorRegressionIntegrationTests` — **o teste que
  prova a fronteira de escopo de fato se sustentou**: com o interceptor
  registrado globalmente, `GET /api/v1/categories` (catálogo),
  `GET /api/v1/ingredients` (estoque) e `GET /api/v1/notifications`
  (notificação) — três domínios não relacionados — continuam retornando
  `200` sem nenhum header `Authorization`, exatamente como antes deste
  ticket. **Estendida pelo FARELO-123** (ver subseção abaixo) com
  `GET /api/v1/products` (prova que a fronteira dentro do próprio
  `ProductController` — `create`/`update` protegidos, `list` não — se
  sustenta) e `GET /api/v1/orders` (prova que o escopo de três controllers
  do FARELO-123 não vazou para `ordering`).

### FARELO-123 — Proteger Admin (aplicação do RBAC à superfície Admin)

**Primeiro ticket a de fato restringir acesso**: FARELO-122 construiu só o
mecanismo (`@RequireRole`/`RoleAuthorizationInterceptor`), aplicado a zero
endpoints de produção. Este ticket decide *quem* pode chamar *qual*
endpoint real, para exatamente três controllers — `CategoryController`,
`ProductController` (`catalog.web`) e `UserController` (`security.web`) —
os módulos "Produtos", "Categorias" e "Usuários" da seção 21 do prompt
mestre (Admin). Fora de escopo, explicitamente: qualquer outro controller
(`CommandController`, `OrderController`, `PrintJobController`,
`IngredientController`, `RecipeController`, `NotificationController`,
`AuthController`) — a superfície PDV/cozinha é FARELO-124, um ticket
futuro e distinto.

**Perfis usados**: dos cinco definidos em `UserRole` (FARELO-120) —
`ADMIN`, `MANAGER`, `CASHIER`, `KITCHEN`, `ATTENDANT` — só `ADMIN` e
`MANAGER` aparecem em algum `@RequireRole` abaixo. `CASHIER`/`KITCHEN`/
`ATTENDANT` são perfis operacionais de PDV/cozinha (FARELO-124, ainda não
decidido) e não têm hoje nenhum endpoint que os exija — eles só aparecem
neste ticket como o "papel errado" nos testes de `403`.

**`CategoryController`/`ProductController` — o achado central: `GET` fica
público, só `POST`/`PUT` viram Admin**. A primeira leitura óbvia do
ticket ("catálogo é Admin") sugeriria proteger o controller inteiro, mas
o prompt mestre (EPIC 3, FARELO-042/043; seção 2, domínio
`pedido.farelo.com.br`) já documenta que `GET /api/v1/categories` e
`GET /api/v1/products` são consumidos pelo **Cardápio QR — um cliente
anônimo, sem login/conta de qualquer tipo, escaneando o QR da mesa**.
Proteger esses dois `GET`s não os restringiria a um papel interno mais
adequado; quebraria esse fluxo público por completo, que está totalmente
fora do que "superfície Admin" significa. A superfície Admin de
catálogo é *autorá-lo* (criar/editar categorias e produtos), não
*lê-lo* — então:

- `POST /api/v1/categories` (criar categoria) → `ADMIN`, `MANAGER`.
- `POST /api/v1/products` / `PUT /api/v1/products/{id}` (criar/editar
  produto) → `ADMIN`, `MANAGER`.
- `GET /api/v1/categories` / `GET /api/v1/products` → **sem
  `@RequireRole`, deliberadamente** — continuam exatamente como antes
  deste ticket (nenhum header `Authorization` sequer é lido, mesmo
  comportamento de qualquer endpoint não anotado, ver
  `RoleAuthorizationInterceptor`).

`ADMIN`+`MANAGER` (não só `ADMIN`) nos dois `POST`/`PUT`: um gerente de
turno precisa rotineiramente tirar um item de cardápio, mudar
`availableOnMenu`/`availableOnPos` ou ajustar um preço sem depender da
conta do dono/admin — e, diferente de `UserController` (ver abaixo), esses
dois controllers nunca permitem que um chamador conceda a si mesmo mais
acesso do que já tem, então não há risco de escalonamento de privilégio em
incluir `MANAGER`.

**`UserController` — `ADMIN` para tudo, exceto leitura que também permite
`MANAGER`**: anotação de classe `@RequireRole(UserRole.ADMIN)` é o default
para todo método; `list()` e `getById()` têm override de método
alargando para `ADMIN`+`MANAGER` — prova real (não só o teste unitário de
FARELO-122) de que anotação de método vence a de classe sem união (ver
javadoc de `RequireRole`).

- `POST /api/v1/users`, `PUT /api/v1/users/{id}`,
  `PATCH /api/v1/users/{id}/password` → `ADMIN` **somente**.
- `GET /api/v1/users`, `GET /api/v1/users/{id}` → `ADMIN`, `MANAGER`.

Por que os três endpoints de escrita ficam `ADMIN`-only, diferente do
catálogo (que aceita `MANAGER`): `@RequireRole` não tem como inspecionar o
corpo da requisição — só decide por papel do chamador, endpoint inteiro.
`create`/`update` recebem um campo `role` livre no payload; permitir
`MANAGER` ali deixaria um gerente criar ou promover uma conta `ADMIN`
(escalonamento de privilégio). `updatePassword` ainda não confirma a
senha atual (decisão original do FARELO-120, não revisitada aqui — ver
javadoc de `UserService#updatePassword`), então permitir `MANAGER`
significaria um gerente conseguir sequestrar a senha de **qualquer**
conta, inclusive de outro `ADMIN`. Administração de contas é
deliberadamente mantida mais restrita que edição de cardápio — a seção 21
do prompt mestre já trata "Usuários"/"Permissões" como módulos Admin à
parte. Ler a lista/detalhe de um usuário, por outro lado, não pode ser
usado para escalonar privilégio nenhum (e `UserResponse` nunca inclui
`passwordHash` — ver FARELO-120), daí o alargamento para `MANAGER` só ali.

**Testes**: `CategoryControllerIntegrationTests`/
`ProductControllerIntegrationTests`/`UserControllerIntegrationTests` foram
atualizados — todo teste que bate em um endpoint agora protegido passou a
mintar um token real (`JwtTokenService#issue`, mesmo padrão de
`RoleAuthorizationInterceptorIntegrationTests`, FARELO-122) e enviá-lo via
`Authorization: Bearer <token>`; os `GET`s de categoria/produto
continuam sem header nenhum, provando que eles de fato ficaram de fora.
Cada uma das três classes ganhou testes dedicados por endpoint protegido:
sem header → `401`/`UNAUTHENTICATED`; papel errado (ex:
`CASHIER`/`ATTENDANT`/`KITCHEN`, conforme o endpoint) → `403`/`FORBIDDEN`;
papel certo → sucesso como antes. `UserControllerIntegrationTests` ganhou
ainda um par de testes provando que o alargamento de `list()`/`getById()`
para `MANAGER` não vaza para `create()`/`update()`/`updatePassword()`
(`rejectsCreateWhenCallerRoleIsNotAllowed`,
`rejectsUpdateWhenCallerRoleIsNotAllowed`,
`rejectsPasswordChangeWhenCallerRoleIsNotAllowed` — todos usando um token
`MANAGER` de propósito, não um papel operacional qualquer, exatamente para
testar essa fronteira fina). Ver também a extensão de
`RoleAuthorizationInterceptorRegressionIntegrationTests` acima.

Ver `docs/api.md` para o detalhe "Requer" em cada um dos endpoints acima.

## notification

Pacote: `com.farelo.api.notification`.

- **`Notification`** (FARELO-110): entidade JPA — o registro durável de
  algo que precisa ser (ou já foi) enviado a um destinatário. Na prática,
  hoje, sempre uma mensagem de WhatsApp (prompt mestre seção 19: "Utilizar
  futuramente: Meta WhatsApp Cloud API. Fluxo: `ORDER_READY → Notification
  Worker → WhatsApp`. Notificações internas também poderão existir:
  estoque baixo, estoque zerado, falha de impressão."). `id` (UUID, mesma
  estratégia de `Category`), `type` (enum `NotificationType`, ver abaixo),
  `recipient` (`String` — número de WhatsApp formatado, ex:
  `"5511999999999"`), `content` (`String`/`TEXT`, texto já formatado,
  congelado no momento da criação), `status` (enum `NotificationStatus`,
  ver abaixo, padrão `PENDING`), `createdAt`/`updatedAt`. Tabela criada
  pela migration `V22__create_notification_table.sql` (renumerada de V21
  — colidia com `InventoryMovement`/FARELO-093, despachado do mesmo commit
  base).

  **Escopo deste ticket é só a entidade em si** — nenhum produtor real
  existe ainda (reagir a `ORDER_READY` é FARELO-112, "estoque baixo" é
  FARELO-113) e nenhum adapter de envio existe (Meta WhatsApp Cloud API é
  FARELO-111). Mesma abordagem incremental de `Ingredient`/`Recipe`/
  `InventoryMovement`/`PrintJob` (FARELO-071): entidade primeiro, sem
  produtor/consumidor.

  **Decisão de design — entidade standalone, sem depender de
  `OutboxEvent`**: duas formas foram consideradas — (a) um futuro
  consumidor constrói `Notification` a partir de um `OutboxEvent` que
  despacha, o mesmo formato que `com.farelo.api.outbox.OutboxWorker` já
  usa para criar `PrintJob` a partir de `OrderCreated`, com `notification`
  dependendo dos tipos de `outbox`; ou (b) uma entidade de domínio
  independente, que um futuro worker (FARELO-112/113) povoa reagindo a um
  evento de domínio, sem dependência entre os dois pacotes em nenhuma
  direção. Esta classe usa (b) — a opção mais segura para um ticket que
  não constrói produtor nem consumidor algum: a direção de dependência já
  documentada no `package-info.java` de `outbox` é que domínios de negócio
  dependem de `outbox` para *publicar*, e que `outbox` só depende de volta
  para um domínio (hoje, só `PrintJobService`) no ponto estreito onde de
  fato despacha um evento para trabalho real — nada aqui despacha nada
  ainda, então não há um segundo consumidor real que justifique
  `OutboxWorker` aprender sobre `notification` hoje. O link eventual —
  algum worker futuro reagindo a um `OrderCreated`/`STOCK_LOW` drenado
  criando uma `Notification`, do jeito que `OutboxWorker` cria um
  `PrintJob` hoje — é responsabilidade de FARELO-112/113 desenhar, no
  ponto em que existir um segundo alvo de despacho real contra o qual
  desenhar o mecanismo.

  **`recipient` na própria entidade, sem abstração de canal separada**: um
  `String` simples em vez de, digamos, um enum `channel` mais um endereço
  polimórfico — o prompt mestre (seção 19) só nomeia um canal real
  (WhatsApp) tanto para o fluxo voltado ao cliente (`ORDER_READY`) quanto
  para "notificações internas" (estoque baixo/zerado, falha de impressão);
  nada sugere que notificações internas sejam um canal *diferente*, só um
  `recipient` diferente (um número interno da equipe em vez do número do
  cliente). Campo nomeado `recipient`, não `phoneNumber`, especificamente
  para que um canal genuinamente novo no futuro (ex: email) não force um
  rename.

  **`content` é um snapshot congelado em texto plano**: mesma lógica
  "snapshot, não referência viva" de `PrintJob.getContent()` — o texto com
  que uma `Notification` foi criada nunca deve mudar depois só porque,
  digamos, um pedido ou produto foi editado; é um registro do que
  realmente foi enviado (ou tentado), não um ponteiro para recalcular do
  estado atual. Diferente de `PrintJob.content` (JSON estruturado, porque
  seu consumidor — um futuro Edge Agent formatando uma comanda física —
  precisa ler campos individuais), aqui o consumidor (um futuro adapter
  WhatsApp, FARELO-111) só precisa de uma coisa: o corpo da mensagem já
  formatado, para entregar como está à API do WhatsApp Cloud. Coluna
  `TEXT`, não `jsonb`.

  **`NotificationType`**: `ORDER_READY`, `STOCK_LOW`, `STOCK_CRITICAL`,
  `OUT_OF_STOCK`, `PRINT_FAILED` — o subconjunto que a seção 19 de fato
  nomeia como gatilhos de notificação: `ORDER_READY` ("Fluxo: ORDER_READY
  → Notification Worker → WhatsApp") e o trio de "notificações internas"
  (estoque baixo/zerado, falha de impressão). `STOCK_CRITICAL` incluído
  por simetria com o trio completo de eventos de estoque mínimo já
  estabelecido nas seções 17/29 — a seção 19 não o nomeia individualmente,
  mas é a mesma categoria de alerta de estoque que os outros dois.
  `ORDER_CREATED`/`ORDER_CANCELLED`/`COMMAND_CLOSED`/`PRINT_REQUESTED`/
  `PRINT_COMPLETED` são deliberadamente **não** incluídos: são eventos de
  domínio reais (usados por `outbox`/`printing`), mas a seção 19 nunca os
  nomeia como gatilho de notificação — adicioná-los aqui seria inventar um
  requisito, não modelar um já escrito. Nenhum produtor existe ainda para
  nenhum destes valores (FARELO-112/113, tickets futuros).

  **`NotificationStatus`**: `PENDING` (padrão) / `SENT` / `FAILED` — mesmo
  formato de três estados de `PrintJobStatus`. `markSent()`/`markFailed()`
  são as únicas formas de mudar `status` após a criação (sem setter
  público, sem validação — mesma divisão "mutador burro, validação vive no
  service" que `PrintJob` começou no seu próprio ticket somente-entidade,
  FARELO-071); nada chama esses métodos ainda, já que nenhum componente
  real transiciona uma `Notification` para fora de `PENDING` hoje
  (FARELO-111/112). `updatedAt` existe (diferente do ledger append-only
  `InventoryMovement`, que deliberadamente não tem `updatedAt`) justamente
  porque `Notification` tem estado que muda com o tempo.

  `NotificationRepository`: `findByStatusOrderByCreatedAtAsc` (consulta
  derivada, sem `JOIN FETCH` necessário — `Notification` não tem nenhuma
  associação `@ManyToOne`/lazy, todo campo é uma coluna simples) — backs
  `NotificationService#listPending()`/`#list(status)`, mesma lógica FIFO
  já usada por `PrintJobRepository#findByStatusOrderByCreatedAtAsc`/
  `OutboxEventRepository#findByStatusOrderByCreatedAtAsc`.

  `NotificationService`: só métodos de leitura — `listPending()` (todo
  `PENDING`, mais antigo primeiro; não chamado por nada neste ticket, mas
  a consulta que um futuro worker vai precisar) e `list(status)` (backs
  `GET /api/v1/notifications`, com filtro opcional por status — quando
  `status` é `null`, lista tudo, mais antigo primeiro). **Sem métodos de
  escrita**: nada neste ticket cria ou transiciona uma `Notification`.

  **Endpoint**: só `GET /api/v1/notifications?status=` (filtro opcional)
  — mesmo padrão minimalista de outros domínios no seu primeiro ticket
  (ex: `Printer`/FARELO-070). Sem `POST` nem endpoints de transição
  (`/sent`, `/failed`): nada neste ticket constrói ou transiciona uma
  `Notification` real. Ver `docs/api.md` para o endpoint completo.

  **Testes**: `NotificationRepositoryIntegrationTests` (mapeamento JPA
  contra Postgres real — grava e encontra, `markSent`/`markFailed`
  transicionam status, `findByStatusOrderByCreatedAtAsc` retorna só
  `PENDING` em ordem) e `NotificationControllerIntegrationTests` (lista
  vazia, listagem completa em ordem sem filtro, filtro por status —
  `@BeforeEach` limpa a tabela `notification` inteira, seguro porque é uma
  tabela nova sem nenhuma FK vinda de outra entidade e os testes desta
  suíte rodam sequencialmente, não concorrentemente).

- **FARELO-111 — adapter WhatsApp Cloud API** (mecanismo de envio, não
  gatilho). Escopo estrito: dado um `Notification` já existente e `PENDING`,
  enviá-lo e registrar o resultado (`SENT`/`FAILED`). **Não** constrói
  nenhum produtor (reagir a `ORDER_READY` é FARELO-112, "estoque baixo" é
  FARELO-113 — ambos tickets futuros) nem um poller/scheduler que
  automaticamente varre `PENDING` e envia — essa orquestração (quando/como
  o envio é disparado) fica para FARELO-112/113, no ponto em que existir um
  gatilho real para desenhar o mecanismo contra.

  **`com.farelo.api.notification.whatsapp.WhatsAppClient`**: interface com
  um método, `sendTextMessage(recipient, messageBody)`. Existe como seam
  testável — não porque uma segunda implementação real seja esperada, mas
  para que `NotificationSender` (abaixo) seja testável sem uma conta Meta
  real, com um fake trivial no lugar de um mock HTTP em todo teste que
  exercita só a orquestração.

  **`WhatsAppCloudApiClient`** (única implementação real, `@Component`):
  fala com a Meta WhatsApp Cloud API via `org.springframework.web.client.RestClient`
  (Spring 6.1+, já transitivo via `spring-boot-starter-web` — nenhuma
  dependência nova). `RestClient` escolhido sobre `WebClient` (esta
  aplicação é Spring MVC puro, `spring-webflux` nem está no classpath — um
  `WebClient` puxaria a stack reativa inteira para um caso de uso
  síncrono/bloqueante) e sobre `RestTemplate` (em modo de manutenção,
  `RestClient` é o sucessor indicado). Este é o primeiro cliente HTTP de
  saída deste codebase — não havia precedente (`RestTemplate`/`WebClient`/
  `HttpClient` não apareciam em nenhum lugar de `apps/api` antes deste
  ticket).

  Requisição: `POST {base-url}/{phone-number-id}/messages`, header
  `Authorization: Bearer {access-token}`, corpo (`WhatsAppMessageRequest`,
  classe package-private) no formato real da Cloud API:
  ```json
  { "messaging_product": "whatsapp", "to": "5511999999999", "type": "text", "text": { "body": "..." } }
  ```
  Toda falha (erro de rede, timeout, resposta não-2xx — todas subtipos de
  `RestClientException`) é capturada e reembrulhada como
  `WhatsAppSendException` (unchecked, único tipo de falha que este pacote
  expõe) — nunca deixa uma exceção de baixo nível escapar. Mesma filosofia
  "nada aqui derruba o processo" já aplicada por `apps/edge-agent`'s
  `poller.ts`/`printOverTcp`.

  **Config** (`application.yml`, lidos de variáveis de ambiente, nunca
  hardcoded — mesmo padrão `${ENV_VAR:default}` já usado por
  `spring.datasource.*`):
  - `whatsapp.api.base-url` (`WHATSAPP_API_BASE_URL`, padrão
    `https://graph.facebook.com/v20.0`) — sobrescrito em teste para apontar
    a um stub HTTP local.
  - `whatsapp.api.phone-number-id` (`WHATSAPP_PHONE_NUMBER_ID`, padrão
    vazio — nenhuma conta Meta real existe neste ambiente de dev).
  - `whatsapp.api.access-token` (`WHATSAPP_ACCESS_TOKEN`, padrão vazio).
  - `whatsapp.api.connect-timeout-ms`/`whatsapp.api.read-timeout-ms`
    (`WHATSAPP_API_CONNECT_TIMEOUT_MS`/`WHATSAPP_API_READ_TIMEOUT_MS`,
    padrão 5000/10000) — limitam quanto tempo uma tentativa de envio pode
    bloquear esperando uma API externa possivelmente fora do ar.

  **`NotificationSender`** (novo `@Service`, não em `NotificationService` —
  aquela classe é documentada como só-leitura desde FARELO-110, então em
  vez de reescrevê-la este ticket soma uma classe nova e estreita, aditiva):
  `send(Notification)` chama `WhatsAppClient#sendTextMessage`, captura
  `WhatsAppSendException` e chama `markFailed()`, ou chama `markSent()` no
  sucesso — sempre salva e sempre retorna, nunca deixa a exceção escapar
  (mesmo contrato "nenhuma falha derruba o processo" um nível acima).
  `sendById(UUID)` busca por id (lança `NotificationNotFoundException`,
  novo, se não existir) e delega a `send`. **Sem validação de transição de
  status** antes de tentar o envio — mesma razão pela qual
  `markSent()`/`markFailed()` na entidade continuam sem validação (ver
  javadoc de `NotificationStatus`): os únicos chamadores previstos
  (FARELO-112/113, e o endpoint manual abaixo) só fariam sentido reenviando
  livremente, não haveria um caso real conflitante para desenhar uma regra
  contra hoje.

  **Endpoint manual — `POST /api/v1/notifications/{id}/send`**: acionamento
  manual do mecanismo real de envio, para operabilidade/testabilidade hoje
  (não é o gatilho automático que a seção 19 eventualmente quer —
  FARELO-112/113 continuam sendo os gatilhos reais). Sempre `200 OK` com o
  `NotificationResponse` atualizado, esteja `status` como `SENT` ou
  `FAILED` — é um relato de resultado de tentativa, não uma falha de
  validação de request. `404 Not Found`/`NOTIFICATION_NOT_FOUND` se o `id`
  não existir (`ApiExceptionHandler`, novo handler).

  **Testes**: `WhatsAppCloudApiClientTests` (JUnit puro, sem Spring/Postgres
  — contra um `com.sun.net.httpserver.HttpServer` local real, não uma
  chamada HTTP mockada; nenhuma dependência de teste nova foi adicionada —
  `MockWebServer`/WireMock não estavam no `pom.xml` — mesmo espírito do
  teste de `printerTransport.ts` do `apps/edge-agent`, que usa um
  `net.createServer` local real em vez de mock; verifica método/caminho/
  header de autorização/corpo da requisição no sucesso, e que 4xx/5xx/falha
  de conexão sempre viram `WhatsAppSendException`, nunca uma exceção de
  baixo nível), `NotificationSenderIntegrationTests` (`@SpringBootTest` +
  Postgres real via Testcontainers + o mesmo stub HTTP local apontado via
  `whatsapp.api.base-url` — prova o ciclo completo enviar→persistir
  resultado, `SENT` no sucesso, `FAILED` sem lançar exceção na falha, mais
  `sendById` não encontrado) e `NotificationSendControllerIntegrationTests`
  (`@SpringBootTest` + `MockMvc` + Postgres real, mesmo padrão de
  `NotificationControllerIntegrationTests` — `200 OK` com `SENT`/`FAILED`
  conforme a resposta do stub, `404`/`NOTIFICATION_NOT_FOUND` para id
  inexistente).

- **FARELO-112 — "Disparar WhatsApp em ORDER_READY"** (o gatilho real,
  finalmente): fecha a lacuna deixada em aberto desde FARELO-110/111 — até
  aqui nada criava uma `Notification` de verdade nem decidia quando enviar
  uma. Implementa literalmente o fluxo do prompt mestre (seção 19):
  `ORDER_READY → Notification Worker → WhatsApp`.

  **Mecanismo escolhido — reaproveitar o Transactional Outbox existente, não
  inventar um novo**: `OrderService#markAsReady` (`ordering`, transição
  `PREPARING` → `READY`, FARELO-058) passou a publicar um evento de outbox
  `OrderReady` (payload `OrderReadyEvent`, um record simples com `orderId` +
  `commandNumber` — mesmo formato minimalista de `OrderCreatedEvent`) via
  `OutboxPublisher`, na mesma transação da transição — segunda integração
  real do outbox depois de `OrderCreated` (FARELO-060). Publicado *depois*
  que a transição em si já validou com sucesso (não antes) — publicar antes
  registraria um evento para uma transição que ainda podia falhar sua
  própria validação (ex: pedido não estava em `PREPARING`); ambas as
  escritas continuam commitando/revertendo juntas, dentro do mesmo método
  `@Transactional`.

  Esta é exatamente a mesma forma de problema que `OrderCreated` →
  `PrintJob` já resolve (FARELO-072): "um evento de domínio aconteceu, faça
  um efeito colateral assíncrono em reação". Em vez de desenhar um segundo
  mecanismo do zero, `com.farelo.api.outbox.OutboxWorker` ganhou um segundo
  branch no seu `dispatch(...)` (ainda um `if`/`else if` simples, não um
  registro de handlers — ver a entrada do próprio `OutboxWorker` na seção
  "Outbox" abaixo para a decisão revisada de manter isso simples mesmo com
  dois consumidores reais agora): um evento `OrderReady` é despachado para
  `com.farelo.api.notification.OrderReadyNotificationService#createForOrder`,
  que cria uma `Notification` `PENDING` (tipo `ORDER_READY`) — ou nada, se o
  pedido não tiver `customerPhone` (ver decisão abaixo).

  **Por que criar a `Notification` é uma escrita síncrona no banco (dentro
  da transação do `OutboxWorker`), mas ENVIAR é assíncrono, num worker
  separado**: `NotificationSender#send(Notification)` (FARELO-111) faz uma
  chamada HTTP real de saída para a Meta WhatsApp Cloud API — exatamente o
  tipo de chamada lenta/não confiável que o desenho do `OutboxWorker` já
  mantém fora da sua própria transação `@Transactional` em lote (batch
  inteiro sob `FOR UPDATE`, rollback do lote inteiro se `dispatch(...)`
  lançar — ver javadoc de `OutboxWorker`). Colocar `NotificationSender.send(...)`
  direto dentro de `OutboxWorker.dispatch(...)` reintroduziria esse mesmo
  problema, só que apontado para a tabela `notification` em vez de
  `outbox_event`. Por isso `OrderReadyNotificationService.createForOrder(...)`
  só faz uma escrita de banco pura (mesmo formato de
  `PrintJobService#createForOrder` para `PrintJob`) — nenhuma chamada de
  rede acontece dentro da transação do `OutboxWorker`.

  **`com.farelo.api.notification.NotificationWorker`** (novo `@Component`,
  pacote `notification`): o "Notification Worker" citado literalmente na
  seção 19 do prompt mestre. `@Scheduled` próprio (`notification.worker.poll-interval-ms`,
  default 5000ms, mesmo padrão configurável de `outbox.worker.poll-interval-ms`),
  independente do agendamento do `OutboxWorker`. `processPendingNotifications()`
  lista toda `Notification` `PENDING` via `NotificationService#listPending()`
  (primeira chamadora real dessa query, que já existia desde FARELO-110 sem
  nenhum consumidor) e chama `NotificationSender#send(...)` para cada uma.

  **Deliberadamente sem `@Transactional` no método do worker em si**: `send(...)`
  já é `@Transactional` por chamada (FARELO-111); anotar o método do loop
  também dobraria toda a chamada em uma única transação envolvente,
  recriando "chamada HTTP lenta seguran do uma transação aberta" um nível
  acima — exatamente o problema que este desenho existe para evitar. Sem
  essa anotação, cada `Notification` do lote é tentada e persistida de
  forma independente: um envio lento ou que falha para um destinatário
  nunca segura lock nem transação para os vizinhos, e (já que `send(...)`
  nunca deixa uma falha de entrega lançar exceção — ver seu próprio
  javadoc) um envio ruim nunca aborta o resto do lote.

  **Sem `SKIP LOCKED`, diferente do `OutboxWorker`**: `processPendingNotifications()`
  lista `PENDING` com uma query simples, sem lock de linha — seguro hoje
  pela mesma razão que `OutboxWorker` era seguro antes do FARELO-063 (só
  uma instância da aplicação roda, e `@Scheduled` com `fixedDelay` padrão
  nunca sobrepõe a si mesmo dentro de uma JVM). Limitação conhecida,
  documentada no javadoc da classe — merece o mesmo tratamento
  (`FOR UPDATE SKIP LOCKED`) que `OutboxEventRepository` já tem, no dia em
  que escalar horizontalmente for uma necessidade real, não hipotética.
  Também sem limite de tamanho de lote (`batchSize`) — mesmo raciocínio de
  volume naturalmente baixo já usado por `PrintJobService#listPending()`/
  `OrderService#listQueue()`: no máximo uma notificação `ORDER_READY` por
  pedido que chega a `READY`.

  **`Order.customerPhone` nulo/vazio não é uma falha** — `OrderReadyNotificationService#createForOrder`
  trata isso como um resultado legítimo e esperado (retorna `Optional.empty()`,
  não lança), não como um erro de dispatch. Isso importa especificamente
  por causa do contrato de falha do `OutboxWorker`: lançar aqui reverteria
  o lote inteiro do outbox sendo drenado naquele momento, por uma condição
  que não é realmente um erro — um cliente que não deixou telefone
  simplesmente não tem para quem notificar. A transição `READY` do pedido
  já commitou, na sua própria transação anterior
  (`OrderService#markAsReady`); este método roda depois, de forma
  assíncrona, e nunca pode afetar se aquela transição teve sucesso — o
  fluxo de cozinha/retirada não pode depender de se um telefone foi
  coletado.

  **Conteúdo da mensagem**: texto em português, formatado, referenciando o
  número da comanda (o que o cliente fisicamente segura e reconhece) e,
  quando disponível, o nome do cliente — mesmo formato "snapshot congelado
  em texto plano" que `Notification.content` já documenta desde FARELO-110.
  Nenhum detalhe de item/produto é incluído — o fluxo `ORDER_READY` da
  seção 19 é sobre avisar que o pedido está pronto para retirada, não
  reproduzir um recibo.

  **`OutboxEvent`/`Notification` continuam sem dependência um do outro**: a
  decisão de desenho 1 do `Notification` (FARELO-110 — "entidade standalone,
  sem depender de `OutboxEvent`") se mantém de pé mesmo com o link real
  agora existindo — `OrderReadyNotificationService` depende para frente de
  `com.farelo.api.ordering.Order` (para ler `customerPhone`), não de nada
  em formato de outbox. Quem aprendeu sobre o consumidor foi só o
  `OutboxWorker` (ver `package-info.java` de `outbox`, atualizado nesta
  seção).

  **Testes**: `OrderReadyNotificationServiceIntegrationTests` (pacote
  `notification`, chama `createForOrder(...)` diretamente, sem passar pelo
  outbox — mesma decisão de escopo que `PrintJobServiceIntegrationTests`
  já tomou para `PrintJobService#createForOrder`; cobre com telefone, sem
  nome, telefone nulo, telefone em branco, e pedido inexistente),
  `NotificationWorkerIntegrationTests` (pacote `notification`, testa
  `processPendingNotifications()` isoladamente contra um stub HTTP local —
  mesmo padrão de `NotificationSenderIntegrationTests` — múltiplas
  notificações num lote, uma falha não aborta as demais, lote vazio) e
  `OutboxWorkerOrderReadyIntegrationTests` (pacote `outbox`, ponta a ponta:
  cria pedido com telefone → `markAsPreparing` → `markAsReady` → drena o
  evento `OrderReady` via `outboxWorker.processPendingEvents()` → confirma
  `Notification` `PENDING` com destinatário/conteúdo corretos → drena via
  `notificationWorker.processPendingNotifications()` → confirma `SENT`;
  mais os dois casos negativos exigidos pelo ticket: pedido sem telefone
  chegando a `READY` não cria `Notification` nenhuma e a própria transição
  não falha; transicionar só até `PREPARING` não publica `OrderReady` nem
  cria notificação). Todos os três seguem a mesma convenção anti-flakiness
  já estabelecida para `OutboxWorker` — cada estágio é acionado por uma
  chamada direta e determinística (`processPendingEvents()`/
  `processPendingNotifications()`), nunca por um `sleep`/espera de
  wall-clock — e por isso `AbstractIntegrationTest` passou a desabilitar
  também o `@Scheduled` real do `NotificationWorker`
  (`notification.worker.poll-interval-ms`), mesmo raciocínio já aplicado a
  `outbox.worker.poll-interval-ms`.

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
  findByStatusOrderByCreatedAtAsc`/depois `findPendingForUpdateSkipLocked`,
  ver FARELO-063 abaixo, FIFO). Requer `@EnableScheduling` em
  `FareloApiApplication` (primeiro uso de `@Scheduled` no projeto).

  **Primeiro consumidor real (FARELO-072)**: até aqui o worker era um
  **stub deliberado** — logava cada evento `PENDING` e marcava
  `PROCESSED`, sem nenhum dispatch real, porque não existia nenhum
  consumidor (impressão/notificação/estoque eram epics futuros ainda não
  iniciados). Isso muda no FARELO-072: um evento `eventType ==
  "OrderCreated"` agora resulta na criação de um `PrintJob` `PENDING`
  (ver `PrintJobService`, seção `printing` acima).

  **Segundo consumidor real (FARELO-112)**: um evento `eventType ==
  "OrderReady"` (publicado por `OrderService#markAsReady`, ver seção
  `notification` acima, entrada "FARELO-112") agora resulta na criação de
  uma `Notification` `PENDING` (ou nada, se o pedido não tiver
  `customerPhone`) via `OrderReadyNotificationService#createForOrder`.
  Estoque continua sem consumidor — qualquer outro `eventType` (nenhum
  existe hoje) permanece um no-op.

  **Mecanismo de dispatch**: um método privado `dispatch(OutboxEvent)`
  com um `if`/`else if` em `event.getEventType()` — não um registro
  plugável de handlers (ex: `Map<String, OutboxEventHandler>`).
  Continuou deliberado (YAGNI) mesmo depois de ganhar o segundo branch no
  FARELO-112: dois `if`s em linha reta, cada um chamando exatamente um
  método de exatamente um consumidor, ainda é mais simples de ler do que a
  indireção de um registro, e os dois branches não têm nenhum
  comportamento em comum para extrair (nenhuma política de retry
  compartilhada, nenhum handler múltiplo por tipo de evento). As perguntas
  que um registro de verdade precisaria responder — um handler por tipo de
  evento, ou vários inscritos no mesmo tipo? síncrono ou enfileirado? como
  falhas parciais entre handlers se comportam, independente da história de
  rollback-do-lote-inteiro abaixo? — ainda têm só uma resposta concreta
  cada uma neste uso real, não duas concorrentes. Isso deve virar um
  mecanismo de verdade quando um **terceiro** `eventType`/consumidor
  aparecer — ex: `inventory` reagindo a `OrderCreated` também, ou
  `STOCK_LOW`/`STOCK_CRITICAL`/`OUT_OF_STOCK` alimentando `notification` do
  mesmo jeito que `OrderReady` alimenta hoje (FARELO-113 e além) — só então
  há três casos reais (e provavelmente uma forma repetida entre pelo menos
  dois deles) para desenhar a abstração contra, em vez de extrapolar de
  dois.

  **Direção de dependência, revisada**: até o FARELO-071, esta seção
  dizia que o pacote `outbox` "nunca depende de volta para um pacote de
  domínio" (ver nota no final desta seção, "Direção de dependência"). Isso
  muda no FARELO-072: `OutboxWorker` passa a depender de
  `com.farelo.api.printing.PrintJobService` para fazer o dispatch real, e
  ganha uma segunda dependência forward no FARELO-112, para
  `com.farelo.api.notification.OrderReadyNotificationService`. Exceção
  deliberada e estreita — despachar um evento para trabalho de verdade
  exige necessariamente chamar o domínio que faz esse trabalho; revisitar
  com um registro de handlers de verdade (que restauraria um worker
  genérico) quando um **terceiro** consumidor real justificar a abstração.
  Ver o javadoc revisado do `package-info.java` de `outbox` para o texto
  completo.

  **Falha ao despachar (FARELO-072)**: `dispatch(event)` roda dentro da
  mesma transação `@Transactional` de `processPendingEvents()`, antes do
  evento ser marcado `PROCESSED`. Se lançar (ex: `PrintJobService` não
  encontra o pedido — não esperado na prática, já que o pedido foi
  escrito na mesma transação que publicou o evento, mas sem nenhuma
  proteção contra isso além de deixar a exceção propagar), a exceção
  propaga e a transação do método inteiro reverte:
  **todo** o lote atual volta a `PENDING` (nada nele chegou a ser
  commitado como `PROCESSED`), não só o evento que falhou. Limitação
  conhecida e documentada no javadoc da classe, não um bug escondido — um
  mecanismo de retry/isolamento por evento (ex: marcar só o evento
  problemático de alguma forma, sem bloquear os vizinhos do mesmo lote)
  é deliberadamente **não** construído aqui; isso é FARELO-079 ("Criar
  retry de impressão"), escopado para quando um modo de falha real
  (impressora fora do ar etc) justificar. Coberto por
  `OutboxWorkerPrintJobIntegrationTests#dispatchFailureRollsBackTheBatchLeavingTheEventPending`
  (ver seção `printing`, entrada FARELO-072, para o teste completo).
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
  literal `5000` fixo que existia antes.

  **Desabilitado globalmente em teste** (FARELO-072, `AbstractIntegrationTest`):
  motivo real, não hipotético. A primeira tentativa de corrigir isso foi
  local — `OutboxWorkerBatchSizeIntegrationTests` sobrescrevendo
  `poll-interval-ms` para 1h só no seu próprio `@TestPropertySource` — mas
  isso não bastava: Spring Test cacheia `ApplicationContext`s entre classes
  de teste pra suíte inteira, então o `@Scheduled` de verdade de QUALQUER
  OUTRO contexto já em cache (a maioria das classes de teste não
  sobrescreve nada, ficando no default de 5s) continua rodando em segundo
  plano contra o mesmo Postgres singleton pelo resto da execução da JVM de
  teste — e conseguiu "roubar" linhas semeadas por dois testes diferentes
  (o de batch-size, e depois `OutboxWorkerPrintJobIntegrationTests`,
  FARELO-072) antes das chamadas explícitas de cada um, quebrando as
  asserções. Como nenhum teste da suíte depende do disparo automático de
  verdade (todos chamam `processPendingEvents()` diretamente — a forma
  padrão de testar um `@Scheduled` sem depender de tempo real), a correção
  definitiva foi desabilitar o intervalo globalmente: `AbstractIntegrationTest`
  registra `outbox.worker.poll-interval-ms=3600000` via
  `@DynamicPropertySource` para TODO contexto de teste que estende essa
  classe — que tem precedência MAIOR que `@TestPropertySource` no Spring
  Test (o oposto do que se esperaria), então nenhuma classe de teste
  individual precisa (nem consegue) sobrescrever isso de volta hoje.

**Direção de dependência (publicação)**: domínios de negócio (ex:
`ordering`) dependem de `outbox` para publicar eventos — o formato de cada
payload (ex: `OrderCreatedEvent`) vive no domínio que o produz, não aqui.
Isso continua valendo sem exceção: `OutboxEvent`/`OutboxPublisher` não
sabem nada sobre `printing`, `ordering` ou qualquer outro domínio, só como
guardar/publicar `(aggregateType, aggregateId, eventType, payload)`
opacos.

**Direção de dependência (dispatch), revisada no FARELO-072, estendida no
FARELO-112**: do lado do consumo, isso não vale mais sem exceção.
`OutboxWorker` depende de `com.farelo.api.printing.PrintJobService` para de
fato fazer algo com um evento `OrderCreated` (criar um `PrintJob`), e, desde
FARELO-112, também de
`com.farelo.api.notification.OrderReadyNotificationService` para um evento
`OrderReady` (criar uma `Notification` `PENDING`, ou nada) — ver a entrada
do `OutboxWorker` acima para a justificativa completa e o
`package-info.java` de `outbox` para o texto formal. Despachar um evento
para trabalho real exige chamar o domínio que faz esse trabalho; com
exatamente dois consumidores reais hoje, um `if`/`else if` simples em
`OutboxWorker` ainda diz tudo que um registro de handlers diria, com menos
indireção. Revisitar com um registro de handlers quando um **terceiro**
consumidor real aparecer (`inventory` reagindo a `OrderCreated`, ou
`STOCK_LOW`/`STOCK_CRITICAL`/`OUT_OF_STOCK` alimentando `notification` —
FARELO-113 e além).
