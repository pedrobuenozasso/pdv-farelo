/**
 * Teste de `pollOnce` (FARELO-078) — orquestração do ciclo de polling:
 * buscar jobs, tentar imprimir, reportar o desfecho certo. Usa
 * `PollerDeps` (injeção de dependências, ver `poller.ts`) com
 * implementações falsas simples (funções que só registram chamadas) em vez
 * de uma lib de mock — mesmo espírito de não adicionar dependência sem
 * necessidade já seguido no resto do projeto (ADR-002). Não sobe rede nem
 * socket de verdade aqui: isso já é coberto por `printerTransport.test.ts`
 * (transporte TCP) e `printJobsClient.test.ts` (cliente HTTP) — este
 * arquivo cobre só a decisão de qual endpoint chamar (`printed`/`failed`)
 * conforme o resultado da tentativa de impressão.
 */

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { pollOnce, type PollerDeps } from "./poller.js";
import type { PrintJob } from "./printJobsClient.js";
import type { DeliveryJournal, DeliveryState } from "./deliveryState.js";
import { FileDeliveryJournal } from "./deliveryState.js";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

const deviceKey = "test-device-key-32-bytes-long-value";

function makeJournal(): DeliveryJournal {
  const states = new Map<string, DeliveryState>();
  return {
    get: async (id) => states.get(id) ?? null,
    prepare: async (id) => {
      if (states.has(id)) throw new Error("job já preparado");
      states.set(id, "PREPARED");
    },
    markSpoolAccepted: async (id) => {
      if (states.get(id) !== "PREPARED") throw new Error("job não preparado");
      states.set(id, "SPOOL_ACCEPTED");
    },
  };
}

function makeJob(overrides: Partial<PrintJob> = {}): PrintJob {
  return {
    id: "job-1",
    type: "KITCHEN_TICKET",
    orderId: "order-1",
    commandNumber: null,
    content: {
      commandNumber: 37,
      productionStation: "BAR",
      items: [{ productName: "Cappuccino", quantity: 2 }],
      customerName: "Cliente Teste",
      customerPhone: "11999999999",
      orderCreatedAt: "2026-01-01T12:00:00Z",
    },
    commandCheckContent: null,
    status: "PENDING",
    createdAt: "2026-01-01T12:00:00Z",
    ...overrides,
  };
}

// FARELO-210/211: fixture de um job COMMAND_CHECK, mesmo padrão de makeJob
// acima — usado pelo teste que confirma o branch por job.type em printJob.
function makeCommandCheckJob(overrides: Partial<PrintJob> = {}): PrintJob {
  return {
    id: "job-conference-1",
    type: "COMMAND_CHECK",
    orderId: null,
    commandNumber: 37,
    content: null,
    commandCheckContent: {
      commandNumber: 37,
      items: [
        { productName: "Cappuccino", quantity: 2, unitPrice: 8, lineTotal: 16 },
      ],
      total: 16,
    },
    status: "PENDING",
    createdAt: "2026-01-01T12:00:00Z",
    ...overrides,
  };
}

/**
 * Stub genérico para uma dependência que não deve ser chamada num
 * determinado cenário de teste. Assinatura `(...args: unknown[]) => never`
 * é estruturalmente compatível com qualquer método de `PollerDeps` (síncrono
 * ou assíncrono, qualquer aridade) — `never` é atribuível a qualquer tipo de
 * retorno esperado, então não precisa de nenhum cast por chamada.
 */
function neverCalled(label: string): (...args: unknown[]) => never {
  return () => {
    throw new Error(`${label} não deveria ter sido chamado neste cenário`);
  };
}

