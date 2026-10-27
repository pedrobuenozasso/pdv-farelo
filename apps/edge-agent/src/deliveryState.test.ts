import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { FileDeliveryJournal, resolveStateDirectory } from "./deliveryState.js";

test("journal sobrevive a reinício e impede preparar o mesmo job duas vezes", async () => {
  const dir = await mkdtemp(path.join(tmpdir(), "farelo-delivery-"));
  try {
    const first = new FileDeliveryJournal(dir);
    await first.prepare("job-1");
    const restarted = new FileDeliveryJournal(dir);
    assert.equal(await restarted.get("job-1"), "PREPARED");
    await assert.rejects(() => restarted.prepare("job-1"), /EEXIST/);
    await restarted.markSpoolAccepted("job-1");
    assert.equal(
      await new FileDeliveryJournal(dir).get("job-1"),
      "SPOOL_ACCEPTED",
    );
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("arquivo parcial/corrompido bloqueia entrega em vez de ser tratado como novo", async () => {
  const dir = await mkdtemp(path.join(tmpdir(), "farelo-delivery-"));
  try {
    const journal = new FileDeliveryJournal(dir);
    await journal.prepare("job-2");
    const [name] = await readdir(dir);
    assert.ok(name);
    await writeFile(path.join(dir, name), "{");
    await assert.rejects(() => journal.get("job-2"));
    await assert.rejects(() => journal.prepare("job-2"), /EEXIST/);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("recuperação exige decisão explícita e conserva apenas ID/estado", async () => {
  const dir = await mkdtemp(path.join(tmpdir(), "farelo-delivery-"));
  try {
    const journal = new FileDeliveryJournal(dir);
    await journal.prepare("job-3");
    const [name] = await readdir(dir);
    assert.ok(name);
    const content = await readFile(path.join(dir, name), "utf8");
    assert.match(content, /"jobId":"job-3"/);
    assert.doesNotMatch(content, /product|customer|phone|ticket/i);
    await journal.recover("job-3", "printed");
    assert.equal(await journal.get("job-3"), "SPOOL_ACCEPTED");
    await assert.rejects(
      () => journal.recover("job-3", "no-paper"),
      /status na API/,
    );
    await journal.prepare("job-4");
    await journal.recover("job-4", "no-paper");
    assert.equal(await journal.get("job-4"), null);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("state directory configurado deve ser absoluto", () => {
  assert.throws(
    () => resolveStateDirectory({ FARELO_EDGE_AGENT_STATE_DIR: "./tmp" }),
    /absoluto/,
  );
});
