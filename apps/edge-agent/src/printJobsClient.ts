/**
 * Cliente HTTP para os endpoints de `PrintJob` consumidos pelo Edge Agent:
 *
 * - `GET /api/v1/print-jobs` (FARELO-076): lista `PrintJob`s pendentes.
 * - `POST /api/v1/print-jobs/{id}/printed` e `.../failed` (FARELO-077):
 *   reportam o desfecho de um job (sem corpo, `200 OK`).
 *
 * Implementados em paralelo por um backend-agent em `apps/api` (contrato
 * exato dos três endpoints, ver `docs/PROMPT_MESTRE.md` seção 11/12 e
 * `docs/domain-model.md` — pacote `printing`).
 *
 * Os três ficam neste único módulo (em vez de um arquivo novo só para
 * `printed`/`failed`): é o mesmo contrato HTTP de `PrintJob`, pequeno o
 * suficiente (poucas linhas cada endpoint) para não justificar fragmentar
 * em dois módulos por causa deste ticket.
 *
 * Usa `fetch` nativo do Node (disponível desde o Node 18, estável — ver
 * ADR-002) em vez de adicionar uma lib de HTTP client: não há necessidade
 * hoje (sem retries/interceptors/streaming) que justifique a dependência
 * extra.
 *
 * Lembrete de escopo (AGENTS.md / PROMPT_MESTRE.md seção 11): o Edge Agent
 * não tem regra de negócio de pedidos. `parsePrintJobs` só valida o formato
 * mínimo necessário para não deixar o restante do código lidar com `unknown`
 * — não interpreta nem decide nada sobre o conteúdo do pedido.
 *
 * `type` (FARELO-210/211): um `PrintJob` agora vem em dois formatos —
 * `"KITCHEN_TICKET"` (o original, por pedido, `orderId`/`content`
 * populados) ou `"COMMAND_CHECK"` ("conferência", por comanda inteira,
 * `commandNumber`/`commandCheckContent` populados). Exatamente um par é
 * populado por job — o outro fica `null` — mesma exclusividade que o
 * backend garante via `ck_print_job_type_scope`
 * (V36__add_print_job_type_and_command_columns.sql). `poller.ts` decide o
 * que fazer (endereço de impressora, formatação ESC/POS) a partir de
 * `type`, nunca inferindo do que está populado.
 */

export type PrintJobItem = {
  productName: string;
  quantity: number;
};

export type PrintJobContent = {
  commandNumber: number;
  // null = itens sem estação de produção atribuída (Product.productionStation
  // nullable no backend — ver docs/domain-model.md, pacote `printing`,
  // FARELO-074: esses itens não são descartados, viram um PrintJob próprio
  // com productionStation explicitamente null no JSON, nunca omitido).
  productionStation: string | null;
  items: PrintJobItem[];
};

// FARELO-210/211: conteúdo de uma "conferência" (COMMAND_CHECK) — pré-conta
// da comanda inteira, com preços e total (diferente de PrintJobContent, que
// só tem nome/quantidade — um ticket de cozinha não precisa de preço).
export type CommandCheckItem = {
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
};

export type CommandCheckContent = {
  commandNumber: number;
  items: CommandCheckItem[];
  total: number;
};

export type PrintJobType = "KITCHEN_TICKET" | "COMMAND_CHECK";

export type PrintJob = {
  id: string;
  type: PrintJobType;
  // Exatamente um de cada par é não-nulo, conforme `type` — ver comentário
  // do topo deste arquivo.
  orderId: string | null;
  commandNumber: number | null;
  content: PrintJobContent | null;
  commandCheckContent: CommandCheckContent | null;
  status: string;
  createdAt: string;
};

/**
 * Valida o formato mínimo da resposta e converte para `PrintJob[]`.
 * Lança um erro descritivo (nunca deixa passar um `unknown` silenciosamente)
 * quando o corpo não é o array esperado — quem chama decide o que fazer com
 * o erro (aqui, o poller apenas loga e tenta de novo no próximo ciclo).
 */
export function parsePrintJobs(body: unknown): PrintJob[] {
  if (!Array.isArray(body)) {
    throw new Error(
      `Resposta de GET /api/v1/print-jobs não é um array: ${JSON.stringify(body)}`,
    );
  }
  return body.map(parsePrintJob);
}

