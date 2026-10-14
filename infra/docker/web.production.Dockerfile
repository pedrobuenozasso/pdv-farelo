FROM node:22-alpine AS build
WORKDIR /app
COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci
COPY apps/web ./
ARG API_BASE_URL=http://api:8080
ENV API_BASE_URL=${API_BASE_URL}
RUN npm run build

FROM node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
RUN addgroup -S farelo && adduser -S farelo -G farelo
COPY --from=build --chown=farelo:farelo /app/package.json /app/package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build --chown=farelo:farelo /app/.next ./.next
COPY --from=build --chown=farelo:farelo /app/next.config.ts ./next.config.ts
USER farelo
EXPOSE 3000
CMD ["npm", "run", "start", "--", "--hostname", "0.0.0.0"]
