import { authHeaders, parseResponse } from "./client";

export type CompanyFiscalConfiguration = {
  id: string;
  cnpj: string;
  legalName: string;
  tradeName: string | null;
  stateRegistration: string | null;
  address: string | null;
  taxRegime?: string | null;
  street?: string | null;
  streetNumber?: string | null;
  district?: string | null;
  cityCode?: string | null;
  city?: string | null;
  postalCode?: string | null;
};

// The API returns 404 only when no configuration has ever been saved.
// Authentication, network, and server failures must remain visible as errors.
export async function getCompanyFiscalConfiguration(): Promise<CompanyFiscalConfiguration | null> {
  const response = await fetch("/api/v1/company-fiscal-configuration", {
    cache: "no-store",
    headers: authHeaders(),
  });
  if (response.status === 404) {
    const body: unknown = await response.json().catch(() => null);
    if (
      body &&
      typeof body === "object" &&
      "code" in body &&
      body.code === "COMPANY_FISCAL_CONFIGURATION_NOT_FOUND"
    ) {
      return null;
    }
  }
  return parseResponse<CompanyFiscalConfiguration>(response);
}
