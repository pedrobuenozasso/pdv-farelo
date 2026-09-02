# ADR-002: Node.js + TypeScript para o Farelo Edge Agent

## Status

Aceito

## Contexto

O Farelo Edge Agent (`apps/edge-agent`, docs/PROMPT_MESTRE.md seção 11) é um serviço
separado do backend/frontend principal, executado fisicamente num mini PC dedicado na
cafeteria. Suas responsabilidades (implementadas a partir de FARELO-076, fora do
escopo deste ADR): conectar com a API, buscar/receber `PrintJob`s, imprimir
(preferencialmente ESC/POS), reportar status, verificar impressoras e manter fila
temporária local. Ele nunca deve conter regra de negócio de pedidos — é apenas
infraestrutura de dispositivos (por isso pertence ao `infra-agent`, não ao
`backend-agent`, conforme `AGENTS.md`).

Diferente do backend (obrigatoriamente Java 21 / Spring Boot 3) e do frontend
(obrigatoriamente Next.js), `docs/PROMPT_MESTRE.md` não mandata uma linguagem
específica para o Edge Agent — é uma decisão de infraestrutura em aberto.

## Decisão

Construir o Edge Agent em **Node.js + TypeScript**.

Motivos:

- **Nenhuma tecnologia nova para o projeto como um todo**: `apps/web` já usa
  Node.js/TypeScript/npm, então não há novo runtime, gerenciador de pacotes nem
  linguagem a manter — apenas reaproveitamento do ecossistema JS já presente no
  repositório.
- **Bibliotecas ESC/POS maduras**: `node-thermal-printer` e `escpos` cobrem o caso de
  uso principal (impressão térmica), reduzindo a necessidade de integração de baixo
  nível feita à mão.
- **Leve o suficiente para um mini PC dedicado**: um processo Node.js roda sem
  instalação pesada (sem JVM, sem runtime de container obrigatório), adequado para um
  dispositivo físico de recursos modestos na cafeteria.

## Consequências

- Duas linguagens de aplicação no repositório (Java no backend, TypeScript no
  frontend e agora no Edge Agent) — aceitável porque TypeScript já era usado em
  `apps/web`; não introduz um terceiro ecossistema.
- Tooling próprio (`package.json`, `tsconfig.json`, ESLint/Prettier) dentro de
  `apps/edge-agent`, independente do backend Maven e não orquestrado por
  `infra/docker-compose.dev.yml` (roda fisicamente fora da stack de desenvolvimento
  local — ver `apps/edge-agent/README.md`).
- Bibliotecas ESC/POS (`node-thermal-printer`/`escpos`) só serão adicionadas como
  dependência real quando a lógica de impressão for implementada (FARELO-076+), não
  neste esqueleto inicial.
