# apps/edge-agent

`farelo-edge-agent` — serviço executado no mini PC local da cafeteria, responsável por:

- conectar com a API
- buscar/receber `PrintJob`s
- imprimir (preferencialmente ESC/POS)
- reportar status de impressão
- verificar impressoras
- manter fila temporária local

Este serviço nunca deve conter regra de negócio de pedidos — é apenas infraestrutura
de dispositivos.

## Status atual

**Impressão ESC/POS real via TCP (FARELO-078)**, sobre a base de polling +
report já existente (FARELO-076/077). O serviço conecta com a API e faz
polling periódico de `GET /api/v1/print-jobs`. Para cada `PrintJob` pendente
encontrado, o Edge Agent:

1. loga o job (id, comanda, estação, itens);
2. resolve o endereço IP/porta da impressora responsável pela
   `productionStation` do job (ver "Endereços de impressora por estação"
   abaixo);
3. monta os bytes ESC/POS do ticket (`src/escpos.ts`) — ver "Formato do
   ticket impresso" abaixo;
4. tenta enviar o ticket via TCP para a impressora (`src/printerTransport.ts`).

Sucesso nas três últimas etapas → `POST /api/v1/print-jobs/{id}/printed`.
Qualquer falha — nenhum endereço configurado para a estação (nem fallback
default), impressora offline/recusando conexão, timeout de rede — é logada
com o motivo e reportada via `POST /api/v1/print-jobs/{id}/failed`. **Não há
mais nenhum "logar = processado com sucesso"**: esse era um estado
intermediário deliberado do FARELO-077, documentado como temporário até este
ticket existir — agora o "processamento" real do loop é a tentativa de
impressão em si.

Uma falha de rede/API indisponível — seja no polling, seja na tentativa de
impressão, seja na chamada de report — é logada e o próximo ciclo (ou próximo
job, dentro do mesmo ciclo) tenta de novo; o processo nunca cai por causa
disso, e não há lógica de retry sofisticada (fila temporária local,
mencionada no prompt mestre seção 11, é responsabilidade futura, ainda não
implementada; retry de `PrintJob`s `FAILED` é escopo do próximo ticket,
FARELO-079, **não implementado aqui**).

FARELO-075 (esqueleto mínimo — só inicializar e logar que o processo subiu) foi o
ponto de partida deste app.

### Decisão — comandos ESC/POS escritos à mão, sem lib nova

