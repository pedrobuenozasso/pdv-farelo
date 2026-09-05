// Client-side session storage for the internal tools (/pdv, /admin, /kds).
//
// Farelo OS's auth (FARELO-120/121/122) is a stateless JWT with no
// server-side session/revocation — the token itself, plus its
// `expiresAt`, is the whole of what "being logged in" means (see
// docs/domain-model.md, seção `security`). So the frontend just holds
// both in localStorage: no cookie, no server round-trip to check
// validity, same "trust the token until it expires or a call 401s"
// model the backend already implements. Guarded by `typeof window` since
// this module is imported from client components only, but Next can
// still evaluate it during SSR/build.

// No React import here, deliberately: this module is pulled into the
// Server Component graph too (via api/client.ts's authHeaders(), used by
// categories.ts/products.ts/commands.ts, which the public customer menu's
// Server Component calls during SSR) — importing a hook like
// `useSyncExternalStore` here breaks that build ("You're importing a
// module that depends on useSyncExternalStore into a React Server
// Component module"), even though these plain functions never call it.
// The `useSession` hook lives in `use-session.ts`, a `"use client"` file
// that imports `getSession` from here instead.

// Type-only import — erased at compile time, so it doesn't reintroduce the
// "hook into a Server Component module" problem described above.
import type { UserRole } from "./api/users";

const TOKEN_KEY = "farelo:auth:token";
const EXPIRES_AT_KEY = "farelo:auth:expiresAt";

export type Session = {
  token: string;
  expiresAt: string;
  // FARELO-304 ("Guardas de rota... conforme role"): decoded from the
  // JWT's own `role` claim (JwtTokenService#issue on the backend), not a
  // second field the login response returns separately — LoginResponse's
  // own javadoc already anticipates this exact move ("a caller who needs
  // their own profile can decode the JWT's claims client-side"). Reading
  // an unverified claim client-side is safe here specifically because it
  // only ever drives which UI a staff member sees (AuthGuard below) —
  // the backend's own @RequireRole checks on every write endpoint remain
  // the real enforcement, same "client-side check is UX only" reasoning
  // AuthGuard's own comment already states for the logged-in/out check.
  role: UserRole | null;
};

// Decodes the JWT payload's `role` claim without verifying the signature
// (verification is meaningless client-side anyway — the browser has no
// way to keep the signing secret; a tampered token would still fail on
// the very next API call's real @RequireRole check). No jwt-decode
// dependency: a JWT payload is just the middle `.`-separated segment,
// base64url-encoded JSON — trivial to decode by hand.
export function decodeRole(token: string): UserRole | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const claims: unknown = JSON.parse(atob(base64));
    if (
      typeof claims === "object" &&
      claims !== null &&
      "role" in claims &&
      typeof (claims as { role: unknown }).role === "string"
    ) {
      return (claims as { role: UserRole }).role;
    }
    return null;
  } catch {
    return null;
  }
}

// Memoized by the raw localStorage strings, not just called fresh every
// time: `getSession` doubles as `useSession`'s `useSyncExternalStore`
// snapshot (see use-session.ts), which requires a snapshot function to
// return the SAME reference across calls when nothing actually changed —
// a plain `{ token, expiresAt }` literal returned fresh every call trips
// React's "getSnapshot should be cached" infinite-loop guard.
let cachedRaw: string | null = null;
let cachedSession: Session | null = null;

export function getSession(): Session | null {
  if (typeof window === "undefined") return null;
  const token = window.localStorage.getItem(TOKEN_KEY);
  const expiresAt = window.localStorage.getItem(EXPIRES_AT_KEY);

  // Expiry is time-dependent, not just a function of the stored strings,
  // so it's re-checked on every call regardless of the cache below —
  // `null` is a stable primitive either way, so this alone doesn't break
  // getSnapshot's "same reference" requirement.
  if (!token || !expiresAt || new Date(expiresAt).getTime() <= Date.now()) {
    if (token || expiresAt) clearSession();
    cachedRaw = null;
    cachedSession = null;
    return null;
  }

  const raw = `${token} ${expiresAt}`;
  if (raw !== cachedRaw) {
    cachedRaw = raw;
    cachedSession = { token, expiresAt, role: decodeRole(token) };
  }
  return cachedSession;
}

export function setSession(session: Session): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(TOKEN_KEY, session.token);
  window.localStorage.setItem(EXPIRES_AT_KEY, session.expiresAt);
}

export function clearSession(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem(EXPIRES_AT_KEY);
}
