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
