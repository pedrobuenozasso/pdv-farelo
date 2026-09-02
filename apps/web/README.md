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
`src/lib/api/client.ts`.
Ainda não há shadcn/ui, nem edição/exclusão de categoria ou produto (sem
endpoint `PUT`/`DELETE` para nenhum dos dois no backend ainda — para
produto está explicitamente marcado como escopo de FARELO-016+ em
`docs/api.md`), nem as demais rotas de negócio (`/pdv`, `/kitchen`,
`/c/{commandNumber}`, shell completo de `/admin`) — isso é escopo de
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

Para testar as telas `/admin/categories` e `/admin/products` de ponta a
ponta, suba o Postgres e o backend antes do frontend:

```bash
cp infra/.env.example infra/.env
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d
cd apps/api && ./mvnw spring-boot:run
```

Depois, com o frontend rodando (`npm run dev`), acesse
[http://localhost:3000/admin/categories](http://localhost:3000/admin/categories)
ou
[http://localhost:3000/admin/products](http://localhost:3000/admin/products)
(crie ao menos uma categoria antes de cadastrar produtos).

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