function parsePrintJob(raw: unknown, index: number): PrintJob {
  if (!isRecord(raw)) {
    throw new Error(`PrintJob no índice ${index} não é um objeto`);
  }

  const { id, type, orderId, commandNumber, content, commandCheckContent, status, createdAt } = raw;

  if (typeof id !== "string") {
    throw new Error(`PrintJob no índice ${index} não tem "id" (string)`);
  }
  if (type !== "KITCHEN_TICKET" && type !== "COMMAND_CHECK") {
    throw new Error(
      `PrintJob "${id}" não tem "type" válido ("KITCHEN_TICKET" ou "COMMAND_CHECK")`,
    );
  }
  if (typeof status !== "string") {
    throw new Error(`PrintJob "${id}" não tem "status" (string)`);
  }
  if (typeof createdAt !== "string") {
    throw new Error(`PrintJob "${id}" não tem "createdAt" (string)`);
  }

  if (type === "KITCHEN_TICKET") {
    if (typeof orderId !== "string") {
      throw new Error(`PrintJob "${id}" (KITCHEN_TICKET) não tem "orderId" (string)`);
    }
    return {
      id,
      type,
      orderId,
      commandNumber: null,
      content: parsePrintJobContent(content, id),
      commandCheckContent: null,
      status,
      createdAt,
    };
  }

  if (typeof commandNumber !== "number") {
    throw new Error(`PrintJob "${id}" (COMMAND_CHECK) não tem "commandNumber" (number)`);
  }
  return {
    id,
    type,
    orderId: null,
    commandNumber,
    content: null,
    commandCheckContent: parseCommandCheckContent(commandCheckContent, id),
    status,
    createdAt,
  };
}

function parsePrintJobContent(
  raw: unknown,
  printJobId: string,
): PrintJobContent {
  if (!isRecord(raw)) {
    throw new Error(`PrintJob "${printJobId}" não tem "content" (objeto)`);
  }

  const { commandNumber, productionStation, items } = raw;

  if (typeof commandNumber !== "number") {
    throw new Error(
      `PrintJob "${printJobId}".content não tem "commandNumber" (number)`,
    );
  }
  // null é um valor válido e esperado (itens sem estação atribuída — ver o
  // tipo PrintJobContent acima), não um campo ausente/malformado.
  if (typeof productionStation !== "string" && productionStation !== null) {
    throw new Error(
      `PrintJob "${printJobId}".content."productionStation" não é string nem null`,
    );
  }
  if (!Array.isArray(items)) {
    throw new Error(`PrintJob "${printJobId}".content não tem "items" (array)`);
  }

  return {
    commandNumber,
    productionStation,
    items: items.map((item, index) =>
      parsePrintJobItem(item, printJobId, index),
    ),
  };
}

function parsePrintJobItem(
  raw: unknown,
  printJobId: string,
  index: number,
): PrintJobItem {
  if (!isRecord(raw)) {
    throw new Error(
      `PrintJob "${printJobId}".content.items[${index}] não é um objeto`,
    );
  }

  const { productName, quantity } = raw;

  if (typeof productName !== "string") {
    throw new Error(
      `PrintJob "${printJobId}".content.items[${index}] não tem "productName" (string)`,
    );
  }
  if (typeof quantity !== "number") {
    throw new Error(
      `PrintJob "${printJobId}".content.items[${index}] não tem "quantity" (number)`,
    );
  }

  return { productName, quantity };
}

// FARELO-210/211: parser de CommandCheckContent, mesmo nível de validação
// campo-a-campo que parsePrintJobContent/parsePrintJobItem acima.
function parseCommandCheckContent(
  raw: unknown,
  printJobId: string,
): CommandCheckContent {
  if (!isRecord(raw)) {
    throw new Error(`PrintJob "${printJobId}" não tem "commandCheckContent" (objeto)`);
  }

  const { commandNumber, items, total } = raw;

  if (typeof commandNumber !== "number") {
    throw new Error(
      `PrintJob "${printJobId}".commandCheckContent não tem "commandNumber" (number)`,
    );
  }
  if (!Array.isArray(items)) {
    throw new Error(`PrintJob "${printJobId}".commandCheckContent não tem "items" (array)`);
  }
  if (typeof total !== "number") {
    throw new Error(`PrintJob "${printJobId}".commandCheckContent não tem "total" (number)`);
  }

  return {
    commandNumber,
    items: items.map((item, index) => parseCommandCheckItem(item, printJobId, index)),
    total,
  };
}

