import { authHeaders, parseResponse } from "./client";

export type CommandQr = { number: number; qrToken: string };

export async function listCommandQrTokens(): Promise<CommandQr[]> {
  const response = await fetch("/api/v1/commands/qr", {
    headers: authHeaders(),
    cache: "no-store",
  });
  return parseResponse<CommandQr[]>(response);
}
