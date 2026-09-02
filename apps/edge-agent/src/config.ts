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
 *
 * ────────────────────────────────────────────────────────────────────────
 * Endereços de impressora por estação (FARELO-078)
 * ────────────────────────────────────────────────────────────────────────
 *
 * Mapear `productionStation` → endereço IP/porta da impressora física é
 * infraestrutura de dispositivo local (prompt mestre, seção 11: "o Edge
 * Agent nunca deve possuir regra de negócio de pedidos — é apenas
 * infraestrutura de dispositivos"), não regra de negócio de pedido — por
 * isso vive aqui, no Edge Agent, via variáveis de ambiente, e não no
 * backend. O backend só sabe que existe uma `productionStation` (string);
 * qual impressora física atende cada estação é decisão local de quem opera
 * o mini PC da cafeteria.
 *
 * Convenção de nome de variável: `FARELO_PRINTER_<ESTAÇÃO>_HOST` /
 * `FARELO_PRINTER_<ESTAÇÃO>_PORT`, onde `<ESTAÇÃO>` é o valor de
 * `productionStation` normalizado (maiúsculas; qualquer caractere que não
 * seja `A-Z`/`0-9` vira `_`) — ex: `productionStation: "BAR"` procura
 * `FARELO_PRINTER_BAR_HOST`/`FARELO_PRINTER_BAR_PORT`;
 * `productionStation: "KITCHEN"` procura `FARELO_PRINTER_KITCHEN_HOST`/
 * `FARELO_PRINTER_KITCHEN_PORT`. Deliberadamente genérico (nenhuma estação
 * específica hardcoded no código): o Edge Agent não precisa conhecer de
 * antemão quais estações existem — isso seria voltar a acoplar
 * conhecimento de negócio (nomes de estação são decisão de `Product`, no
 * backend) na infraestrutura de dispositivo. `resolvePrinterAddress`
 * apenas deriva o nome da variável a partir do valor que já veio no
 * `PrintJob.content`.
 *
 * `FARELO_PRINTER_DEFAULT_HOST`/`FARELO_PRINTER_DEFAULT_PORT`: fallback
 * usado quando não há uma variável específica para a estação (inclusive
 * quando `productionStation` é `null` — itens sem estação atribuída, ver
 * FARELO-074 em `docs/domain-model.md`) — mesma convenção de nome, com
 * `DEFAULT` no lugar da estação.
 *
 * Sem nenhum endereço configurado (nem específico, nem default):
 * `resolvePrinterAddress` devolve `null` — quem chama (`poller.ts`) trata
 * isso como falha de impressão (`reportPrintJobFailed`), não crasha o
 * processo nem inventa um endereço.
 */

export type Config = {
  apiBaseUrl: string;
  pollIntervalMs: number;
};

export type PrinterAddress = {
  host: string;
  port: number;
};

const DEFAULT_API_BASE_URL = "http://localhost:8080";
const DEFAULT_POLL_INTERVAL_MS = 5000;

const PRINTER_ENV_PREFIX = "FARELO_PRINTER_";
const DEFAULT_STATION_ENV_KEY = "DEFAULT";

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

/**
 * `"BAR"` → `"BAR"`, `"Salão Externo"` → `"SALAO_EXTERNO"`. Diacríticos são
 * removidos (não viram `_`) via normalização Unicode NFD — decompõe "ã" em
 * "a" + acento combinante, depois descarta os acentos combinantes antes de
 * tratar o restante dos caracteres não-alfanuméricos (espaços, etc) como
 * separador `_`. Sem isso, "ã"/"ç"/etc virariam `_` como qualquer símbolo,
 * produzindo nomes de variável menos previsíveis (ex: "Salão" → "SAL_O" em
 * vez de "SALAO").
 */
function normalizeStationEnvKey(station: string): string {
  return station
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "") // bloco Unicode "Combining Diacritical Marks"
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

function readPrinterAddress(
  env: NodeJS.ProcessEnv,
  stationEnvKey: string,
): PrinterAddress | null {
  const prefix = `${PRINTER_ENV_PREFIX}${stationEnvKey}`;
  const rawHost = env[`${prefix}_HOST`]?.trim();
  if (!rawHost) {
    return null;
  }

  const rawPort = env[`${prefix}_PORT`]?.trim();
  const port = rawPort ? Number(rawPort) : NaN;
  if (!Number.isFinite(port) || port <= 0) {
    // Host configurado mas porta ausente/inválida: tratado como "não
    // configurado" (cai para o fallback default), não como erro fatal —
    // mesmo espírito defensivo de `loadConfig` acima com valores
    // inválidos de `FARELO_EDGE_AGENT_POLL_INTERVAL_MS`.
    return null;
  }

  return { host: rawHost, port };
}

/**
 * Resolve o endereço IP/porta da impressora responsável por `station`
 * (o `productionStation` de um `PrintJobContent`, ou `null` para itens sem
 * estação atribuída) — ver convenção de nome de variável no comentário do
 * topo deste arquivo. Tenta primeiro a variável específica da estação,
 * depois o fallback `FARELO_PRINTER_DEFAULT_HOST`/`_PORT`. Devolve `null`
 * quando nada está configurado — quem chama decide como tratar isso (no
 * Edge Agent, hoje: falha de impressão).
 */
export function resolvePrinterAddress(
  station: string | null,
  env: NodeJS.ProcessEnv = process.env,
): PrinterAddress | null {
  if (station !== null) {
    const stationAddress = readPrinterAddress(
      env,
      normalizeStationEnvKey(station),
    );
    if (stationAddress) {
      return stationAddress;
    }
  }

  return readPrinterAddress(env, DEFAULT_STATION_ENV_KEY);
}
