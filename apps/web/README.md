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
Query.
FARELO-042/043 adicionaram o cardápio em si: quando a comanda está
`AVAILABLE`/`OPEN`, `/c/[commandNumber]` mostra os produtos agrupados
por categoria (nome, descrição, preço em BRL, imagem via `<img>` simples
— sem `next/image` por enquanto, YAGNI). `listCategories`/`listProducts`
(`src/lib/api/categories.ts`/`products.ts`) ficaram isomórficos: caminho
relativo via proxy quando chamados de um client component (Admin), URL
absoluta via `API_BASE_URL` quando chamados de um Server Component
(cardápio), decidido em runtime por `typeof window`. Como
`GET /api/v1/categories`/`GET /api/v1/products` ainda não têm filtro de
`active`/`availableOnMenu`, o cardápio filtra no frontend mesmo (produto
visível apenas se `active && availableOnMenu`; categoria visível apenas
se tiver ao menos um produto visível) — aceitável para o volume de dados
atual; um endpoint público filtrado é candidato natural se o catálogo
crescer bastante.
FARELO-044 adicionou o carrinho local ao cardápio: cada produto ganhou um
"+"/"−" para adicionar/remover, com um rodapé mostrando itens, quantidade,
subtotal por item e total (BRL) — sem persistência entre reloads (fora de
escopo por ora). **Mudança de arquitetura**: como o carrinho precisa de
estado no cliente, a parte do cardápio que renderiza os produtos foi
extraída para `src/app/c/[commandNumber]/menu.tsx`, um Client Component
(`"use client"`, `useState` local — não precisa de Zustand/Context nesse
escopo) que recebe `sections` já carregado via SSR como prop; `page.tsx`
continua Server Component (busca da comanda e do cardápio, mensagens de
erro/status).
FARELO-045 fechou o fluxo do cliente: "Finalizar pedido" abre um
formulário de nome/telefone (`react-hook-form` + `zod`, mesmo padrão do
Admin) e envia o pedido via `createOrder`
(`src/lib/api/orders.ts` → `POST /api/v1/orders`). **Importante**:
nome/telefone existem só para a experiência do fluxo (prompt mestre seção 6) — não são enviados ao backend nem persistidos (`CreateOrderRequest` só
tem `commandNumber`/`items`; não existe domínio `customer` ainda),
documentado em `orders.ts`. Sucesso mostra uma confirmação e limpa o
carrinho; erros de negócio (`COMMAND_CANNOT_ACCEPT_ORDERS`,
`PRODUCT_NOT_AVAILABLE`) aparecem perto do formulário sem derrubar o
carrinho nem os dados já digitados — mesmo tratamento genérico de
`ApiError` usado no Admin (extraído para `apiErrorMessage` em
`src/lib/api/client.ts`, terceira ocorrência do padrão).
FARELO-035 adicionou a primeira tela do PDV (`app.farelo.com.br/pdv`,
interface **interna**, tom/densidade de atendente — diferente do
cardápio do cliente): uma grade com os números 1-100 (gerada no próprio
frontend — não existe `GET /api/v1/commands` de listagem, e os números
são fixos desde o seed, então inventar um endpoint só pra isso seria
prematuro); ao selecionar um número, busca `GET /api/v1/commands/{number}`
e `GET /api/v1/commands/{number}/orders` e mostra tudo num painel inline
(pedidos com itens e subtotal em BRL), com botões "Abrir
comanda"/"Fechar comanda" habilitados conforme o status atual
(`AVAILABLE`→abrir; `OPEN`/`PAYMENT_REQUESTED`→fechar). Client Component
com TanStack Query, mesmo padrão das páginas do Admin (sem necessidade de
SSR aqui — ferramenta interna, muita interatividade). `commands.ts`
(antes só usado por Server Component) virou isomórfico, mesmo padrão de
`categories.ts`/`products.ts`, para servir tanto `/c/[commandNumber]`
quanto o novo `/pdv`; `listCommandOrders` foi para `orders.ts` (já dono
dos tipos `Order`/`OrderItem`), mesmo que a URL fique aninhada sob
`/commands`.
Ainda não há shadcn/ui, nem edição/exclusão de categoria (sem endpoint
`PUT`/`DELETE` para categoria no backend ainda) nem exclusão de produto
(sem `DELETE`, fora do roadmap atual — ver `docs/api.md`), nem shell de
navegação completo de `/pdv`/`/admin`, nem `/kitchen` — isso é escopo de
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

Para testar as telas `/admin/categories`, `/admin/products`,
`/c/[commandNumber]` ou `/pdv` de ponta a ponta, suba o Postgres e o
backend antes do frontend:

```bash
cp infra/.env.example infra/.env
docker compose -f infra/docker-compose.yml --env-file infra/.env up -d
cd apps/api && ./mvnw spring-boot:run
```

Depois, com o frontend rodando (`npm run dev`), acesse
[http://localhost:3000/admin/categories](http://localhost:3000/admin/categories),
[http://localhost:3000/admin/products](http://localhost:3000/admin/products)
(crie ao menos uma categoria antes de cadastrar produtos),
[http://localhost:3000/c/1](http://localhost:3000/c/1) (comandas 1-100
já vêm no seed — qualquer número fora desse intervalo mostra a mensagem
de "não encontrada") ou [http://localhost:3000/pdv](http://localhost:3000/pdv)
(selecione qualquer número de 1 a 100).

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