ADR-002 havia cogitado `node-thermal-printer`/`escpos` como opção futura para
quando a impressão de verdade fosse implementada. Na hora de implementar
(este ticket), a decisão foi **não** adicionar nenhuma delas: o conjunto de
comandos ESC/POS necessário é pequeno e padronizado (`ESC @` inicializar,
`ESC !` negrito/fonte grande, `ESC a` alinhamento, `GS V` corte de papel) —
poucas sequências de bytes fixas (ver `src/escpos.ts`), sem necessidade de
nada que uma lib ofereceria e este ticket não precisa (detecção de múltiplos
protocolos/fabricantes de impressora, impressão de imagem/QR code, etc).
Seguindo o princípio já registrado em `AGENTS.md`/ADR-002 ("não adicionar
dependência sem necessidade real"), a lib não paga o próprio custo aqui.
Sem ADR-003 novo por não haver dependência nova a justificar.

## Stack

Node.js + TypeScript. Ver [`ADR-002-edge-agent-nodejs.md`](../../docs/decisions/ADR-002-edge-agent-nodejs.md)
para a justificativa (reaproveita o ecossistema JS já usado em `apps/web`, bibliotecas
ESC/POS maduras disponíveis, leve o suficiente para um mini PC dedicado).

## Configuração

Variáveis de ambiente (nenhuma obrigatória, exceto para imprimir de verdade — ver abaixo):

| Variável                             | Default                 | Descrição                                                                                                                                                                  |
| ------------------------------------ | ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `FARELO_API_BASE_URL`                | `http://localhost:8080` | URL base da API (`apps/api`). Aponte para o backend rodando nativamente (`./mvnw spring-boot:run`) ou via `infra/docker-compose.dev.yml` (porta `8080` publicada no host). |
| `FARELO_EDGE_AGENT_POLL_INTERVAL_MS` | `5000`                  | Intervalo, em milissegundos, entre consultas a `GET /api/v1/print-jobs`.                                                                                                   |

### Endereços de impressora por estação (FARELO-078)

Mapear `productionStation` → endereço IP/porta da impressora física é
infraestrutura de dispositivo local (prompt mestre, seção 11: "o Edge Agent
nunca deve possuir regra de negócio de pedidos — é apenas infraestrutura de
dispositivos"), então essa configuração vive **aqui**, no Edge Agent, via
variáveis de ambiente — nunca no backend. Nenhuma delas é obrigatória para o
processo iniciar, mas sem pelo menos o fallback default configurado, todo
`PrintJob` vai falhar ao tentar imprimir (ver "Sem endereço configurado"
abaixo).

**Convenção de nome**: `FARELO_PRINTER_<ESTAÇÃO>_HOST` /
`FARELO_PRINTER_<ESTAÇÃO>_PORT`, onde `<ESTAÇÃO>` é o valor de
`productionStation` normalizado (maiúsculas, acentos removidos, qualquer
outro caractere que não seja `A-Z`/`0-9` vira `_`). Exemplos:

| `productionStation` | Variáveis                                                     |
| ------------------- | ------------------------------------------------------------- |
| `"BAR"`             | `FARELO_PRINTER_BAR_HOST` / `FARELO_PRINTER_BAR_PORT`         |
| `"KITCHEN"`         | `FARELO_PRINTER_KITCHEN_HOST` / `FARELO_PRINTER_KITCHEN_PORT` |
| `"Salão Externo"`   | `FARELO_PRINTER_SALAO_EXTERNO_HOST` / `..._PORT`              |

**Fallback**: `FARELO_PRINTER_DEFAULT_HOST` / `FARELO_PRINTER_DEFAULT_PORT` —
usado quando não há variável específica para a estação, e também para itens
sem estação atribuída (`productionStation: null`, ver FARELO-074 em
`docs/domain-model.md`).

**Sem endereço configurado** (nem específico da estação, nem default): o job
não é impresso — o Edge Agent loga o motivo e chama
`POST /api/v1/print-jobs/{id}/failed`. Nunca inventa um endereço default nem
derruba o processo.

Exemplo de `.env`/variáveis exportadas para rodar localmente contra uma
impressora de rede de verdade:

```bash
export FARELO_PRINTER_BAR_HOST=192.168.0.50
export FARELO_PRINTER_BAR_PORT=9100
export FARELO_PRINTER_KITCHEN_HOST=192.168.0.51
export FARELO_PRINTER_KITCHEN_PORT=9100
export FARELO_PRINTER_DEFAULT_HOST=192.168.0.50
export FARELO_PRINTER_DEFAULT_PORT=9100
```

## Rodando localmente

Pré-requisito: Node.js 20+, backend (`apps/api`) rodando (nativamente ou via
`infra/docker-compose.dev.yml`) se você quiser ver PrintJobs de verdade sendo
consultados — sem ele, o polling simplesmente loga erro de conexão e tenta de
novo a cada ciclo, sem crashar.

```bash
cd apps/edge-agent
npm install

# desenvolvimento (recarrega a cada mudança, via tsx)
npm run dev

# apontando para uma API em outro host/porta, com polling mais rápido
FARELO_API_BASE_URL=http://localhost:8080 FARELO_EDGE_AGENT_POLL_INTERVAL_MS=2000 npm run dev

# build + execução do artefato compilado
npm run build
npm start
```

Saída esperada (com a API no ar e nenhum `PrintJob` pendente):

```
Farelo Edge Agent iniciado
Consultando PrintJobs pendentes em http://localhost:8080/api/v1/print-jobs a cada 5000ms
Nenhum PrintJob pendente.
```

Saída esperada com um `PrintJob` pendente e impressora `BAR` configurada e
respondendo:

```
1 PrintJob(s) pendente(s):
  PrintJob 3fa85f64-... — comanda 37 [BAR]: 2x Cappuccino
  → PrintJob 3fa85f64-... impresso em 192.168.0.50:9100 e reportado como PRINTED.
```

Saída esperada quando a estação não tem impressora configurada (nem
`FARELO_PRINTER_DEFAULT_HOST`/`_PORT`):

```
1 PrintJob(s) pendente(s):
  PrintJob 3fa85f64-... — comanda 37 [BAR]: 2x Cappuccino
  → Falha ao imprimir PrintJob 3fa85f64-...: Nenhum endereço de impressora configurado para a estação "BAR" (nem fallback FARELO_PRINTER_DEFAULT_HOST/FARELO_PRINTER_DEFAULT_PORT).
  → PrintJob 3fa85f64-... reportado como FAILED.
```

## Formato do ticket impresso (ESC/POS)

`src/escpos.ts` monta os bytes ESC/POS a partir de um `PrintJobContent`
(número da comanda, estação de produção, itens). O ticket resultante, quando
impresso numa impressora térmica de 32/48 colunas, tem este layout lógico:

```
        [negrito, fonte dobrada, centralizado]
              COMANDA 37
        [modo normal, alinhado à esquerda]
Estacao: BAR
--------------------------------
2x Cappuccino
1x Coca-Cola

              [corte de papel]
```

Quando `productionStation` é `null` (item sem estação atribuída — ver
FARELO-074 em `docs/domain-model.md`), a linha vira
`Estacao: (sem estacao)` — nunca o literal `null`. Quando não há itens, a
linha `(sem itens)` aparece no lugar da lista.

Comandos ESC/POS usados (todos padrão, família ESC/GS — ver
[especificação Epson ESC/POS](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/)):

| Comando   | Bytes                 | Efeito                                              |
| --------- | --------------------- | --------------------------------------------------- |
| `ESC @`   | `1B 40`               | Inicializa a impressora (limpa estado residual)     |
| `ESC a n` | `1B 61 00`/`1B 61 01` | Alinhamento esquerda (`00`) / centro (`01`)         |
| `ESC ! n` | `1B 21 38`/`1B 21 00` | Negrito + fonte dobrada (`38`) / modo normal (`00`) |
| `GS V m`  | `1D 56 00`            | Corte total de papel                                |

**Limitação conhecida — codificação de texto**: o texto do ticket é
codificado como ASCII puro (`Buffer.from(text, "ascii")`). Nomes de
produto/estação com acentuação (ex: "Pão", "Café") não são impressos
corretamente — o encode ASCII do Node trunca/distorce caracteres fora da
faixa ASCII (não os remove nem os transliteram de forma legível). Suportar a
codepage correta (ex: CP850/CP860, comum em impressoras térmicas para
português) é responsabilidade de um ticket futuro, fora do escopo aqui — não
antecipado por YAGNI (nenhum caso de uso real testado contra hardware ainda).

## Limitação deste ambiente — sem impressora física

Este ambiente de desenvolvimento não tem uma impressora térmica física
disponível. Isso não bloqueou o ticket: a prova de correção usada foi

1. **Testes unitários da formatação ESC/POS** (`src/escpos.test.ts`) — bytes
   exatos dos comandos de controle, sem I/O nenhum;
2. **Teste de integração do transporte de rede** (`src/printerTransport.test.ts`)
   — um servidor TCP local de teste (`net.createServer` do próprio Node,
   escutando em `127.0.0.1` numa porta efêmera) recebe e verifica os bytes
   enviados por `printOverTcp`, além de casos reais de conexão recusada
   (porta sem nada escutando) e timeout (servidor que nunca drena os dados,
   forçando o controle de fluxo do TCP a estagnar a escrita) — sem mock de
   rede, tráfego TCP de verdade em loopback.

Isso valida o transporte de ponta a ponta (abrir socket, escrever bytes,
tratar erro/timeout) sem depender de hardware. A validação contra uma
impressora térmica física de verdade (byte a byte chegando ao papel) fica
para quando o Edge Agent for implantado no mini PC da cafeteria.

## Testes

```bash
npm test
```

Usa o test runner nativo do Node (`node:test`, via `tsx --test`) — sem lib de
teste adicional em todo o serviço. Arquivos e o que cada um cobre:

- `src/printJobsClient.test.ts`: parsing/formatação da resposta de
  `GET /api/v1/print-jobs` com um fixture do JSON do contrato, incluindo
  casos de entrada malformada, e a construção da chamada (URL/método) de
  `POST .../printed` e `.../failed` estubando `global.fetch` — sem subir
  servidor de verdade.
- `src/escpos.test.ts` (FARELO-078): formatação ESC/POS pura — bytes exatos
  dos comandos de controle nas posições certas, texto/quantidade dos itens,
  casos de estação `null` e lista de itens vazia. Sem I/O.
- `src/printerTransport.test.ts` (FARELO-078): teste de **integração** real
  do transporte TCP contra um `net.createServer` local (porta efêmera em
  `127.0.0.1`) — bytes recebidos idênticos aos enviados, conexão recusada
  (impressora offline) e timeout (impressora que nunca drena os dados)
  rejeitando a Promise. Sem mock de rede — ver "Limitação deste ambiente"
  acima.
- `src/config.test.ts` (FARELO-078): resolução de endereço de impressora por
  estação (`resolvePrinterAddress`) — específico vs. fallback default,
  normalização de nome de estação, ausência de configuração.
- `src/poller.test.ts` (FARELO-078): orquestração do ciclo de polling
  (`pollOnce`) via injeção de dependências (`PollerDeps`, funções falsas
  simples, sem lib de mock) — sucesso de impressão → `reportPrintJobPrinted`;
  falha (sem endereço configurado, erro de transporte TCP) →
  `reportPrintJobFailed`; nenhuma falha (de busca, impressão ou report)
  derruba o processo.

Não há teste de integração contra uma API (`apps/api`) rodando de verdade:
para o escopo deste serviço (sem lógica de negócio, só
consultar/imprimir/reportar), isso seria mais pesado do que o ticket pede — a
validação de ponta a ponta (cliente HTTP + parsing + impressão contra o
backend real) é feita manualmente ao rodar `npm run dev` apontando para uma
API local, como descrito acima.

### Lint / format

```bash
npm run lint
npm run format        # aplica
npm run format:check  # só verifica
```

ESLint aqui usa apenas as regras recomendadas de JS/TypeScript (`typescript-eslint`)
mais integração com Prettier — não reaproveita `eslint-config-next` de `apps/web`
porque este não é um projeto Next.js, mas segue o mesmo espírito de configuração
(flat config + Prettier desligando regras de formatação conflitantes).

## Por que este serviço roda separado da stack principal

O Edge Agent **não** faz parte de `infra/docker-compose.dev.yml`. Ele não é um serviço
de backend/frontend rodando junto do resto da stack de desenvolvimento — é um processo
que, em produção, roda fisicamente num mini PC dedicado dentro da cafeteria, conectado
a impressoras locais via USB/rede local. Subir a stack principal (`docker compose -f
infra/docker-compose.yml -f infra/docker-compose.dev.yml up`) não inclui nem depende
dele.

O FARELO-076 adicionou a primeira lógica real de rede (polling de
`GET /api/v1/print-jobs`), rodando nativamente (`npm run dev`/`npm start`) contra o
backend local — mas isso não muda a decisão acima: o Edge Agent continua fora de
`infra/docker-compose.dev.yml` de propósito, já que em produção ele nunca roda como
container junto do resto da stack, e sim como processo nativo no mini PC dedicado da
cafeteria. Adicionar um serviço de container para ele aqui só faria sentido se algo no
fluxo de desenvolvimento passasse a depender disso — não é o caso hoje.