describe("pollOnce", () => {
  it("duas instâncias concorrentes não enviam o mesmo job duas vezes", async () => {
    const dir = await mkdtemp(path.join(tmpdir(), "farelo-poller-race-"));
    try {
      let spools = 0;
      const deps: PollerDeps = {
        journal: new FileDeliveryJournal(dir),
        fetchPendingPrintJobs: async () => [makeJob()],
        resolvePrinterTarget: () => ({
          transport: "tcp",
          host: "127.0.0.1",
          port: 9100,
        }),
        buildEscPosTicket: () => Buffer.from("ticket"),
        buildCommandCheckEscPosTicket: neverCalled(
          "buildCommandCheckEscPosTicket",
        ),
        printOverTcp: async () => {
          spools++;
        },
        printOverWindowsSpooler: neverCalled("printOverWindowsSpooler"),
        reportPrintJobPrinted: async () => {},
        reportPrintJobFailed: neverCalled("reportPrintJobFailed"),
      };
      await Promise.all([
        pollOnce("http://localhost:8080", deviceKey, deps),
        pollOnce("http://localhost:8080", deviceKey, deps),
      ]);
      assert.equal(spools, 1);
      assert.equal(
        await new FileDeliveryJournal(dir).get("job-1"),
        "SPOOL_ACCEPTED",
      );
    } finally {
      await rm(dir, { recursive: true, force: true });
    }
  });

  it("repete somente o report PRINTED após falha da API, sem imprimir outra vez", async () => {
    const journal = makeJournal();
    let spools = 0;
    let reports = 0;
    const deps: PollerDeps = {
      journal,
      fetchPendingPrintJobs: async () => [makeJob()],
      resolvePrinterTarget: () => ({
        transport: "windows-spooler",
        printerName: "EPSON",
      }),
      buildEscPosTicket: () => Buffer.from("ticket"),
      buildCommandCheckEscPosTicket: neverCalled(
        "buildCommandCheckEscPosTicket",
      ),
      printOverTcp: neverCalled("printOverTcp"),
      printOverWindowsSpooler: async () => {
        spools++;
      },
      reportPrintJobPrinted: async () => {
        reports++;
        if (reports === 1) throw new Error("API offline");
      },
      reportPrintJobFailed: neverCalled("reportPrintJobFailed"),
    };
    await pollOnce("http://localhost:8080", deviceKey, deps);
    assert.equal(await journal.get("job-1"), "SPOOL_ACCEPTED");
    await pollOnce("http://localhost:8080", deviceKey, deps);
    assert.equal(spools, 1);
    assert.equal(reports, 2);
  });

  it("entrega o ticket à fila Windows USB antes de reportar PRINTED", async () => {
    const events: string[] = [];
    const deps: PollerDeps = {
      journal: makeJournal(),
      fetchPendingPrintJobs: async () => [makeJob()],
      resolvePrinterTarget: () => ({
        transport: "windows-spooler",
        printerName: "EPSON TM-T20X",
      }),
      buildEscPosTicket: () => Buffer.from([0x1b, 0x40, 0x00, 0xff]),
      buildCommandCheckEscPosTicket: neverCalled(
        "buildCommandCheckEscPosTicket",
      ),
      printOverTcp: neverCalled("printOverTcp"),
      printOverWindowsSpooler: async (name, bytes) => {
        assert.equal(name, "EPSON TM-T20X");
        assert.deepEqual(bytes, Buffer.from([0x1b, 0x40, 0x00, 0xff]));
        events.push("spooled");
      },
      reportPrintJobPrinted: async () => {
        events.push("printed");
      },
      reportPrintJobFailed: neverCalled("reportPrintJobFailed"),
    };
    await pollOnce("http://localhost:8080", deviceKey, deps);
    assert.deepEqual(events, ["spooled", "printed"]);
  });

  it("imprime via TCP e reporta PRINTED quando a impressão é bem-sucedida", async () => {
    const job = makeJob();
    const printedCalls: string[] = [];
    const passedKeys: string[] = [];
    const printOverTcpCalls: { host: string; port: number; ticket: Buffer }[] =
      [];

    const deps: PollerDeps = {
      journal: makeJournal(),
      fetchPendingPrintJobs: async (_apiBaseUrl, key) => {
        passedKeys.push(key);
        return [job];
      },
      resolvePrinterTarget: () => ({
        transport: "tcp",
        host: "192.168.0.10",
        port: 9100,
      }),
      buildEscPosTicket: () => Buffer.from("ticket-bytes"),
      buildCommandCheckEscPosTicket: neverCalled(
        "buildCommandCheckEscPosTicket",
      ),
      printOverWindowsSpooler: neverCalled("printOverWindowsSpooler"),
      printOverTcp: async (host, port, ticket) => {
        printOverTcpCalls.push({ host, port, ticket });
      },
      reportPrintJobPrinted: async (_apiBaseUrl, jobId, key) => {
        printedCalls.push(jobId);
        passedKeys.push(key);
      },
      reportPrintJobFailed: neverCalled("reportPrintJobFailed"),
    };

    await pollOnce("http://localhost:8080", deviceKey, deps);

    assert.deepEqual(printedCalls, ["job-1"]);
    assert.deepEqual(passedKeys, [deviceKey, deviceKey]);
    assert.equal(printOverTcpCalls.length, 1);
    assert.equal(printOverTcpCalls[0]?.host, "192.168.0.10");
    assert.equal(printOverTcpCalls[0]?.port, 9100);
    assert.deepEqual(printOverTcpCalls[0]?.ticket, Buffer.from("ticket-bytes"));
  });

  it("reporta FAILED quando nenhum endereço de impressora está configurado para a estação", async () => {
    const job = makeJob();
    const failedCalls: string[] = [];
    const passedKeys: string[] = [];

    const deps: PollerDeps = {
      journal: makeJournal(),
      fetchPendingPrintJobs: async (_apiBaseUrl, key) => {
        passedKeys.push(key);
        return [job];
      },
      resolvePrinterTarget: () => null,
      buildEscPosTicket: neverCalled("buildEscPosTicket"),
      buildCommandCheckEscPosTicket: neverCalled(
        "buildCommandCheckEscPosTicket",
      ),
      printOverWindowsSpooler: neverCalled("printOverWindowsSpooler"),
      printOverTcp: neverCalled("printOverTcp"),
      reportPrintJobPrinted: neverCalled("reportPrintJobPrinted"),
      reportPrintJobFailed: async (_apiBaseUrl, jobId, key) => {
        failedCalls.push(jobId);
        passedKeys.push(key);
      },
    };

    await pollOnce("http://localhost:8080", deviceKey, deps);

    assert.deepEqual(failedCalls, ["job-1"]);
    assert.deepEqual(passedKeys, [deviceKey, deviceKey]);
  });

  it("bloqueia reimpressão quando a tentativa de transporte falha de modo incerto", async () => {
    const job = makeJob();
    const journal = makeJournal();
    let attempts = 0;

    const deps: PollerDeps = {
      journal,
      fetchPendingPrintJobs: async () => [job],
      resolvePrinterTarget: () => ({
        transport: "tcp",
        host: "192.168.0.10",
        port: 9100,
      }),
      buildEscPosTicket: () => Buffer.from("ticket-bytes"),
      buildCommandCheckEscPosTicket: neverCalled(
        "buildCommandCheckEscPosTicket",
      ),
      printOverWindowsSpooler: neverCalled("printOverWindowsSpooler"),
      printOverTcp: async () => {
        attempts++;
        throw new Error("ECONNREFUSED (impressora offline, simulado)");
      },
      reportPrintJobPrinted: neverCalled("reportPrintJobPrinted"),
      reportPrintJobFailed: neverCalled("reportPrintJobFailed"),
    };

    await pollOnce("http://localhost:8080", deviceKey, deps);
    await pollOnce("http://localhost:8080", deviceKey, deps);
    assert.equal(attempts, 1);
    assert.equal(await journal.get("job-1"), "PREPARED");
  });

  it("não derruba o processo quando reportPrintJobFailed também falha (API indisponível)", async () => {
    const job = makeJob();

    const deps: PollerDeps = {
      journal: makeJournal(),
      fetchPendingPrintJobs: async () => [job],
      resolvePrinterTarget: () => null,
      buildEscPosTicket: neverCalled("buildEscPosTicket"),
      buildCommandCheckEscPosTicket: neverCalled(
        "buildCommandCheckEscPosTicket",
      ),
      printOverWindowsSpooler: neverCalled("printOverWindowsSpooler"),
      printOverTcp: neverCalled("printOverTcp"),
      reportPrintJobPrinted: neverCalled("reportPrintJobPrinted"),
      reportPrintJobFailed: async () => {
        throw new Error("API indisponível, simulado");
      },
    };

    // Não deve lançar/rejeitar — a falha de report é só logada.
    await pollOnce("http://localhost:8080", deviceKey, deps);
  });

  it("não derruba o processo quando fetchPendingPrintJobs falha (rede/API indisponível)", async () => {
    const deps: PollerDeps = {
      journal: makeJournal(),
      fetchPendingPrintJobs: async () => {
        throw new Error("rede indisponível, simulado");
      },
      resolvePrinterTarget: neverCalled("resolvePrinterAddress"),
      buildEscPosTicket: neverCalled("buildEscPosTicket"),
      buildCommandCheckEscPosTicket: neverCalled(
        "buildCommandCheckEscPosTicket",
      ),
      printOverWindowsSpooler: neverCalled("printOverWindowsSpooler"),
      printOverTcp: neverCalled("printOverTcp"),
      reportPrintJobPrinted: neverCalled("reportPrintJobPrinted"),
      reportPrintJobFailed: neverCalled("reportPrintJobFailed"),
    };

    await pollOnce("http://localhost:8080", deviceKey, deps);
  });

  it("processa múltiplos jobs pendentes de forma independente (um falha, outro é impresso)", async () => {
    const okJob = makeJob({
      id: "job-ok",
      content: {
        commandNumber: 1,
        productionStation: "BAR",
        items: [],
        customerName: null,
        customerPhone: null,
        orderCreatedAt: "2026-01-01T12:00:00Z",
      },
    });
    const failJob = makeJob({
      id: "job-fail",
      content: {
        commandNumber: 2,
        productionStation: "KITCHEN",
        items: [],
        customerName: null,
        customerPhone: null,
        orderCreatedAt: "2026-01-01T12:00:00Z",
      },
    });

    const printedCalls: string[] = [];
    const failedCalls: string[] = [];

    const deps: PollerDeps = {
      journal: makeJournal(),
      fetchPendingPrintJobs: async () => [okJob, failJob],
      resolvePrinterTarget: (station) =>
        station === "BAR"
          ? { transport: "tcp", host: "192.168.0.10", port: 9100 }
          : null,
      buildEscPosTicket: () => Buffer.from("ticket-bytes"),
      buildCommandCheckEscPosTicket: neverCalled(
        "buildCommandCheckEscPosTicket",
      ),
      printOverWindowsSpooler: neverCalled("printOverWindowsSpooler"),
      printOverTcp: async () => {},
      reportPrintJobPrinted: async (_apiBaseUrl, jobId) => {
        printedCalls.push(jobId);
      },
      reportPrintJobFailed: async (_apiBaseUrl, jobId) => {
        failedCalls.push(jobId);
      },
    };

    await pollOnce("http://localhost:8080", deviceKey, deps);

    assert.deepEqual(printedCalls, ["job-ok"]);
    assert.deepEqual(failedCalls, ["job-fail"]);
  });

  // FARELO-210/211: um job COMMAND_CHECK usa buildCommandCheckEscPosTicket
  // (não buildEscPosTicket) e resolve o endereço de impressora pela
  // "estação" fictícia "CONFERENCE" (ver poller.ts), não por
  // job.content.productionStation (que é null para este tipo de job).
  it("imprime uma conferência (COMMAND_CHECK) usando o formatador e a estação corretos", async () => {
    const job = makeCommandCheckJob();
    const printedCalls: string[] = [];
    const resolveCalls: (string | null)[] = [];
    const printOverTcpCalls: { host: string; port: number; ticket: Buffer }[] =
      [];

    const deps: PollerDeps = {
      journal: makeJournal(),
      fetchPendingPrintJobs: async () => [job],
      resolvePrinterTarget: (station) => {
        resolveCalls.push(station);
        return { transport: "tcp", host: "192.168.0.20", port: 9100 };
      },
      buildEscPosTicket: neverCalled("buildEscPosTicket"),
      buildCommandCheckEscPosTicket: () =>
        Buffer.from("conference-ticket-bytes"),
      printOverWindowsSpooler: neverCalled("printOverWindowsSpooler"),
      printOverTcp: async (host, port, ticket) => {
        printOverTcpCalls.push({ host, port, ticket });
      },
      reportPrintJobPrinted: async (_apiBaseUrl, jobId) => {
        printedCalls.push(jobId);
      },
      reportPrintJobFailed: neverCalled("reportPrintJobFailed"),
    };

    await pollOnce("http://localhost:8080", deviceKey, deps);

    assert.deepEqual(printedCalls, ["job-conference-1"]);
    assert.deepEqual(resolveCalls, ["CONFERENCE"]);
    assert.deepEqual(
      printOverTcpCalls[0]?.ticket,
      Buffer.from("conference-ticket-bytes"),
    );
  });
});
