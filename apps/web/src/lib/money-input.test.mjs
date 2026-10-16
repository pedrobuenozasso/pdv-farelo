import assert from "node:assert/strict";
import test from "node:test";

import { parseMoneyCents } from "./money-input.ts";

test("payment amounts are parsed as exact cents", () => {
  assert.equal(parseMoneyCents("12"), 1200);
  assert.equal(parseMoneyCents("12,50"), 1250);
  assert.equal(parseMoneyCents("12.5"), 1250);
  assert.equal(parseMoneyCents(" 0,01 "), 1);
  assert.equal(parseMoneyCents("0"), 0);
});

test("payment form rejects ambiguous or fractional-cent amounts", () => {
  for (const value of [
    "",
    "-1",
    "1e3",
    "1,234",
    "1.234,56",
    "NaN",
    "Infinity",
    "1,",
    ".50",
  ]) {
    assert.equal(parseMoneyCents(value), null, value);
  }
  assert.equal(parseMoneyCents("999999999999999"), null);
});
