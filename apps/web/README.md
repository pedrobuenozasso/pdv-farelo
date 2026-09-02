# apps/web

Next.js + TypeScript + Tailwind CSS + shadcn/ui.

Interfaces servidas por este app:

- `pedido.farelo.com.br` — cardápio QR do cliente (`/c/{commandNumber}`)
- `app.farelo.com.br` — aplicação interna:
  - `/pdv` — ponto de venda
  - `/kitchen` — KDS (Kitchen Display System)
  - `/admin` — painel administrativo

## Status

Setup inicial do projeto (Next.js + TypeScript + Tailwind CSS) concluído em FARELO-005:
App Router, TypeScript, Tailwind CSS e ESLint configurados via `create-next-app`.
FARELO-006 adicionou Prettier (com `prettier-plugin-tailwindcss` para ordenar
classes Tailwind) integrado ao ESLint via `eslint-config-prettier`.
FARELO-018 adicionou a primeira tela real do Admin (`/admin/categories`,
lista + criação de categorias), estabelecendo os padrões de acesso à API
(proxy via `next.config.ts`, TanStack Query, react-hook-form + Zod) que os
próximos tickets de UI reaproveitam.
FARELO-019 adicionou `/admin/products` (lista + criação de produtos, com
seleção de categoria) no mesmo padrão, e extraiu o parsing de erro
(`ApiError`/`parseResponse`) compartilhado entre os dois domínios para
`src/lib/api/client.ts`. Um follow-up do FARELO-019 adicionou edição
inline de produto (`PUT /api/v1/products/{id}`, incluindo `active`,
`availableOnMenu` e `availableOnPos`), assim que esse endpoint
(FARELO-016/FARELO-017) foi mergeado no backend.
FARELO-040/041 adicionaram a primeira rota do cardápio QR
(`pedido.farelo.com.br`, interface do **cliente** — separada do Admin,
tom sem jargão de staff): `/c/[commandNumber]` busca a comanda pelo
número da URL e mostra uma confirmação simples ou uma mensagem amigável
de "não encontrada" (número inexistente, não numérico ou fora de
1-100). É um Server Component (`src/lib/api/commands.ts` busca direto
via `API_BASE_URL`, sem passar pelo proxy do `next.config.ts` — ver
comentário no próprio arquivo) em vez de client component + TanStack
Query, já que ainda não há interatividade nesta tela.
Ainda não há shadcn/ui, nem edição/exclusão de categoria (sem endpoint
`PUT`/`DELETE` para categoria no backend ainda) nem exclusão de produto
(sem `DELETE`, fora do roadmap atual — ver `docs/api.md`), nem cardápio
em `/c/{commandNumber}` (FARELO-042/043) nem as demais rotas de negócio
(`/pdv`, `/kitchen`, shell completo de `/admin`) — isso é escopo de
tickets futuros.

## Como rodar

Pré-requisitos: Node.js 20+ e npm.

```bash
cd apps/web
npm install
npm run dev
```

O app fica disponível em [http://localhost:3000](http://localhost:3000).

### Backend (proxy de `/api`)

As páginas do Admin chamam a API sempre com caminho relativo (ex.:
`/api/v1/categories`), nunca a URL absoluta do backend. O `next.config.ts`
faz o proxy de `/api/*` para o backend via `rewrites()`, usando a env var
`API_BASE_URL` (default `http://localhost:8080` se não definida). Em
produção esse proxy não é necessário — o Caddy assume esse papel (ver
`infra/README.md`) — mas ele também funciona com `next start` standalone.

Para testar as telas `/admin/categories`, `/admin/products` ou
`/c/[commandNumber]` de ponta a ponta, suba o Postgres e o backend antes
do frontend:

```bash
cp infra/.env.example infra/.env
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d
cd apps/api && ./mvnw spring-boot:run
```

Depois, com o frontend rodando (`npm run dev`), acesse
[http://localhost:3000/admin/categories](http://localhost:3000/admin/categories),
[http://localhost:3000/admin/products](http://localhost:3000/admin/products)
(crie ao menos uma categoria antes de cadastrar produtos) ou
[http://localhost:3000/c/1](http://localhost:3000/c/1) (comandas 1-100
já vêm no seed — qualquer número fora desse intervalo mostra a mensagem
de "não encontrada").

## Build

```bash
cd apps/web
npm run build
```

## Lint

```bash
cd apps/web
npm run lint
```

## Formatação (Prettier)

```bash
cd apps/web
npm run format        # formata os arquivos (prettier --write)
npm run format:check  # só verifica, sem alterar (útil em CI)
```
