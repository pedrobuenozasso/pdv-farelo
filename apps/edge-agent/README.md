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

**Consulta PrintJobs pendentes (FARELO-076).** O serviço conecta com a API e faz
polling periódico de `GET /api/v1/print-jobs`, logando os `PrintJob`s pendentes
encontrados (id, comanda, estação, itens). Ainda não imprime nada de verdade
(FARELO-078) nem reporta status de impressão (FARELO-077) — só busca e loga. Uma
falha de rede/API indisponível é logada e o próximo ciclo tenta de novo; o
processo nunca cai por causa disso (fila temporária local, mencionada no prompt
mestre seção 11, é responsabilidade futura, ainda não implementada).

FARELO-075 (esqueleto mínimo — só inicializar e logar que o processo subiu) foi o
ponto de partida deste app.

## Stack

Node.js + TypeScript. Ver [`ADR-002-edge-agent-nodejs.md`](../../docs/decisions/ADR-002-edge-agent-nodejs.md)
para a justificativa (reaproveita o ecossistema JS já usado em `apps/web`, bibliotecas
ESC/POS maduras disponíveis, leve o suficiente para um mini PC dedicado).

## Configuração

Variáveis de ambiente (nenhuma obrigatória — todas têm default para dev local):

| Variável                             | Default                 | Descrição                                                                                                                                                                  |
| ------------------------------------ | ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `FARELO_API_BASE_URL`                | `http://localhost:8080` | URL base da API (`apps/api`). Aponte para o backend rodando nativamente (`./mvnw spring-boot:run`) ou via `infra/docker-compose.dev.yml` (porta `8080` publicada no host). |
| `FARELO_EDGE_AGENT_POLL_INTERVAL_MS` | `5000`                  | Intervalo, em milissegundos, entre consultas a `GET /api/v1/print-jobs`.                                                                                                   |

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

## Testes

```bash
npm test
```

Usa o test runner nativo do Node (`node:test`, via `tsx --test`) — sem lib de
teste adicional. Cobre o parsing/formatação da resposta de
`GET /api/v1/print-jobs` (`src/printJobsClient.test.ts`) com um fixture do
JSON do contrato do endpoint, incluindo casos de entrada malformada. Não há
teste de integração contra uma API rodando de verdade: para o tamanho atual
deste serviço (sem lógica de negócio, só consultar + logar), isso seria mais
pesado do que o escopo pede — a validação de ponta a ponta (cliente HTTP +
parsing contra o backend real) é feita manualmente ao rodar `npm run dev`
apontando para uma API local, como descrito acima.

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
