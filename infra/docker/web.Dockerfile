# Frontend — Dockerfile de desenvolvimento (não é build de produção).
#
# Build context: raiz do repositório (ver infra/docker-compose.dev.yml), por
# isso os caminhos de COPY abaixo começam em "apps/web/". Isso mantém este
# Dockerfile dentro de infra/**, evitando editar arquivos em apps/web/**.
#
# Estágio único rodando "next dev" (não "next build && next start" — isso é
# proposital: este compose é só DX de desenvolvimento local). O
# docker-compose.dev.yml monta apps/web como bind mount por cima do /app da
# imagem, então mudanças no código do host são pegas pelo Fast Refresh do
# Next.js sem precisar rebuildar a imagem; apenas node_modules instalados
# aqui (COPY + npm ci) são preservados via volume anônimo no compose.

FROM node:22-alpine
WORKDIR /app

COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci

COPY apps/web ./

EXPOSE 3000
CMD ["npm", "run", "dev", "--", "--hostname", "0.0.0.0"]
