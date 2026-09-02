/**
 * Farelo Edge Agent — entrypoint.
 *
 * A partir do FARELO-076, o Edge Agent conecta com a API e faz polling
 * periódico de `GET /api/v1/print-jobs`, logando os `PrintJob`s pendentes
 * encontrados (id, comanda, estação, itens). Ainda não imprime nada de
 * verdade (FARELO-078) nem mantém fila local — só busca e loga.
 *
 * Lembrete de propósito (docs/PROMPT_MESTRE.md, seção 11): o Edge Agent é
 * apenas infraestrutura de dispositivos — nunca deve conter regra de
 * negócio de pedidos.
 */

import { loadConfig } from "./config.js";
import { startPolling } from "./poller.js";

function main(): void {
  console.log("Farelo Edge Agent iniciado");

  const config = loadConfig();
  console.log(
    `Consultando PrintJobs pendentes em ${config.apiBaseUrl}/api/v1/print-jobs ` +
      `a cada ${config.pollIntervalMs}ms`,
  );

  startPolling(config);
}

main();
