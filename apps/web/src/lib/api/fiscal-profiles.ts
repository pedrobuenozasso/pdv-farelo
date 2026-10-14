import { authHeaders, parseResponse } from "./client";

export type FiscalProfile = {
  id: string;
  name: string;
  description: string | null;
  ncm: string | null;
  cfop: string | null;
  cst: string | null;
  csosn: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

export type FiscalProfileInput = {
  name: string;
  description: string | null;
  ncm: string | null;
  cfop: string | null;
  cst: string | null;
  csosn: string | null;
};

const URL = "/api/v1/fiscal-profiles";

export async function listFiscalProfiles(): Promise<FiscalProfile[]> {
  const response = await fetch(URL, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<FiscalProfile[]>(response);
}

export async function createFiscalProfile(
  input: FiscalProfileInput,
): Promise<FiscalProfile> {
  const response = await fetch(URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<FiscalProfile>(response);
}

export async function updateFiscalProfile(
  id: string,
  input: FiscalProfileInput & { active: boolean },
): Promise<FiscalProfile> {
  const response = await fetch(`${URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<FiscalProfile>(response);
}
