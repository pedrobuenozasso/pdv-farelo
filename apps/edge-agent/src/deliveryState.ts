import { createHash } from "node:crypto";
import { promises as fs } from "node:fs";
import os from "node:os";
import path from "node:path";

export type DeliveryState = "PREPARED" | "SPOOL_ACCEPTED";

type RecordOnDisk = {
  version: 1;
  jobId: string;
  state: DeliveryState;
  recordedAt: string;
};

export interface DeliveryJournal {
  get(jobId: string): Promise<DeliveryState | null>;
  prepare(jobId: string): Promise<void>;
  markSpoolAccepted(jobId: string): Promise<void>;
}

/** Diretório estável entre reinícios; nunca use uma pasta temporária. */
export function resolveStateDirectory(
  env: NodeJS.ProcessEnv = process.env,
): string {
  const configured = env.FARELO_EDGE_AGENT_STATE_DIR?.trim();
  if (configured && !path.isAbsolute(configured)) {
    throw new Error(
      "FARELO_EDGE_AGENT_STATE_DIR deve ser um caminho absoluto.",
    );
  }
  return configured || path.join(os.homedir(), ".farelo-os", "print-delivery");
}

function fileName(jobId: string): string {
  if (!jobId) throw new Error("PrintJob id vazio.");
  return `${createHash("sha256").update(jobId).digest("hex")}.json`;
}

export class FileDeliveryJournal implements DeliveryJournal {
  constructor(readonly directory: string) {
    if (!path.isAbsolute(directory))
      throw new Error("Diretório do journal deve ser absoluto.");
  }

  private file(jobId: string): string {
    return path.join(this.directory, fileName(jobId));
  }

  private async read(jobId: string): Promise<RecordOnDisk | null> {
    let raw: string;
    try {
      raw = await fs.readFile(this.file(jobId), "utf8");
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === "ENOENT") return null;
      throw error;
    }
    // Registro parcial/corrompido é erro fechado: jamais tratar como job novo.
    const value: unknown = JSON.parse(raw);
    if (
      typeof value !== "object" ||
      value === null ||
      (value as RecordOnDisk).version !== 1 ||
      (value as RecordOnDisk).jobId !== jobId ||
      !["PREPARED", "SPOOL_ACCEPTED"].includes((value as RecordOnDisk).state)
    ) {
      throw new Error(`Estado de entrega inválido para PrintJob ${jobId}.`);
    }
    return value as RecordOnDisk;
  }

  async get(jobId: string): Promise<DeliveryState | null> {
    return (await this.read(jobId))?.state ?? null;
  }

  async prepare(jobId: string): Promise<void> {
    await fs.mkdir(this.directory, { recursive: true, mode: 0o700 });
    const handle = await fs.open(this.file(jobId), "wx", 0o600);
    try {
      const record: RecordOnDisk = {
        version: 1,
        jobId,
        state: "PREPARED",
        recordedAt: new Date().toISOString(),
      };
      await handle.writeFile(JSON.stringify(record));
      await handle.sync();
    } finally {
      await handle.close();
    }
  }

  async markSpoolAccepted(jobId: string): Promise<void> {
    const current = await this.read(jobId);
    if (!current || current.state !== "PREPARED") {
      throw new Error(`PrintJob ${jobId} não está preparado para impressão.`);
    }
    const temp = `${this.file(jobId)}.${process.pid}.${Date.now()}.tmp`;
    const handle = await fs.open(temp, "wx", 0o600);
    try {
      await handle.writeFile(
        JSON.stringify({ ...current, state: "SPOOL_ACCEPTED" }),
      );
      await handle.sync();
    } finally {
      await handle.close();
    }
    await fs.rename(temp, this.file(jobId));
  }

  /** Somente ferramenta de recuperação explícita, com o agente parado. */
  async recover(jobId: string, outcome: "printed" | "no-paper"): Promise<void> {
    const current = await this.read(jobId);
    if (!current)
      throw new Error(`PrintJob ${jobId} não encontrado no journal.`);
    if (outcome === "printed") {
      if (current.state === "PREPARED") await this.markSpoolAccepted(jobId);
    } else {
      if (current.state !== "PREPARED") {
        throw new Error(
          "Entrega já aceita pelo spooler; consulte o status na API antes de reimprimir.",
        );
      }
      await fs.unlink(this.file(jobId));
    }
  }
}
