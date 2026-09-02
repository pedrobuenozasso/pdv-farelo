# Domain Model — Farelo OS

Este documento é preenchido incrementalmente à medida que cada domínio é implementado.

## Domínios previstos

| Domínio | Responsabilidade | Status |
|---|---|---|
| `auth` | Autenticação e RBAC de usuários internos | Não iniciado |
| `catalog` | `Product`, `Category` — fonte única de verdade do cardápio | Em andamento |
| `customer` | Dados do cliente coletados no fluxo de pedido (nome, WhatsApp) | Snapshot simples em `orders` (ver seção `ordering`) — domínio próprio ainda **não iniciado** |
| `command` | `Command` (comanda) e seu ciclo de vida | Em andamento |
| `ordering` | `Order`, `OrderItem`, snapshot de preço, histórico de status | Em andamento |
| `kitchen` | KDS — visualização e transição de status de preparo | Não iniciado — `GET /api/v1/orders` (fila da cozinha, FARELO-059) já existe, mas ficou em `ordering` por enquanto; ver nota na seção `ordering` abaixo |
| `printing` | `Printer`, `PrintJob`, integração com Edge Agent | Em andamento |
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
  contra. Também não existe transição de volta `FAILED`→`PENDING`
  (retry) — a seção 10 do prompt mestre menciona "permitindo retry", mas o
  mecanismo real de retry (quem decide re-enfileirar, com que critério)
  ainda não foi desenhado; ver `PrintJobStatus` para a nota completa.

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

  **Decisão — nenhum split por estação ainda**: um `PrintJob` por pedido,
  não um por `productionStation` — isso é FARELO-074, ticket seguinte,
  fora de escopo aqui (`Product.productionStation`, FARELO-073, ainda não
  é lido por nada em `printing`).

  Teste de integração: `OutboxWorkerPrintJobIntegrationTests` (pacote
  `outbox`, mesmo padrão de `OutboxWorkerIntegrationTests`) cria um pedido
  de verdade via `OrderService.create(...)`, chama
  `OutboxWorker#processPendingEvents()` explicitamente e confirma um
  `PrintJob` `PENDING` com `content` correto (nomes/quantidades dos dois
  itens, número da comanda) — mais um segundo teste que prova o
  comportamento de falha documentado no `OutboxWorker` (evento com
  `aggregateId` que não corresponde a nenhum pedido: a exceção propaga,
  o lote inteiro reverte, o evento permanece `PENDING`). Contexto Spring
  próprio (`outbox.worker.poll-interval-ms` bem alto via
  `@TestPropertySource`), mesma razão de
  `OutboxWorkerBatchSizeIntegrationTests`: sem isso, o `@Scheduled` real
  de um contexto já em cache poderia disputar as mesmas linhas com as
  chamadas explícitas deste teste.

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
  (ver `PrintJobService`, seção `printing` acima). Notificação e estoque
  continuam sem consumidor — qualquer outro `eventType` (nenhum existe
  hoje) permanece um no-op.

  **Mecanismo de dispatch**: um método privado `dispatch(OutboxEvent)`
  com um `if` direto em `event.getEventType()` — não um registro
  plugável de handlers (ex: `Map<String, OutboxEventHandler>`).
  Deliberado (YAGNI), não descuido: com exatamente um `eventType`
  (`OrderCreated`) e um consumidor (impressão), um registro seria uma
  abstração com uma única entrada — não há um segundo caso real para
  desenhar a forma certa contra (um handler por evento, ou vários
  inscritos no mesmo tipo? síncrono ou enfileirado? como falhas parciais
  entre handlers se comportam?). Isso deve virar um mecanismo de verdade
  quando um segundo `eventType`/consumidor aparecer — ex: `inventory`
  reagindo a `OrderCreated` também, ou um `eventType` novo para
  `notification` (ambos epics futuros) — só então há dois casos reais
  para desenhar a abstração contra, em vez de um caso imaginado.

  **Direção de dependência, revisada**: até o FARELO-071, esta seção
  dizia que o pacote `outbox` "nunca depende de volta para um pacote de
  domínio" (ver nota no final desta seção, "Direção de dependência"). Isso
  muda aqui: `OutboxWorker` agora depende de
  `com.farelo.api.printing.PrintJobService` para fazer o dispatch real.
  Exceção deliberada e estreita — despachar um evento para trabalho de
  verdade exige necessariamente chamar o domínio que faz esse trabalho;
  revisitar com um registro de handlers de verdade (que restauraria um
  worker genérico) quando um segundo consumidor real justificar a
  abstração. Ver o javadoc revisado do `package-info.java` de `outbox`
  para o texto completo.

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
  literal `5000` fixo que existia antes. Motivo real, não hipotético: como
  `OutboxWorkerBatchSizeIntegrationTests` sobe um `@SpringBootTest`
  completo (não um slice), o `@Scheduled` de verdade continuava rodando no
  fundo enquanto o teste semeava sua própria fixture e fazia sua chamada
  explícita — e chegou a "roubar" um evento semeado antes da chamada
  explícita, quebrando a contagem exata esperada pelo teste. Esse teste
  agora sobrescreve `poll-interval-ms` para 1 hora via
  `@TestPropertySource`, tornando essa corrida impossível em vez de só
  improvável — os demais testes do worker continuam no default de 5s.

**Direção de dependência (publicação)**: domínios de negócio (ex:
`ordering`) dependem de `outbox` para publicar eventos — o formato de cada
payload (ex: `OrderCreatedEvent`) vive no domínio que o produz, não aqui.
Isso continua valendo sem exceção: `OutboxEvent`/`OutboxPublisher` não
sabem nada sobre `printing`, `ordering` ou qualquer outro domínio, só como
guardar/publicar `(aggregateType, aggregateId, eventType, payload)`
opacos.

**Direção de dependência (dispatch), revisada no FARELO-072**: do lado do
consumo, isso não vale mais sem exceção. `OutboxWorker` agora depende de
`com.farelo.api.printing.PrintJobService` para de fato fazer algo com um
evento `OrderCreated` (criar um `PrintJob`) — ver a entrada do
`OutboxWorker` acima para a justificativa completa e o `package-info.java`
de `outbox` para o texto formal. Despachar um evento para trabalho real
exige chamar o domínio que faz esse trabalho; com exatamente um consumidor
real hoje, não há uma segunda instância para desenhar uma abstração
genérica contra sem adivinhar. Revisitar com um registro de handlers
quando um segundo consumidor real aparecer (`inventory`, `notification` —
epics futuros).
