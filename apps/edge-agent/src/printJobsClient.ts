/**
 * Cliente HTTP para `GET /api/v1/print-jobs` (FARELO-076), implementado em
 * paralelo por um backend-agent em `apps/api`. Retorna todos os `PrintJob`s
 * pendentes, do mais antigo pro mais novo (contrato exato do endpoint, ver
 * `docs/PROMPT_MESTRE.md` seção 11/12 e `docs/domain-model.md` — pacote
 * `printing`).
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

export type PrintJob = {
  id: string;
  orderId: string;
  content: PrintJobContent;
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

  const { id, orderId, content, status, createdAt } = raw;

  if (typeof id !== "string") {
    throw new Error(`PrintJob no índice ${index} não tem "id" (string)`);
  }
  if (typeof orderId !== "string") {
    throw new Error(`PrintJob "${id}" não tem "orderId" (string)`);
  }
  if (typeof status !== "string") {
    throw new Error(`PrintJob "${id}" não tem "status" (string)`);
  }
  if (typeof createdAt !== "string") {
    throw new Error(`PrintJob "${id}" não tem "createdAt" (string)`);
  }

  return {
    id,
    orderId,
    content: parsePrintJobContent(content, id),
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

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

/** Uma linha legível por `PrintJob`, para o log do polling. */
export function formatPrintJobLog(job: PrintJob): string {
  const itemsSummary = job.content.items
    .map((item) => `${item.quantity}x ${item.productName}`)
    .join(", ");
  // null vira um rótulo legível em vez do literal "null" na interpolação de
  // string — mesmo caso de itens sem estação atribuída descrito no type acima.
  const station = job.content.productionStation ?? "sem estação atribuída";

  return (
    `PrintJob ${job.id} — comanda ${job.content.commandNumber} ` +
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
