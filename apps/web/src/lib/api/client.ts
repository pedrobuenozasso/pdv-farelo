// Shared HTTP/error-handling helpers for the /api/v1/* client modules (see
// categories.ts, products.ts). Requests always use a relative path so they
// go through the Next.js rewrite in next.config.ts — the frontend never
// talks to the backend's absolute URL directly (see AGENTS.md / FARELO-018).

/**
 * Backend error body, per AGENTS.md:
 * `{ "code": "...", "message": "...", "correlationId": "..." }`.
 */
export class ApiError extends Error {
  code: string;
  correlationId: string;

  constructor(body: { code: string; message: string; correlationId: string }) {
    super(body.message);
    this.name = "ApiError";
    this.code = body.code;
    this.correlationId = body.correlationId;
  }
}

// Same "is this an ApiError, or something else (network failure, etc.)?"
// ternary that showed up independently in the Admin's category and
// product forms — third occurrence (orders.ts's checkout form), so it
// moved here instead of getting copy-pasted again. Returns null when
// there's no error at all.
export function apiErrorMessage(
  error: unknown,
  fallback: string,
): string | null {
  if (error instanceof ApiError) return error.message;
  if (error) return fallback;
  return null;
}

export async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const body: unknown = await response.json().catch(() => null);
    if (
      body &&
      typeof body === "object" &&
      "message" in body &&
      "code" in body &&
      "correlationId" in body
    ) {
      throw new ApiError(
        body as { code: string; message: string; correlationId: string },
      );
    }
    throw new Error(`Request failed with status ${response.status}`);
  }

  return response.json() as Promise<T>;
}
