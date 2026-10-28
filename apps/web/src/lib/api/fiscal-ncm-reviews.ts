import { authHeaders, parseResponse } from "./client";

export type FiscalNcmReview = {
  productId: string;
  sourceNcm: string | null;
  draftNcm: string | null;
  sourceCest: string | null;
  draftCest: string | null;
  sourceCfop: string | null;
  draftCfop: string | null;
  sourceCsosn: string | null;
  draftCsosn: string | null;
  reviewed: boolean;
  updatedAt: string;
};

const URL = "/api/v1/fiscal-ncm-reviews";

export async function listFiscalNcmReviews(): Promise<FiscalNcmReview[]> {
  const response = await fetch(URL, {
    cache: "no-store",
    headers: authHeaders(),
  });
  return parseResponse<FiscalNcmReview[]>(response);
}

export async function saveFiscalTaxCodesReview(
  productId: string,
  input: { cest: string | null; cfop: string | null; csosn: string | null },
): Promise<FiscalNcmReview> {
  const response = await fetch(`${URL}/${productId}/tax-codes`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<FiscalNcmReview>(response);
}

export async function saveFiscalNcmReview(
  productId: string,
  input: { draftNcm: string | null; reviewed: boolean },
): Promise<FiscalNcmReview> {
  const response = await fetch(`${URL}/${productId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify(input),
  });
  return parseResponse<FiscalNcmReview>(response);
}
