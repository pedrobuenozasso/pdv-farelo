/**
 * Polling de PrintJobs e entrega à impressora. O journal local registra
 * PREPARED antes do envio; falhas após esse ponto são tratadas como entrega
 * incerta e nunca reimpressas automaticamente (PRINT-02).
 */

import {
  fetchPendingPrintJobs as fetchPendingPrintJobsImpl,
  reportPrintJobPrinted as reportPrintJobPrintedImpl,
  reportPrintJobFailed as reportPrintJobFailedImpl,
  formatPrintJobLog,
  type PrintJob,
} from "./printJobsClient.js";
import { resolvePrinterTarget as resolvePrinterTargetImpl } from "./config.js";
// ADR-003: modo gráfico (imagem renderizada + comando ESC/POS de imagem
// raster) substituiu o modo texto de `escpos.ts` como formatador padrão —
// ver `ticketImage.ts` para o porquê. `escpos.ts` continua no repositório
// (testado, histórico do FARELO-078), só não é mais o default aqui.
import {
  buildEscPosImageTicket as buildEscPosTicketImpl,
  buildCommandCheckEscPosImageTicket as buildCommandCheckEscPosTicketImpl,
} from "./ticketImage.js";
import { printOverTcp as printOverTcpImpl } from "./printerTransport.js";
import { printOverWindowsSpooler as printOverWindowsSpoolerImpl } from "./windowsSpooler.js";
import {
  FileDeliveryJournal,
  resolveStateDirectory,
  type DeliveryJournal,
} from "./deliveryState.js";
import type { PrinterTarget } from "./config.js";

// FARELO-210/211: "estação" fictícia usada para resolver o endereço de
// impressora de uma conferência (COMMAND_CHECK) através do mesmo mecanismo
// de config.ts#resolvePrinterAddress já usado para productionStation — ver
// esse arquivo para a convenção FARELO_PRINTER_<ESTAÇÃO>_HOST/_PORT. Uma
// conferência não tem productionStation (não pertence a nenhuma estação de
// produção), mas reaproveitar o mesmo mecanismo (com fallback automático
// para FARELO_PRINTER_DEFAULT_HOST/_PORT quando FARELO_PRINTER_CONFERENCE_*
// não está configurado) evita inventar uma segunda convenção de variável de
// ambiente só para este caso.
const CONFERENCE_STATION_KEY = "CONFERENCE";

export type PollerOptions = {
  apiBaseUrl: string;
  pollIntervalMs: number;
  deviceKey: string;
};

export type PollerDeps = {
  fetchPendingPrintJobs: typeof fetchPendingPrintJobsImpl;
  reportPrintJobPrinted: typeof reportPrintJobPrintedImpl;
  reportPrintJobFailed: typeof reportPrintJobFailedImpl;
  resolvePrinterTarget: typeof resolvePrinterTargetImpl;
  buildEscPosTicket: typeof buildEscPosTicketImpl;
  buildCommandCheckEscPosTicket: typeof buildCommandCheckEscPosTicketImpl;
  printOverTcp: typeof printOverTcpImpl;
  printOverWindowsSpooler: typeof printOverWindowsSpoolerImpl;
  journal: DeliveryJournal;
};

const defaultDeps: PollerDeps = {
  fetchPendingPrintJobs: fetchPendingPrintJobsImpl,
  reportPrintJobPrinted: reportPrintJobPrintedImpl,
  reportPrintJobFailed: reportPrintJobFailedImpl,
  resolvePrinterTarget: resolvePrinterTargetImpl,
  buildEscPosTicket: buildEscPosTicketImpl,
  buildCommandCheckEscPosTicket: buildCommandCheckEscPosTicketImpl,
  printOverTcp: printOverTcpImpl,
  printOverWindowsSpooler: printOverWindowsSpoolerImpl,
  journal: new FileDeliveryJournal(resolveStateDirectory()),
};

