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
Ainda não há shadcn/ui nem as rotas de negócio (`/pdv`, `/kitchen`, `/admin`,
`/c/{commandNumber}`) — isso é escopo de tickets futuros (a partir de FARELO-018).

## Como rodar

Pré-requisitos: Node.js 20+ e npm.

```bash
cd apps/web
npm install
npm run dev
```

O app fica disponível em [http://localhost:3000](http://localhost:3000).

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
