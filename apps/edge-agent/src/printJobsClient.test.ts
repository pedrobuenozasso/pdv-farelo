/**
 * Teste unitário de `parsePrintJobs`/`formatPrintJobLog` — a parte
 * determinística deste ticket (parsing/formatação da resposta de
 * `GET /api/v1/print-jobs`), testável com um fixture do JSON do contrato,
 * sem I/O real. Também cobre `reportPrintJobPrinted`/`reportPrintJobFailed`
 * (FARELO-077) estubando `global.fetch` — mesmo nível de teste (sem subir
 * servidor real), só validando URL/método construídos e o tratamento de
 * status não-2xx.
 *
 * Escolha deliberada de não escrever um teste de integração contra uma API
 * rodando de verdade: para o tamanho deste esqueleto (nenhuma lógica de
 * negócio, só consultar/reportar + logar), a cobertura de valor está em
 * garantir que a resposta é interpretada corretamente e que entradas
 * malformadas falham de forma clara — não em orquestrar
 * Testcontainers/backend real a partir de `apps/edge-agent`. A prova de
 * ponta a ponta (cliente HTTP + parsing contra a API de verdade) é feita
 * manualmente (ver README.md / relato do ticket), como qualquer outro
 * esqueleto deste tamanho no repositório.
 *
 * Usa o test runner nativo do Node (`node:test`, via `tsx --test`) — nenhuma
 * lib de teste nova, no mesmo espírito de não adicionar dependências sem
 * necessidade (ADR-002).
 */

import { describe, it, beforeEach, afterEach } from "node:test";
import assert from "node:assert/strict";
import {
  parsePrintJobs,
  formatPrintJobLog,
  reportPrintJobPrinted,
  reportPrintJobFailed,
} from "./printJobsClient.js";

// Fixture: exatamente o JSON de exemplo do contrato do endpoint
// (docs/PROMPT_MESTRE.md / ticket FARELO-076; type/commandNumber/
// commandCheckContent adicionados no FARELO-210/211 — ver PrintJobResponse
// no backend).
const validResponseFixture = [
  {
    id: "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    type: "KITCHEN_TICKET",
    orderId: "a1b2c3d4-1234-4562-b3fc-2c963f66afa6",
    commandNumber: null,
    content: {
      commandNumber: 37,
      productionStation: "BAR",
      items: [{ productName: "Cappuccino", quantity: 2 }],
    },
    commandCheckContent: null,
    status: "PENDING",
    createdAt: "2026-01-01T12:00:00Z",
  },
];

// Fixture de um job COMMAND_CHECK (FARELO-210/211) — mesma comanda 37.
const commandCheckResponseFixture = [
  {
    id: "8f14e45f-ceea-4d38-b4f8-2d7e63b3c5a1",
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
    createdAt: "2026-01-01T12:05:00Z",
  },
];

describe("parsePrintJobs", () => {
  it("interpreta uma resposta válida no formato do contrato", () => {
    const jobs = parsePrintJobs(validResponseFixture);

    assert.equal(jobs.length, 1);
    assert.equal(jobs[0]?.id, "3fa85f64-5717-4562-b3fc-2c963f66afa6");
    assert.equal(jobs[0]?.type, "KITCHEN_TICKET");
    assert.equal(jobs[0]?.orderId, "a1b2c3d4-1234-4562-b3fc-2c963f66afa6");
    assert.equal(jobs[0]?.commandNumber, null);
    assert.equal(jobs[0]?.commandCheckContent, null);
    assert.equal(jobs[0]?.status, "PENDING");
    assert.equal(jobs[0]?.createdAt, "2026-01-01T12:00:00Z");
    assert.equal(jobs[0]?.content?.commandNumber, 37);
    assert.equal(jobs[0]?.content?.productionStation, "BAR");
    assert.deepEqual(jobs[0]?.content?.items, [
      { productName: "Cappuccino", quantity: 2 },
    ]);
  });

  it("interpreta uma lista vazia (nenhum PrintJob pendente)", () => {
    assert.deepEqual(parsePrintJobs([]), []);
  });

  it("aceita productionStation nula (itens sem estação atribuída, FARELO-074)", () => {
    const unassigned = [
      {
        ...validResponseFixture[0],
        content: {
          ...validResponseFixture[0]!.content,
          productionStation: null,
        },
      },
    ];

    const jobs = parsePrintJobs(unassigned);
    assert.equal(jobs[0]?.content?.productionStation, null);
  });

  it("lança um erro quando o corpo não é um array", () => {
    assert.throws(() => parsePrintJobs({ not: "an array" }), /não é um array/);
  });

  it("lança um erro quando um PrintJob não tem \"type\" válido", () => {
    assert.throws(() => parsePrintJobs([{ id: "only-id" }]), /"type"/);
  });

  it("lança um erro quando um KITCHEN_TICKET não tem os campos esperados", () => {
    assert.throws(
      () => parsePrintJobs([{ id: "only-id", type: "KITCHEN_TICKET", status: "PENDING", createdAt: "2026-01-01T12:00:00Z" }]),
      /orderId/,
    );
  });

  it("lança um erro quando um item de content.items é malformado", () => {
    const malformed = [
      {
        ...validResponseFixture[0],
        content: {
          ...validResponseFixture[0]!.content,
          items: [{ productName: "Cappuccino" }], // sem "quantity"
        },
      },
    ];

    assert.throws(() => parsePrintJobs(malformed), /quantity/);
  });

  it("interpreta um PrintJob COMMAND_CHECK (FARELO-210/211)", () => {
    const jobs = parsePrintJobs(commandCheckResponseFixture);

    assert.equal(jobs.length, 1);
    assert.equal(jobs[0]?.type, "COMMAND_CHECK");
    assert.equal(jobs[0]?.orderId, null);
    assert.equal(jobs[0]?.content, null);
    assert.equal(jobs[0]?.commandNumber, 37);
    assert.equal(jobs[0]?.commandCheckContent?.total, 16);
    assert.equal(jobs[0]?.commandCheckContent?.items[0]?.productName, "Cappuccino");
    assert.equal(jobs[0]?.commandCheckContent?.items[0]?.lineTotal, 16);
  });

  it("lança um erro quando um COMMAND_CHECK não tem commandCheckContent", () => {
    const malformed = [{ ...commandCheckResponseFixture[0], commandCheckContent: null }];
    assert.throws(() => parsePrintJobs(malformed), /commandCheckContent/);
  });
});

