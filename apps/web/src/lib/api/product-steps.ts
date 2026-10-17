import { authHeaders, parseResponse } from "./client";

export type ProductStepOption = {
  id: string;
  optionProductId: string;
  optionName: string;
  optionActive: boolean;
  displayOrder: number;
  price: string;
};

export type ProductStep = {
  id: string;
  label: string;
  minChoices: number;
  maxChoices: number;
  displayOrder: number;
  level: string;
  chargeType: string;
  options: ProductStepOption[];
};

export type ProductStepInput = {
  label: string;
  minChoices: number;
  maxChoices: number;
  displayOrder: number;
  level: string;
  chargeType: string;
  options: {
    optionProductId: string;
    displayOrder: number;
    price: string;
  }[];
};

const stepsUrl = (productId: string) => `/api/v1/products/${productId}/steps`;

export async function listProductSteps(
  productId: string,
): Promise<ProductStep[]> {
  const response = await fetch(stepsUrl(productId), {
    headers: authHeaders(),
    cache: "no-store",
  });
  return parseResponse<ProductStep[]>(response);
}

export async function replaceProductSteps(
  productId: string,
  steps: ProductStepInput[],
): Promise<ProductStep[]> {
  const response = await fetch(stepsUrl(productId), {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ steps }),
  });
  return parseResponse<ProductStep[]>(response);
}
