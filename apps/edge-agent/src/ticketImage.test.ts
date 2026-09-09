/**
 * Teste de `buildRasterImageCommand` — a única parte de `ticketImage.ts`
 * testável byte-a-byte (a renderização em si, via `@napi-rs/canvas`, é
 * visual — ver a própria javadoc do arquivo). Bitmaps sintéticos pequenos
 * (8x2, 16x1), sem passar por canvas nenhum, para isolar só a lógica de
 * empacotamento de bits/binarização por luminância.
 */

import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { buildRasterImageCommand } from "./ticketImage.js";

// Um pixel RGBA branco (255,255,255,255) ou preto (0,0,0,255) — luminância
// exatamente nos dois extremos, sem ambiguidade de threshold.
function pixel(
  r: number,
  g: number,
  b: number,
): [number, number, number, number] {
  return [r, g, b, 255];
}

function makePixels(
  rows: [number, number, number, number][][],
): Uint8ClampedArray {
  const flat: number[] = [];
  for (const row of rows) {
    for (const p of row) {
      flat.push(...p);
    }
  }
  return new Uint8ClampedArray(flat);
}

const WHITE = pixel(255, 255, 255);
const BLACK = pixel(0, 0, 0);

describe("buildRasterImageCommand", () => {
  it("monta o cabecalho GS v 0 com largura/altura corretas (8x2)", () => {
    const pixels = makePixels([
      [WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE],
      [WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE],
    ]);

    const command = buildRasterImageCommand(pixels, 8, 2);

    // GS v 0 m xL xH yL yH — widthBytes=1 (8/8), heightDots=2
    assert.deepEqual(
      command.subarray(0, 8),
      Buffer.from([0x1d, 0x76, 0x30, 0x00, 0x01, 0x00, 0x02, 0x00]),
    );
  });

  it("todo pixel branco vira todos os bits 0 (nada impresso)", () => {
    const pixels = makePixels([
      [WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE],
    ]);

    const command = buildRasterImageCommand(pixels, 8, 1);
    const imageBytes = command.subarray(8);

    assert.deepEqual(imageBytes, Buffer.from([0x00]));
  });

  it("todo pixel preto vira todos os bits 1 (linha inteira impressa)", () => {
    const pixels = makePixels([
      [BLACK, BLACK, BLACK, BLACK, BLACK, BLACK, BLACK, BLACK],
    ]);

    const command = buildRasterImageCommand(pixels, 8, 1);
    const imageBytes = command.subarray(8);

    assert.deepEqual(imageBytes, Buffer.from([0xff]));
  });

  it("empacota bits MSB-primeiro na ordem correta dos pixels", () => {
    // Só o primeiro pixel (bit mais significativo) é preto -> 0b10000000
    const pixels = makePixels([
      [BLACK, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE],
    ]);

    const command = buildRasterImageCommand(pixels, 8, 1);
    assert.deepEqual(command.subarray(8), Buffer.from([0b10000000]));
  });

  it("só o último pixel (bit menos significativo) é preto -> 0b00000001", () => {
    const pixels = makePixels([
      [WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, BLACK],
    ]);

    const command = buildRasterImageCommand(pixels, 8, 1);
    assert.deepEqual(command.subarray(8), Buffer.from([0b00000001]));
  });

  it("binariza por luminância: cinza escuro (< 128) vira preto, cinza claro (>= 128) vira branco", () => {
    const darkGray = pixel(50, 50, 50); // luminância 50 -> preto
    const lightGray = pixel(200, 200, 200); // luminância 200 -> branco
    const pixels = makePixels([
      [darkGray, lightGray, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE],
    ]);

    const command = buildRasterImageCommand(pixels, 8, 1);
    // bit 7 (darkGray) = 1, bit 6 (lightGray) = 0 -> 0b10000000
    assert.deepEqual(command.subarray(8), Buffer.from([0b10000000]));
  });

  it("processa múltiplas linhas independentemente, na ordem correta", () => {
    const pixels = makePixels([
      [BLACK, BLACK, BLACK, BLACK, BLACK, BLACK, BLACK, BLACK],
      [WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE, WHITE],
    ]);

    const command = buildRasterImageCommand(pixels, 8, 2);
    assert.deepEqual(command.subarray(8), Buffer.from([0xff, 0x00]));
  });

  it("largura de 16 dots vira 2 bytes por linha", () => {
    const pixels = makePixels([
      [
        BLACK,
        BLACK,
        BLACK,
        BLACK,
        BLACK,
        BLACK,
        BLACK,
        BLACK,
        WHITE,
        WHITE,
        WHITE,
        WHITE,
        WHITE,
        WHITE,
        WHITE,
        WHITE,
      ],
    ]);

    const command = buildRasterImageCommand(pixels, 16, 1);
    assert.deepEqual(
      command.subarray(0, 8),
      Buffer.from([0x1d, 0x76, 0x30, 0x00, 0x02, 0x00, 0x01, 0x00]),
    );
    assert.deepEqual(command.subarray(8), Buffer.from([0xff, 0x00]));
  });

  it("lança um erro quando widthDots não é múltiplo de 8", () => {
    const pixels = makePixels([[WHITE, WHITE, WHITE]]);
    assert.throws(() => buildRasterImageCommand(pixels, 3, 1), /multiplo de 8/);
  });
});