describe("formatPrintJobLog", () => {
  it("formata id, comanda, estação e itens numa linha legível", () => {
    const [job] = parsePrintJobs(validResponseFixture);
    const line = formatPrintJobLog(job!);

    assert.match(line, /3fa85f64-5717-4562-b3fc-2c963f66afa6/);
    assert.match(line, /comanda 37/);
    assert.match(line, /\[BAR\]/);
    assert.match(line, /2x Cappuccino/);
  });

  it("indica estação não atribuída de forma legível, nunca o literal 'null'", () => {
    const [job] = parsePrintJobs([
      {
        ...validResponseFixture[0],
        content: {
          ...validResponseFixture[0]!.content,
          productionStation: null,
        },
      },
    ]);

    const line = formatPrintJobLog(job!);
    assert.match(line, /sem estação atribuída/);
    assert.doesNotMatch(line, /\[null\]/);
  });

  it("indica quando um PrintJob não tem itens", () => {
    const [job] = parsePrintJobs([
      {
        ...validResponseFixture[0],
        content: { ...validResponseFixture[0]!.content, items: [] },
      },
    ]);

    assert.match(formatPrintJobLog(job!), /\(sem itens\)/);
  });

  it("formata um PrintJob COMMAND_CHECK como conferência com total (FARELO-210/211)", () => {
    const [job] = parsePrintJobs(commandCheckResponseFixture);
    const line = formatPrintJobLog(job!);

    assert.match(line, /conferência comanda 37/);
    assert.match(line, /1 item\(ns\)/);
    assert.match(line, /R\$ 16\.00/);
  });
});

describe("reportPrintJobPrinted / reportPrintJobFailed", () => {
  const originalFetch = global.fetch;
  let calls: { url: string; init: RequestInit | undefined }[] = [];

  beforeEach(() => {
    calls = [];
  });

  afterEach(() => {
    global.fetch = originalFetch;
  });

  function stubFetch(status: number): void {
    global.fetch = (async (url: string | URL | Request, init?: RequestInit) => {
      calls.push({ url: String(url), init });
      return new Response(null, { status });
    }) as typeof fetch;
  }

  it("reportPrintJobPrinted faz POST em /print-jobs/{id}/printed sem corpo", async () => {
    stubFetch(200);

    await reportPrintJobPrinted("http://localhost:8080", "job-1");

    assert.equal(calls.length, 1);
    assert.equal(
      calls[0]?.url,
      "http://localhost:8080/api/v1/print-jobs/job-1/printed",
    );
    assert.equal(calls[0]?.init?.method, "POST");
    assert.equal(calls[0]?.init?.body, undefined);
  });

  it("reportPrintJobFailed faz POST em /print-jobs/{id}/failed sem corpo", async () => {
    stubFetch(200);

    await reportPrintJobFailed("http://localhost:8080", "job-2");

    assert.equal(calls.length, 1);
    assert.equal(
      calls[0]?.url,
      "http://localhost:8080/api/v1/print-jobs/job-2/failed",
    );
    assert.equal(calls[0]?.init?.method, "POST");
  });

  it("lança um erro quando a resposta não é 2xx", async () => {
    stubFetch(500);

    await assert.rejects(
      () => reportPrintJobPrinted("http://localhost:8080", "job-3"),
      /falhou com status 500/,
    );
  });
});
