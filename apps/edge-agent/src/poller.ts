/**
 * Polling periódico de `GET /api/v1/print-jobs` (FARELO-076) + report de
 * desfecho (FARELO-077). Não imprime nada de verdade ainda (FARELO-078) e
 * não mantém fila local (responsabilidade futura mencionada em
 * `docs/PROMPT_MESTRE.md` seção 11, ainda não implementada).
 *
 * Requisito central deste ticket: uma falha de rede/API indisponível nunca
 * pode derrubar o processo — por isso todo erro de qualquer chamada de rede
 * (polling ou report) é capturado e logado, e o próximo ciclo tenta de novo
 * normalmente, sem lógica de retry sofisticada.
 *
 * ────────────────────────────────────────────────────────────────────────
 * DECISÃO IMPORTANTE (FARELO-077) — leia antes de mexer aqui:
 *
 * Hoje o Edge Agent NÃO imprime nada de verdade — a impressão física via
 * ESC/POS é o escopo do FARELO-078, ainda não implementado. O único
 * "processamento" que existe agora é buscar o job e logá-lo (FARELO-076).
 *
 * Ainda assim, este ticket exige chamar `POST .../printed` ou `.../failed`
 * para cada job. A decisão tomada aqui é: **conseguir buscar e logar o job
 * é tratado como um substituto temporário e explicitamente documentado de
 * "consegui processar o job"** — por isso `reportPrinted` abaixo chama
 * sempre `/printed`, nunca `/failed`, para todo job que chega até aqui.
 *
 * Isso NÃO é o comportamento final do produto. É um estado intermediário
 * deliberado, só até o FARELO-078 existir: quando a impressão ESC/POS real
 * for implementada, o "processamento" deste loop passa a ser a tentativa de
 * impressão de verdade — que aí sim pode falhar por um motivo real
 * (impressora offline, sem papel, erro de driver, etc) e justificar chamar
 * `.../failed`. `reportPrintJobFailed` já existe em `printJobsClient.ts`
 * hoje, pronto para ser usado por aquele ticket — só não é chamado por
 * nenhum caminho deste arquivo ainda, porque não há uma falha real de
 * impressão para reportar.
 * ────────────────────────────────────────────────────────────────────────
 */

import {
  fetchPendingPrintJobs,
  formatPrintJobLog,
  reportPrintJobPrinted,
  type PrintJob,
} from "./printJobsClient.js";

export type PollerOptions = {
  apiBaseUrl: string;
  pollIntervalMs: number;
};

/**
 * Reporta `job` como `PRINTED` (ver decisão documentada no topo do arquivo).
 * Resiliente do mesmo jeito que o polling: falha ao chamar o endpoint de
 * report (rede indisponível, API fora do ar, etc) é logada e o loop segue
 * — sem lógica de retry (mesma fila local ainda inexistente, mesmo
 * raciocínio do README). Isolado em try/catch próprio para que a falha de
 * reportar um job não impeça o log/report dos jobs seguintes no mesmo ciclo.
 */
async function reportPrinted(apiBaseUrl: string, job: PrintJob): Promise<void> {
  try {
    await reportPrintJobPrinted(apiBaseUrl, job.id);
    console.log(`  → PrintJob ${job.id} reportado como PRINTED.`);
  } catch (error) {
    console.error(
      `  → Falha ao reportar PrintJob ${job.id} como PRINTED ` +
        `(tentando de novo no próximo ciclo):`,
      error instanceof Error ? error.message : error,
    );
  }
}

async function pollOnce(apiBaseUrl: string): Promise<void> {
  try {
    const jobs = await fetchPendingPrintJobs(apiBaseUrl);

    if (jobs.length === 0) {
      console.log("Nenhum PrintJob pendente.");
      return;
    }

    console.log(`${jobs.length} PrintJob(s) pendente(s):`);
    for (const job of jobs) {
      console.log(`  ${formatPrintJobLog(job)}`);
      await reportPrinted(apiBaseUrl, job);
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
export function startPolling(options: PollerOptions): () => void {
  void pollOnce(options.apiBaseUrl);
  const timer = setInterval(
    () => void pollOnce(options.apiBaseUrl),
    options.pollIntervalMs,
  );
  return () => clearInterval(timer);
}
