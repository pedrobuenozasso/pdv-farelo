/**
 * Polling periódico de `GET /api/v1/print-jobs` (FARELO-076) + impressão
 * ESC/POS real via TCP (FARELO-078) + report de desfecho (FARELO-077).
 *
 * **Fluxo atual (FARELO-078)**: para cada `PrintJob` pendente encontrado,
 * o Edge Agent (a) resolve o endereço da impressora pela
 * `productionStation` do job (`resolvePrinterAddress`, `config.ts`), (b)
 * monta os bytes ESC/POS do ticket a partir de `job.content`
 * (`buildEscPosTicket`, `escpos.ts`), (c) tenta enviar via TCP
 * (`printOverTcp`, `printerTransport.ts`). Sucesso em todas as três etapas
 * → `reportPrintJobPrinted`. Qualquer falha — sem endereço configurado
 * (nem específico da estação, nem fallback default), impressora
 * offline/recusando conexão, timeout de rede — é capturada, logada com o
 * motivo, e reportada como `reportPrintJobFailed`. **Não** existe mais
 * nenhum "logar = processado com sucesso": isso era um stand-in temporário
 * do FARELO-077, substituído por este ticket pela tentativa de impressão
 * real (ver histórico em `docs/domain-model.md`/README.md se precisar do
 * raciocínio anterior).
 *
 * Requisito central deste arquivo, inalterado desde o FARELO-076: uma
 * falha de rede/impressão/API nunca pode derrubar o processo — todo erro é
 * capturado e logado, e o próximo ciclo (ou próximo job, dentro do mesmo
 * ciclo) segue normalmente, sem lógica de retry sofisticada (fila local
 * mencionada no prompt mestre seção 11 continua responsabilidade futura,
 * ainda não implementada).
 *
 * **Injeção de dependências (`PollerDeps`)**: `pollOnce`/`startPolling`
 * recebem as funções de rede/impressão como parâmetro, com as
 * implementações reais como default. Existe só para permitir testar a
 * orquestração deste arquivo (sucesso → printed, falha → failed) sem subir
 * rede/socket de verdade e sem precisar de uma lib de mock — o mesmo
 * espírito de não adicionar dependência sem necessidade já seguido no
 * resto do projeto (ver `poller.test.ts`).
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
};

/**
 * Tenta imprimir `job` (resolver endereço → montar ticket → enviar via
 * TCP) e reporta o desfecho para a API. Isolado em try/catch próprio para
 * que a falha de um job não impeça o processamento dos jobs seguintes no
 * mesmo ciclo — mesmo raciocínio já documentado desde o FARELO-077.
 *
 * A falha ao *reportar* o desfecho (rede/API indisponível na chamada de
 * `printed`/`failed` em si) é, por sua vez, capturada e logada
 * separadamente: o próximo ciclo tenta de novo (o job continua `PENDING`
 * no backend até algum report suceder), sem lógica de retry sofisticada.
 */
async function printJob(
  apiBaseUrl: string,
  job: PrintJob,
  deps: PollerDeps,
  deviceKey: string,
): Promise<void> {
  try {
    // FARELO-210/211: qual estação resolve o endereço da impressora, e qual
    // formatador monta o ticket, depende de job.type — nunca inferido do
    // que está populado (content vs. commandCheckContent), mesmo princípio
    // documentado em printJobsClient.ts.
    const station =
      job.type === "COMMAND_CHECK"
        ? CONFERENCE_STATION_KEY
        : job.content!.productionStation;
    const target = deps.resolvePrinterTarget(station);
    if (!target) {
      throw new Error(
        `Nenhuma impressora configurada para a estação ` +
          `"${station ?? "sem estação"}" (nem fallback ` +
          `FARELO_PRINTER_DEFAULT_*).`,
      );
    }

    const ticket =
      job.type === "COMMAND_CHECK"
        ? deps.buildCommandCheckEscPosTicket(job.commandCheckContent!)
        : deps.buildEscPosTicket(job.content!);
    if (target.transport === "windows-spooler") {
      await deps.printOverWindowsSpooler(target.printerName, ticket);
    } else {
      await deps.printOverTcp(target.host, target.port, ticket);
    }

    await deps.reportPrintJobPrinted(apiBaseUrl, job.id, deviceKey);
    console.log(
      `  → PrintJob ${job.id} entregue a ${target.transport === "tcp" ? `${target.host}:${target.port}` : `fila ${target.printerName}`} ` +
        `e reportado como PRINTED.`,
    );
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    console.error(`  → Falha ao imprimir PrintJob ${job.id}: ${reason}`);

    try {
      await deps.reportPrintJobFailed(apiBaseUrl, job.id, deviceKey);
      console.log(`  → PrintJob ${job.id} reportado como FAILED.`);
    } catch (reportError) {
      console.error(
        `  → Falha ao reportar PrintJob ${job.id} como FAILED ` +
          `(tentando de novo no próximo ciclo):`,
        reportError instanceof Error ? reportError.message : reportError,
      );
    }
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
  void pollOnce(options.apiBaseUrl, options.deviceKey, deps);
  const timer = setInterval(
    () => void pollOnce(options.apiBaseUrl, options.deviceKey, deps),
    options.pollIntervalMs,
  );
  return () => clearInterval(timer);
}
