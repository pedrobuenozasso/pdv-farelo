/**
 * Configuração do Edge Agent via variáveis de ambiente — mesmo espírito de
 * `apps/api` (`application.yml`, defaults sensatos com `${VAR:default}`) e
 * `apps/web` (`process.env.API_BASE_URL ?? "http://localhost:8080"`).
 *
 * `FARELO_API_BASE_URL`: URL base da API do backend (`apps/api`). Default
 * `http://localhost:8080` — válido para quem roda o backend nativamente
 * (`./mvnw spring-boot:run`) ou via `infra/docker-compose.dev.yml` com a
 * porta do backend publicada no host. Sem prefixo genérico ("API_BASE_URL")
 * de propósito: o Edge Agent roda fisicamente fora da stack de
 * desenvolvimento (README.md), então seu ambiente nunca é o mesmo
 * processo/container que já usa `API_BASE_URL` em `apps/web` — um nome
 * específico evita confusão de qual serviço a variável configura quando o
 * mini PC tiver outros processos/scripts no futuro.
 *
 * `FARELO_EDGE_AGENT_POLL_INTERVAL_MS`: intervalo, em milissegundos, entre
 * consultas a `GET /api/v1/print-jobs`. Default 5000ms (mesma ordem de
 * grandeza do `outbox.worker.poll-interval-ms` em `apps/api`, que também é
 * 5000 por padrão) — frequente o suficiente para não atrasar a impressão
 * perceptivelmente, sem martelar a API.
 */

export type Config = {
  apiBaseUrl: string;
  pollIntervalMs: number;
};

const DEFAULT_API_BASE_URL = "http://localhost:8080";
const DEFAULT_POLL_INTERVAL_MS = 5000;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const rawApiBaseUrl = env.FARELO_API_BASE_URL?.trim();
  const apiBaseUrl =
    rawApiBaseUrl && rawApiBaseUrl.length > 0
      ? rawApiBaseUrl.replace(/\/+$/, "")
      : DEFAULT_API_BASE_URL;

  const rawPollInterval = env.FARELO_EDGE_AGENT_POLL_INTERVAL_MS;
  const parsedPollInterval = rawPollInterval ? Number(rawPollInterval) : NaN;
  const pollIntervalMs =
    Number.isFinite(parsedPollInterval) && parsedPollInterval > 0
      ? parsedPollInterval
      : DEFAULT_POLL_INTERVAL_MS;

  return { apiBaseUrl, pollIntervalMs };
}
