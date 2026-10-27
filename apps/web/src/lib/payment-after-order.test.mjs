import assert from "node:assert/strict";
import test from "node:test";

import { freshPaymentAmount } from "./payment-after-order.ts";

test("payment prompt waits for the new server balance, not the old cached amount", async () => {
  let finishBalanceRequest;
  const request = new Promise((resolve) => {
    finishBalanceRequest = resolve;
  });
  const amount = freshPaymentAmount(() => request);

  let settled = false;
  void amount.then(() => {
    settled = true;
  });
  await Promise.resolve();
  assert.equal(settled, false);

  finishBalanceRequest({ remaining: 28 });
  assert.equal(await amount, 28);
});

test("a balance lookup failure cannot mark an already-created order as failed", async () => {
  assert.equal(
    await freshPaymentAmount(() => Promise.reject(new Error("network down"))),
    null,
  );
});
