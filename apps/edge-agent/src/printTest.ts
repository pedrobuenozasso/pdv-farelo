/** Ensaio local explícito: não consulta API e não altera PrintJob. */
import { resolvePrinterTarget } from "./config.js";
import { buildEscPosImageTicket } from "./ticketImage.js";
import { printOverTcp } from "./printerTransport.js";
import { printOverWindowsSpooler } from "./windowsSpooler.js";

async function main(): Promise<void> {
  const target = resolvePrinterTarget(null);
  if (!target)
    throw new Error(
      "Configure FARELO_PRINTER_DEFAULT_WINDOWS_NAME ou DEFAULT_HOST/PORT.",
    );
  const ticket = buildEscPosImageTicket({
    commandNumber: 0,
    productionStation: "TESTE - NAO E PEDIDO",
    items: [{ productName: "TESTE DE IMPRESSAO - NAO PREPARAR", quantity: 1 }],
    customerName: null,
    customerPhone: null,
    orderCreatedAt: new Date().toISOString(),
  });
  if (target.transport === "windows-spooler") {
    await printOverWindowsSpooler(target.printerName, ticket);
  } else {
    await printOverTcp(target.host, target.port, ticket);
  }
  console.log(
    "Ticket de teste entregue ao transporte. Confira o papel na impressora.",
  );
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : error);
  process.exitCode = 1;
});