function parseCommandCheckItem(
  raw: unknown,
  printJobId: string,
  index: number,
): CommandCheckItem {
  if (!isRecord(raw)) {
    throw new Error(
      `PrintJob "${printJobId}".commandCheckContent.items[${index}] não é um objeto`,
    );
  }

  const { productName, quantity, unitPrice, lineTotal } = raw;

  if (typeof productName !== "string") {
    throw new Error(
      `PrintJob "${printJobId}".commandCheckContent.items[${index}] não tem "productName" (string)`,
    );
  }
  if (typeof quantity !== "number") {
    throw new Error(
      `PrintJob "${printJobId}".commandCheckContent.items[${index}] não tem "quantity" (number)`,
    );
  }
  if (typeof unitPrice !== "number") {
    throw new Error(
      `PrintJob "${printJobId}".commandCheckContent.items[${index}] não tem "unitPrice" (number)`,
    );
  }
  if (typeof lineTotal !== "number") {
    throw new Error(
      `PrintJob "${printJobId}".commandCheckContent.items[${index}] não tem "lineTotal" (number)`,
    );
  }

  return { productName, quantity, unitPrice, lineTotal };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

/** Uma linha legível por `PrintJob`, para o log do polling. */
export function formatPrintJobLog(job: PrintJob): string {
  if (job.type === "COMMAND_CHECK") {
    // Não-nulo por construção — ver parsePrintJob (branch COMMAND_CHECK).
    const content = job.commandCheckContent!;
    return (
      `PrintJob ${job.id} — conferência comanda ${content.commandNumber}: ` +
      `${content.items.length} item(ns), total R$ ${content.total.toFixed(2)}`
    );
  }

  // Não-nulo por construção — ver parsePrintJob (branch KITCHEN_TICKET).
  const content = job.content!;
  const itemsSummary = content.items
    .map((item) => `${item.quantity}x ${item.productName}`)
    .join(", ");
  // null vira um rótulo legível em vez do literal "null" na interpolação de
  // string — mesmo caso de itens sem estação atribuída descrito no type acima.
  const station = content.productionStation ?? "sem estação atribuída";

  return (
    `PrintJob ${job.id} — comanda ${content.commandNumber} ` +
    `[${station}]: ${itemsSummary || "(sem itens)"}`
  );
}

/**
 * Busca os `PrintJob`s pendentes em `GET {apiBaseUrl}/api/v1/print-jobs`.
 * Lança em caso de erro de rede ou resposta não-2xx/malformada — quem chama
 * (o poller) é responsável por não deixar isso derrubar o processo.
 */
export async function fetchPendingPrintJobs(
  apiBaseUrl: string,
): Promise<PrintJob[]> {
  const response = await fetch(`${apiBaseUrl}/api/v1/print-jobs`);

  if (!response.ok) {
    throw new Error(
      `GET /api/v1/print-jobs falhou com status ${response.status}`,
    );
  }

  const body: unknown = await response.json();
  return parsePrintJobs(body);
}

/**
 * Reporta o desfecho de um `PrintJob` via
 * `POST {apiBaseUrl}/api/v1/print-jobs/{jobId}/printed|failed` — sem corpo,
 * `200 OK` esperado (contrato do backend-agent, FARELO-077). Lança em caso
 * de erro de rede ou resposta não-2xx; quem chama decide como lidar com
 * isso (no poller, hoje: loga e segue, sem retry — ver `poller.ts`).
 */
async function reportPrintJobOutcome(
  apiBaseUrl: string,
  jobId: string,
  outcome: "printed" | "failed",
): Promise<void> {
  const path = `/api/v1/print-jobs/${jobId}/${outcome}`;
  const response = await fetch(`${apiBaseUrl}${path}`, { method: "POST" });

  if (!response.ok) {
    throw new Error(`POST ${path} falhou com status ${response.status}`);
  }
}

/** Reporta o `PrintJob` `jobId` como `PRINTED`. Ver `reportPrintJobOutcome`. */
export function reportPrintJobPrinted(
  apiBaseUrl: string,
  jobId: string,
): Promise<void> {
  return reportPrintJobOutcome(apiBaseUrl, jobId, "printed");
}

/** Reporta o `PrintJob` `jobId` como `FAILED`. Ver `reportPrintJobOutcome`. */
export function reportPrintJobFailed(
  apiBaseUrl: string,
  jobId: string,
): Promise<void> {
  return reportPrintJobOutcome(apiBaseUrl, jobId, "failed");
}
