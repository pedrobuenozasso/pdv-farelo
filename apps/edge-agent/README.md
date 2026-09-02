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

**Esqueleto mínimo (FARELO-075).** Ainda não conecta com a API, não busca/recebe
`PrintJob`s e não imprime nada. Isso é escopo de tickets futuros (FARELO-076+).
O único comportamento hoje é inicializar e logar que o processo subiu — o suficiente
para validar que o projeto Node.js/TypeScript builda e roda.

## Stack

Node.js + TypeScript. Ver [`ADR-002-edge-agent-nodejs.md`](../../docs/decisions/ADR-002-edge-agent-nodejs.md)
para a justificativa (reaproveita o ecossistema JS já usado em `apps/web`, bibliotecas
ESC/POS maduras disponíveis, leve o suficiente para um mini PC dedicado).

## Rodando localmente

Pré-requisito: Node.js 20+.

```bash
cd apps/edge-agent
npm install

# desenvolvimento (recarrega a cada mudança, via tsx)
npm run dev

# build + execução do artefato compilado
npm run build
npm start
```

Saída esperada: `Farelo Edge Agent iniciado`.

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

Neste momento, com apenas o esqueleto mínimo implementado (sem lógica real de conexão
com a API), não há ainda o que orquestrar via Compose — a decisão de adicionar (ou não)
um serviço de container para ele fica para quando houver comportamento real de rede a
testar localmente junto do backend (FARELO-076+).