/** Processa um job sem transformar falha de report em nova impressão. */
async function printJob(
  apiBaseUrl: string,
  job: PrintJob,
  deps: PollerDeps,
  deviceKey: string,
): Promise<void> {
  let state: Awaited<ReturnType<DeliveryJournal["get"]>>;
  try {
    state = await deps.journal.get(job.id);
  } catch (error) {
    console.error(
      `  → Journal indisponível para PrintJob ${job.id}; impressão bloqueada:`,
      error,
    );
    return;
  }

  if (state === "PREPARED") {
    console.error(
      `  → PrintJob ${job.id} com entrega incerta. Conferir papel/fila e resolver manualmente; sem reimpressão automática.`,
    );
    return;
  }
  if (state === "SPOOL_ACCEPTED") {
    try {
      await deps.reportPrintJobPrinted(apiBaseUrl, job.id, deviceKey);
    } catch (error) {
      console.error(
        `  → PrintJob ${job.id} já entregue; falha ao reportar PRINTED (será repetido sem reimprimir):`,
        error,
      );
    }
    return;
  }

  let target: PrinterTarget;
  let ticket: Buffer;
  try {
    const station =
      job.type === "COMMAND_CHECK"
        ? CONFERENCE_STATION_KEY
        : job.content!.productionStation;
    const resolved = deps.resolvePrinterTarget(station);
    if (!resolved) {
      throw new Error(
        `Nenhuma impressora configurada para a estação "${station ?? "sem estação"}" (nem fallback FARELO_PRINTER_DEFAULT_*).`,
      );
    }
    target = resolved;
    ticket =
      job.type === "COMMAND_CHECK"
        ? deps.buildCommandCheckEscPosTicket(job.commandCheckContent!)
        : deps.buildEscPosTicket(job.content!);
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    console.error(`  → Falha antes de enviar PrintJob ${job.id}: ${reason}`);
    try {
      await deps.reportPrintJobFailed(apiBaseUrl, job.id, deviceKey);
    } catch (reportError) {
      console.error(
        `  → Falha ao reportar PrintJob ${job.id} como FAILED:`,
        reportError,
      );
    }
    return;
  }

  // Persistir PREPARED antes de qualquer I/O irreversível. Uma falha ou queda
  // depois daqui torna a entrega incerta e exige conferência humana.
  try {
    await deps.journal.prepare(job.id);
  } catch (error) {
    console.error(
      `  → Não foi possível persistir PrintJob ${job.id}; impressão bloqueada:`,
      error,
    );
    return;
  }
  try {
    if (target.transport === "windows-spooler") {
      await deps.printOverWindowsSpooler(target.printerName, ticket);
    } else {
      await deps.printOverTcp(target.host, target.port, ticket);
    }
    await deps.journal.markSpoolAccepted(job.id);
  } catch (error) {
    console.error(
      `  → Entrega incerta do PrintJob ${job.id}; não reportado como FAILED nem reimpresso:`,
      error,
    );
    return;
  }
  try {
    await deps.reportPrintJobPrinted(apiBaseUrl, job.id, deviceKey);
    console.log(
      `  → PrintJob ${job.id} entregue ao transporte e reportado como PRINTED.`,
    );
  } catch (error) {
    console.error(
      `  → PrintJob ${job.id} entregue ao transporte; falha ao reportar PRINTED (sem reimprimir):`,
      error,
    );
  }
}

/** Um ciclo de polling: busca jobs pendentes e tenta imprimir/reportar cada um. Exportado para teste (ver `poller.test.ts`). */
export async function pollOnce(
  apiBaseUrl: string,
  deviceKey: string,
  deps: PollerDeps = defaultDeps,
): Promise<void> {
  try {
    const jobs = await deps.fetchPendingPrintJobs(apiBaseUrl, deviceKey);

    if (jobs.length === 0) {
      console.log("Nenhum PrintJob pendente.");
      return;
    }

    console.log(`${jobs.length} PrintJob(s) pendente(s):`);
    for (const job of jobs) {
      console.log(`  ${formatPrintJobLog(job)}`);
      await printJob(apiBaseUrl, job, deps, deviceKey);
    }
  } catch (error) {
    // Rede indisponível, API fora do ar ou resposta malformada: loga e
    // segue — o próximo ciclo tenta de novo. Nunca deixa o processo cair.
    console.error(
      "Falha ao consultar PrintJobs pendentes (tentando de novo no próximo ciclo):",
      error instanceof Error ? error.message : error,
    );
  }
}

/**
 * Inicia o polling: consulta imediatamente e depois a cada
 * `options.pollIntervalMs`. Retorna a função para parar o polling (usada
 * em testes/shutdown gracioso, se necessário no futuro) — hoje o processo
 * do Edge Agent roda indefinidamente e nunca chama isso.
 */
export function startPolling(
  options: PollerOptions,
  deps: PollerDeps = defaultDeps,
): () => void {
  let running = false;
  const run = async () => {
    if (running) return;
    running = true;
    try {
      await pollOnce(options.apiBaseUrl, options.deviceKey, deps);
    } finally {
      running = false;
    }
  };
  void run();
  const timer = setInterval(() => void run(), options.pollIntervalMs);
  return () => clearInterval(timer);
}
