/**
 * Teste unitário de `resolvePrinterAddress` (FARELO-078) — ver a convenção
 * de nome de variável documentada no topo de `config.ts`. `loadConfig` em
 * si (FARELO-075/076, apiBaseUrl/pollIntervalMs) já não tinha teste
 * dedicado antes deste ticket; permanece fora de escopo aqui (nenhuma
 * mudança de comportamento nele) — este arquivo cobre só a peça nova.
 */

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { resolvePrinterAddress } from "./config.js";

describe("resolvePrinterAddress", () => {
  it("resolve o endereço específico da estação quando configurado", () => {
    const env = {
      FARELO_PRINTER_BAR_HOST: "192.168.0.10",
      FARELO_PRINTER_BAR_PORT: "9100",
    };

    assert.deepEqual(resolvePrinterAddress("BAR", env), {
      host: "192.168.0.10",
      port: 9100,
    });
  });

  it("normaliza o nome da estação (minúsculas/espaços/acentos) para o nome da variável", () => {
    const env = {
      FARELO_PRINTER_SALAO_EXTERNO_HOST: "192.168.0.20",
      FARELO_PRINTER_SALAO_EXTERNO_PORT: "9100",
    };

    assert.deepEqual(resolvePrinterAddress("Salão Externo", env), {
      host: "192.168.0.20",
      port: 9100,
    });
  });

  it("cai para o fallback default quando não há variável específica da estação", () => {
    const env = {
      FARELO_PRINTER_DEFAULT_HOST: "192.168.0.99",
      FARELO_PRINTER_DEFAULT_PORT: "9100",
    };

    assert.deepEqual(resolvePrinterAddress("KITCHEN", env), {
      host: "192.168.0.99",
      port: 9100,
    });
  });

  it("usa o fallback default para productionStation null (itens sem estação)", () => {
    const env = {
      FARELO_PRINTER_DEFAULT_HOST: "192.168.0.99",
      FARELO_PRINTER_DEFAULT_PORT: "9100",
    };

    assert.deepEqual(resolvePrinterAddress(null, env), {
      host: "192.168.0.99",
      port: 9100,
    });
  });

  it("prefere o endereço específico da estação sobre o default quando ambos existem", () => {
    const env = {
      FARELO_PRINTER_BAR_HOST: "192.168.0.10",
      FARELO_PRINTER_BAR_PORT: "9100",
      FARELO_PRINTER_DEFAULT_HOST: "192.168.0.99",
      FARELO_PRINTER_DEFAULT_PORT: "9100",
    };

    assert.deepEqual(resolvePrinterAddress("BAR", env), {
      host: "192.168.0.10",
      port: 9100,
    });
  });

  it("devolve null quando nada está configurado (nem estação, nem default)", () => {
    assert.equal(resolvePrinterAddress("BAR", {}), null);
    assert.equal(resolvePrinterAddress(null, {}), null);
  });

  it("trata porta ausente/inválida como não configurado e cai para o default", () => {
    const env = {
      FARELO_PRINTER_BAR_HOST: "192.168.0.10",
      // sem FARELO_PRINTER_BAR_PORT
      FARELO_PRINTER_DEFAULT_HOST: "192.168.0.99",
      FARELO_PRINTER_DEFAULT_PORT: "9100",
    };

    assert.deepEqual(resolvePrinterAddress("BAR", env), {
      host: "192.168.0.99",
      port: 9100,
    });
  });

  it("trata porta não numérica como não configurado", () => {
    const env = {
      FARELO_PRINTER_BAR_HOST: "192.168.0.10",
      FARELO_PRINTER_BAR_PORT: "not-a-number",
    };

    assert.equal(resolvePrinterAddress("BAR", env), null);
  });
});
