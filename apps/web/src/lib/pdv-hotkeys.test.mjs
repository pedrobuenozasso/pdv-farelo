import assert from "node:assert/strict";
import test from "node:test";

import { resolvePdvHotkey, verticalNeighborIndex } from "./pdv-hotkeys.ts";

const context = { hasCart: true, hasSelection: false, hasProducts: true };
const key = (name, extra = {}) => ({
  key: name,
  ctrlKey: false,
  metaKey: false,
  typing: false,
  typingEmpty: false,
  ...extra,
});

test("↑/↓ navegam a grade mesmo com texto na busca", () => {
  assert.deepEqual(
    resolvePdvHotkey(key("ArrowDown", { typing: true }), context),
    {
      type: "move-highlight",
      direction: "down",
    },
  );
  assert.deepEqual(
    resolvePdvHotkey(key("ArrowUp", { typing: true }), context),
    {
      type: "move-highlight",
      direction: "up",
    },
  );
});

test("←/→ navegam fora de campo ou com o campo vazio", () => {
  assert.deepEqual(resolvePdvHotkey(key("ArrowRight"), context), {
    type: "move-highlight",
    direction: "right",
  });
  assert.deepEqual(
    resolvePdvHotkey(
      key("ArrowLeft", { typing: true, typingEmpty: true }),
      context,
    ),
    { type: "move-highlight", direction: "left" },
  );
});

test("←/→ ficam com o cursor quando o campo tem texto", () => {
  assert.equal(
    resolvePdvHotkey(key("ArrowLeft", { typing: true }), context),
    null,
  );
  assert.equal(
    resolvePdvHotkey(key("ArrowRight", { typing: true }), context),
    null,
  );
});

test("setas não agem sem produtos; F10/F9 exigem carrinho", () => {
  const empty = { hasCart: false, hasSelection: false, hasProducts: false };
  assert.equal(resolvePdvHotkey(key("ArrowRight"), empty), null);
  assert.equal(resolvePdvHotkey(key("ArrowDown"), empty), null);
  assert.equal(resolvePdvHotkey(key("F10"), empty), null);
  assert.equal(resolvePdvHotkey(key("F9"), empty), null);
  assert.deepEqual(resolvePdvHotkey(key("F10", { typing: true }), context), {
    type: "confirm",
  });
  assert.deepEqual(
    resolvePdvHotkey(key("f", { ctrlKey: true, typing: true }), context),
    {
      type: "focus-search",
    },
  );
});

// Grade 3 colunas, cards 100x80 com 10px de espaço; seção A com 4 cards
// (última linha incompleta), seção B com 2 cards começando abaixo de um título.
const card = (col, top) => ({
  left: col * 110,
  right: col * 110 + 100,
  top,
  bottom: top + 80,
});
const rects = [
  card(0, 0),
  card(1, 0),
  card(2, 0), // 0..2
  card(0, 90), // 3 (linha incompleta)
  card(0, 210),
  card(1, 210), // 4..5 (seção B, após título)
];

test("↓ vai para o card da linha de baixo na mesma coluna", () => {
  assert.equal(verticalNeighborIndex(rects, 0, "down"), 3);
  assert.equal(verticalNeighborIndex(rects, 3, "down"), 4);
  assert.equal(verticalNeighborIndex(rects, 1, "up"), null);
});

test("↓ numa coluna sem card abaixo escolhe o mais próximo da linha seguinte", () => {
  assert.equal(verticalNeighborIndex(rects, 1, "down"), 3);
  assert.equal(verticalNeighborIndex(rects, 2, "down"), 3);
  assert.equal(verticalNeighborIndex(rects, 2, "up"), null);
});

test("↑ volta para a linha de cima, atravessando o título da seção", () => {
  assert.equal(verticalNeighborIndex(rects, 5, "up"), 3);
  assert.equal(verticalNeighborIndex(rects, 3, "up"), 0);
});

test("sem linha na direção devolve null", () => {
  assert.equal(verticalNeighborIndex(rects, 0, "up"), null);
  assert.equal(verticalNeighborIndex(rects, 5, "down"), null);
  assert.equal(verticalNeighborIndex([], 0, "down"), null);
});

test("fora da busca, setas e Enter ficam com o campo (ex.: nome do cliente)", () => {
  const outro = { typing: true, typingEmpty: false, inOtherField: true };
  assert.equal(resolvePdvHotkey(key("Enter", outro), context), null);
  assert.equal(resolvePdvHotkey(key("ArrowDown", outro), context), null);
  assert.equal(resolvePdvHotkey(key("ArrowUp", outro), context), null);
  assert.equal(
    resolvePdvHotkey(
      key("ArrowLeft", { ...outro, typingEmpty: true }),
      context,
    ),
    null,
  );
  // Atalhos de commit e Ctrl+F continuam valendo em qualquer campo.
  assert.deepEqual(resolvePdvHotkey(key("F10", outro), context), {
    type: "confirm",
  });
  assert.deepEqual(
    resolvePdvHotkey(key("f", { ...outro, ctrlKey: true }), context),
    {
      type: "focus-search",
    },
  );
});
