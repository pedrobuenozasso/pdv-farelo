/**
 * Polling periódico de `GET /api/v1/print-jobs` (FARELO-076). Só busca e
 * *loga* os `PrintJob`s pendentes — não imprime nada ainda (FARELO-078) e
 * não mantém fila local (responsabilidade futura mencionada em
 * `docs/PROMPT_MESTRE.md` seção 11, ainda não implementada).
 *
 * Requisito central deste ticket: uma falha de rede/API indisponível nunca
 * pode derrubar o processo — por isso todo erro do ciclo de polling é
 * capturado e logado, e o próximo ciclo tenta de novo normalmente.
 */

import { fetchPendingPrintJobs, formatPrintJobLog } from "./printJobsClient.js";

export type PollerOptions = {
  apiBaseUrl: string;
  pollIntervalMs: number;
};

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
