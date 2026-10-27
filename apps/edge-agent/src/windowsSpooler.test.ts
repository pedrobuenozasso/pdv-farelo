import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import {
  sendBinaryToProcess,
  printOverWindowsSpooler,
} from "./windowsSpooler.js";

test("entrega bytes ESC/POS binários sem conversão de texto ao processo filho", async () => {
  const dir = await mkdtemp(path.join(tmpdir(), "farelo-spool-test-"));
  try {
    const output = path.join(dir, "received.bin");
    const ticket = Buffer.from([0x1b, 0x40, 0x00, 0xff, 0x1d, 0x56, 0x00]);
    const code =
      'const fs=require("node:fs");const chunks=[];process.stdin.on("data",x=>chunks.push(x));process.stdin.on("end",()=>fs.writeFileSync(process.argv[1],Buffer.concat(chunks)))';
    await sendBinaryToProcess(
      process.execPath,
      ["-e", code, output],
      process.env,
      ticket,
    );
    assert.deepEqual(await readFile(output), ticket);
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
});

test("não confirma quando o spooler retorna erro", async () => {
  await assert.rejects(
    () =>
      sendBinaryToProcess(
        process.execPath,
        ["-e", 'process.stderr.write("fila indisponível");process.exit(3)'],
        process.env,
        Buffer.from("x"),
      ),
    /Spooler falhou \(3\): fila indisponível/,
  );
});

test("falha fechado fora do Windows", async () => {
  if (process.platform === "win32") return;
  await assert.rejects(
    () => printOverWindowsSpooler("EPSON", Buffer.from("x")),
    /exige Windows/,
  );
});
