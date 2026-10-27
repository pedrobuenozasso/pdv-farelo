/** Recuperação manual de um PrintJob incerto; execute somente com o agente parado. */
import { FileDeliveryJournal, resolveStateDirectory } from "./deliveryState.js";

async function main(): Promise<void> {
  const [jobId, outcome, confirmation] = process.argv.slice(2);
  if (
    !jobId ||
    !["printed", "no-paper"].includes(outcome ?? "") ||
    confirmation !== "--confirm-inspected-paper-and-queue"
  ) {
    throw new Error(
      "Uso: npm run delivery:resolve -- <job-id> printed|no-paper --confirm-inspected-paper-and-queue",
    );
  }
  const journal = new FileDeliveryJournal(resolveStateDirectory());
  await journal.recover(jobId, outcome as "printed" | "no-paper");
  console.log(
    outcome === "printed"
      ? `PrintJob ${jobId}: confirmado no papel; o agente reenviará somente o report PRINTED.`
      : `PrintJob ${jobId}: papel ausente e fila limpa; o agente poderá tentar imprimir novamente.`,
  );
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
