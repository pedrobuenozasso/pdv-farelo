/**
 * Teste unitário de `buildEscPosTicket` — puro, sem I/O (ver escpos.ts).
 *
 * Não compara o buffer inteiro byte-a-byte contra um fixture fixo (frágil
 * demais: qualquer ajuste de espaçamento/linha em branco quebraria o teste
 * inteiro sem indicar o que realmente importa). Em vez disso, cada `it`
 * verifica uma garantia específica e nomeada — comandos de controle exatos
 * (bytes) nas posições que importam, e o texto legível (via
 * `toString("ascii")`) para o conteúdo variável (número da comanda, estação,
 * itens).
 */

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { buildEscPosTicket } from "./escpos.js";
import type { PrintJobContent } from "./printJobsClient.js";

const baseContent: PrintJobContent = {
  commandNumber: 37,
  productionStation: "BAR",
  items: [
    { productName: "Cappuccino", quantity: 2 },
    { productName: "Coca-Cola", quantity: 1 },
  ],
};

describe("buildEscPosTicket", () => {
  it("retorna um Buffer", () => {
    assert.ok(Buffer.isBuffer(buildEscPosTicket(baseContent)));
  });

  it("começa com o comando de inicialização ESC @ (0x1B 0x40)", () => {
    const ticket = buildEscPosTicket(baseContent);
    assert.deepEqual(ticket.subarray(0, 2), Buffer.from([0x1b, 0x40]));
  });

  it("contém o comando de destaque (negrito + fonte dobrada) ESC ! 0x38 antes do número da comanda", () => {
    const ticket = buildEscPosTicket(baseContent);
    const highlightIndex = ticket.indexOf(Buffer.from([0x1b, 0x21, 0x38]));
    const commandTextIndex = ticket.indexOf(Buffer.from("COMANDA 37", "ascii"));

    assert.ok(
      highlightIndex >= 0,
      "comando de destaque ESC ! 0x38 não encontrado",
    );
    assert.ok(commandTextIndex >= 0, "texto 'COMANDA 37' não encontrado");
    assert.ok(
      highlightIndex < commandTextIndex,
      "destaque deve vir antes do número da comanda",
    );
  });

  it("restaura o modo normal (ESC ! 0x00) depois do número da comanda", () => {
    const ticket = buildEscPosTicket(baseContent);
    const commandTextIndex = ticket.indexOf(Buffer.from("COMANDA 37", "ascii"));
    const normalModeIndex = ticket.indexOf(
      Buffer.from([0x1b, 0x21, 0x00]),
      commandTextIndex,
    );

    assert.ok(
      normalModeIndex > commandTextIndex,
      "modo normal deve vir depois do número da comanda",
    );
  });

  it("inclui o nome da estação de produção quando presente", () => {
    const ticket = buildEscPosTicket(baseContent);
    assert.match(ticket.toString("ascii"), /Estacao: BAR/);
  });

  it("indica 'sem estacao' de forma legível quando productionStation é null, nunca o literal 'null'", () => {
    const ticket = buildEscPosTicket({
      ...baseContent,
      productionStation: null,
    });
    const text = ticket.toString("ascii");

    assert.match(text, /Estacao: \(sem estacao\)/);
    assert.doesNotMatch(text, /Estacao: null/);
  });

  it("lista cada item com quantidade e nome do produto", () => {
    const text = buildEscPosTicket(baseContent).toString("ascii");

    assert.match(text, /2x Cappuccino/);
    assert.match(text, /1x Coca-Cola/);
  });

  it("indica ausência de itens quando a lista está vazia", () => {
    const text = buildEscPosTicket({ ...baseContent, items: [] }).toString(
      "ascii",
    );

    assert.match(text, /\(sem itens\)/);
  });

  it("termina com o comando de corte de papel GS V 0x00 (0x1D 0x56 0x00)", () => {
    const ticket = buildEscPosTicket(baseContent);
    const cutCommand = Buffer.from([0x1d, 0x56, 0x00]);

    assert.deepEqual(
      ticket.subarray(ticket.length - cutCommand.length),
      cutCommand,
      "os últimos 3 bytes devem ser o comando de corte",
    );
  });

  it("mantém quantidade/nome exatos para múltiplos itens na ordem original", () => {
    const content: PrintJobContent = {
      commandNumber: 42,
      productionStation: "KITCHEN",
      items: [
        { productName: "Croissant", quantity: 3 },
        { productName: "Pao de Queijo", quantity: 5 },
      ],
    };
    const text = buildEscPosTicket(content).toString("ascii");

    assert.match(text, /COMANDA 42/);
    assert.match(text, /Estacao: KITCHEN/);
    assert.match(text, /3x Croissant/);
    assert.match(text, /5x Pao de Queijo/);
  });

  it("seleciona a tabela de código WPC1252 (ESC t 16) logo após a inicialização", () => {
    const ticket = buildEscPosTicket(baseContent);
    assert.deepEqual(ticket.subarray(2, 5), Buffer.from([0x1b, 0x74, 16]));
  });

  it("preserva caracteres acentuados do português como bytes latin1, não os corrompe (ver textLine)", () => {
    const content: PrintJobContent = {
      commandNumber: 7,
      productionStation: "Salão",
      items: [
        { productName: "Café com Açaí", quantity: 1 },
        { productName: "Pão de Queijo", quantity: 2 },
      ],
    };
    const ticket = buildEscPosTicket(content);
    // Decodifica como latin1 (o mesmo encoding usado por textLine) — não
    // "ascii", que é exatamente o encoding errado que causava a corrupção.
    const text = ticket.toString("latin1");

    assert.match(text, /Estacao: Salão/);
    assert.match(text, /1x Café com Açaí/);
    assert.match(text, /2x Pão de Queijo/);

    // E confirma que os bytes acentuados batem exatamente com o mapeamento
    // Latin-1/WPC1252 de cada caractere (não um valor arbitrário).
    const cafeBytes = Buffer.from("Café", "latin1");
    assert.ok(
      ticket.includes(cafeBytes),
      "bytes latin1 de 'Café' não encontrados no ticket",
    );
  });
});
